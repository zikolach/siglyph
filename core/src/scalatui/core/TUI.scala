package scalatui.core

import scalatui.ansi.Ansi
import scalatui.components.{ComponentEffectCoordinator, ScrollView, ViewportSearchState}
import scalatui.syntax.Equality.*
import scalatui.syntax.Containment.*
import scalatui.terminal.{
  KeyEventType,
  KeybindingCommand,
  KeybindingManager,
  ImageCellDimensions,
  RgbColor,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalCapabilities,
  TerminalCapabilityOverrides,
  TerminalCursorProtocol,
  TerminalImageProtocol,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions,
  MouseAction,
  MouseButton,
  MouseButtonState,
  MouseInputContext,
  MouseWheelDirection,
  TerminalInput,
  TerminalInputChunk,
  TerminalKey,
  TerminalUtf8Decoder,
  TerminalRawKind,
  TerminalRawTermination,
  TerminalRenderControl
}

import scalatui.unicode.Unicode

import scala.collection.mutable.ArrayBuffer

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** Terminal screen buffer mode owned by [[TUI]] for one runtime lifecycle. */
enum TUIScreenMode derives CanEqual:
  /**
   * Render in the normal terminal screen. This is the default and preserves existing
   * transcript-like behavior where rendered frames remain in the shell scrollback after exit.
   */
  case Normal

  /**
   * Enter the terminal alternate screen while the TUI is running and exit it during cleanup. This
   * prevents TUI frames from being appended to normal shell scrollback. It does not add temporary
   * modal sessions, a full-screen editor, or height-aware component rendering.
   */
  case Alternate

/**
 * Runtime options for [[TUI]].
 *
 * @param hardwareCursorPositioning
 *   When true, the shared renderer selects the first row-major surviving structured cursor
 *   candidate and moves the terminal hardware cursor there after output. The default is false, so
 *   applications rely on the rendered fake cursor only. This option is backend-independent and
 *   ordinary strings cannot influence cursor placement.
 * @param screenMode
 *   Terminal screen buffer mode for this TUI lifecycle. The default [[TUIScreenMode.Normal]] keeps
 *   existing normal-screen behavior. [[TUIScreenMode.Alternate]] enters the terminal alternate
 *   screen on start and exits it during cleanup without changing the component render contract.
 * @param mouseInput
 *   Enable legacy basic mouse reporting. This remains source-compatible and selects xterm mode
 *   `1000` with SGR coordinates unless `mouseTracking` is set.
 * @param mouseTracking
 *   Optional typed tracking request. Drag selects mode `1002`. All-motion selects mode `1003` only
 *   outside a detected multiplexer unless its instance option permits multiplexer forwarding.
 * @param mouseGestures
 *   Injectable monotonic clock and deterministic click, drag, and multi-click bounds.
 * @param normalResizeClearPolicy
 *   Normal-screen dimension-change policy. The default clears viewport and scrollback for legacy
 *   redraw behavior; preserving scrollback clears and homes only the active viewport and is
 *   required by [[TUI.appendToScrollback]]. Alternate-screen redraw behavior is unchanged.
 * @param diagnosticObserver
 *   Optional instance-scoped observer for redacted structured runtime metadata. Observer failures
 *   are contained and permanently disable that observer without preventing terminal cleanup.
 * @param capabilityOverrides
 *   Typed per-session overrides for true color, OSC 8 hyperlinks, and image protocol. Detection is
 *   retained for unspecified values. Disabled values are a hard ceiling for attached components.
 * @param keybindings
 *   Backend-independent command bindings used by fullscreen viewport routing. Empty user bindings
 *   keep registered commands unbound.
 * @param hostClipboard
 *   Optional portable host callback for explicit fullscreen selection copy. Core reports host
 *   success, unsupported targets, and callback failure without emitting OSC 52.
 * @param kittyImageRetention
 *   Per-TUI count and accepted-generation bounds for fullscreen Kitty offscreen reuse. This option
 *   does not affect normal-screen or append-only image ownership.
 * @param normalResizeRecovery
 *   Optional synchronous provider for reconstructing a bounded durable text tail after a
 *   geometry-changing normal-screen preserve-scrollback resize. Its context includes previous and
 *   current geometry with a strict budget bounded by both viewport capacities. The provider is
 *   retryable Render work and receives no component, cursor, typed-control, or raw-terminal
 *   authority. Configuring it with alternate screen or scrollback-clearing resize policy fails
 *   before terminal startup.
 */
final case class TUIOptions(
    hardwareCursorPositioning: Boolean = false,
    screenMode: TUIScreenMode = TUIScreenMode.Normal,
    mouseInput: Boolean = false,
    normalResizeClearPolicy: NormalResizeClearPolicy = NormalResizeClearPolicy.ClearScrollback,
    diagnosticObserver: Option[TUIDiagnosticObserver] = None,
    capabilityOverrides: TerminalCapabilityOverrides = TerminalCapabilityOverrides(),
    mouseTracking: Option[TerminalMouseTrackingOptions] = None,
    mouseGestures: MouseGestureOptions = MouseGestureOptions(),
    keybindings: KeybindingManager = KeybindingManager(),
    hostClipboard: Option[HostClipboard] = None,
    kittyImageRetention: KittyImageRetentionOptions = KittyImageRetentionOptions(),
    normalResizeRecovery: Option[NormalResizeRecoveryProvider] = None
) derives CanEqual

object TUIOptions:
  /** Preserve the original five-argument positional constructor. */
  def apply(
      hardwareCursorPositioning: Boolean,
      screenMode: TUIScreenMode,
      mouseInput: Boolean,
      normalResizeClearPolicy: NormalResizeClearPolicy,
      diagnosticObserver: Option[TUIDiagnosticObserver]
  ): TUIOptions = new TUIOptions(
    hardwareCursorPositioning,
    screenMode,
    mouseInput,
    normalResizeClearPolicy,
    diagnosticObserver
  )

  /** Preserve the original five-field extractor shape. */
  def unapply(
      options: TUIOptions
  ): Some[(
      Boolean,
      TUIScreenMode,
      Boolean,
      NormalResizeClearPolicy,
      Option[TUIDiagnosticObserver]
  )] =
    Some((
      options.hardwareCursorPositioning,
      options.screenMode,
      options.mouseInput,
      options.normalResizeClearPolicy,
      options.diagnosticObserver
    ))

/**
 * Main JVM and Scala Native terminal runtime with a synchronous, single-owner work drain.
 *
 * Direct construction selects normal-screen rendering unless [[TUIOptions.screenMode]] requests the
 * legacy width-only alternate screen. Use [[TUI.fullscreen]] for fixed-height layout. Application
 * callbacks run without component-state, lifecycle, or terminal-output locks. Backend output uses a
 * separate write boundary. One per-lifecycle coordinator admits at most 4096 queued component
 * effect batches plus one executing batch. State commit and admission share one order across
 * attached components. Reentrant and concurrent publishers do not wait for callback completion.
 * Stopping rejects new external batches, drains the finite accepted prefix, and bounds
 * runtime-owned context teardown before terminal cleanup. Unexpected active terminal-worker or
 * component-effect failure enters lifecycle control directly, wakes [[run]], restores terminal
 * state, and is rethrown after cleanup.
 */
final class TUI(
    val terminal: Terminal,
    val options: TUIOptions = TUIOptions(),
    private[core] val viewportRoot: Option[Component] = None
) extends TUIContext,
      OverlayHost:
  private val root                                                      = Container()
  private val lifecycleLock                                             = Object()
  override val terminalCapabilityOverrides: TerminalCapabilityOverrides =
    options.capabilityOverrides
  private var currentTerminalCapabilities                               = TerminalCapabilities.Conservative

  override def terminalCapabilities: TerminalCapabilities =
    lifecycleLock.synchronized(currentTerminalCapabilities)
  private val diagnosticLock                              = Object()
  private val overlayStack                                = ArrayBuffer.empty[TUI.OverlayEntry]
  private val pendingIngress                              = scala.collection.mutable.ArrayDeque.empty[TUI.Ingress]
  private var replayContinuation                          = Option.empty[TUI.ReplayContinuation]
  private val retainedQueryCompletions                    = ArrayBuffer.empty[TUI.QueryCompletion[?]]
  private val postRestorationQueryCompletions             = ArrayBuffer.empty[TUI.QueryCompletion[?]]
  private val pendingAppendCompletions                    = ArrayBuffer.empty[TUI.AppendCompletion]
  private val retainedAppendCompletions                   = ArrayBuffer.empty[TUI.AppendCompletion]
  private val postRestorationAppendCompletions            = ArrayBuffer.empty[TUI.AppendCompletion]
  private val pendingAppends                              = ArrayBuffer.empty[TUI.AppendOperation]
  private val appendOwnedKittyIds                         = scala.collection.mutable.HashSet.empty[Int]
  private var acceptedIncompleteAppends                   = 0
  private var nextAppendId                                = 0L
  private var activeAppend                                = Option.empty[TUI.AppendOperation]
  private var postRestorationCutoff                       = false
  private[scalatui] val testRuntimeCounters               = RuntimeCounters()

  private val pendingActions                                                                 = ArrayBuffer.empty[() => Unit]
  private val pendingControlOutput                                                           = ArrayBuffer.empty[() => Unit]
  private val pendingStructural                                                              = ArrayBuffer.empty[TUI.StructuralOperation]
  private var desiredChildren                                                                = Vector.empty[TUI.ChildEntry]
  private var committedChildren                                                              = Vector.empty[TUI.ChildEntry]
  private var nextChildEntryId                                                               = 0L
  private var lifecycleState: TUI.LifecycleState                                             = TUI.LifecycleState.Stopped
  private var startupOwner                                                                   = false
  private var drainOwned                                                                     = false
  private val drainOwnerMarker                                                               = new ThreadLocal[Boolean]:
    override def initialValue(): Boolean = false
  private var lastOrdinaryCategory                                                           = TUI.OrdinaryCategory.Render
  private var cleanupOwned                                                                   = false
  private var contextDetachScheduled                                                         = false
  private var resizeGeneration                                                               = 0L
  @volatile private var latestOverlayVisibility                                              = false
  private var queryWriteReservations                                                         = 0
  private var nextQueryFlightId                                                              = 0L
  private var nextQuerySubscriberId                                                          = 0L
  private var latestBaseLayout                                                               = Option.empty[LayoutNode]
  private var latestOverlayLayouts                                                           = Vector.empty[TUI.OverlayLayout]
  private var mouseCapture                                                                   = Option.empty[TUI.MouseTarget]
  private var mousePress                                                                     = Option.empty[TUI.MousePress]
  private var lastMouseClick                                                                 = Option.empty[TUI.MouseClick]
  private var focusedComponent: Option[Component]                                            = None
  private var baseFocusedComponent: Option[Component]                                        = None
  private var exitRequested                                                                  = false
  private var renderRequested                                                                = false
  private var forceRenderRequested                                                           = false
  private var clearRequested                                                                 = false
  private var pendingResizeRecoveryGeneration                                                = Option.empty[Long]
  private var runtimeFailure                                                                 = Option.empty[Throwable]
  override private[scalatui] lazy val componentEffectCoordinator: ComponentEffectCoordinator =
    ComponentEffectCoordinator.runtime(
      () => drainOrReturn(deferRender = true),
      error => recordFailure(error)
    )
  private var nextOverlayId                                                                  = 0L
  private var nextFocusOrder                                                                 = 0L
  private var backgroundColorFlight                                                          = Option.empty[TUI.QueryFlight[RgbColor]]
  private var colorSchemeFlight                                                              = Option.empty[TUI.QueryFlight[TerminalColorScheme]]
  private var cursorPositionFlight                                                           =
    Option.empty[TUI.QueryFlight[TerminalCursorProtocol.CursorPosition]]
  private var rawCorrelation                                                                 = Option.empty[TUI.RawCorrelation]
  private val terminalColorSchemeListeners                                                   = ArrayBuffer.empty[TerminalColorScheme => Unit]
  private var terminalColorSchemeNotificationsEnabled                                        = false
  private val inputListeners                                                                 = ArrayBuffer.empty[TerminalInput => InputResult]
  private var currentImageCellDimensions                                                     = TerminalImageProtocol.DefaultCellDimensions
  private var viewportRootContextAttached                                                    = false
  private val searchPasteDecoder                                                             = TerminalUtf8Decoder()
  private val searchPasteBuffer                                                              = StringBuilder()
  private var searchPasteActive                                                              = false
  @volatile private var currentClipboardResult                                               = Option.empty[ClipboardCopyResult]
  @volatile private var diagnosticObserver                                                   = options.diagnosticObserver
  private val terminalServices                                                               = RuntimeTerminalServices(
    terminal,
    testRuntimeCounters,
    emitWriteDiagnostic
  )
  private val rendererPolicy: RendererPolicy                                                 = viewportRoot.fold[RendererPolicy](
    NormalScreenPolicy(
      terminal,
      options,
      testRuntimeCounters,
      terminalServices,
      emitRedrawDiagnostic
    )
  )(_ =>
    FullscreenViewportPolicy(
      terminal,
      options,
      testRuntimeCounters,
      terminalServices,
      emitRedrawDiagnostic
    )
  )

  var handlesControlC: Boolean = true
  var exitsOnEscape: Boolean   = false

  override def imageCellDimensions: ImageCellDimensions =
    lifecycleLock.synchronized(currentImageCellDimensions)

  def addChild(component: Component): Unit =
    publishStructural { () =>
      nextChildEntryId += 1
      val entry = TUI.ChildEntry(nextChildEntryId, component)
      desiredChildren :+= entry
      TUI.StructuralOperation.Add(entry)
    }

  def removeChild(component: Component): Unit =
    publishStructural { () =>
      desiredChildren.indexWhere(_.component eq component) match
        case -1    => TUI.StructuralOperation.NoOp
        case index =>
          val entry = desiredChildren(index)
          desiredChildren = desiredChildren.patch(index, Nil, 1)
          TUI.StructuralOperation.Remove(entry.id)
    }

  def clear(): Unit =
    publishStructural { () =>
      val ids = desiredChildren.map(_.id).toSet
      desiredChildren = Vector.empty
      TUI.StructuralOperation.Clear(ids)
    }

  def children: Vector[Component] = lifecycleLock.synchronized(desiredChildren.map(_.component))

  /** Current primary-transcript search state for a fullscreen viewport. */
  def viewportSearchState: Option[ViewportSearchState] = primaryScrollView.map(_.searchState)

  /** Current bounded primary-transcript selection, if it contains selectable plain text. */
  def viewportSelection: Option[ViewportSelection] = primaryScrollView.flatMap(_.selection)

  /** Support state for one explicit clipboard target in this TUI session. */
  def clipboardSupport(target: ClipboardTarget): ClipboardTargetSupport = target match
    case ClipboardTarget.Host =>
      if options.hostClipboard.nonEmpty then ClipboardTargetSupport.Supported
      else ClipboardTargetSupport.Unsupported

  /** Most recent result produced by a copy-selection command. */
  def lastClipboardCopyResult: Option[ClipboardCopyResult] = currentClipboardResult

  /**
   * Copy the bounded plain-text primary selection to one explicit target.
   *
   * The host callback runs without component-state, lifecycle, or terminal-output locks. OSC 52 is
   * unsupported and no terminal sequence is emitted by this operation.
   */
  def copySelection(target: ClipboardTarget = ClipboardTarget.Host): ClipboardCopyResult =
    val result = target match
      case ClipboardTarget.Host => options.hostClipboard match
          case None       => ClipboardCopyResult.Unsupported(target)
          case Some(host) => viewportSelection match
              case None        => ClipboardCopyResult.Failure(target)
              case Some(value) =>
                try
                  if host.copy(value.text) then ClipboardCopyResult.Success(target)
                  else ClipboardCopyResult.Failure(target)
                catch case error: Throwable => ClipboardCopyResult.Failure(target, Some(error))
    currentClipboardResult = Some(result)
    result

  /** Clear the current primary-transcript selection. */
  def clearSelection(): Unit = primaryScrollView.foreach(_.clearSelection())

  /** Number of final rendered lines sanitized because they exceeded terminal width. */
  def sanitizedLineCount: Int = lifecycleLock.synchronized(rendererPolicy.sanitizationCount)

  /** Most recent final rendered line sanitization diagnostic, if any occurred. */
  def lastSanitizedLine: Option[TUI.RenderSanitization] =
    lifecycleLock.synchronized(rendererPolicy.lastSanitization)

  /**
   * Set the terminal window title when the backend supports title operations.
   *
   * Unsupported terminals return `false` and emit no title sequence. Control characters are removed
   * before the backend writes the title protocol sequence. During start or running, `true` means
   * non-discardable output was accepted and will precede any later cleanup. Stopping rejects it;
   * stopped runtimes apply supported output directly.
   */
  def setTerminalTitle(title: String): Boolean =
    scheduleControlOutput(terminal.isInstanceOf[scalatui.terminal.TerminalTitleSupport]) {
      terminalServices.write(Terminal.setTitle(terminal, title))
      ()
    }

  /**
   * Set terminal progress state when the backend supports progress operations.
   *
   * Unsupported terminals return `false`. Active runtimes serialize accepted non-discardable output
   * before later cleanup; stopping rejects it and stopped runtimes apply it directly.
   */
  def setTerminalProgress(active: Boolean): Boolean =
    scheduleControlOutput(terminal.isInstanceOf[scalatui.terminal.TerminalProgressSupport]) {
      terminalServices.write(Terminal.setProgress(terminal, active))
      ()
    }

  /**
   * Query the terminal default background color using OSC 11.
   *
   * Subscribers share one terminal request and complete in subscription order on the runtime drain.
   * Completion may occur before this method returns. The returned cancellation is idempotent and
   * silent; callers own any timeout scheduling. No request is emitted outside `Running`. In
   * `Stopped`, `Stopped` completion is invoked synchronously because no runtime owner exists.
   */
  def queryTerminalBackgroundColor(
      onComplete: TerminalQueryResult[RgbColor] => Unit
  ): () => Unit =
    subscribeQuery(
      () => backgroundColorFlight,
      flight => backgroundColorFlight = flight,
      TerminalColorProtocol.BackgroundColorQuery,
      onComplete
    )

  /**
   * Query the terminal color scheme using DSR `CSI ? 996 n`.
   *
   * Subscribers share one terminal request and complete in subscription order on the runtime drain.
   * Completion may occur before this method returns. The returned cancellation is idempotent and
   * silent; callers own any timeout scheduling. No request is emitted outside `Running`. In
   * `Stopped`, `Stopped` completion is invoked synchronously because no runtime owner exists.
   */
  def queryTerminalColorScheme(
      onComplete: TerminalQueryResult[TerminalColorScheme] => Unit
  ): () => Unit =
    subscribeQuery(
      () => colorSchemeFlight,
      flight => colorSchemeFlight = flight,
      TerminalColorProtocol.ColorSchemeQuery,
      onComplete
    )

  /**
   * Subscribe to terminal color-scheme reports. Listeners run on the work drain without the
   * lifecycle lock. Returns a function that removes the listener.
   */
  def onTerminalColorSchemeChange(listener: TerminalColorScheme => Unit): () => Unit =
    lifecycleLock.synchronized(terminalColorSchemeListeners += listener)
    () => lifecycleLock.synchronized(terminalColorSchemeListeners -= listener)

  /**
   * Enable or disable terminal color-scheme notifications.
   *
   * When the TUI is running, this writes the terminal notification enable or disable sequence. When
   * not running, the setting is applied on the next start. Unsupported terminals can ignore the
   * sequence; incoming color-scheme reports are still consumed before component input routing.
   */
  def setTerminalColorSchemeNotifications(enabled: Boolean): Unit =
    val publish = lifecycleLock.synchronized {
      if terminalColorSchemeNotificationsEnabled === enabled then false
      else
        terminalColorSchemeNotificationsEnabled = enabled
        lifecycleState === TUI.LifecycleState.Running
    }
    if publish then
      scheduleControlOutput(supported = true) {
        terminalServices.writeData(
          if enabled then TerminalColorProtocol.EnableColorSchemeNotifications
          else TerminalColorProtocol.DisableColorSchemeNotifications,
          TUIDiagnosticWriteKind.Control
        )
      }

  override def setFocus(component: Component | Null): Unit =
    publishAction(() => setFocusNow(component))

  private def setFocusNow(component: Component | Null): Unit =
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = false
      case _                    => ()
    }
    focusedComponent = Option(component)
    if !focusedComponent.exists(isOverlayComponent) then baseFocusedComponent = focusedComponent
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = true
      case _                    => ()
    }

  override def overlays: OverlayHost = this

  override def showOverlay(
      component: Component,
      options: OverlayOptions = OverlayOptions()
  ): OverlayHandle =
    val id    = lifecycleLock.synchronized {
      nextOverlayId += 1
      OverlayId(nextOverlayId)
    }
    val entry = TUI.OverlayEntry(
      id = id,
      component = component,
      options = options,
      preFocus = None,
      hidden = false,
      focusOrder = 0L
    )
    publishAction(() => {
      entry.preFocus = focusedComponent
      nextFocusOrder += 1
      entry.focusOrder = nextFocusOrder
      attachContext(component)
      overlayStack += entry
      if entry.options.focusCapturing && isOverlayVisible(entry) then focusOverlay(entry)
      latestOverlayVisibility = overlayStack.exists(isOverlayVisible)
      requestRender()
    })
    makeOverlayHandle(entry)

  override def hideOverlay(): Unit = publishAction(() => topVisibleOverlay.foreach(removeOverlay))

  override def hasOverlay: Boolean = latestOverlayVisibility

  /**
   * Register a typed global input listener.
   *
   * Listeners run before focused component routing. `InputResult.Ignored` lets routing continue to
   * the focused component. Handled or exit results stop routing for that input. The returned
   * function removes this listener.
   */
  def addInputListener(listener: TerminalInput => InputResult): () => Unit =
    lifecycleLock.synchronized(inputListeners += listener)
    () => removeInputListener(listener)

  /** Remove a previously registered typed global input listener. */
  def removeInputListener(listener: TerminalInput => InputResult): Unit =
    lifecycleLock.synchronized(inputListeners -= listener)

  def start(): Unit =
    validateNormalResizeRecoveryOptions()
    var reattachRetainedContexts = false
    val shouldStart              = lifecycleLock.synchronized {
      if (lifecycleState !== TUI.LifecycleState.Stopped) || drainOwned then false
      else
        componentEffectCoordinator.openGeneration()
        reattachRetainedContexts = contextDetachScheduled
        contextDetachScheduled = false
        exitRequested = false
        runtimeFailure = None
        currentTerminalCapabilities = TerminalCapabilities.resolve(
          TerminalCapabilities.detect(),
          terminalCapabilityOverrides
        )
        lifecycleState = TUI.LifecycleState.Starting
        startupOwner = true
        true
    }
    if shouldStart then
      emitDiagnostic(TUIDiagnosticEvent.Lifecycle(
        TUIDiagnosticLifecycleState.Starting,
        options.screenMode
      ))
      try
        val contextsToAttach = lifecycleLock.synchronized {
          if reattachRetainedContexts then retainedContextComponentsLocked()
          else viewportRoot.filter(_ => !viewportRootContextAttached).toVector
        }
        componentEffectCoordinator.withLifecycleAdmission {
          contextsToAttach.foreach(attachContext)
          viewportRootContextAttached = viewportRoot.nonEmpty
        }
        Terminal.setMouseTracking(terminal, effectiveMouseTrackingMode)
        terminalServices.write(terminal.start(
          input => safeRuntimeCallback(publishInput(input)),
          () => safeRuntimeCallback(publishResize()),
          error => handleRuntimeFailure(error)
        ))
        if startupMayContinue then rendererPolicy.start()
        if startupMayContinue && terminalColorSchemeNotificationsEnabled then
          terminalServices.writeData(
            TerminalColorProtocol.EnableColorSchemeNotifications,
            TUIDiagnosticWriteKind.Control
          )
        if startupMayContinue then
          lifecycleLock.synchronized {
            currentImageCellDimensions = TerminalImageProtocol.DefaultCellDimensions
          }
          terminalServices.write(terminal.hideCursor())
          terminalServices.writeData(
            TerminalImageProtocol.QueryCellDimensions,
            TUIDiagnosticWriteKind.Protocol
          )
          if !mouseInputEnabled then
            requestRenderInternal(force = true, clear = rendererPolicy.isAlternateScreen)
        val enteredRunning   = lifecycleLock.synchronized {
          startupOwner = false
          if lifecycleState === TUI.LifecycleState.Starting then
            lifecycleState = TUI.LifecycleState.Running
            true
          else false
        }
        if enteredRunning then
          emitDiagnostic(TUIDiagnosticEvent.Lifecycle(
            TUIDiagnosticLifecycleState.Running,
            options.screenMode
          ))
        if mouseInputEnabled && lifecycleLock.synchronized(
            lifecycleState === TUI.LifecycleState.Running
          )
        then
          if rendererPolicy.isFullscreenViewport then
            requestRenderInternal(force = true, clear = true)
          else initializeMouseFrameOrigin()
        drainOrReturn()
        finishDeferredCleanupIfNeeded()
        runtimeFailure.foreach(throw _)
      catch
        case e: Throwable =>
          recordFailure(e)
          lifecycleLock.synchronized { startupOwner = false }
          try finishDeferredCleanupIfNeeded()
          catch case cleanupFailure: Throwable => e.addSuppressed(cleanupFailure)
          throw runtimeFailure.getOrElse(e)

  private def validateNormalResizeRecoveryOptions(): Unit =
    options.normalResizeRecovery.foreach { _ =>
      if rendererPolicy.isAlternateScreen then
        throw IllegalArgumentException(
          "Normal resize recovery requires normal-screen mode"
        )
      if options.normalResizeClearPolicy !== NormalResizeClearPolicy.PreserveScrollback then
        throw IllegalArgumentException(
          "Normal resize recovery requires preserve-scrollback resize policy"
        )
    }

  /** Start, wait for exit, and return only after deferred cleanup reaches `Stopped`. */
  def run(): Unit =
    try
      start()
      lifecycleLock.synchronized {
        while lifecycleState === TUI.LifecycleState.Running && !exitRequested do
          lifecycleLock.wait()
      }
    finally stop()
    lifecycleLock.synchronized {
      while lifecycleState !== TUI.LifecycleState.Stopped do lifecycleLock.wait()
    }
    runtimeFailure.foreach(throw _)

  override def requestExit(): Unit = lifecycleLock.synchronized {
    exitRequested = true
    lifecycleLock.notifyAll()
  }

  /**
   * Request terminal restoration. An uncontended caller performs cleanup synchronously. A reentrant
   * or concurrent caller marks `Stopping` and returns without waiting; the active startup or drain
   * owner completes single-owner cleanup. Repeated calls do not duplicate restoration.
   */
  def stop(): Unit =
    val (own, transitioned) = lifecycleLock.synchronized {
      lifecycleState match
        case TUI.LifecycleState.Stopped | TUI.LifecycleState.Stopping |
            TUI.LifecycleState.Cleaning => false -> false
        case _ =>
          transitionToStoppingLocked()
          (!startupOwner && !drainOwned && queryWriteReservations === 0) -> true
    }
    if transitioned then
      emitDiagnostic(TUIDiagnosticEvent.Lifecycle(
        TUIDiagnosticLifecycleState.Stopping,
        options.screenMode
      ))
    if own then finishDeferredCleanupIfNeeded(propagateCleanupFailure = true)

  override def requestRender(force: Boolean = false): Unit =
    requestRenderInternal(force = force, clear = false)

  private def requestRenderInternal(force: Boolean, clear: Boolean): Unit =
    lifecycleLock.synchronized {
      if lifecycleState === TUI.LifecycleState.Starting ||
        lifecycleState === TUI.LifecycleState.Running
      then
        if force then
          forceRenderRequested = true
        if clear then clearRequested = true
        renderRequested = true
    }

  override def flushRender(): Unit = drainOrReturn()

  /**
   * Append one detached component above the retained normal-screen frame.
   *
   * Admission requires a running normal-screen TUI configured with
   * [[NormalResizeClearPolicy.PreserveScrollback]] and a committed frame. The callback is invoked
   * exactly once through the serialized runtime owner and may run before this method returns when
   * draining is uncontended. Reentrant and concurrent [[flushRender]] calls remain non-waiting;
   * applications use this callback as the operation-completion boundary.
   */
  def appendToScrollback(
      component: Component,
      onComplete: AppendResult => Unit = _ => ()
  ): Unit =
    val publication = lifecycleLock.synchronized {
      nextAppendId += 1
      val requestId = nextAppendId
      appendAdmissionRejectionLocked(component) match
        case Some(reason) =>
          val completion = TUI.AppendCompletion(
            onComplete,
            AppendResult.Rejected(reason),
            Some(requestId),
            TUIDiagnosticAppendOutcome.Rejected,
            Some(diagnosticFailure(reason)),
            rowCount = 0,
            controlCount = 0
          )
          if drainOwnerMarker.get() then
            TUI.AppendPublication.Direct(completion)
          else
            while acceptsIngress && pendingIngress.length >= TUI.IngressCapacity do
              lifecycleLock.wait()
            if acceptsIngress then pendingIngress += TUI.Ingress.AppendCompletion(completion)
            else queueAppendCompletionLocked(completion)
            val own = !drainOwned && !startupOwner
            if own then drainOwned = true
            TUI.AppendPublication.Queued(own)
        case None         =>
          val operation = TUI.AppendOperation(
            requestId,
            component,
            onComplete,
            TUI.AppendViolationLatch()
          )
          pendingAppends += operation
          acceptedIncompleteAppends += 1
          TUI.AppendPublication.Queued(own = false)
    }
    publication match
      case TUI.AppendPublication.Direct(completion) => processAppendCompletion(completion)
      case TUI.AppendPublication.Queued(true)       => drainWork(propagateCleanupFailure = false)
      case TUI.AppendPublication.Queued(false)      => drainOrReturn()
  private def appendAdmissionRejectionLocked(component: Component): Option[AppendRejection] =
    if lifecycleState !== TUI.LifecycleState.Running then
      Some(AppendRejection.LifecycleUnavailable(diagnosticLifecycleState(lifecycleState)))
    else if rendererPolicy.isAlternateScreen then Some(AppendRejection.AlternateScreen)
    else if options.normalResizeClearPolicy !== NormalResizeClearPolicy.PreserveScrollback then
      Some(AppendRejection.ScrollbackClearingResizePolicy)
    else if rendererPolicy.retainedFrame.isEmpty then Some(AppendRejection.NoCommittedFrame)
    else if retainedFrameContainsITerm2(rendererPolicy.retainedFrame.get) then
      Some(AppendRejection.RetainedITerm2Control)
    else if isAttachedForAppend(component) then Some(AppendRejection.AttachedComponent)
    else if acceptedIncompleteAppends >= TUI.AppendCapacity then
      Some(AppendRejection.QueueCapacityExceeded)
    else None

  private def diagnosticLifecycleState(
      state: TUI.LifecycleState
  ): TUIDiagnosticLifecycleState = state match
    case TUI.LifecycleState.Starting => TUIDiagnosticLifecycleState.Starting
    case TUI.LifecycleState.Running  => TUIDiagnosticLifecycleState.Running
    case TUI.LifecycleState.Stopping => TUIDiagnosticLifecycleState.Stopping
    case TUI.LifecycleState.Cleaning => TUIDiagnosticLifecycleState.Cleaning
    case TUI.LifecycleState.Stopped  => TUIDiagnosticLifecycleState.Stopped

  private def queueAppendCompletionLocked(completion: TUI.AppendCompletion): Unit =
    lifecycleState match
      case TUI.LifecycleState.Cleaning =>
        if postRestorationCutoff then retainedAppendCompletions += completion
        else postRestorationAppendCompletions += completion
      case TUI.LifecycleState.Stopping =>
        val index = completion.order.flatMap(order =>
          retainedAppendCompletions.indexWhere(_.order.exists(_ > order)) match
            case -1    => None
            case value => Some(value)
        )
        index.fold(retainedAppendCompletions += completion)(
          retainedAppendCompletions.insert(_, completion)
        )
      case _                           => pendingAppendCompletions += completion

  private def queueAcceptedAppendCompletionLocked(completion: TUI.AppendCompletion): Unit =
    if lifecycleState === TUI.LifecycleState.Stopping then
      val index = retainedAppendCompletions.indexWhere(existing =>
        existing.order.exists(_ > completion.order.get)
      )
      if index < 0 then retainedAppendCompletions += completion
      else retainedAppendCompletions.insert(index, completion)
    else queueAppendCompletionLocked(completion)

  private def isAttachedForAppend(component: Component): Boolean =
    committedChildren.exists(_.component eq component) ||
      desiredChildren.exists(_.component eq component) ||
      overlayStack.exists(_.component eq component) ||
      latestBaseLayout.exists(layoutContainsComponent(_, component)) ||
      latestOverlayLayouts.exists(layout => layoutContainsComponent(layout.node, component))

  private def layoutContainsComponent(node: LayoutNode, component: Component): Boolean =
    (node.component eq component) || node.children.exists(layoutContainsComponent(_, component))

  private def layoutOwnerContains(
      node: LayoutNode,
      owner: Component,
      target: Component
  ): Boolean =
    if node.component eq owner then layoutContainsComponent(node, target)
    else node.children.exists(layoutOwnerContains(_, owner, target))

  private def componentOwnsMouseTarget(owner: Component, target: Component): Boolean =
    (owner eq target) || latestBaseLayout.exists(layoutOwnerContains(_, owner, target)) ||
      latestOverlayLayouts.exists(layout => layoutOwnerContains(layout.node, owner, target))

  private def clearMouseStateFor(component: Component): Unit =
    if mouseCapture.exists(target => componentOwnsMouseTarget(component, target.component)) then
      mouseCapture = None
    if mousePress.exists(press => componentOwnsMouseTarget(component, press.target.component)) then
      mousePress = None
    if lastMouseClick.exists(click => componentOwnsMouseTarget(component, click.target.component))
    then
      lastMouseClick = None

  private def retainedFrameContainsITerm2(frame: PreparedFrame): Boolean =
    frame.controls.exists(_.control.details match
      case _: scalatui.terminal.TerminalRenderControlDetails.ITerm2Image => true
      case _                                                             => false)

  private def handleInput(input: TerminalInput): Unit =
    if isMouseInputDisabled(input) then ()
    else if routeGlobalInputListeners(input) !== InputResult.Ignored then ()
    else
      routeViewportInput(input) match
        case Some(result) if result !== InputResult.Ignored => handleInputResult(result)
        case Some(_)                                        => handleUnroutedInput(input, routeTarget = false)
        case None                                           => handleUnroutedInput(input, routeTarget = true)

  private def handleUnroutedInput(input: TerminalInput, routeTarget: Boolean): Unit =
    if isIgnoredKeyRelease(input) then ()
    else if handlesControlC && isCtrl(input, "c") then requestExit()
    else if exitsOnEscape && (input === TerminalInput.Key(TerminalKey.Escape)) then requestExit()
    else
      input match
        case mouse: TerminalInput.Mouse => routeMouseInput(mouse)
        case _ if routeTarget           =>
          inputTarget.map(_.handleInputResult(input)).foreach(handleInputResult)
        case _                          => ()

  private def isMouseInputDisabled(input: TerminalInput): Boolean = input match
    case _: TerminalInput.Mouse => !mouseInputEnabled
    case _                      => false

  private def mouseInputEnabled: Boolean =
    effectiveMouseTrackingMode !== TerminalMouseTrackingMode.Disabled

  private def effectiveMouseTrackingMode: TerminalMouseTrackingMode =
    TerminalMouseTrackingOptions.resolve(options.mouseInput, options.mouseTracking)

  private def subscribeQuery[A](
      getFlight: () => Option[TUI.QueryFlight[A]],
      setFlight: Option[TUI.QueryFlight[A]] => Unit,
      request: String,
      onComplete: TerminalQueryResult[A] => Unit
  ): () => Unit =
    val registration = lifecycleLock.synchronized {
      nextQuerySubscriberId += 1
      val subscriber = TUI.QuerySubscriber(nextQuerySubscriberId, onComplete)
      lifecycleState match
        case TUI.LifecycleState.Running                                =>
          getFlight() match
            case Some(flight) =>
              setFlight(Some(flight.copy(subscribers = flight.subscribers :+ subscriber)))
              TUI.QueryRegistration.Registered(subscriber, None)
            case None         =>
              nextQueryFlightId += 1
              val flight = TUI.QueryFlight(
                nextQueryFlightId,
                TUI.QueryFlightPhase.Reserved,
                Vector(subscriber)
              )
              setFlight(Some(flight))
              queryWriteReservations += 1
              TUI.QueryRegistration.Registered(subscriber, Some(flight.id))
        case TUI.LifecycleState.Starting | TUI.LifecycleState.Stopping =>
          retainedQueryCompletions += TUI.QueryCompletion(
            subscriber,
            TerminalQueryResult.Stopped
          )
          TUI.QueryRegistration.Registered(subscriber, None)
        case TUI.LifecycleState.Cleaning                               =>
          val completion = TUI.QueryCompletion(subscriber, TerminalQueryResult.Stopped)
          if postRestorationCutoff then retainedQueryCompletions += completion
          else postRestorationQueryCompletions += completion
          TUI.QueryRegistration.Registered(subscriber, None)
        case TUI.LifecycleState.Stopped                                =>
          retainedQueryCompletions += TUI.QueryCompletion(
            subscriber,
            TerminalQueryResult.Stopped
          )
          val own = !drainOwned
          if own then drainOwned = true
          TUI.QueryRegistration.QueuedStopped(subscriber, own)
    }
    registration match
      case TUI.QueryRegistration.Registered(subscriber, flightId) =>
        flightId.foreach(id => emitQueryRequest(id, getFlight, setFlight, request))
        drainOrReturn()
        () => cancelQuerySubscriber(subscriber)
      case TUI.QueryRegistration.QueuedStopped(subscriber, own)   =>
        if own then drainWork(propagateCleanupFailure = false)
        () => cancelQuerySubscriber(subscriber)

  private def emitQueryRequest[A](
      flightId: Long,
      getFlight: () => Option[TUI.QueryFlight[A]],
      setFlight: Option[TUI.QueryFlight[A]] => Unit,
      request: String
  ): Unit =
    try
      terminalServices.writeData(request, TUIDiagnosticWriteKind.Protocol)
      lifecycleLock.synchronized {
        getFlight() match
          case Some(flight) if flight.id === flightId =>
            if lifecycleState === TUI.LifecycleState.Stopping then
              retainQueryCompletionsLocked(flight.subscribers, TerminalQueryResult.Stopped)
              setFlight(None)
            else
              setFlight(Some(flight.copy(phase = TUI.QueryFlightPhase.Emitted)))
          case _                                      => ()
        queryWriteReservations -= 1
        lifecycleLock.notifyAll()
      }
    catch
      case error: Throwable =>
        lifecycleLock.synchronized {
          getFlight() match
            case Some(flight) if flight.id === flightId =>
              retainQueryCompletionsLocked(
                flight.subscribers,
                TerminalQueryResult.Failed(error)
              )
              setFlight(None)
            case _                                      => ()
          queryWriteReservations -= 1
          recordFailureLocked(error)
          lifecycleLock.notifyAll()
        }
    finishDeferredCleanupIfNeeded()

  private def cancelQuerySubscriber(subscriber: TUI.QuerySubscriber[?]): Unit =
    lifecycleLock.synchronized {
      if subscriber.state === TUI.QuerySubscriberState.Active then
        subscriber.state = TUI.QuerySubscriberState.Cancelled
        backgroundColorFlight = removeQuerySubscriber(backgroundColorFlight, subscriber)
        colorSchemeFlight = removeQuerySubscriber(colorSchemeFlight, subscriber)
        cursorPositionFlight = removeQuerySubscriber(cursorPositionFlight, subscriber)
    }

  private def removeQuerySubscriber[A](
      flight: Option[TUI.QueryFlight[A]],
      subscriber: TUI.QuerySubscriber[?]
  ): Option[TUI.QueryFlight[A]] =
    flight.map(value => value.copy(subscribers = value.subscribers.filterNot(_ eq subscriber)))

  private def initializeMouseFrameOrigin(timeoutMillis: Long = 100L): Unit =
    rendererPolicy.frameStartRow = None
    val stateLock              = Object()
    val ready                  = CountDownLatch(1)
    var result                 = Option.empty[TerminalCursorProtocol.CursorPosition]
    var initialRenderRequested = false
    subscribeQuery(
      () => cursorPositionFlight,
      flight => cursorPositionFlight = flight,
      "\r" + TerminalCursorProtocol.CursorPositionQuery,
      {
        case TerminalQueryResult.Success(position) =>
          val arrivedLate = stateLock.synchronized {
            result = Some(position)
            ready.countDown()
            initialRenderRequested
          }
          if arrivedLate then establishMouseFrameOrigin(position)
        case _                                     => ready.countDown()
      }
    )
    ready.await(math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS)
    stateLock.synchronized {
      initialRenderRequested = true
      rendererPolicy.frameStartRow = result.map(_.row)
    }
    requestRenderInternal(force = true, clear = rendererPolicy.isAlternateScreen)

  private def establishMouseFrameOrigin(
      position: TerminalCursorProtocol.CursorPosition
  ): Unit =
    rendererPolicy.frameStartRow = Some(position.row)
    requestRenderInternal(force = true, clear = rendererPolicy.isAlternateScreen)

  private def classifyIngressLocked(input: TerminalInput): TUI.IngressClassification =
    if replayContinuation.nonEmpty then TUI.IngressClassification.Blocked
    else classifyIngressWithoutReplayLocked(input)

  private def classifyIngressWithoutReplayLocked(
      input: TerminalInput
  ): TUI.IngressClassification =
    val hasCapacity = pendingIngress.length < TUI.IngressCapacity
    input match
      case TerminalInput.RawStart(kind) if rawReplyEligible(kind)       =>
        TUI.IngressClassification.Consumed(() =>
          rawCorrelation = Some(TUI.RawCorrelation(kind, Vector.empty, 0))
        )
      case TerminalInput.RawChunk(chunk) if rawCorrelation.nonEmpty     =>
        val correlation = rawCorrelation.get
        if correlation.length + chunk.length <= TerminalInputChunk.MaxBytes then
          TUI.IngressClassification.Consumed(() =>
            rawCorrelation = Some(correlation.copy(
              chunks = correlation.chunks :+ chunk,
              length = correlation.length + chunk.length
            ))
          )
        else if !hasCapacity then TUI.IngressClassification.Blocked
        else
          val replay = TerminalInput.RawStart(correlation.kind) +:
            (correlation.chunks.map(TerminalInput.RawChunk(_)) :+ TerminalInput.RawChunk(chunk))
          TUI.IngressClassification.Publish(
            TUI.Ingress.Input(replay.head),
            replay.tail.map(TUI.Ingress.Input(_)),
            () => rawCorrelation = None
          )
      case TerminalInput.RawEnd(termination) if rawCorrelation.nonEmpty =>
        if !hasCapacity then TUI.IngressClassification.Blocked
        else
          val correlation = rawCorrelation.get
          val data        = String(
            correlation.chunks.flatMap(_.toArray).toArray,
            java.nio.charset.StandardCharsets.UTF_8
          )
          classifyRawReplyLocked(data, termination, correlation)
      case _ if !hasCapacity                                            =>
        TUI.IngressClassification.Blocked
      case _                                                            =>
        TUI.IngressClassification.Publish(TUI.Ingress.Input(input), Vector.empty, () => ())

  private def classifyRawReplyLocked(
      data: String,
      termination: TerminalRawTermination,
      correlation: TUI.RawCorrelation
  ): TUI.IngressClassification =
    if termination === TerminalRawTermination.Complete && TerminalImageProtocol.isCellSizeResponse(
        data
      )
    then
      val previousDimensions = currentImageCellDimensions
      TUI.IngressClassification.Publish(
        TUI.Ingress.Protocol(Vector.empty, Vector.empty),
        Vector.empty,
        () =>
          rawCorrelation = None
          TerminalImageProtocol.parseCellSizeResponse(data).foreach { dimensions =>
            currentImageCellDimensions = dimensions
            if dimensions !== previousDimensions then renderRequested = true
          }
      )
    else if termination === TerminalRawTermination.Complete && TerminalColorProtocol.isOsc11BackgroundColorResponse(
        data
      )
    then
      val result      = TerminalColorProtocol.parseOsc11BackgroundColor(data)
        .map(TerminalQueryResult.Success(_))
        .getOrElse(TerminalQueryResult.InvalidResponse)
      val completions = completeFlightLocked(backgroundColorFlight, result)
      TUI.IngressClassification.Publish(
        TUI.Ingress.Protocol(completions, Vector.empty),
        Vector.empty,
        () =>
          rawCorrelation = None
          backgroundColorFlight = None
      )
    else if termination === TerminalRawTermination.Complete && TerminalColorProtocol.isTerminalColorSchemeReport(
        data
      )
    then
      val parsed        = TerminalColorProtocol.parseTerminalColorSchemeReport(data)
      val result        = parsed
        .map(TerminalQueryResult.Success(_))
        .getOrElse(TerminalQueryResult.InvalidResponse)
      val completions   = completeFlightLocked(colorSchemeFlight, result)
      val notifications = parsed.toVector.flatMap { scheme =>
        if terminalColorSchemeNotificationsEnabled then
          terminalColorSchemeListeners.toVector.map(listener => () => listener(scheme))
        else Vector.empty
      }
      TUI.IngressClassification.Publish(
        TUI.Ingress.Protocol(completions, notifications),
        Vector.empty,
        () =>
          rawCorrelation = None
          colorSchemeFlight = None
      )
    else if termination === TerminalRawTermination.Complete && TerminalCursorProtocol.isCursorPositionReport(
        data
      )
    then
      val result      = TerminalCursorProtocol.parseCursorPositionReport(data)
        .map(TerminalQueryResult.Success(_))
        .getOrElse(TerminalQueryResult.InvalidResponse)
      val completions = completeFlightLocked(cursorPositionFlight, result)
      TUI.IngressClassification.Publish(
        TUI.Ingress.Protocol(completions, Vector.empty),
        Vector.empty,
        () =>
          rawCorrelation = None
          cursorPositionFlight = None
      )
    else
      val replay = TerminalInput.RawStart(correlation.kind) +:
        (correlation.chunks.map(TerminalInput.RawChunk(_)) :+ TerminalInput.RawEnd(termination))
      TUI.IngressClassification.Publish(
        TUI.Ingress.Input(replay.head),
        replay.tail.map(TUI.Ingress.Input(_)),
        () => rawCorrelation = None
      )

  private def rawReplyEligible(kind: TerminalRawKind): Boolean = kind match
    case TerminalRawKind.Csi => true
    case TerminalRawKind.Osc => backgroundColorFlight.nonEmpty
    case _                   => false

  private def completeFlightLocked[A](
      flight: Option[TUI.QueryFlight[A]],
      result: TerminalQueryResult[A]
  ): Vector[TUI.QueryCompletion[A]] =
    flight.toVector.flatMap(_.subscribers).map(TUI.QueryCompletion(_, result))

  private def retainQueryCompletionsLocked[A](
      subscribers: Vector[TUI.QuerySubscriber[A]],
      result: TerminalQueryResult[A]
  ): Unit =
    retainedQueryCompletions ++= subscribers.map(TUI.QueryCompletion(_, result))

  private def publishInput(input: TerminalInput): Unit =
    val publication = lifecycleLock.synchronized {
      var result = classifyIngressLocked(input)
      while acceptsIngress && result === TUI.IngressClassification.Blocked do
        lifecycleLock.wait()
        result = classifyIngressLocked(input)
      if !acceptsIngress then None
      else
        result match
          case TUI.IngressClassification.Consumed(commit)                  =>
            commit()
            Some(Vector.empty)
          case TUI.IngressClassification.Publish(first, remaining, commit) =>
            commit()
            pendingIngress += first
            refillReplayLocked(remaining)
            Some(Vector.empty)
          case TUI.IngressClassification.Blocked                           => None
    }
    val accepted    = publication.nonEmpty
    if accepted then drainOrReturn()

  private def refillReplayLocked(replay: Vector[TUI.Ingress] = Vector.empty): Unit =
    if replay.nonEmpty then
      require(replay.length <= TUI.MaxReplayEvents - 1)
      replayContinuation = Some(TUI.ReplayContinuation(replay, 0))
    replayContinuation.foreach { continuation =>
      val available = TUI.IngressCapacity - pendingIngress.length
      val admitted  = math.min(available, continuation.events.length - continuation.nextIndex)
      pendingIngress ++= continuation.events.slice(
        continuation.nextIndex,
        continuation.nextIndex + admitted
      )
      val nextIndex = continuation.nextIndex + admitted
      if nextIndex === continuation.events.length then
        replayContinuation = None
        lifecycleLock.notifyAll()
      else replayContinuation = Some(continuation.copy(nextIndex = nextIndex))
    }

  private def publishResize(): Unit =
    val diagnostic = lifecycleLock.synchronized {
      resizeGeneration += 1
      if lifecycleState === TUI.LifecycleState.Running then
        renderRequested = true
        forceRenderRequested = true
        clearRequested = true
        pendingResizeRecoveryGeneration = Some(resizeGeneration)
        Some(resizeGeneration)
      else None
    }
    diagnostic.foreach { generation =>
      emitDiagnostic(TUIDiagnosticEvent.Resize(
        positiveDimension(terminal.columns),
        positiveDimension(terminal.rows),
        generation,
        options.screenMode
      ))
    }
    drainOrReturn()

  private def drainOrReturn(
      propagateCleanupFailure: Boolean = false,
      deferRender: Boolean = false
  ): Unit =
    val own = lifecycleLock.synchronized {
      if drainOwned || startupOwner then false
      else
        drainOwned = true
        true
    }
    if own then drainWork(propagateCleanupFailure, deferRender)

  private def drainWork(propagateCleanupFailure: Boolean, deferRender: Boolean = false): Unit =
    drainOwnerMarker.set(true)
    var continue               = true
    var completed              = false
    var deferredCleanupFailure = Option.empty[Throwable]
    try
      while continue do
        val work = lifecycleLock.synchronized {
          if retainedQueryCompletions.nonEmpty then
            TUI.Work.QueryCompletion(retainedQueryCompletions.remove(0))
          else if retainedAppendCompletions.nonEmpty then
            TUI.Work.AppendCompletion(retainedAppendCompletions.remove(0))
          else if pendingAppendCompletions.nonEmpty then
            TUI.Work.AppendCompletion(pendingAppendCompletions.remove(0))
          else if componentEffectCoordinator.hasRuntimeEffects then TUI.Work.ComponentEffect
          else if lifecycleState === TUI.LifecycleState.Stopping then
            if !contextDetachScheduled then
              scheduleContextDetachLocked()
              TUI.Work.ComponentEffect
            else if pendingControlOutput.nonEmpty then
              TUI.Work.Control(pendingControlOutput.remove(0))
            else if queryWriteReservations === 0 then
              claimCleanupLocked()
              TUI.Work.Cleanup
            else
              drainOwned = false
              TUI.Work.Done
          else if hasOrdinaryWorkLocked && (!deferRender || hasNonRenderOrdinaryWorkLocked) then
            selectOrdinaryWorkLocked(allowRender = !deferRender)
          else
            drainOwned = false
            TUI.Work.Done
        }
        work match
          case TUI.Work.Ingress(ingress)                         => processIngress(ingress)
          case TUI.Work.QueryCompletion(completion)              => processQueryCompletion(completion)
          case TUI.Work.AppendCompletion(completion)             => processAppendCompletion(completion)
          case TUI.Work.ComponentEffect                          =>
            componentEffectCoordinator.runNextRuntimeBatch()
          case TUI.Work.Structural(claimed)                      => applyStructural(claimed)
          case TUI.Work.Action(action)                           =>
            try action()
            catch case error: Throwable => recordFailure(error)
          case TUI.Work.Control(action)                          => action()
          case TUI.Work.Append(operation)                        => processAppend(operation)
          case TUI.Work.Render(force, clear, recoveryGeneration) =>
            renderNow(force, clear, recoveryGeneration)
          case TUI.Work.Cleanup                                  =>
            emitDiagnostic(TUIDiagnosticEvent.Lifecycle(
              TUIDiagnosticLifecycleState.Cleaning,
              options.screenMode
            ))
            deferredCleanupFailure = cleanup()
          case TUI.Work.Done                                     => continue = false
      completed = true
    catch
      case e: Throwable =>
        recordFailure(e)
        lifecycleLock.synchronized { drainOwned = false }
        finishDeferredCleanupIfNeeded()
    if completed then
      deferredCleanupFailure.foreach { error =>
        if propagateCleanupFailure then
          drainOwnerMarker.set(false)
          throw error
        else recordFailure(error)
      }
    drainOwnerMarker.set(false)

  private def processIngress(ingress: TUI.Ingress): Unit = ingress match
    case TUI.Ingress.Input(input)                         => handleInput(input)
    case TUI.Ingress.AppendCompletion(completion)         =>
      processAppendCompletion(completion)
    case TUI.Ingress.Protocol(completions, notifications) =>
      completions.foreach(processQueryCompletion)
      val running = lifecycleLock.synchronized {
        lifecycleState === TUI.LifecycleState.Running
      }
      if running then notifications.foreach(_())

  private def processQueryCompletion(completion: TUI.QueryCompletion[?]): Unit =
    val claimed = lifecycleLock.synchronized {
      if completion.subscriber.state === TUI.QuerySubscriberState.Active then
        completion.subscriber.state = TUI.QuerySubscriberState.Claimed
        true
      else false
    }
    if claimed then
      try completion.invoke()
      catch case error: Throwable => recordFailure(error)
      finally
        lifecycleLock.synchronized {
          completion.subscriber.state = TUI.QuerySubscriberState.Completed
        }

  private def processAppendCompletion(completion: TUI.AppendCompletion): Unit =
    emitAppendDiagnostic(
      completion.outcome,
      completion.failure,
      completion.rowCount,
      completion.controlCount
    )
    try completion.callback(completion.result)
    catch
      case error: Throwable =>
        emitAppendDiagnostic(
          TUIDiagnosticAppendOutcome.Failed,
          Some(TUIDiagnosticAppendFailure.Callback),
          0,
          0
        )
        recordFailure(error)

  private def processAppend(operation: TUI.AppendOperation): Unit =
    val width    = positiveDimension(terminal.columns)
    val height   = positiveDimension(terminal.rows)
    val snapshot = lifecycleLock.synchronized {
      if lifecycleState !== TUI.LifecycleState.Running then
        Left(AppendRejection.StoppedBeforePublication)
      else
        rendererPolicy.retainedFrame match
          case None                                                => Left(AppendRejection.NoCommittedFrame)
          case Some(frame) if retainedFrameContainsITerm2(frame)   =>
            Left(AppendRejection.RetainedITerm2Control)
          case Some(_) if isAttachedForAppend(operation.component) =>
            Left(AppendRejection.AttachedComponent)
          case Some(frame)                                         => Right(TUI.AppendSnapshot(
              resizeGeneration,
              width,
              height,
              frame,
              currentImageCellDimensions,
              terminalCapabilities,
              terminalCapabilityOverrides
            ))
    }
    snapshot match
      case Left(reason) => finishAppendRejected(operation, reason)
      case Right(value) =>
        var failureCategory = TUIDiagnosticAppendFailure.Render
        try
          operation.violation.failure.foreach(throw _)
          val rendered      = renderAppendCandidate(operation, value)
          failureCategory = TUIDiagnosticAppendFailure.Identity
          val remapped      = remapAppendKittyControls(rendered, value.frame)
          failureCategory = TUIDiagnosticAppendFailure.Planning
          val candidate     = rendererPolicy.prepareFrame(remapped, value.width)
          val currentWidth  = positiveDimension(terminal.columns)
          val currentHeight = positiveDimension(terminal.rows)
          val decision      = lifecycleLock.synchronized {
            if lifecycleState !== TUI.LifecycleState.Running then
              TUI.AppendCommitDecision.Reject(AppendRejection.StoppedBeforePublication)
            else if (resizeGeneration !== value.generation) ||
              (currentWidth !== value.width) || (currentHeight !== value.height)
            then
              activeAppend = None
              pendingAppends.prepend(operation)
              TUI.AppendCommitDecision.Retry
            else if rendererPolicy.retainedFrame.exists(retainedFrameContainsITerm2) then
              TUI.AppendCommitDecision.Reject(AppendRejection.RetainedITerm2Control)
            else
              operation.violation.claimPublication() match
                case Some(error) => TUI.AppendCommitDecision.Fail(error)
                case None        => TUI.AppendCommitDecision.Publish
          }
          decision match
            case TUI.AppendCommitDecision.Retry          => ()
            case TUI.AppendCommitDecision.Reject(reason) => finishAppendRejected(operation, reason)
            case TUI.AppendCommitDecision.Fail(error)    => throw error
            case TUI.AppendCommitDecision.Publish        =>
              failureCategory = TUIDiagnosticAppendFailure.Write
              rendererPolicy.publishAppend(candidate, value.frame, value.height)
              val remappedIds = candidate.controls.flatMap(kittyImageId)
              lifecycleLock.synchronized { appendOwnedKittyIds ++= remappedIds }
              finishAppend(
                operation,
                AppendResult.Published(candidate.lines.length, candidate.controls.length),
                TUIDiagnosticAppendOutcome.Published,
                None,
                candidate.lines.length,
                candidate.controls.length
              )
        catch
          case error: Throwable =>
            val (cause, category) = error match
              case classified: TUI.AppendClassifiedFailure =>
                classified.original -> classified.category
              case other                                   =>
                other -> classifyAppendFailure(other, failureCategory)
            finishAppend(
              operation,
              AppendResult.Failed(cause),
              TUIDiagnosticAppendOutcome.Failed,
              Some(category),
              0,
              0
            )
            recordFailure(cause)

  private def renderAppendCandidate(
      operation: TUI.AppendOperation,
      snapshot: TUI.AppendSnapshot
  ): ComponentRender =
    operation.violation.failure.foreach(throw _)
    operation.component match
      case contextual: ContextualComponent =>
        val context         = TUI.RestrictedAppendContext(
          snapshot.cellDimensions,
          snapshot.capabilities,
          snapshot.capabilityOverrides,
          operation.violation
        )
        var result          = Option.empty[ComponentRender]
        var failure         = Option.empty[Throwable]
        var failureCategory = TUIDiagnosticAppendFailure.Context
        try
          contextual.tuiContext_=(Some(context))
          failureCategory = TUIDiagnosticAppendFailure.Render
          testRuntimeCounters.recordComponentRender()
          val rendered = operation.component.render(snapshot.width)
          failureCategory = TUIDiagnosticAppendFailure.Validation
          result = Some(rendered.validated(snapshot.width))
        catch case error: Throwable => failure = Some(error)
        finally
          context.revoke()
          try contextual.tuiContext_=(None)
          catch
            case detachFailure: Throwable => failure match
                case Some(first) => first.addSuppressed(detachFailure)
                case None        =>
                  failure = Some(detachFailure)
                  failureCategory = TUIDiagnosticAppendFailure.Context
        failure.foreach(error => throw TUI.AppendClassifiedFailure(error, failureCategory))
        operation.violation.failure.foreach(throw _)
        try validateAppendMetadata(result.get)
        catch
          case error: Throwable =>
            throw TUI.AppendClassifiedFailure(error, TUIDiagnosticAppendFailure.Validation)
      case _                               =>
        val rendered =
          try
            testRuntimeCounters.recordComponentRender()
            operation.component.render(snapshot.width)
          catch
            case error: Throwable =>
              throw TUI.AppendClassifiedFailure(error, TUIDiagnosticAppendFailure.Render)
        val result   =
          try rendered.validated(snapshot.width)
          catch
            case error: Throwable =>
              throw TUI.AppendClassifiedFailure(error, TUIDiagnosticAppendFailure.Validation)
        operation.violation.failure.foreach(throw _)
        try validateAppendMetadata(result)
        catch
          case error: Throwable =>
            throw TUI.AppendClassifiedFailure(error, TUIDiagnosticAppendFailure.Validation)

  private def validateAppendMetadata(render: ComponentRender): ComponentRender =
    if render.cursorPlacements.nonEmpty then
      throw IllegalArgumentException("Append-only output cannot contain cursor placements")
    if render.controls.exists(_.control.details match
        case _: scalatui.terminal.TerminalRenderControlDetails.KittyCleanup => true
        case _                                                              => false)
    then throw IllegalArgumentException("Append-only output cannot contain Kitty cleanup controls")
    render

  private def remapAppendKittyControls(
      render: ComponentRender,
      retainedFrame: PreparedFrame
  ): ComponentRender =
    val kittyCount = render.controls.count(kittyImageId(_).nonEmpty)
    if appendOwnedKittyIds.size + kittyCount > TUI.AppendKittyLedgerCapacity then
      throw IllegalStateException("Append-only Kitty image ownership capacity exceeded")
    val excluded   = scala.collection.mutable.HashSet.empty[Int]
    excluded ++= appendOwnedKittyIds
    excluded ++= retainedFrame.controls.flatMap(kittyImageId)
    val controls   = render.controls.map { placement =>
      placement.control.details match
        case _: scalatui.terminal.TerminalRenderControlDetails.KittyImage =>
          var imageId = TerminalImageProtocol.allocateImageId()
          while excluded(imageId) do imageId = TerminalImageProtocol.allocateImageId()
          excluded += imageId
          placement.copy(control =
            TerminalRenderControl.remapKittyImage(
              placement.control,
              imageId
            )
          )
        case _                                                            => placement
    }
    render.copy(controls = controls)

  private def finishAppendRejected(
      operation: TUI.AppendOperation,
      reason: AppendRejection
  ): Unit =
    finishAppend(
      operation,
      AppendResult.Rejected(reason),
      TUIDiagnosticAppendOutcome.Rejected,
      Some(diagnosticFailure(reason)),
      0,
      0
    )

  private def finishAppend(
      operation: TUI.AppendOperation,
      result: AppendResult,
      outcome: TUIDiagnosticAppendOutcome,
      failure: Option[TUIDiagnosticAppendFailure],
      rowCount: Int,
      controlCount: Int
  ): Unit =
    lifecycleLock.synchronized {
      if activeAppend.exists(_.id === operation.id) then activeAppend = None
      require(acceptedIncompleteAppends > 0)
      acceptedIncompleteAppends -= 1
      queueAcceptedAppendCompletionLocked(TUI.AppendCompletion(
        operation.callback,
        result,
        Some(operation.id),
        outcome,
        failure,
        rowCount,
        controlCount
      ))
    }

  private def classifyAppendFailure(
      error: Throwable,
      stage: TUIDiagnosticAppendFailure
  ): TUIDiagnosticAppendFailure =
    error match
      case _: IllegalArgumentException   => TUIDiagnosticAppendFailure.Validation
      case _: TUI.AppendContextViolation => TUIDiagnosticAppendFailure.Context
      case _                             => stage

  private def diagnosticFailure(reason: AppendRejection): TUIDiagnosticAppendFailure = reason match
    case AppendRejection.LifecycleUnavailable(_)                                       => TUIDiagnosticAppendFailure.Lifecycle
    case AppendRejection.AlternateScreen                                               => TUIDiagnosticAppendFailure.ScreenMode
    case AppendRejection.ScrollbackClearingResizePolicy                                =>
      TUIDiagnosticAppendFailure.ResizePolicy
    case AppendRejection.NoCommittedFrame                                              => TUIDiagnosticAppendFailure.FrameUnavailable
    case AppendRejection.AttachedComponent                                             => TUIDiagnosticAppendFailure.AttachedComponent
    case AppendRejection.QueueCapacityExceeded                                         => TUIDiagnosticAppendFailure.Capacity
    case AppendRejection.RetainedITerm2Control                                         => TUIDiagnosticAppendFailure.RetainedITerm2
    case AppendRejection.StoppedBeforeClaim | AppendRejection.StoppedBeforePublication =>
      TUIDiagnosticAppendFailure.Stopped

  private def emitAppendDiagnostic(
      outcome: TUIDiagnosticAppendOutcome,
      failure: Option[TUIDiagnosticAppendFailure],
      rowCount: Int,
      controlCount: Int
  ): Unit =
    emitDiagnostic(TUIDiagnosticEvent.Append(
      outcome,
      failure,
      rowCount,
      controlCount,
      options.screenMode,
      lifecycleLock.synchronized(resizeGeneration)
    ))

  private def scheduleControlOutput(supported: Boolean)(action: => Unit): Boolean =
    if !supported then false
    else
      val state = lifecycleLock.synchronized {
        lifecycleState match
          case TUI.LifecycleState.Starting | TUI.LifecycleState.Running =>
            pendingControlOutput += (() => action)
            lifecycleState
          case value                                                    => value
      }
      state match
        case TUI.LifecycleState.Starting                               => true
        case TUI.LifecycleState.Running                                =>
          drainOrReturn()
          true
        case TUI.LifecycleState.Stopping | TUI.LifecycleState.Cleaning => false
        case TUI.LifecycleState.Stopped                                =>
          val direct = lifecycleLock.synchronized {
            if drainOwned then
              pendingControlOutput += (() => action)
              false
            else true
          }
          if direct then terminalServices.write(action)
          else drainOrReturn()
          true

  private def publishAction(action: () => Unit): Unit =
    val publication = lifecycleLock.synchronized {
      lifecycleState match
        case TUI.LifecycleState.Stopped                                =>
          pendingActions += action
          val own = !drainOwned
          if own then drainOwned = true
          TUI.ActionPublication.Accepted(own)
        case TUI.LifecycleState.Stopping | TUI.LifecycleState.Cleaning =>
          TUI.ActionPublication.Rejected
        case _                                                         =>
          pendingActions += action
          TUI.ActionPublication.Accepted(own = false)
    }
    publication match
      case TUI.ActionPublication.Accepted(true)  =>
        drainWork(propagateCleanupFailure = false)
      case TUI.ActionPublication.Accepted(false) => drainOrReturn()
      case TUI.ActionPublication.Rejected        => ()

  private def publishStructural(create: () => TUI.StructuralOperation): Unit =
    val accepted = lifecycleLock.synchronized {
      lifecycleState match
        case TUI.LifecycleState.Stopping | TUI.LifecycleState.Cleaning => false
        case _                                                         =>
          pendingStructural += create()
          true
    }
    if accepted then drainOrReturn()

  private def hasOrdinaryWorkLocked: Boolean =
    hasNonRenderOrdinaryWorkLocked || renderRequested

  private def hasNonRenderOrdinaryWorkLocked: Boolean =
    pendingStructural.nonEmpty || pendingActions.nonEmpty || pendingIngress.nonEmpty ||
      pendingControlOutput.nonEmpty || pendingAppends.nonEmpty

  private def selectOrdinaryWorkLocked(allowRender: Boolean = true): TUI.Work =
    val categories = TUI.OrdinaryCategory.values
    var offset     = 1
    var selected   = Option.empty[TUI.OrdinaryCategory]
    while selected.isEmpty && offset <= categories.length do
      val candidate = categories((lastOrdinaryCategory.ordinal + offset) % categories.length)
      if ordinaryCategoryReadyLocked(candidate) &&
        (allowRender || !(candidate === TUI.OrdinaryCategory.Render))
      then selected = Some(candidate)
      offset += 1
    val category   = selected.get
    lastOrdinaryCategory = category
    category match
      case TUI.OrdinaryCategory.Structural =>
        TUI.Work.Structural(claimStructuralLocked(pendingStructural.remove(0)))
      case TUI.OrdinaryCategory.Action     => TUI.Work.Action(pendingActions.remove(0))
      case TUI.OrdinaryCategory.Ingress    =>
        val ingress = pendingIngress.removeHead()
        refillReplayLocked()
        lifecycleLock.notifyAll()
        TUI.Work.Ingress(ingress)
      case TUI.OrdinaryCategory.Control    =>
        TUI.Work.Control(pendingControlOutput.remove(0))
      case TUI.OrdinaryCategory.Append     =>
        val operation = pendingAppends.remove(0)
        activeAppend = Some(operation)
        TUI.Work.Append(operation)
      case TUI.OrdinaryCategory.Render     =>
        renderRequested = false
        val force              = forceRenderRequested
        val clear              = clearRequested
        val recoveryGeneration = pendingResizeRecoveryGeneration
        forceRenderRequested = false
        clearRequested = false
        pendingResizeRecoveryGeneration = None
        TUI.Work.Render(force, clear, recoveryGeneration)

  private def ordinaryCategoryReadyLocked(category: TUI.OrdinaryCategory): Boolean = category match
    case TUI.OrdinaryCategory.Structural => pendingStructural.nonEmpty
    case TUI.OrdinaryCategory.Action     => pendingActions.nonEmpty
    case TUI.OrdinaryCategory.Ingress    => pendingIngress.nonEmpty
    case TUI.OrdinaryCategory.Control    => pendingControlOutput.nonEmpty
    case TUI.OrdinaryCategory.Append     => pendingAppends.nonEmpty
    case TUI.OrdinaryCategory.Render     => renderRequested

  private def claimStructuralLocked(
      operation: TUI.StructuralOperation
  ): TUI.ClaimedStructural = operation match
    case TUI.StructuralOperation.NoOp       => TUI.ClaimedStructural(Vector.empty)
    case TUI.StructuralOperation.Add(entry) =>
      val attach = committedChildren.count(_.component eq entry.component) === 0
      committedChildren :+= entry
      TUI.ClaimedStructural(Vector(TUI.StructuralEffect.Add(entry.component, attach)))
    case TUI.StructuralOperation.Remove(id) =>
      committedChildren.indexWhere(_.id === id) match
        case -1    => TUI.ClaimedStructural(Vector.empty)
        case index =>
          val entry  = committedChildren(index)
          committedChildren = committedChildren.patch(index, Nil, 1)
          val detach = !committedChildren.exists(_.component eq entry.component)
          TUI.ClaimedStructural(Vector(TUI.StructuralEffect.Remove(entry.component, detach)))
    case TUI.StructuralOperation.Clear(ids) =>
      val removed = committedChildren.filter(entry => ids.contains(entry.id))
      committedChildren = committedChildren.filterNot(entry => ids.contains(entry.id))
      TUI.ClaimedStructural(removed.zipWithIndex.map { (entry, index) =>
        val remainingRemoved = removed.drop(index + 1)
        val detach           = !committedChildren.exists(_.component eq entry.component) &&
          !remainingRemoved.exists(_.component eq entry.component)
        TUI.StructuralEffect.Remove(entry.component, detach)
      })

  private def applyStructural(claimed: TUI.ClaimedStructural): Unit =
    claimed.effects.foreach {
      case TUI.StructuralEffect.Add(component, attach)    =>
        root.addChild(component)
        if attach then attachContext(component)
      case TUI.StructuralEffect.Remove(component, detach) =>
        clearMouseStateFor(component)
        root.removeChild(component)
        if detach then detachContext(component)
    }

  private def emitRedrawDiagnostic(
      kind: TUIDiagnosticRedrawKind,
      columns: Int,
      rows: Int,
      frameRows: Int,
      firstRow: Int,
      clearReason: Option[TUIDiagnosticClearReason]
  ): Unit =
    emitDiagnostic(TUIDiagnosticEvent.Redraw(
      kind,
      columns,
      rows,
      frameRows,
      firstRow,
      clearReason,
      options.screenMode
    ))

  private def emitWriteDiagnostic(kind: TUIDiagnosticWriteKind, value: String): Unit =
    emitDiagnostic(TUIDiagnosticEvent.Write(
      kind,
      value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
    ))

  private def emitDiagnostic(event: => TUIDiagnosticEvent): Unit =
    if diagnosticObserver.nonEmpty then
      diagnosticLock.synchronized {
        diagnosticObserver.foreach { observer =>
          try observer.onEvent(event)
          catch case _: Throwable => diagnosticObserver = None
        }
      }

  private def startupMayContinue: Boolean = lifecycleLock.synchronized {
    lifecycleState === TUI.LifecycleState.Starting
  }

  private def recordFailure(error: Throwable): Unit = lifecycleLock.synchronized {
    recordFailureLocked(error)
  }

  private def recordFailureLocked(error: Throwable): Unit =
    runtimeFailure match
      case Some(first) if first ne error => first.addSuppressed(error)
      case None                          => runtimeFailure = Some(error)
      case _                             => ()
    if lifecycleState === TUI.LifecycleState.Starting ||
      lifecycleState === TUI.LifecycleState.Running
    then transitionToStoppingLocked()
    lifecycleLock.notifyAll()

  private def transitionToStoppingLocked(): Unit =
    componentEffectCoordinator.closeGeneration()
    lifecycleState = TUI.LifecycleState.Stopping
    mouseCapture = None
    mousePress = None
    lastMouseClick = None
    retainedAppendCompletions ++= pendingAppendCompletions
    pendingAppendCompletions.clear()
    pendingAppends.foreach { operation =>
      require(acceptedIncompleteAppends > 0)
      acceptedIncompleteAppends -= 1
      queueAcceptedAppendCompletionLocked(TUI.AppendCompletion(
        operation.callback,
        AppendResult.Rejected(AppendRejection.StoppedBeforeClaim),
        Some(operation.id),
        TUIDiagnosticAppendOutcome.Rejected,
        Some(TUIDiagnosticAppendFailure.Stopped),
        rowCount = 0,
        controlCount = 0
      ))
    }
    pendingAppends.clear()
    pendingIngress.foreach {
      case TUI.Ingress.Protocol(completions, _)     => retainedQueryCompletions ++= completions
      case TUI.Ingress.AppendCompletion(completion) =>
        queueAcceptedAppendCompletionLocked(completion)
      case _                                        => ()
    }
    pendingIngress.clear()
    replayContinuation = None
    pendingActions.clear()
    pendingStructural.clear()
    desiredChildren = committedChildren
    renderRequested = false
    forceRenderRequested = false
    clearRequested = false
    pendingResizeRecoveryGeneration = None
    backgroundColorFlight match
      case Some(flight) if flight.phase === TUI.QueryFlightPhase.Emitted =>
        retainQueryCompletionsLocked(flight.subscribers, TerminalQueryResult.Stopped)
        backgroundColorFlight = None
      case _                                                             => ()
    colorSchemeFlight match
      case Some(flight) if flight.phase === TUI.QueryFlightPhase.Emitted =>
        retainQueryCompletionsLocked(flight.subscribers, TerminalQueryResult.Stopped)
        colorSchemeFlight = None
      case _                                                             => ()
    exitRequested = true
    lifecycleLock.notifyAll()

  private def acceptsIngress: Boolean =
    lifecycleState === TUI.LifecycleState.Starting ||
      lifecycleState === TUI.LifecycleState.Running

  private def finishDeferredCleanupIfNeeded(
      propagateCleanupFailure: Boolean = false
  ): Unit =
    val shouldDrain  = lifecycleLock.synchronized {
      lifecycleState === TUI.LifecycleState.Stopping && !startupOwner && !drainOwned &&
      queryWriteReservations === 0 &&
      (retainedQueryCompletions.nonEmpty || pendingControlOutput.nonEmpty)
    }
    if shouldDrain then drainOrReturn(propagateCleanupFailure)
    val shouldFinish = lifecycleLock.synchronized {
      lifecycleState === TUI.LifecycleState.Stopping && !startupOwner && !drainOwned &&
      queryWriteReservations === 0
    }
    if shouldFinish then drainOrReturn(propagateCleanupFailure)

  private def inputTarget: Option[Component] =
    topCapturingOverlay.map(_.component).orElse(focusedComponent)

  private def routeViewportInput(input: TerminalInput): Option[InputResult] =
    if !rendererPolicy.isFullscreenViewport then None
    else
      val command = KeybindingCommand.values.find(value =>
        value.scope === scalatui.terminal.KeybindingScope.Viewport &&
          options.keybindings.matches(input, value)
      )
      if primaryScrollView.exists(_.searchState.active) && !command.exists(isSelectionCommand) then
        val result = routeSearchInput(input)
        Option.when(result !== InputResult.Ignored)(result)
      else
        command.map { value =>
          val target        = inputTarget
          val focusedResult = target.fold(InputResult.Ignored)(_.handleInputResult(input))
          if focusedResult !== InputResult.Ignored then focusedResult
          else
            val viewportResult = viewportRoot.collect {
              case handler: ViewportCommandHandler if !target.exists(_ eq handler) =>
                handler.handleViewportCommand(value)
            }.getOrElse(InputResult.Ignored)
            if viewportResult !== InputResult.Ignored then viewportResult
            else routePrimaryViewportCommand(value)
        }

  private def isSelectionCommand(command: KeybindingCommand): Boolean = command match
    case KeybindingCommand.ViewportCopySelection | KeybindingCommand.ViewportClearSelection => true
    case _                                                                                  => false

  private def routePrimaryViewportCommand(command: KeybindingCommand): InputResult =
    primaryScrollView.fold(InputResult.Ignored) { scrollView =>
      var render  = false
      val handled = command match
        case KeybindingCommand.ViewportLineUp         => scrollView.scrollBy(-1); true
        case KeybindingCommand.ViewportLineDown       => scrollView.scrollBy(1); true
        case KeybindingCommand.ViewportHalfPageUp     =>
          scrollView.scrollBy(-math.max(1, scrollView.viewportExtent / 2)); true
        case KeybindingCommand.ViewportHalfPageDown   =>
          scrollView.scrollBy(math.max(1, scrollView.viewportExtent / 2)); true
        case KeybindingCommand.ViewportPageUp         =>
          scrollView.scrollBy(-math.max(1, scrollView.viewportExtent - 4)); true
        case KeybindingCommand.ViewportPageDown       =>
          scrollView.scrollBy(math.max(1, scrollView.viewportExtent - 4)); true
        case KeybindingCommand.ViewportDocumentStart  => scrollView.jumpToStart(); true
        case KeybindingCommand.ViewportDocumentEnd    => scrollView.jumpToEnd(); true
        case KeybindingCommand.ViewportPreviousPrompt => scrollView.scrollToPrompt(-1); true
        case KeybindingCommand.ViewportNextPrompt     => scrollView.scrollToPrompt(1); true
        case KeybindingCommand.ViewportSearchToggle   =>
          scrollView.openSearch()
          clearSearchPaste()
          render = true
          true
        case KeybindingCommand.ViewportCopySelection  =>
          copySelection()
          true
        case KeybindingCommand.ViewportClearSelection =>
          scrollView.clearSelection()
          true
        case _                                        => false
      if !handled then InputResult.Ignored
      else if render then InputResult.Render
      else InputResult.NoRender
    }

  private def routeSearchInput(input: TerminalInput): InputResult =
    primaryScrollView.fold(InputResult.Ignored) { scrollView =>
      val command = Vector(
        KeybindingCommand.ViewportSearchClose,
        KeybindingCommand.ViewportSearchToggle,
        KeybindingCommand.ViewportSearchPrevious,
        KeybindingCommand.ViewportSearchNext
      ).find(options.keybindings.matches(input, _))
      command match
        case Some(KeybindingCommand.ViewportSearchClose | KeybindingCommand.ViewportSearchToggle) =>
          scrollView.closeSearch()
          clearSearchPaste()
          InputResult.Render
        case Some(KeybindingCommand.ViewportSearchPrevious)                                       =>
          scrollView.moveSearchMatch(-1, terminal.columns, testRuntimeCounters.recordSearchScans)
          InputResult.Render
        case Some(KeybindingCommand.ViewportSearchNext)                                           =>
          scrollView.moveSearchMatch(1, terminal.columns, testRuntimeCounters.recordSearchScans)
          InputResult.Render
        case _                                                                                    =>
          val oldQuery              = scrollView.searchState.query
          val (nextQuery, consumed) = input match
            case TerminalInput.KeyEvent(TerminalKey.Character(value), modifiers, eventType)
                if (eventType !== KeyEventType.Release) && !modifiers.ctrl && !modifiers.alt &&
                  !modifiers.superKey => Some(scrollView.boundSearchQuery(oldQuery + value)) -> true
            case TerminalInput.KeyEvent(TerminalKey.Backspace, _, eventType)
                if eventType !== KeyEventType.Release =>
              Some(Unicode.graphemeClusters(oldQuery).dropRight(1).mkString) -> true
            case TerminalInput.PasteStart                             =>
              searchPasteDecoder.clear()
              searchPasteBuffer.clear()
              searchPasteBuffer.append(oldQuery)
              searchPasteActive = true
              None -> true
            case TerminalInput.PasteChunk(chunk) if searchPasteActive =>
              val decoded = searchPasteDecoder.process(chunk)
              val bounded = scrollView.boundSearchQuery(searchPasteBuffer.result() + decoded)
              searchPasteBuffer.clear()
              searchPasteBuffer.append(bounded)
              None -> true
            case TerminalInput.PasteEnd if searchPasteActive          =>
              val bounded = scrollView.boundSearchQuery(
                searchPasteBuffer.result() + searchPasteDecoder.flush()
              )
              clearSearchPaste()
              Some(bounded) -> true
            case _                                                    => None -> false
          nextQuery match
            case Some(value) if value !== oldQuery =>
              scrollView.updateSearchQuery(
                value,
                terminal.columns,
                testRuntimeCounters.recordSearchScans
              )
              InputResult.Render
            case _ if consumed                     => InputResult.NoRender
            case _                                 => InputResult.Ignored
    }

  override private[scalatui] def clearViewportSearchInput(): Unit =
    publishAction(() => clearSearchPaste())

  private def clearSearchPaste(): Unit =
    searchPasteDecoder.clear()
    searchPasteBuffer.clear()
    searchPasteActive = false

  private def primaryScrollView: Option[ScrollView] =
    latestBaseLayout.flatMap(primaryScrollViewIn)

  private def primaryScrollViewIn(node: LayoutNode): Option[ScrollView] = node.component match
    case scroll: ScrollView if scroll.primary => Some(scroll)
    case _                                    =>
      node.children.iterator.flatMap(primaryScrollViewIn).nextOption()

  private def routeGlobalInputListeners(input: TerminalInput): InputResult =
    val listeners = lifecycleLock.synchronized(inputListeners.toVector)
    var result    = InputResult.Ignored
    var index     = 0
    while index < listeners.length && result === InputResult.Ignored do
      result = listeners(index)(input)
      index += 1
    handleInputResult(result)
    result

  private def routeMouseInput(input: TerminalInput.Mouse): Unit =
    val semanticHandled = input.action match
      case MouseAction.Press(button)    => routeMousePress(input, button)
      case MouseAction.Release(button)  => routeMouseRelease(input, button)
      case MouseAction.Move(state)      => routeMouseMove(input, state)
      case MouseAction.Wheel(direction) => routeMouseWheel(input, direction)
    if !semanticHandled then handleInputResult(routeLegacyMouseInput(input))

  private def routeMouseWheel(
      input: TerminalInput.Mouse,
      direction: MouseWheelDirection
  ): Boolean =
    val unitDelta           = direction match
      case MouseWheelDirection.Up   => -1
      case MouseWheelDirection.Down => 1
      case _                        => 0
    val initial             = unitDelta * (if input.modifiers.alt then 5 else 1)
    var remaining           = initial
    var handled             = false
    val targets             = semanticTargetsAt(input.row, input.col)
    var index               = 0
    var deliveredHorizontal = false
    while index < targets.length &&
      (if initial === 0 then !deliveredHorizontal else remaining !== 0)
    do
      val target = targets(index)
      target.component match
        case handler: MouseEventHandler =>
          val result = handler.handleMouseEvent(
            MouseEvent.Wheel(
              mouseLocation(target, input),
              direction,
              input.modifiers,
              remaining
            )
          )
          deliveredHorizontal = initial === 0 && result.handled
          applyMouseHandlerResult(target, result)
          handled = handled || result.handled
          remaining = result.wheelRemainder.getOrElse(if result.handled then 0 else remaining)
        case _                          => ()
      index += 1
    handled || (remaining !== initial)

  private def routeMousePress(input: TerminalInput.Mouse, button: MouseButton): Boolean =
    val now           = options.mouseGestures.clock.nanoTime()
    val targets       = semanticTargetsAt(input.row, input.col)
    val handledTarget = firstHandledSemanticTarget(
      targets,
      target => MouseEvent.Press(mouseLocation(target, input), button, input.modifiers)
    )
    val target        = mouseCapture.orElse(handledTarget).orElse(targets.headOption)
    mousePress = target.map(TUI.MousePress(_, button, input.row, input.col, now, dragged = false))
    handledTarget.nonEmpty

  private def routeMouseMove(
      input: TerminalInput.Mouse,
      buttonState: MouseButtonState
  ): Boolean = mousePress match
    case Some(press) if buttonState === MouseButtonState.Pressed(press.button) =>
      val dragged        = press.dragged ||
        mouseDistance(press.row, press.col, input.row, input.col) >
        options.mouseGestures.maxCellDistance
      val currentTargets = semanticTargetsAt(input.row, input.col)
      val target         = mouseCapture.orElse(currentTargets.headOption)
      mousePress = Some(press.copy(dragged = dragged))
      target.exists { value =>
        val remainsOnPressedTarget = value.component eq press.target.component
        dispatchSemanticTarget(
          value,
          event =
            if dragged && remainsOnPressedTarget then
              MouseEvent.Drag(mouseLocation(value, input), press.button, input.modifiers)
            else MouseEvent.Move(mouseLocation(value, input), buttonState, input.modifiers)
        )
      }
    case _                                                                     =>
      val targets = mouseCapture.toVector ++
        Option.when(mouseCapture.isEmpty)(semanticTargetsAt(input.row, input.col)).toVector.flatten
      dispatchSemanticTargets(
        targets,
        target => MouseEvent.Move(mouseLocation(target, input), buttonState, input.modifiers)
      )

  private def routeMouseRelease(input: TerminalInput.Mouse, button: MouseButton): Boolean =
    val targets = mouseCapture.toVector ++
      Option.when(mouseCapture.isEmpty)(semanticTargetsAt(input.row, input.col)).toVector.flatten
    var handled = dispatchSemanticTargets(
      targets,
      target => MouseEvent.Release(mouseLocation(target, input), button, input.modifiers)
    )
    mousePress.foreach { press =>
      val now        = options.mouseGestures.clock.nanoTime()
      val sameButton = press.button === button
      val sameTarget = mouseCapture.exists(_.component eq press.target.component) ||
        semanticTargetsAt(input.row, input.col).exists(_.component eq press.target.component)
      val inBounds   = mouseDistance(press.row, press.col, input.row, input.col) <=
        options.mouseGestures.maxCellDistance
      val inTime     = elapsedMillis(press.startedAtNanos, now) <=
        options.mouseGestures.clickMaxDurationMillis
      if sameButton && sameTarget && inBounds && inTime && !press.dragged then
        val target = mouseCapture.orElse(refreshMouseTarget(press.target)).getOrElse(press.target)
        val count  = nextClickCount(target, button, input, now)
        handled = dispatchSemanticTarget(
          target,
          MouseEvent.Click(mouseLocation(target, input), button, count, input.modifiers)
        ) || handled
        lastMouseClick = Some(TUI.MouseClick(target, button, input.row, input.col, now, count))
    }
    mousePress = None
    mouseCapture = None
    handled

  private def dispatchSemanticTargets(
      targets: Vector[TUI.MouseTarget],
      event: TUI.MouseTarget => MouseEvent
  ): Boolean =
    firstHandledSemanticTarget(targets, event).nonEmpty

  private def firstHandledSemanticTarget(
      targets: Vector[TUI.MouseTarget],
      event: TUI.MouseTarget => MouseEvent
  ): Option[TUI.MouseTarget] =
    targets.iterator.find(target => dispatchSemanticTarget(target, event(target)))

  private def dispatchSemanticTarget(target: TUI.MouseTarget, event: MouseEvent): Boolean =
    target.component match
      case handler: MouseEventHandler =>
        val result = handler.handleMouseEvent(event)
        applyMouseHandlerResult(target, result)
        result.handled
      case _                          => false

  private def applyMouseHandlerResult(
      target: TUI.MouseTarget,
      result: MouseHandlerResult
  ): Unit =
    result.captureIntent match
      case MouseCaptureIntent.Preserve => ()
      case MouseCaptureIntent.Capture  => mouseCapture = Some(target)
      case MouseCaptureIntent.Release  => mouseCapture = None
    result.focusIntent match
      case MouseFocusIntent.Preserve => ()
      case MouseFocusIntent.Request  => setFocusNow(target.component)
      case MouseFocusIntent.Clear    => setFocusNow(null)
    if result.renderIntent === MouseRenderIntent.Render then
      requestRender()
      flushRender()

  private def semanticTargetsAt(absoluteRow: Int, absoluteCol: Int): Vector[TUI.MouseTarget] =
    rendererPolicy.frameStartRow.fold(Vector.empty) { startRow =>
      val frameRow          = absoluteRow - startRow
      val visibleOverlayIds = overlayStack.iterator.filter(isOverlayVisible).map(_.id).toSet
      val overlayHit        = latestOverlayLayouts.reverseIterator
        .filter(layout => visibleOverlayIds.contains_(layout.id))
        .find(_.node.bounds.contains(frameRow, absoluteCol))
      overlayHit.fold(
        latestBaseLayout.toVector.flatMap(
          semanticTargetsInNode(frameRow, absoluteCol, startRow, _)
        )
      )(layout => semanticTargetsInNode(frameRow, absoluteCol, startRow, layout.node))
    }

  private def semanticTargetsInNode(
      frameRow: Int,
      frameCol: Int,
      frameStartRow: Int,
      node: LayoutNode
  ): Vector[TUI.MouseTarget] =
    if !node.bounds.contains(frameRow, frameCol) then Vector.empty
    else
      val children = node.children.reverseIterator.flatMap(
        semanticTargetsInNode(frameRow, frameCol, frameStartRow, _)
      ).toVector
      node.component match
        case _: MouseEventHandler =>
          val bounds = node.bounds.copy(row = node.bounds.row + frameStartRow)
          children :+ TUI.MouseTarget(node.component, bounds)
        case _                    => children

  private def mouseLocation(
      target: TUI.MouseTarget,
      input: TerminalInput.Mouse
  ): MouseEventLocation = MouseEventLocation(
    input.row,
    input.col,
    input.row - target.bounds.row,
    input.col - target.bounds.col,
    target.bounds
  )

  private def refreshMouseTarget(target: TUI.MouseTarget): Option[TUI.MouseTarget] =
    allCommittedMouseTargets.find(_.component eq target.component)

  private def allCommittedMouseTargets: Vector[TUI.MouseTarget] =
    rendererPolicy.frameStartRow.fold(Vector.empty) { startRow =>
      val visibleOverlayIds = overlayStack.iterator.filter(isOverlayVisible).map(_.id).toSet
      latestOverlayLayouts.reverseIterator
        .filter(layout => visibleOverlayIds.contains_(layout.id))
        .flatMap(layout => allMouseTargetsInNode(startRow, layout.node))
        .toVector ++ latestBaseLayout.toVector.flatMap(allMouseTargetsInNode(startRow, _))
    }

  private def allMouseTargetsInNode(frameStartRow: Int, node: LayoutNode): Vector[TUI.MouseTarget] =
    val children = node.children.flatMap(allMouseTargetsInNode(frameStartRow, _))
    node.component match
      case _: MouseEventHandler =>
        children :+ TUI.MouseTarget(
          node.component,
          node.bounds.copy(row = node.bounds.row + frameStartRow)
        )
      case _                    => children

  private def nextClickCount(
      target: TUI.MouseTarget,
      button: MouseButton,
      input: TerminalInput.Mouse,
      now: Long
  ): Int = lastMouseClick match
    case Some(click)
        if (click.target.component eq target.component) && click.button === button &&
          elapsedMillis(click.atNanos, now) <= options.mouseGestures.multiClickMaxDelayMillis &&
          mouseDistance(click.row, click.col, input.row, input.col) <=
          options.mouseGestures.maxCellDistance => click.count + 1
    case _ => 1

  private def mouseDistance(row1: Int, col1: Int, row2: Int, col2: Int): Int =
    math.max(math.abs(row1 - row2), math.abs(col1 - col2))

  private def elapsedMillis(startNanos: Long, endNanos: Long): Long =
    math.max(0L, endNanos - startNanos) / 1000000L

  private def routeLegacyMouseInput(input: TerminalInput.Mouse): InputResult =
    rendererPolicy.frameStartRow.fold(InputResult.Ignored) { startRow =>
      val frameRow          = input.row - startRow
      val visibleOverlayIds = overlayStack.iterator.filter(isOverlayVisible).map(_.id).toSet
      latestOverlayLayouts.reverseIterator
        .filter(layout => visibleOverlayIds.contains_(layout.id))
        .map(layout => routeLegacyMouseInNode(input, frameRow, input.col, startRow, layout.node))
        .find(_ !== InputResult.Ignored)
        .orElse(latestBaseLayout.map(routeLegacyMouseInNode(
          input,
          frameRow,
          input.col,
          startRow,
          _
        )))
        .getOrElse(InputResult.Ignored)
    }

  private def routeLegacyMouseInNode(
      input: TerminalInput.Mouse,
      frameRow: Int,
      frameCol: Int,
      frameStartRow: Int,
      node: LayoutNode
  ): InputResult =
    if !node.bounds.contains(frameRow, frameCol) then InputResult.Ignored
    else
      node.children.reverseIterator
        .map(routeLegacyMouseInNode(input, frameRow, frameCol, frameStartRow, _))
        .find(_ !== InputResult.Ignored)
        .getOrElse {
          node.component match
            case handler: MouseInputHandler =>
              val bounds = node.bounds
              handler.handleMouse(MouseInputContext(
                input,
                frameStartRow + bounds.row,
                bounds.col,
                bounds.width,
                bounds.height,
                frameRow - bounds.row,
                frameCol - bounds.col
              ))
            case _                          => InputResult.Ignored
        }

  private def handleInputResult(result: InputResult): Unit = result match
    case InputResult.Ignored               => ()
    case InputResult.Handled(shouldRender) =>
      if shouldRender then
        requestRender()
        flushRender()
    case InputResult.Exit                  => requestExit()

  private def isIgnoredKeyRelease(input: TerminalInput): Boolean = input match
    case TerminalInput.KeyEvent(_, _, KeyEventType.Release) =>
      !inputTarget.exists(_.wantsKeyRelease)
    case _                                                  => false

  private def renderOverlays(
      baseFrame: ComponentRender,
      width: Int,
      height: Int
  ): (ComponentRender, Vector[TUI.OverlayLayout]) =
    val visible  = overlayStack.toVector.filter(isOverlayVisible)
    val rendered = visible
      .sortBy(_.focusOrder)
      .flatMap { entry =>
        val initialLayout = OverlayRenderer.resolve(entry.options, overlayHeight = 0, width, height)
        val rawFrame      = entry.component.renderFrame(
          initialLayout.width,
          initialLayout.row,
          initialLayout.col
        )
        val validated     = rawFrame.render.validated(initialLayout.width)
        val clippedLines  = initialLayout.maxHeight.fold(validated.lines)(validated.lines.take)
        val overlayClip   = ClipRect(0, 0, initialLayout.width, clippedLines.length)
        val controls      = validated.controls.flatMap(
          TypedControlClipping.clipPlacement(_, overlayClip)
        )
        val cursors       = validated.cursorPlacements.filter(_.row < clippedLines.length)
        val markers       = validated.documentMetadata.markers.filter(
          _.position.row < clippedLines.length
        )
        val clippedFrame  = ComponentRender(
          clippedLines,
          controls,
          cursors,
          DocumentMetadata(markers)
        ).validated(initialLayout.width)
        val layout        = OverlayRenderer.resolve(entry.options, clippedLines.length, width, height)
        Option.when(clippedLines.nonEmpty) {
          val shifted = translateLayout(
            rawFrame.layout,
            rowDelta = layout.row - rawFrame.layout.bounds.row,
            colDelta = layout.col - rawFrame.layout.bounds.col
          )
          val node    = shifted.copy(
            bounds = LayoutBounds(layout.row, layout.col, layout.width, clippedLines.length)
          )
          (clippedFrame -> layout) -> TUI.OverlayLayout(entry.id, node)
        }
      }
    latestOverlayVisibility = visible.nonEmpty
    val frame    = OverlayRenderer.composite(baseFrame, rendered.map(_._1), width, height)
    frame -> rendered.map(_._2)

  private def makeOverlayHandle(entry: TUI.OverlayEntry): OverlayHandle = new OverlayHandle:
    override def id: OverlayId = entry.id

    override def hide(): Unit = publishAction(() => removeOverlay(entry))

    override def setHidden(hidden: Boolean): Unit = publishAction(() => {
      if overlayStack.exists(_ eq entry) && (entry.hidden !== hidden) then
        if hidden then clearMouseStateFor(entry.component)
        entry.hidden = hidden
        if hidden && focusedComponent.exists(_ eq entry.component) then restoreFocusAfter(entry)
        else if !hidden && entry.options.focusCapturing && isOverlayVisible(entry) then
          focusOverlay(entry)
        latestOverlayVisibility = overlayStack.exists(isOverlayVisible)
        requestRender()
    })

    override def isHidden: Boolean = lifecycleLock.synchronized(entry.hidden)

    override def focus(): Unit = publishAction(() => {
      if overlayStack.exists(_ eq entry) && entry.options.focusCapturing && isOverlayVisible(entry)
      then
        focusOverlay(entry)
        requestRender()
    })

    override def unfocus(options: Option[OverlayUnfocusOptions]): Unit =
      publishAction(() =>
        if focusedComponent.exists(_ eq entry.component) then
          options match
            case Some(value) => setFocusNow(value.target)
            case None        => restoreFocusAfter(entry)
          requestRender()
      )

    override def isFocused: Boolean =
      lifecycleLock.synchronized(focusedComponent.exists(_ eq entry.component))

    override def update(
        component: Component,
        options: Option[OverlayOptions],
        requestRender: Boolean
    ): Unit =
      publishAction(() =>
        if overlayStack.exists(_ eq entry) then
          val wasFocused = focusedComponent.exists(_ eq entry.component)
          clearMouseStateFor(entry.component)
          detachContext(entry.component)
          entry.component = component
          attachContext(component)
          options.foreach(entry.options = _)
          if wasFocused then setFocusNow(component)
          latestOverlayVisibility = overlayStack.exists(isOverlayVisible)
          if requestRender then TUI.this.requestRender()
      )

  private def removeOverlay(entry: TUI.OverlayEntry): Unit =
    val index = overlayStack.indexWhere(_ eq entry)
    if index >= 0 then
      val wasFocused = focusedComponent.exists(_ eq entry.component)
      clearMouseStateFor(entry.component)
      overlayStack.remove(index)
      detachContext(entry.component)
      if wasFocused then restoreFocusAfter(entry)
      latestOverlayVisibility = overlayStack.exists(isOverlayVisible)
      requestRender()

  private def restoreFocusAfter(entry: TUI.OverlayEntry): Unit =
    val fallback = topCapturingOverlay.filterNot(
      _ eq entry
    ).map(_.component).orElse(entry.preFocus).orElse(baseFocusedComponent)
    setFocusNow(fallback.orNull)

  private def focusOverlay(entry: TUI.OverlayEntry): Unit =
    nextFocusOrder += 1
    entry.focusOrder = nextFocusOrder
    setFocusNow(entry.component)

  private def topCapturingOverlay: Option[TUI.OverlayEntry] =
    overlayStack.filter(entry => entry.options.focusCapturing && isOverlayVisible(entry)).sortBy(
      _.focusOrder
    ).lastOption

  private def topVisibleOverlay: Option[TUI.OverlayEntry] =
    overlayStack.filter(isOverlayVisible).sortBy(_.focusOrder).lastOption

  private def isOverlayVisible(entry: TUI.OverlayEntry): Boolean =
    !entry.hidden && entry.options.visible(
      positiveDimension(terminal.columns),
      positiveDimension(terminal.rows)
    )

  private def isOverlayComponent(component: Component): Boolean =
    overlayStack.exists(_.component eq component)

  private def translateLayout(node: LayoutNode, rowDelta: Int, colDelta: Int): LayoutNode =
    node.copy(
      bounds = node.bounds.copy(
        row = node.bounds.row + rowDelta,
        col = node.bounds.col + colDelta
      ),
      children = node.children.map(translateLayout(_, rowDelta, colDelta))
    )

  private def retainedContextComponentsLocked(): Vector[Component] =
    (committedChildren.map(_.component) ++ overlayStack.map(_.component) ++
      viewportRoot.toVector).foldLeft(Vector.empty[Component]) { (result, component) =>
      if result.exists(_ eq component) then result else result :+ component
    }

  private def scheduleContextDetachLocked(): Unit =
    contextDetachScheduled = true
    val attached = retainedContextComponentsLocked()
    componentEffectCoordinator.enqueueCleanup(() => {
      attached.foreach(component => safeRuntimeCallback(detachContext(component)))
      viewportRootContextAttached = false
    })

  private def attachContext(component: Component): Unit = component match
    case contextual: ContextualComponent => contextual.tuiContext_=(Some(this))
    case _                               => ()

  private def detachContext(component: Component): Unit = component match
    case contextual: ContextualComponent => contextual.tuiContext_=(None)
    case _                               => ()

  private def safeRuntimeCallback(action: => Unit): Unit =
    try action
    catch case e: Throwable => handleRuntimeFailure(e)

  private def handleRuntimeFailure(error: Throwable): Unit =
    recordFailure(error)
    finishDeferredCleanupIfNeeded()

  private def claimCleanupLocked(): Unit =
    require(drainOwned)
    require(!cleanupOwned)
    require(lifecycleState === TUI.LifecycleState.Stopping)
    require(!startupOwner)
    require(queryWriteReservations === 0)
    require(retainedQueryCompletions.isEmpty)
    require(retainedAppendCompletions.isEmpty)
    require(pendingAppendCompletions.isEmpty)
    require(pendingAppends.isEmpty)
    require(activeAppend.isEmpty)
    require(pendingControlOutput.isEmpty)
    cleanupOwned = true
    postRestorationCutoff = false
    lifecycleState = TUI.LifecycleState.Cleaning

  private def cleanup(): Option[Throwable] =
    var failure                        = Option.empty[Throwable]
    def attempt(action: => Unit): Unit =
      try action
      catch
        case e: Throwable => failure match
            case Some(first) => first.addSuppressed(e)
            case None        => failure = Some(e)
    attempt(rendererPolicy.parkCursorForCleanup())
    if terminalColorSchemeNotificationsEnabled then
      attempt(
        terminalServices.writeData(
          TerminalColorProtocol.DisableColorSchemeNotifications,
          TUIDiagnosticWriteKind.Cleanup
        )
      )
    attempt(terminalServices.write(Terminal.drainInput(terminal)))
    attempt(rendererPolicy.restoreTypedControls())
    attempt(terminalServices.write(terminal.showCursor()))
    attempt(rendererPolicy.exitScreen())
    attempt(terminalServices.write(terminal.stop()))
    primaryScrollView.foreach { scrollView =>
      scrollView.clearSearch()
      scrollView.clearSelectionRetained()
    }
    currentClipboardResult = None
    clearSearchPaste()
    attempt(componentEffectCoordinator.finishGeneration())

    val (detachedQueryCompletions, detachedAppendCompletions) = lifecycleLock.synchronized {
      postRestorationCutoff = true
      val queries = postRestorationQueryCompletions.toVector
      val appends = postRestorationAppendCompletions.toVector
      postRestorationQueryCompletions.clear()
      postRestorationAppendCompletions.clear()
      queries -> appends
    }
    detachedQueryCompletions.foreach(processQueryCompletion)
    detachedAppendCompletions.foreach(processAppendCompletion)
    lifecycleLock.synchronized {
      backgroundColorFlight = None
      colorSchemeFlight = None
      cursorPositionFlight = None
      rawCorrelation = None
      pendingIngress.clear()
      replayContinuation = None
      appendOwnedKittyIds.clear()
      pendingAppendCompletions.clear()
      postRestorationAppendCompletions.clear()
      pendingAppends.clear()
      activeAppend = None
      acceptedIncompleteAppends = 0
      lifecycleState = TUI.LifecycleState.Stopped
      cleanupOwned = false
      lifecycleLock.notifyAll()
    }
    emitDiagnostic(TUIDiagnosticEvent.Lifecycle(
      TUIDiagnosticLifecycleState.Stopped,
      options.screenMode
    ))
    failure

  private def isCtrl(input: TerminalInput, char: String): Boolean = input match
    case TerminalInput.KeyEvent(TerminalKey.Character(value), modifiers, eventType) =>
      (eventType !== KeyEventType.Release) && (value === char) && modifiers.ctrl
    case _                                                                          => false

  private def renderNow(
      force: Boolean,
      clear: Boolean,
      recoveryGeneration: Option[Long]
  ): Unit =
    val generation                     = lifecycleLock.synchronized(resizeGeneration)
    val width                          = positiveDimension(terminal.columns)
    val height                         = positiveDimension(terminal.rows)
    val (baseFrame, composed, layouts) = RuntimeCounterScope.withCounters(testRuntimeCounters) {
      val initialBase                = viewportRoot match
        case Some(component) =>
          val layout = ViewportLayoutEngine.layout(component, width, height)
          RenderedFrame(layout.render, layout.root.toLayoutNode)
        case None            => root.renderFrame(width)
      val searchScroll               = primaryScrollViewIn(initialBase.layout)
      searchScroll.foreach(_.refreshSearch(width, testRuntimeCounters.recordSearchScans))
      val renderedBase               =
        if viewportRoot.nonEmpty && searchScroll.exists(_.searchState.active) then
          val component = viewportRoot.get
          val layout    = ViewportLayoutEngine.layout(component, width, height)
          RenderedFrame(layout.render, layout.root.toLayoutNode)
        else initialBase
      val (rendered, overlayLayouts) = renderOverlays(renderedBase.render, width, height)
      val withSearch                 = renderSearchLayer(rendered, width, height, searchScroll)
      (renderedBase, withSearch, overlayLayouts)
    }
    val frame                          = rendererPolicy.prepareFrame(composed.validated(width), width)
    validateRetainedKittyOwnership(frame)
    val widthChanged                   = (rendererPolicy.retainedWidth !== 0) &&
      (rendererPolicy.retainedWidth !== width)
    val heightChanged                  = (rendererPolicy.retainedHeight !== 0) &&
      (rendererPolicy.retainedHeight !== height)
    val resizeGeometryChanged          = widthChanged || heightChanged
    val forceForRender                 =
      force && (recoveryGeneration.isEmpty || resizeGeometryChanged)
    val clearForRender                 =
      clear && (recoveryGeneration.isEmpty || resizeGeometryChanged)
    val recovery                       = Option.when(
      clearForRender && resizeGeometryChanged && rendererPolicy.retainedFrame.nonEmpty &&
        recoveryGeneration.exists(_ === generation) && options.normalResizeRecovery.nonEmpty &&
        !rendererPolicy.isAlternateScreen &&
        options.normalResizeClearPolicy === NormalResizeClearPolicy.PreserveScrollback
    ) {
      prepareResizeRecovery(frame, width, height, generation)
    }

    val currentWidth                      = positiveDimension(terminal.columns)
    val currentHeight                     = positiveDimension(terminal.rows)
    val (currentGeneration, stillRunning) = lifecycleLock.synchronized(
      resizeGeneration -> (lifecycleState === TUI.LifecycleState.Running)
    )
    if !stillRunning then
      recovery.foreach(value =>
        emitResizeRecoveryDiagnostic(
          TUIDiagnosticResizeRecoveryOutcome.Discarded,
          failure = None,
          value.maxRows,
          value.lines.length,
          value.generation
        )
      )
    else if (generation !== currentGeneration) || (width !== currentWidth) ||
      (height !== currentHeight)
    then
      lifecycleLock.synchronized {
        if lifecycleState === TUI.LifecycleState.Running then
          renderRequested = true
          forceRenderRequested = true
          clearRequested = true
          if recoveryGeneration.nonEmpty then
            pendingResizeRecoveryGeneration = Some(resizeGeneration)
      }
      recovery.foreach(value =>
        emitResizeRecoveryDiagnostic(
          TUIDiagnosticResizeRecoveryOutcome.Discarded,
          Some(TUIDiagnosticResizeRecoveryFailure.StaleGeometry),
          value.maxRows,
          value.lines.length,
          value.generation
        )
      )
    else
      try rendererPolicy.render(frame, width, height, forceForRender, clearForRender, recovery)
      catch
        case error: Throwable =>
          recovery.foreach(value =>
            emitResizeRecoveryDiagnostic(
              TUIDiagnosticResizeRecoveryOutcome.Failed,
              Some(TUIDiagnosticResizeRecoveryFailure.Write),
              value.maxRows,
              value.lines.length,
              value.generation
            )
          )
          throw error
      latestBaseLayout = Some(baseFrame.layout)
      latestOverlayLayouts = layouts
      mouseCapture = mouseCapture.flatMap(refreshMouseTarget)
      mousePress = mousePress.flatMap(press =>
        refreshMouseTarget(press.target).map(target => press.copy(target = target))
      )
      recovery.foreach(value =>
        emitResizeRecoveryDiagnostic(
          TUIDiagnosticResizeRecoveryOutcome.Completed,
          failure = None,
          value.maxRows,
          value.lines.length,
          value.generation
        )
      )

  private def prepareResizeRecovery(
      frame: PreparedFrame,
      width: Int,
      height: Int,
      generation: Long
  ): TUI.PreparedResizeRecovery =
    val liveFrameFootprint         = math.max(1, frame.lines.length)
    val currentMaxRows             = math.max(0, height - liveFrameFootprint)
    val previousLiveFrameFootprint = rendererPolicy.retainedFrame.fold(1)(value =>
      math.max(1, value.lines.length)
    )
    val previousMaxRows            = math.max(
      0,
      rendererPolicy.retainedHeight - previousLiveFrameFootprint
    )
    val maxRows                    = math.min(currentMaxRows, previousMaxRows)
    if maxRows === 0 then TUI.PreparedResizeRecovery(Vector.empty, maxRows, generation)
    else
      val context  = NormalResizeRecoveryContext(
        width,
        height,
        maxRows,
        rendererPolicy.retainedWidth,
        rendererPolicy.retainedHeight,
        previousMaxRows
      )
      val rawLines =
        try
          Option(options.normalResizeRecovery.get.render(context)).getOrElse(
            throw NullPointerException("Normal resize recovery provider returned null")
          )
        catch
          case error: Throwable =>
            emitResizeRecoveryDiagnostic(
              TUIDiagnosticResizeRecoveryOutcome.Failed,
              Some(TUIDiagnosticResizeRecoveryFailure.Provider),
              maxRows,
              rowCount = 0,
              generation
            )
            throw error
      if rawLines.length > maxRows then
        emitResizeRecoveryDiagnostic(
          TUIDiagnosticResizeRecoveryOutcome.Failed,
          Some(TUIDiagnosticResizeRecoveryFailure.RowBudget),
          maxRows,
          rawLines.length,
          generation
        )
        throw IllegalArgumentException(
          s"Normal resize recovery returned ${rawLines.length} rows for budget $maxRows"
        )
      val lines    =
        try rendererPolicy.prepareResizeRecovery(rawLines, width)
        catch
          case error: Throwable =>
            emitResizeRecoveryDiagnostic(
              TUIDiagnosticResizeRecoveryOutcome.Failed,
              Some(TUIDiagnosticResizeRecoveryFailure.Provider),
              maxRows,
              rawLines.length,
              generation
            )
            throw error
      TUI.PreparedResizeRecovery(lines, maxRows, generation)

  private def emitResizeRecoveryDiagnostic(
      outcome: TUIDiagnosticResizeRecoveryOutcome,
      failure: Option[TUIDiagnosticResizeRecoveryFailure],
      maxRows: Int,
      rowCount: Int,
      generation: Long
  ): Unit =
    emitDiagnostic(TUIDiagnosticEvent.ResizeRecovery(
      outcome,
      failure,
      maxRows,
      rowCount,
      generation
    ))

  private def renderSearchLayer(
      frame: ComponentRender,
      width: Int,
      height: Int,
      searchScroll: Option[ScrollView]
  ): ComponentRender =
    searchScroll.filter(_.searchState.active).fold(frame) { scrollView =>
      val state     = scrollView.searchState
      val position  = state.currentMatch.fold("0")(value => value.toString)
      val status    = s"Search: ${state.query} [$position/${state.matchCount}]"
      val row       = math.max(0, height - 1)
      val line      = Ansi.padRight(Ansi.truncateToWidth(status, width, ""), width)
      val lines     = frame.lines.padTo(height, "").updated(row, line)
      val cursorCol = math.min(math.max(0, width - 1), Ansi.visibleWidth("Search: " + state.query))
      frame.copy(
        lines = lines,
        controls =
          frame.controls.filter(placement => placement.row + placement.control.rows <= row),
        cursorPlacements = Vector(CursorPlacement(row, cursorCol)),
        documentMetadata = DocumentMetadata(
          frame.documentMetadata.markers.filter(_.position.row < row)
        )
      )
    }

  private def validateRetainedKittyOwnership(frame: PreparedFrame): Unit =
    frame.controls.flatMap(kittyImageId).find(appendOwnedKittyIds).foreach { imageId =>
      throw IllegalArgumentException(
        s"Retained Kitty image ID $imageId collides with append-only ownership"
      )
    }

  private def kittyImageId(placement: TerminalControlPlacement): Option[Int] =
    placement.control.details match
      case kitty: scalatui.terminal.TerminalRenderControlDetails.KittyImage =>
        Some(kitty.imageId)
      case _                                                                => None

  private def positiveDimension(value: Int): Int = math.max(1, value)

object TUI:
  /**
   * Construct an opt-in fixed-height fullscreen viewport on JVM or Scala Native.
   *
   * The runtime enters the alternate screen for each start and restores it during cleanup. The
   * supplied layout root owns the complete terminal viewport. The renderer owns current terminal
   * bounds, layout clipping, primary-scroll fallback, search, selection, and typed image clipping.
   * Existing [[TUI]] constructors and `TUIOptions(screenMode = TUIScreenMode.Alternate)` keep their
   * width-only behavior. Runtime renderer switching, Windows support, Node scheduling, mandatory
   * host clipboard integration, OSC 52, and built-in LaTeX parsing are not provided.
   *
   * @param terminal
   *   Terminal backend owned for the complete start through cleanup lifecycle.
   * @param layoutRoot
   *   Component root measured and painted into each positive terminal width and height.
   * @param options
   *   Session options. Its `screenMode` value does not replace fullscreen viewport policy.
   */
  def fullscreen(
      terminal: Terminal,
      layoutRoot: Component,
      options: TUIOptions = TUIOptions()
  ): TUI = new TUI(terminal, options, Some(layoutRoot))

  private[core] final case class PreparedResizeRecovery(
      lines: Vector[String],
      maxRows: Int,
      generation: Long
  )

  private enum LifecycleState derives CanEqual:
    case Starting, Running, Stopping, Cleaning, Stopped

  private val IngressCapacity           = 4096
  private val AppendCapacity            = 64
  private val AppendKittyLedgerCapacity = 4096

  private enum Work:
    case Ingress(ingress: TUI.Ingress)
    case QueryCompletion(completion: TUI.QueryCompletion[?])
    case AppendCompletion(completion: TUI.AppendCompletion)
    case ComponentEffect
    case Structural(claimed: TUI.ClaimedStructural)
    case Action(action: () => Unit)
    case Control(action: () => Unit)
    case Append(operation: TUI.AppendOperation)
    case Render(force: Boolean, clear: Boolean, recoveryGeneration: Option[Long])
    case Cleanup
    case Done

  private enum Ingress:
    case Input(input: TerminalInput)
    case AppendCompletion(completion: TUI.AppendCompletion)
    case Protocol(
        completions: Vector[TUI.QueryCompletion[?]],
        notifications: Vector[() => Unit]
    )

  private val MaxReplayEvents = TerminalInputChunk.MaxBytes + 2

  private final case class ReplayContinuation(events: Vector[Ingress], nextIndex: Int)

  private enum IngressClassification:
    case Consumed(commit: () => Unit)
    case Blocked
    case Publish(first: Ingress, remaining: Vector[Ingress], commit: () => Unit)

  private final case class ChildEntry(id: Long, component: Component)

  private final case class MouseTarget(component: Component, bounds: LayoutBounds)

  private final case class MousePress(
      target: MouseTarget,
      button: MouseButton,
      row: Int,
      col: Int,
      startedAtNanos: Long,
      dragged: Boolean
  )

  private final case class MouseClick(
      target: MouseTarget,
      button: MouseButton,
      row: Int,
      col: Int,
      atNanos: Long,
      count: Int
  )

  private final case class ClaimedStructural(effects: Vector[StructuralEffect])

  private enum StructuralEffect:
    case Add(component: Component, attach: Boolean)
    case Remove(component: Component, detach: Boolean)

  private enum OrdinaryCategory:
    case Structural, Action, Ingress, Control, Append, Render

  private final case class AppendOperation(
      id: Long,
      component: Component,
      callback: AppendResult => Unit,
      violation: AppendViolationLatch
  )

  private final case class AppendSnapshot(
      generation: Long,
      width: Int,
      height: Int,
      frame: PreparedFrame,
      cellDimensions: ImageCellDimensions,
      capabilities: TerminalCapabilities,
      capabilityOverrides: TerminalCapabilityOverrides
  )

  private final case class AppendCompletion(
      callback: AppendResult => Unit,
      result: AppendResult,
      order: Option[Long],
      outcome: TUIDiagnosticAppendOutcome,
      failure: Option[TUIDiagnosticAppendFailure],
      rowCount: Int,
      controlCount: Int
  )

  private enum AppendPublication:
    case Direct(completion: AppendCompletion)
    case Queued(own: Boolean)

  private enum AppendCommitDecision:
    case Retry
    case Reject(reason: AppendRejection)
    case Fail(error: Throwable)
    case Publish

  private final class AppendClassifiedFailure(
      val original: Throwable,
      val category: TUIDiagnosticAppendFailure
  ) extends RuntimeException(original)

  private final class AppendContextViolation(operation: String)
      extends IllegalStateException(s"Restricted append context forbids $operation")

  private final class AppendViolationLatch:
    private var retainedFailure = Option.empty[Throwable]

    def violate(operation: String): Nothing = synchronized {
      val failure = retainedFailure.getOrElse {
        val created = AppendContextViolation(operation)
        retainedFailure = Some(created)
        created
      }
      throw failure
    }

    def failure: Option[Throwable] = synchronized(retainedFailure)

    /** Linearize final publication against concurrent use of every revoked operation context. */
    def claimPublication(): Option[Throwable] = synchronized(retainedFailure)

  private object AppendViolationLatch:
    def apply(): AppendViolationLatch = new AppendViolationLatch

  private final class RestrictedAppendContext(
      dimensions: ImageCellDimensions,
      capabilities: TerminalCapabilities,
      capabilityOverrides: TerminalCapabilityOverrides,
      violation: AppendViolationLatch
  ) extends TUIContext:
    private var revoked = false

    def revoke(): Unit = synchronized { revoked = true }

    private def ensureActive(): Unit = synchronized {
      if revoked then violation.violate("access after revocation")
    }

    private def forbidden(operation: String): Nothing =
      ensureActive()
      violation.violate(operation)

    override def imageCellDimensions: ImageCellDimensions =
      ensureActive()
      dimensions

    override def terminalCapabilities: TerminalCapabilities =
      ensureActive()
      capabilities

    override def terminalCapabilityOverrides: TerminalCapabilityOverrides =
      ensureActive()
      capabilityOverrides

    override def requestRender(force: Boolean): Unit         = forbidden("render scheduling")
    override def flushRender(): Unit                         = forbidden("nested flush")
    override def requestExit(): Unit                         = forbidden("exit")
    override def setFocus(component: Component | Null): Unit = forbidden("focus")
    override def overlays: OverlayHost                       = forbidden("overlays")

  private object RestrictedAppendContext:
    def apply(
        dimensions: ImageCellDimensions,
        capabilities: TerminalCapabilities,
        capabilityOverrides: TerminalCapabilityOverrides,
        violation: AppendViolationLatch
    ): RestrictedAppendContext =
      new RestrictedAppendContext(dimensions, capabilities, capabilityOverrides, violation)

  private enum ActionPublication:
    case Accepted(own: Boolean)
    case Rejected

  private enum StructuralOperation:
    case Add(entry: ChildEntry)
    case Remove(entryId: Long)
    case Clear(entryIds: Set[Long])
    case NoOp

  private enum QueryFlightPhase derives CanEqual:
    case Reserved, Emitted

  private final case class QueryFlight[A](
      id: Long,
      phase: QueryFlightPhase,
      subscribers: Vector[QuerySubscriber[A]]
  )
  private final case class RawCorrelation(
      kind: TerminalRawKind,
      chunks: Vector[TerminalInputChunk],
      length: Int
  )

  private enum QuerySubscriberState derives CanEqual:
    case Active, Claimed, Cancelled, Completed

  private final class QuerySubscriber[A](
      val id: Long,
      val callback: TerminalQueryResult[A] => Unit
  ):
    var state: QuerySubscriberState = QuerySubscriberState.Active

  private final class QueryCompletion[A](
      val subscriber: QuerySubscriber[A],
      val result: TerminalQueryResult[A]
  ):
    def invoke(): Unit = subscriber.callback(result)

  private enum QueryRegistration[A]:
    case Registered(subscriber: QuerySubscriber[A], flightId: Option[Long])
    case QueuedStopped(subscriber: QuerySubscriber[A], own: Boolean)

  private final class OverlayEntry(
      val id: OverlayId,
      var component: Component,
      var options: OverlayOptions,
      var preFocus: Option[Component],
      var hidden: Boolean,
      var focusOrder: Long
  )

  private final case class OverlayLayout(id: OverlayId, node: LayoutNode)

  final case class RenderSanitization(
      lineIndex: Int,
      originalWidth: Int,
      targetWidth: Int,
      original: String,
      sanitized: String
  ) derives CanEqual

  val SyncStart: String                 = "\u001b[?2026h"
  val SyncEnd: String                   = "\u001b[?2026l"
  val AutoWrapOff: String               = "\u001b[?7l"
  val AutoWrapOn: String                = "\u001b[?7h"
  val AlternateScreenEnter: String      = "\u001b[?1049h"
  val AlternateScreenExit: String       = "\u001b[?1049l"
  val NormalScreenClear: String         = "\u001b[2J\u001b[H\u001b[3J"
  val NormalScreenViewportClear: String = "\u001b[2J\u001b[H"
  val AlternateScreenClear: String      = NormalScreenViewportClear
  val LineReset: String                 = "\u001b[0m\u001b]8;;\u0007"

private val SyncStart                 = TUI.SyncStart
private val SyncEnd                   = TUI.SyncEnd
private val AutoWrapOff               = TUI.AutoWrapOff
private val AutoWrapOn                = TUI.AutoWrapOn
private val AlternateScreenEnter      = TUI.AlternateScreenEnter
private val AlternateScreenExit       = TUI.AlternateScreenExit
private val NormalScreenClear         = TUI.NormalScreenClear
private val NormalScreenViewportClear = TUI.NormalScreenViewportClear
private val AlternateScreenClear      = TUI.AlternateScreenClear
private val LineReset                 = TUI.LineReset
