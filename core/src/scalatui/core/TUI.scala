package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.syntax.Containment.*
import scalatui.terminal.{
  KeyEventType,
  RgbColor,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalCursorProtocol,
  TerminalImageProtocol,
  MouseInputContext,
  TerminalInput,
  TerminalInputChunk,
  TerminalKey,
  TerminalRawKind,
  TerminalRawTermination,
  TerminalRenderControl,
  TerminalRenderControlEncoder
}

import scala.collection.mutable.ArrayBuffer

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

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
 *   Enable opt-in terminal mouse reporting for coordinate-aware mouse input. Disabled by default
 *   because terminal mouse reporting can affect normal text selection.
 */
final case class TUIOptions(
    hardwareCursorPositioning: Boolean = false,
    screenMode: TUIScreenMode = TUIScreenMode.Normal,
    mouseInput: Boolean = false
) derives CanEqual

/**
 * Main terminal UI runtime with a differential renderer and a synchronous, single-owner work drain.
 * Application callbacks run without the lifecycle lock; backend output is serialized by a separate
 * write boundary that is never nested with that lock.
 */
final class TUI(val terminal: Terminal, val options: TUIOptions = TUIOptions())
    extends TUIContext,
      OverlayHost:
  private val root                            = Container()
  private val lifecycleLock                   = Object()
  private val terminalWriteLock               = Object()
  private val overlayStack                    = ArrayBuffer.empty[TUI.OverlayEntry]
  private val pendingIngress                  = scala.collection.mutable.ArrayDeque.empty[TUI.Ingress]
  private var replayContinuation              = Option.empty[TUI.ReplayContinuation]
  private val retainedQueryCompletions        = ArrayBuffer.empty[TUI.QueryCompletion[?]]
  private val postRestorationQueryCompletions = ArrayBuffer.empty[TUI.QueryCompletion[?]]
  private var postRestorationCutoff           = false

  private val pendingActions                          = ArrayBuffer.empty[() => Unit]
  private val pendingControlOutput                    = ArrayBuffer.empty[() => Unit]
  private val pendingStructural                       = ArrayBuffer.empty[TUI.StructuralOperation]
  private var desiredChildren                         = Vector.empty[TUI.ChildEntry]
  private var committedChildren                       = Vector.empty[TUI.ChildEntry]
  private var nextChildEntryId                        = 0L
  private var lifecycleState: TUI.LifecycleState      = TUI.LifecycleState.Stopped
  private var startupOwner                            = false
  private var drainOwned                              = false
  private var lastOrdinaryCategory                    = TUI.OrdinaryCategory.Render
  private var cleanupOwned                            = false
  private var resizeGeneration                        = 0L
  @volatile private var latestOverlayVisibility       = false
  private var queryWriteReservations                  = 0
  private var nextQueryFlightId                       = 0L
  private var nextQuerySubscriberId                   = 0L
  private var previousFrame                           = Option.empty[TUI.PreparedFrame]
  private var latestBaseLayout                        = Option.empty[LayoutNode]
  private var latestOverlayLayouts                    = Vector.empty[TUI.OverlayLayout]
  private var latestFrameStartRow                     = Option.empty[Int]
  private var previousWidth                           = 0
  private var previousHeight                          = 0
  private var cursorRow                               = 0
  private var focusedComponent: Option[Component]     = None
  private var baseFocusedComponent: Option[Component] = None
  private var exitRequested                           = false
  private var renderRequested                         = false
  private var forceRenderRequested                    = false
  private var clearRequested                          = false
  private var autoWrapRestoreNeeded                   = false
  private var alternateScreenEntered                  = false
  private var sanitizationCount                       = 0
  private var lastSanitization                        = Option.empty[TUI.RenderSanitization]
  private var runtimeFailure                          = Option.empty[Throwable]
  private var nextOverlayId                           = 0L
  private var nextFocusOrder                          = 0L
  private var backgroundColorFlight                   = Option.empty[TUI.QueryFlight[RgbColor]]
  private var colorSchemeFlight                       = Option.empty[TUI.QueryFlight[TerminalColorScheme]]
  private var cursorPositionFlight                    =
    Option.empty[TUI.QueryFlight[TerminalCursorProtocol.CursorPosition]]
  private var rawCorrelation                          = Option.empty[TUI.RawCorrelation]
  private val terminalColorSchemeListeners            = ArrayBuffer.empty[TerminalColorScheme => Unit]
  private var terminalColorSchemeNotificationsEnabled = false
  private val inputListeners                          = ArrayBuffer.empty[TerminalInput => InputResult]

  var handlesControlC: Boolean = true
  var exitsOnEscape: Boolean   = false

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

  /** Number of final rendered lines sanitized because they exceeded terminal width. */
  def sanitizedLineCount: Int = lifecycleLock.synchronized(sanitizationCount)

  /** Most recent final rendered line sanitization diagnostic, if any occurred. */
  def lastSanitizedLine: Option[TUI.RenderSanitization] =
    lifecycleLock.synchronized(lastSanitization)

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
      writeTerminal(Terminal.setTitle(terminal, title))
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
      writeTerminal(Terminal.setProgress(terminal, active))
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
        writeTerminal(terminal.write(
          if enabled then TerminalColorProtocol.EnableColorSchemeNotifications
          else TerminalColorProtocol.DisableColorSchemeNotifications
        ))
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
    val shouldStart = lifecycleLock.synchronized {
      if (lifecycleState !== TUI.LifecycleState.Stopped) || drainOwned then false
      else
        exitRequested = false
        runtimeFailure = None
        lifecycleState = TUI.LifecycleState.Starting
        startupOwner = true
        true
    }
    if shouldStart then
      try
        Terminal.setMouseReporting(terminal, options.mouseInput)
        writeTerminal(terminal.start(
          input => safeRuntimeCallback(publishInput(input)),
          () => safeRuntimeCallback(publishResize())
        ))
        if startupMayContinue then
          enterAlternateScreenIfConfigured()
        if startupMayContinue && terminalColorSchemeNotificationsEnabled then
          writeTerminal(terminal.write(TerminalColorProtocol.EnableColorSchemeNotifications))
        if startupMayContinue then
          TerminalImageProtocol.resetCellDimensions()
          writeTerminal(terminal.hideCursor())
          writeTerminal(terminal.write(TerminalImageProtocol.QueryCellDimensions))
          if !options.mouseInput then
            requestRenderInternal(force = true, clear = isAlternateScreenMode)
        lifecycleLock.synchronized {
          startupOwner = false
          if lifecycleState === TUI.LifecycleState.Starting then
            lifecycleState = TUI.LifecycleState.Running
        }
        if options.mouseInput && lifecycleLock.synchronized(
            lifecycleState === TUI.LifecycleState.Running
          )
        then initializeMouseFrameOrigin()
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
    val own = lifecycleLock.synchronized {
      lifecycleState match
        case TUI.LifecycleState.Stopped | TUI.LifecycleState.Stopping |
            TUI.LifecycleState.Cleaning => false
        case _ =>
          transitionToStoppingLocked()
          !startupOwner && !drainOwned && queryWriteReservations === 0
    }
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

  private def handleInput(input: TerminalInput): Unit =
    if isMouseInputDisabled(input) then ()
    else if routeGlobalInputListeners(input) !== InputResult.Ignored then ()
    else if isIgnoredKeyRelease(input) then ()
    else if handlesControlC && isCtrl(input, "c") then requestExit()
    else if exitsOnEscape && (input === TerminalInput.Key(TerminalKey.Escape)) then requestExit()
    else
      input match
        case mouse: TerminalInput.Mouse => routeMouseInput(mouse)
        case _                          =>
          inputTarget.map(_.handleInputResult(input)).foreach(handleInputResult)

  private def isMouseInputDisabled(input: TerminalInput): Boolean = input match
    case _: TerminalInput.Mouse => !options.mouseInput
    case _                      => false

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
      writeTerminal(terminal.write(request))
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
    latestFrameStartRow = None
    val result                 = AtomicReference(Option.empty[TerminalCursorProtocol.CursorPosition])
    val ready                  = CountDownLatch(1)
    val initializationComplete = AtomicBoolean(false)
    subscribeQuery(
      () => cursorPositionFlight,
      flight => cursorPositionFlight = flight,
      "\r" + TerminalCursorProtocol.CursorPositionQuery,
      {
        case TerminalQueryResult.Success(position) =>
          result.set(Some(position))
          ready.countDown()
          if initializationComplete.get() then establishMouseFrameOrigin(position)
        case _                                     => ready.countDown()
      }
    )
    ready.await(math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS)
    initializationComplete.set(true)
    result.get().foreach(establishMouseFrameOrigin)
    requestRenderInternal(force = true, clear = isAlternateScreenMode)

  private def establishMouseFrameOrigin(
      position: TerminalCursorProtocol.CursorPosition
  ): Unit =
    latestFrameStartRow = Some(position.row)
    requestRenderInternal(force = true, clear = isAlternateScreenMode)

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
      val previousDimensions = TerminalImageProtocol.cellDimensions
      TUI.IngressClassification.Publish(
        TUI.Ingress.Protocol(Vector.empty, Vector.empty),
        Vector.empty,
        () =>
          rawCorrelation = None
          TerminalImageProtocol.parseCellSizeResponse(data).foreach { dimensions =>
            TerminalImageProtocol.setCellDimensions(
              dimensions.widthPx,
              dimensions.heightPx
            ).foreach {
              updatedDimensions =>
                if updatedDimensions !== previousDimensions then renderRequested = true
            }
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
    lifecycleLock.synchronized {
      resizeGeneration += 1
      if lifecycleState === TUI.LifecycleState.Running then
        renderRequested = true
        forceRenderRequested = true
        clearRequested = true
    }
    drainOrReturn()

  private def drainOrReturn(propagateCleanupFailure: Boolean = false): Unit =
    val own = lifecycleLock.synchronized {
      if drainOwned || startupOwner then false
      else
        drainOwned = true
        true
    }
    if own then drainWork(propagateCleanupFailure)

  private def drainWork(propagateCleanupFailure: Boolean): Unit =
    var continue               = true
    var completed              = false
    var deferredCleanupFailure = Option.empty[Throwable]
    try
      while continue do
        val work = lifecycleLock.synchronized {
          if retainedQueryCompletions.nonEmpty then
            TUI.Work.QueryCompletion(retainedQueryCompletions.remove(0))
          else if lifecycleState === TUI.LifecycleState.Stopping then
            if pendingControlOutput.nonEmpty then
              TUI.Work.Control(pendingControlOutput.remove(0))
            else if queryWriteReservations === 0 then
              claimCleanupLocked()
              TUI.Work.Cleanup
            else
              drainOwned = false
              TUI.Work.Done
          else if hasOrdinaryWorkLocked then selectOrdinaryWorkLocked()
          else
            drainOwned = false
            TUI.Work.Done
        }
        work match
          case TUI.Work.Ingress(ingress)            => processIngress(ingress)
          case TUI.Work.QueryCompletion(completion) => processQueryCompletion(completion)
          case TUI.Work.Structural(claimed)         => applyStructural(claimed)
          case TUI.Work.Action(action)              =>
            try action()
            catch case error: Throwable => recordFailure(error)
          case TUI.Work.Control(action)             => action()
          case TUI.Work.Render(force, clear)        => renderNow(force, clear)
          case TUI.Work.Cleanup                     =>
            deferredCleanupFailure = cleanup()
          case TUI.Work.Done                        => continue = false
      completed = true
    catch
      case e: Throwable =>
        recordFailure(e)
        lifecycleLock.synchronized { drainOwned = false }
        finishDeferredCleanupIfNeeded()
    if completed then
      deferredCleanupFailure.foreach { error =>
        if propagateCleanupFailure then throw error
        else recordFailure(error)
      }

  private def processIngress(ingress: TUI.Ingress): Unit = ingress match
    case TUI.Ingress.Input(input)                         => handleInput(input)
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
          if direct then writeTerminal(action)
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
    pendingStructural.nonEmpty || pendingActions.nonEmpty || pendingIngress.nonEmpty ||
      pendingControlOutput.nonEmpty || renderRequested

  private def selectOrdinaryWorkLocked(): TUI.Work =
    val categories = TUI.OrdinaryCategory.values
    var offset     = 1
    var selected   = Option.empty[TUI.OrdinaryCategory]
    while selected.isEmpty && offset <= categories.length do
      val candidate = categories((lastOrdinaryCategory.ordinal + offset) % categories.length)
      if ordinaryCategoryReadyLocked(candidate) then selected = Some(candidate)
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
      case TUI.OrdinaryCategory.Render     =>
        renderRequested = false
        val force = forceRenderRequested
        val clear = clearRequested
        forceRenderRequested = false
        clearRequested = false
        TUI.Work.Render(force, clear)

  private def ordinaryCategoryReadyLocked(category: TUI.OrdinaryCategory): Boolean = category match
    case TUI.OrdinaryCategory.Structural => pendingStructural.nonEmpty
    case TUI.OrdinaryCategory.Action     => pendingActions.nonEmpty
    case TUI.OrdinaryCategory.Ingress    => pendingIngress.nonEmpty
    case TUI.OrdinaryCategory.Control    => pendingControlOutput.nonEmpty
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
        root.removeChild(component)
        if detach then detachContext(component)
    }

  private def writeTerminal(action: => Unit): Unit = terminalWriteLock.synchronized(action)

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
    if (lifecycleState !== TUI.LifecycleState.Stopped) &&
      (lifecycleState !== TUI.LifecycleState.Cleaning)
    then transitionToStoppingLocked()
    lifecycleLock.notifyAll()

  private def transitionToStoppingLocked(): Unit =
    lifecycleState = TUI.LifecycleState.Stopping
    pendingIngress.foreach {
      case TUI.Ingress.Protocol(completions, _) => retainedQueryCompletions ++= completions
      case _                                    => ()
    }
    pendingIngress.clear()
    replayContinuation = None
    pendingActions.clear()
    pendingStructural.clear()
    desiredChildren = committedChildren
    renderRequested = false
    forceRenderRequested = false
    clearRequested = false
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
    val frameStart = latestFrameStartRow
    val result     = frameStart match
      case Some(startRow) =>
        val frameRow          = input.row - startRow
        val visibleOverlayIds = overlayStack.iterator
          .filter(isOverlayVisible)
          .map(_.id)
          .toSet
        val overlayResult     = latestOverlayLayouts.reverseIterator
          .filter(layout => visibleOverlayIds.contains_(layout.id))
          .map(layout => routeMouseInNode(input, frameRow, input.col, startRow, layout.node))
          .find(_ !== InputResult.Ignored)
        overlayResult
          .getOrElse(
            latestBaseLayout
              .map(routeMouseInNode(input, frameRow, input.col, startRow, _))
              .getOrElse(InputResult.Ignored)
          )
      case None           => InputResult.Ignored
    handleInputResult(result)

  private def routeMouseInNode(
      input: TerminalInput.Mouse,
      frameRow: Int,
      frameCol: Int,
      frameStartRow: Int,
      node: LayoutNode
  ): InputResult =
    if !node.bounds.contains(frameRow, frameCol) then InputResult.Ignored
    else
      val childResult = node.children.reverseIterator
        .map(routeMouseInNode(input, frameRow, frameCol, frameStartRow, _))
        .find(_ !== InputResult.Ignored)
      childResult.getOrElse {
        node.component match
          case handler: MouseInputHandler =>
            val bounds = node.bounds
            handler.handleMouse(MouseInputContext(
              input = input,
              boundsRow = frameStartRow + bounds.row,
              boundsCol = bounds.col,
              boundsWidth = bounds.width,
              boundsHeight = bounds.height,
              localRow = frameRow - bounds.row,
              localCol = frameCol - bounds.col
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
        val controls      = validated.controls.filter(_.row < clippedLines.length)
        val cursors       = validated.cursorPlacements.filter(_.row < clippedLines.length)
        val clippedFrame  =
          ComponentRender(clippedLines, controls, cursors).validated(initialLayout.width)
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
    attempt(parkCursorBelowContentIfNeeded())
    if terminalColorSchemeNotificationsEnabled then
      attempt(
        writeTerminal(terminal.write(TerminalColorProtocol.DisableColorSchemeNotifications))
      )
    attempt(writeTerminal(Terminal.drainInput(terminal)))
    attempt(restoreAutoWrapIfNeeded())
    attempt(writeTerminal(terminal.showCursor()))
    attempt(exitAlternateScreenIfNeeded())
    attempt(writeTerminal(terminal.stop()))
    attempt(Terminal.setMouseReporting(terminal, enabled = false))

    val detachedCompletions = lifecycleLock.synchronized {
      postRestorationCutoff = true
      val detached = postRestorationQueryCompletions.toVector
      postRestorationQueryCompletions.clear()
      detached
    }
    detachedCompletions.foreach(processQueryCompletion)
    lifecycleLock.synchronized {
      backgroundColorFlight = None
      colorSchemeFlight = None
      cursorPositionFlight = None
      rawCorrelation = None
      pendingIngress.clear()
      replayContinuation = None
      lifecycleState = TUI.LifecycleState.Stopped
      cleanupOwned = false
      lifecycleLock.notifyAll()
    }
    failure

  private def isCtrl(input: TerminalInput, char: String): Boolean = input match
    case TerminalInput.KeyEvent(TerminalKey.Character(value), modifiers, eventType) =>
      (eventType !== KeyEventType.Release) && (value === char) && modifiers.ctrl
    case _                                                                          => false

  private def renderNow(force: Boolean, clear: Boolean): Unit =
    val generation          = lifecycleLock.synchronized(resizeGeneration)
    val width               = positiveDimension(terminal.columns)
    val height              = positiveDimension(terminal.rows)
    val baseFrame           = root.renderFrame(width)
    val (composed, layouts) = renderOverlays(baseFrame.render, width, height)
    latestBaseLayout = Some(baseFrame.layout)
    latestOverlayLayouts = layouts
    val frame               = prepareFrame(composed.validated(width), width)

    val currentWidth      = positiveDimension(terminal.columns)
    val currentHeight     = positiveDimension(terminal.rows)
    val currentGeneration = lifecycleLock.synchronized(resizeGeneration)
    if (generation !== currentGeneration) || (width !== currentWidth) ||
      (height !== currentHeight)
    then
      lifecycleLock.synchronized {
        renderRequested = true
        forceRenderRequested = true
        clearRequested = true
      }
    else
      val widthChanged  = (previousWidth !== 0) && (previousWidth !== width)
      val heightChanged = (previousHeight !== 0) && (previousHeight !== height)
      if previousFrame.isEmpty || force then
        fullRender(frame, width, height, clear = clear)
      else if widthChanged || heightChanged then
        fullRender(frame, width, height, clear = true)
      else
        val firstChanged = firstChangedRow(previousFrame.get, frame)
        if firstChanged >= 0 then partialRender(frame, firstChanged)
        else positionHardwareCursorOnly(frame.position)
        previousFrame = Some(frame)
        previousWidth = width
        previousHeight = height

  private def fullRender(
      frame: TUI.PreparedFrame,
      width: Int,
      height: Int,
      clear: Boolean
  ): Unit =
    val startRowBeforeRender = if clear then Some(0) else latestFrameStartRow
    val builder              = StringBuilder()
    appendRenderStart(builder)
    if clear then builder.append(clearSequence)
    else
      previousFrame.foreach { _ =>
        appendVerticalMove(builder, fromRow = cursorRow, toRow = 0)
        builder.append("\r")
      }
    val paintedRow           = appendFrameContent(
      builder,
      frame,
      fromRow = 0,
      kittyLifecycleCleanup(previousFrame, frame, fromRow = 0)
    )
    appendHardwareCursorMove(builder, frame, paintedRow)
    appendRenderEnd(builder)
    writeRenderBuffer(builder.result())
    latestFrameStartRow =
      startRowBeforeRender.map(scrolledFrameStart(_, 0, frame.lines.length, height))
    previousFrame = Some(frame)
    previousWidth = width
    previousHeight = height
    cursorRow = finalCursorRow(frame, paintedRow)

  private def partialRender(frame: TUI.PreparedFrame, firstChanged: Int): Unit =
    val builder    = StringBuilder()
    appendRenderStart(builder)
    appendVerticalMove(builder, fromRow = cursorRow, toRow = firstChanged)
    builder.append("\r\u001b[J")
    val paintedRow = appendFrameContent(
      builder,
      frame,
      firstChanged,
      kittyLifecycleCleanup(previousFrame, frame, fromRow = firstChanged)
    )
    appendHardwareCursorMove(builder, frame, paintedRow)
    appendRenderEnd(builder)
    writeRenderBuffer(builder.result())
    cursorRow = finalCursorRow(frame, paintedRow)
    latestFrameStartRow = latestFrameStartRow.map(start =>
      scrolledFrameStart(start, firstChanged, frame.lines.length - firstChanged, terminal.rows)
    )

  private def appendFrameContent(
      builder: StringBuilder,
      frame: TUI.PreparedFrame,
      fromRow: Int,
      cleanupControls: Vector[TerminalRenderControl]
  ): Int =
    cleanupControls.foreach { control =>
      builder.append(TerminalRenderControlEncoder.encode(control))
      builder.append("\r")
    }
    val controlsByRow = frame.controls.filter(_.row >= fromRow).groupBy(_.row)
    var row           = fromRow
    while row < frame.lines.length do
      controlsByRow.getOrElse(row, Vector.empty).foreach { placement =>
        appendMoveRight(builder, placement.column)
        builder.append(TerminalRenderControlEncoder.encode(placement.control))
        builder.append("\r")
      }
      builder.append(frame.lines(row))
      if row < frame.lines.length - 1 then builder.append("\r\n")
      row += 1
    if frame.lines.length > fromRow then frame.lines.length - 1 else fromRow

  private def kittyLifecycleCleanup(
      oldFrame: Option[TUI.PreparedFrame],
      newFrame: TUI.PreparedFrame,
      fromRow: Int
  ): Vector[TerminalRenderControl] =
    val newActiveIds = newFrame.controls.iterator.flatMap(kittyImageId).toSet
    val emittedIds   = newFrame.controls.iterator
      .filter(_.row >= fromRow)
      .flatMap(kittyImageId)
      .toSet
    oldFrame.toVector
      .flatMap(_.controls)
      .filter(placement =>
        kittyImageId(placement).exists(imageId =>
          !newActiveIds(imageId) || emittedIds(imageId)
        )
      )
      .flatMap(placement => TerminalRenderControl.cleanupForReplacement(placement.control))

  private def kittyImageId(placement: TerminalControlPlacement): Option[Int] =
    placement.control.details match
      case kitty: scalatui.terminal.TerminalRenderControlDetails.KittyImage =>
        Some(kitty.imageId)
      case _                                                                => None

  private def writeRenderBuffer(buffer: String): Unit =
    autoWrapRestoreNeeded = true
    writeTerminal(terminal.write(buffer))
    autoWrapRestoreNeeded = false

  private def parkCursorBelowContentIfNeeded(): Unit =
    if previousFrame.exists(_.lines.nonEmpty) && !alternateScreenEntered then
      val builder = StringBuilder()
      appendVerticalMove(
        builder,
        fromRow = cursorRow,
        toRow = math.max(0, previousFrame.fold(0)(_.lines.length) - 1)
      )
      builder.append("\r\n")
      writeTerminal(terminal.write(builder.result()))

  private def enterAlternateScreenIfConfigured(): Unit =
    if isAlternateScreenMode && !alternateScreenEntered then
      writeTerminal(terminal.write(AlternateScreenEnter))
      alternateScreenEntered = true

  private def exitAlternateScreenIfNeeded(): Unit =
    if alternateScreenEntered then
      try writeTerminal(terminal.write(AlternateScreenExit))
      finally alternateScreenEntered = false

  private def clearSequence: String =
    if alternateScreenEntered then AlternateScreenClear else NormalScreenClear

  private def isAlternateScreenMode: Boolean = options.screenMode match
    case TUIScreenMode.Alternate => true
    case TUIScreenMode.Normal    => false

  private def restoreAutoWrapIfNeeded(): Unit =
    if autoWrapRestoreNeeded then
      autoWrapRestoreNeeded = false
      writeTerminal(terminal.write(AutoWrapOn))

  private def appendRenderStart(builder: StringBuilder): Unit =
    builder.append(SyncStart)
    // Full-width terminal lines can be marked as soft-wrapped by real terminal emulators. Those
    // soft-wrap markers may be reflowed on resize, invalidating the logical cursorRow used by the
    // differential redraw path. Disable autowrap only while painting frames so full-width content
    // remains a single physical row, then restore the usual terminal mode after synchronized output.
    builder.append(AutoWrapOff)

  private def appendRenderEnd(builder: StringBuilder): Unit =
    builder.append(SyncEnd)
    builder.append(AutoWrapOn)

  private def positionHardwareCursorOnly(position: Option[CursorPlacement]): Unit =
    if options.hardwareCursorPositioning then
      position.foreach { target =>
        val builder = StringBuilder()
        appendVerticalMove(builder, fromRow = cursorRow, toRow = target.row)
        builder.append("\r")
        appendMoveRight(builder, target.column)
        if builder.nonEmpty then
          writeTerminal(terminal.write(builder.result()))
          cursorRow = target.row
      }

  private def prepareFrame(frame: ComponentRender, width: Int): TUI.PreparedFrame =
    val selectedCursor = frame.cursorPlacements.zipWithIndex
      .minByOption { case (placement, index) => (placement.row, placement.column, index) }
      .map(_._1)
    TUI.PreparedFrame(
      applyLineResets(sanitizeLines(frame.lines, width)),
      selectedCursor,
      frame.controls
    )

  private def appendHardwareCursorMove(
      builder: StringBuilder,
      frame: TUI.PreparedFrame,
      fromRow: Int
  ): Unit =
    if options.hardwareCursorPositioning then
      frame.position.foreach { target =>
        appendVerticalMove(
          builder,
          fromRow = fromRow,
          toRow = target.row
        )
        builder.append("\r")
        appendMoveRight(builder, target.column)
      }

  private def scrolledFrameStart(
      frameStartRow: Int,
      writeStartFrameRow: Int,
      writtenLineCount: Int,
      terminalHeight: Int
  ): Int =
    if writtenLineCount <= 0 then frameStartRow
    else
      val writeStartScreenRow = frameStartRow + writeStartFrameRow
      val overflow            = math.max(0, writeStartScreenRow + writtenLineCount - terminalHeight)
      frameStartRow - overflow

  private def finalCursorRow(frame: TUI.PreparedFrame, paintedRow: Int): Int =
    if options.hardwareCursorPositioning then frame.position.map(_.row).getOrElse(paintedRow)
    else paintedRow

  private def appendVerticalMove(builder: StringBuilder, fromRow: Int, toRow: Int): Unit =
    val delta = toRow - fromRow
    if delta > 0 then builder.append(s"\u001b[${delta}B")
    else if delta < 0 then builder.append(s"\u001b[${-delta}A")

  private def appendMoveRight(builder: StringBuilder, columns: Int): Unit =
    if columns > 0 then builder.append(s"\u001b[${columns}C")

  private def positiveDimension(value: Int): Int = math.max(1, value)

  private def sanitizeLines(lines: Vector[String], width: Int): Vector[String] =
    lines.zipWithIndex.map { (line, index) =>
      val lineWidth = Ansi.visibleWidth(line)
      if lineWidth <= width then Ansi.sanitize(line)
      else
        val sanitized = Ansi.truncateToWidth(line, width, "")
        sanitizationCount += 1
        lastSanitization = Some(TUI.RenderSanitization(
          lineIndex = index,
          originalWidth = lineWidth,
          targetWidth = width,
          original = line,
          sanitized = sanitized
        ))
        sanitized
    }

  private def firstChangedLine(oldLines: Vector[String], newLines: Vector[String]): Int =
    val firstDifferent = oldLines.zip(newLines).indexWhere { case (oldLine, newLine) =>
      oldLine !== newLine
    }
    if firstDifferent >= 0 then firstDifferent
    else if oldLines.length === newLines.length then -1
    else math.min(oldLines.length, newLines.length)

  private def firstChangedRow(oldFrame: TUI.PreparedFrame, newFrame: TUI.PreparedFrame): Int =
    val removedControls = oldFrame.controls.diff(newFrame.controls)
    val addedControls   = newFrame.controls.diff(oldFrame.controls)
    val lineRow         = firstChangedLine(oldFrame.lines, newFrame.lines)
    val orderedRow      = firstOrderedControlDifferenceRow(oldFrame.controls, newFrame.controls)
    val controlRow      = removedControls.iterator
      .map(_.row)
      .concat(addedControls.iterator.map(_.row))
      .concat(Option.when(orderedRow >= 0)(orderedRow).iterator)
      .minOption
      .getOrElse(-1)
    if lineRow < 0 then controlRow
    else if controlRow < 0 then lineRow
    else math.min(lineRow, controlRow)

  private def firstOrderedControlDifferenceRow(
      oldControls: Vector[TerminalControlPlacement],
      newControls: Vector[TerminalControlPlacement]
  ): Int =
    val commonLength    = math.min(oldControls.length, newControls.length)
    val firstDifference = (0 until commonLength).find(index =>
      oldControls(index) !== newControls(index)
    )
    val changedFrom     = firstDifference.orElse(
      Option.when(oldControls.length !== newControls.length)(commonLength)
    )
    changedFrom
      .flatMap { index =>
        oldControls.iterator
          .drop(index)
          .map(_.row)
          .concat(newControls.iterator.drop(index).map(_.row))
          .minOption
      }
      .getOrElse(-1)

  private def applyLineResets(lines: Vector[String]): Vector[String] =
    lines.map(_ + LineReset)

object TUI:
  private final case class PreparedFrame(
      lines: Vector[String],
      position: Option[CursorPlacement],
      controls: Vector[TerminalControlPlacement]
  ) derives CanEqual

  private enum LifecycleState derives CanEqual:
    case Starting, Running, Stopping, Cleaning, Stopped

  private val IngressCapacity = 4096

  private enum Work:
    case Ingress(ingress: TUI.Ingress)
    case QueryCompletion(completion: TUI.QueryCompletion[?])
    case Structural(claimed: TUI.ClaimedStructural)
    case Action(action: () => Unit)
    case Control(action: () => Unit)
    case Render(force: Boolean, clear: Boolean)
    case Cleanup
    case Done

  private enum Ingress:
    case Input(input: TerminalInput)
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

  private final case class ClaimedStructural(effects: Vector[StructuralEffect])

  private enum StructuralEffect:
    case Add(component: Component, attach: Boolean)
    case Remove(component: Component, detach: Boolean)

  private enum OrdinaryCategory:
    case Structural, Action, Ingress, Control, Render

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

  val SyncStart: String            = "\u001b[?2026h"
  val SyncEnd: String              = "\u001b[?2026l"
  val AutoWrapOff: String          = "\u001b[?7l"
  val AutoWrapOn: String           = "\u001b[?7h"
  val AlternateScreenEnter: String = "\u001b[?1049h"
  val AlternateScreenExit: String  = "\u001b[?1049l"
  val NormalScreenClear: String    = "\u001b[2J\u001b[H\u001b[3J"
  val AlternateScreenClear: String = "\u001b[2J\u001b[H"
  val LineReset: String            = "\u001b[0m\u001b]8;;\u0007"

private val SyncStart            = TUI.SyncStart
private val SyncEnd              = TUI.SyncEnd
private val AutoWrapOff          = TUI.AutoWrapOff
private val AutoWrapOn           = TUI.AutoWrapOn
private val AlternateScreenEnter = TUI.AlternateScreenEnter
private val AlternateScreenExit  = TUI.AlternateScreenExit
private val NormalScreenClear    = TUI.NormalScreenClear
private val AlternateScreenClear = TUI.AlternateScreenClear
private val LineReset            = TUI.LineReset
