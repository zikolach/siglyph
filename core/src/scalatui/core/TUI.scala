package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyEventType,
  RgbColor,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalImageProtocol,
  TerminalInput,
  TerminalKey
}

import scala.collection.mutable.ArrayBuffer

/**
 * Runtime options for [[TUI]].
 *
 * @param hardwareCursorPositioning
 *   When true, the shared renderer strips focused editor cursor markers from the final frame and
 *   moves the terminal hardware cursor to the marker position after output. The default is false so
 *   existing applications keep relying on the rendered fake cursor only. This option is
 *   backend-independent and does not require application code to emit raw terminal escape strings.
 */
final case class TUIOptions(hardwareCursorPositioning: Boolean = false) derives CanEqual

/** Main terminal UI runtime with a small differential renderer. */
final class TUI(val terminal: Terminal, val options: TUIOptions = TUIOptions())
    extends TUIContext,
      OverlayHost:
  private val root                                    = Container()
  private val lifecycleLock                           = Object()
  private val overlayStack                            = ArrayBuffer.empty[TUI.OverlayEntry]
  private var previousLines                           = Vector.empty[String]
  private var previousWidth                           = 0
  private var previousHeight                          = 0
  private var cursorRow                               = 0
  private var focusedComponent: Option[Component]     = None
  private var baseFocusedComponent: Option[Component] = None
  private var running                                 = false
  private var exitRequested                           = false
  private var renderRequested                         = false
  private var clearRequested                          = false
  private var sanitizationCount                       = 0
  private var lastSanitization                        = Option.empty[TUI.RenderSanitization]
  private var runtimeFailure                          = Option.empty[Throwable]
  private var nextOverlayId                           = 0L
  private var nextFocusOrder                          = 0L
  private val pendingBackgroundColorQueries           = ArrayBuffer.empty[TUI.PendingQuery[RgbColor]]
  private val pendingColorSchemeQueries               = ArrayBuffer.empty[TUI.PendingQuery[TerminalColorScheme]]
  private val terminalColorSchemeListeners            = ArrayBuffer.empty[TerminalColorScheme => Unit]
  private var terminalColorSchemeNotificationsEnabled = false
  private val inputListeners                          = ArrayBuffer.empty[TerminalInput => InputResult]

  var handlesControlC: Boolean = true
  var exitsOnEscape: Boolean   = false

  def addChild(component: Component): Unit =
    attachContext(component)
    root.addChild(component)

  def removeChild(component: Component): Boolean =
    val removed = root.removeChild(component)
    if removed then detachContext(component)
    removed

  def clear(): Unit =
    root.children.foreach(detachContext)
    root.clear()

  def children: Vector[Component] = root.children

  /** Number of final rendered lines sanitized because they exceeded terminal width. */
  def sanitizedLineCount: Int = lifecycleLock.synchronized(sanitizationCount)

  /** Most recent final rendered line sanitization diagnostic, if any occurred. */
  def lastSanitizedLine: Option[TUI.RenderSanitization] =
    lifecycleLock.synchronized(lastSanitization)

  /**
   * Set the terminal window title when the backend supports title operations.
   *
   * Unsupported terminals return `false` and emit no title sequence. Control characters are removed
   * before the backend writes the title protocol sequence.
   */
  def setTerminalTitle(title: String): Boolean = Terminal.setTitle(terminal, title)

  /**
   * Set terminal progress state when the backend supports progress operations.
   *
   * The operation is fire-and-forget. Unsupported terminals return `false` and emit no progress
   * sequence.
   */
  def setTerminalProgress(active: Boolean): Boolean = Terminal.setProgress(terminal, active)

  /**
   * Query the terminal default background color using OSC 11.
   *
   * `TUI` owns request/response correlation. Terminal backends only write requests and deliver raw
   * protocol replies. Returns `None` without writing when the TUI is not running. Returns `None`
   * when the terminal does not answer with a valid RGB response before `timeoutMillis` expires.
   */
  def queryTerminalBackgroundColor(timeoutMillis: Long = 1000L): Option[RgbColor] =
    val query = registerPendingQuery(
      pendingBackgroundColorQueries,
      TerminalColorProtocol.BackgroundColorQuery
    )
    query.flatMap { pending =>
      val result = pending.await(timeoutMillis)
      if result.isEmpty then lifecycleLock.synchronized(pendingBackgroundColorQueries -= pending)
      result
    }

  /**
   * Query the terminal color scheme using DSR `CSI ? 996 n`.
   *
   * Valid terminal reports are parsed as [[scalatui.terminal.TerminalColorScheme.Dark]] or
   * [[scalatui.terminal.TerminalColorScheme.Light]]. Returns `None` without writing when the TUI is
   * not running. Returns `None` when no valid report arrives before `timeoutMillis` expires.
   */
  def queryTerminalColorScheme(timeoutMillis: Long = 1000L): Option[TerminalColorScheme] =
    val query = registerPendingQuery(
      pendingColorSchemeQueries,
      TerminalColorProtocol.ColorSchemeQuery
    )
    query.flatMap { pending =>
      val result = pending.await(timeoutMillis)
      if result.isEmpty then lifecycleLock.synchronized(pendingColorSchemeQueries -= pending)
      result
    }

  /** Subscribe to terminal color-scheme reports. Returns a function that removes the listener. */
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
  def setTerminalColorSchemeNotifications(enabled: Boolean): Unit = lifecycleLock.synchronized {
    if terminalColorSchemeNotificationsEnabled !== enabled then
      terminalColorSchemeNotificationsEnabled = enabled
      if running then
        terminal.write(
          if enabled then TerminalColorProtocol.EnableColorSchemeNotifications
          else TerminalColorProtocol.DisableColorSchemeNotifications
        )
  }

  override def setFocus(component: Component | Null): Unit =
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
    lifecycleLock.synchronized {
      attachContext(component)
      nextOverlayId += 1
      nextFocusOrder += 1
      val entry = TUI.OverlayEntry(
        id = OverlayId(nextOverlayId),
        component = component,
        options = options,
        preFocus = focusedComponent,
        hidden = false,
        focusOrder = nextFocusOrder
      )
      overlayStack += entry
      if entry.options.focusCapturing && isOverlayVisible(entry) then focusOverlay(entry)
      requestRender()
      makeOverlayHandle(entry)
    }

  override def hideOverlay(): Unit = lifecycleLock.synchronized {
    topVisibleOverlay.foreach(removeOverlay)
  }

  override def hasOverlay: Boolean =
    lifecycleLock.synchronized(overlayStack.exists(isOverlayVisible))

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
      if running then false
      else
        exitRequested = false
        runtimeFailure = None
        running = true
        true
    }
    if shouldStart then
      try
        terminal.start(
          input => safeRuntimeCallback(handleInput(input)),
          () =>
            safeRuntimeCallback {
              requestRender()
              flushRender()
            }
        )
        if terminalColorSchemeNotificationsEnabled then
          terminal.write(TerminalColorProtocol.EnableColorSchemeNotifications)
        TerminalImageProtocol.resetCellDimensions()
        terminal.hideCursor()
        terminal.write(TerminalImageProtocol.QueryCellDimensions)
        requestRenderInternal(force = true, clear = false)
        flushRender()
      catch
        case e: Throwable =>
          stop()
          throw e

  def run(): Unit =
    try
      start()
      lifecycleLock.synchronized {
        while running && !exitRequested do lifecycleLock.wait()
      }
    finally stop()
    runtimeFailure.foreach(throw _)

  override def requestExit(): Unit = lifecycleLock.synchronized {
    exitRequested = true
    lifecycleLock.notifyAll()
  }

  def stop(): Unit = lifecycleLock.synchronized {
    if running then
      running = false
      try
        if previousLines.nonEmpty then
          val builder = StringBuilder()
          appendVerticalMove(
            builder,
            fromRow = cursorRow,
            toRow = math.max(0, previousLines.length - 1)
          )
          builder.append("\r\n")
          terminal.write(builder.result())
        if terminalColorSchemeNotificationsEnabled then
          terminal.write(TerminalColorProtocol.DisableColorSchemeNotifications)
        Terminal.drainInput(terminal)
      finally
        try terminal.showCursor()
        finally
          try terminal.stop()
          finally lifecycleLock.notifyAll()
  }

  override def requestRender(force: Boolean = false): Unit = lifecycleLock.synchronized {
    requestRenderInternal(force = force, clear = false)
  }

  private def requestRenderInternal(force: Boolean, clear: Boolean): Unit =
    lifecycleLock.synchronized {
      if force then
        previousLines = Vector.empty
        previousWidth = if clear then -1 else 0
        previousHeight = if clear then -1 else 0
        cursorRow = 0
      if clear then clearRequested = true
      renderRequested = true
    }

  override def flushRender(): Unit = lifecycleLock.synchronized {
    if renderRequested then
      renderRequested = false
      renderNow()
  }

  private def handleInput(input: TerminalInput): Unit =
    val notifications = lifecycleLock.synchronized {
      consumeTerminalProtocolReply(input) match
        case Some(value) => value
        case None        =>
          if routeGlobalInputListeners(input) !== InputResult.Ignored then Vector.empty
          else if isIgnoredKeyRelease(input) then Vector.empty
          else if handlesControlC && isCtrl(input, "c") then
            requestExit()
            Vector.empty
          else if exitsOnEscape && (input === TerminalInput.Key(TerminalKey.Escape)) then
            requestExit()
            Vector.empty
          else
            inputTarget.map(_.handleInputResult(input)).foreach(handleInputResult)
            Vector.empty
    }
    notifications.foreach { case (listener, scheme) => listener(scheme) }

  private def consumeTerminalProtocolReply(
      input: TerminalInput
  ): Option[Vector[(TerminalColorScheme => Unit, TerminalColorScheme)]] = input match
    case TerminalInput.Raw(data) if TerminalImageProtocol.isCellSizeResponse(data)             =>
      val previousDimensions = TerminalImageProtocol.cellDimensions
      TerminalImageProtocol.parseCellSizeResponse(data).foreach { dimensions =>
        TerminalImageProtocol.setCellDimensions(dimensions.widthPx, dimensions.heightPx).foreach {
          updatedDimensions =>
            if updatedDimensions !== previousDimensions then
              requestRender()
              flushRender()
        }
      }
      Some(Vector.empty)
    case TerminalInput.Raw(data) if TerminalColorProtocol.isOsc11BackgroundColorResponse(data) =>
      TerminalColorProtocol.parseOsc11BackgroundColor(data).foreach { color =>
        pendingBackgroundColorQueries.foreach(_.complete(color))
        pendingBackgroundColorQueries.clear()
      }
      Some(Vector.empty)
    case TerminalInput.Raw(data) if TerminalColorProtocol.isTerminalColorSchemeReport(data)    =>
      TerminalColorProtocol.parseTerminalColorSchemeReport(data) match
        case Some(scheme) =>
          pendingColorSchemeQueries.foreach(_.complete(scheme))
          pendingColorSchemeQueries.clear()
          val listeners =
            if terminalColorSchemeNotificationsEnabled then terminalColorSchemeListeners.toVector
            else Vector.empty
          Some(listeners.map(listener => listener -> scheme))
        case None         => Some(Vector.empty)
    case TerminalInput.Raw(_)                                                                  => None
    case _                                                                                     => None

  private def registerPendingQuery[A](
      pendingQueries: ArrayBuffer[TUI.PendingQuery[A]],
      request: String
  ): Option[TUI.PendingQuery[A]] = lifecycleLock.synchronized {
    if running then
      val pending = TUI.PendingQuery[A]()
      pendingQueries += pending
      try
        terminal.write(request)
        Some(pending)
      catch
        case e: Throwable =>
          pendingQueries -= pending
          throw e
    else None
  }

  private def inputTarget: Option[Component] =
    topCapturingOverlay.map(_.component).orElse(focusedComponent)

  private def routeGlobalInputListeners(input: TerminalInput): InputResult =
    val listeners = inputListeners.toVector
    var result    = InputResult.Ignored
    var index     = 0
    while index < listeners.length && result === InputResult.Ignored do
      result = listeners(index)(input)
      index += 1
    handleInputResult(result)
    result

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

  private def renderOverlays(baseLines: Vector[String], width: Int, height: Int): Vector[String] =
    val rendered = overlayStack.toVector
      .filter(isOverlayVisible)
      .sortBy(_.focusOrder)
      .flatMap { entry =>
        val initialLayout = OverlayRenderer.resolve(entry.options, overlayHeight = 0, width, height)
        val rawLines      = entry.component.render(initialLayout.width)
        val clippedLines  = initialLayout.maxHeight.fold(rawLines)(rawLines.take)
        val layout        = OverlayRenderer.resolve(entry.options, clippedLines.length, width, height)
        Option.when(clippedLines.nonEmpty)(clippedLines -> layout)
      }
    OverlayRenderer.composite(baseLines, rendered, width, height)

  private def makeOverlayHandle(entry: TUI.OverlayEntry): OverlayHandle = new OverlayHandle:
    override def id: OverlayId = entry.id

    override def hide(): Unit = lifecycleLock.synchronized(removeOverlay(entry))

    override def setHidden(hidden: Boolean): Unit = lifecycleLock.synchronized {
      if overlayStack.exists(_ eq entry) && (entry.hidden !== hidden) then
        entry.hidden = hidden
        if hidden && focusedComponent.exists(_ eq entry.component) then restoreFocusAfter(entry)
        else if !hidden && entry.options.focusCapturing && isOverlayVisible(entry) then
          focusOverlay(entry)
        requestRender()
    }

    override def isHidden: Boolean = lifecycleLock.synchronized(entry.hidden)

    override def focus(): Unit = lifecycleLock.synchronized {
      if overlayStack.exists(_ eq entry) && entry.options.focusCapturing && isOverlayVisible(entry)
      then
        focusOverlay(entry)
        requestRender()
    }

    override def unfocus(options: Option[OverlayUnfocusOptions]): Unit =
      lifecycleLock.synchronized {
        if focusedComponent.exists(_ eq entry.component) then
          options match
            case Some(value) => setFocus(value.target)
            case None        => restoreFocusAfter(entry)
          requestRender()
      }

    override def isFocused: Boolean =
      lifecycleLock.synchronized(focusedComponent.exists(_ eq entry.component))

    override def update(
        component: Component,
        options: Option[OverlayOptions],
        requestRender: Boolean
    ): Unit =
      lifecycleLock.synchronized {
        if overlayStack.exists(_ eq entry) then
          val wasFocused = focusedComponent.exists(_ eq entry.component)
          detachContext(entry.component)
          entry.component = component
          attachContext(component)
          options.foreach(entry.options = _)
          if wasFocused then setFocus(component)
          if requestRender then TUI.this.requestRender()
      }

  private def removeOverlay(entry: TUI.OverlayEntry): Unit =
    val index = overlayStack.indexWhere(_ eq entry)
    if index >= 0 then
      val wasFocused = focusedComponent.exists(_ eq entry.component)
      overlayStack.remove(index)
      detachContext(entry.component)
      if wasFocused then restoreFocusAfter(entry)
      requestRender()

  private def restoreFocusAfter(entry: TUI.OverlayEntry): Unit =
    val fallback = topCapturingOverlay.filterNot(
      _ eq entry
    ).map(_.component).orElse(entry.preFocus).orElse(baseFocusedComponent)
    setFocus(fallback.orNull)

  private def focusOverlay(entry: TUI.OverlayEntry): Unit =
    nextFocusOrder += 1
    entry.focusOrder = nextFocusOrder
    setFocus(entry.component)

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
    lifecycleLock.synchronized {
      if runtimeFailure.isEmpty then runtimeFailure = Some(error)
      exitRequested = true
      lifecycleLock.notifyAll()
    }
    stop()

  private def isCtrl(input: TerminalInput, char: String): Boolean = input match
    case TerminalInput.KeyEvent(TerminalKey.Character(value), modifiers, eventType) =>
      (eventType !== KeyEventType.Release) && (value === char) && modifiers.ctrl
    case _                                                                          => false

  private def renderNow(): Unit =
    val width        = positiveDimension(terminal.columns)
    val height       = positiveDimension(terminal.rows)
    val rawLines     = root.render(width)
    val overlayLines = renderOverlays(rawLines, width, height)
    val frame        = prepareFrame(overlayLines, width)
    val newLines     = frame.lines

    val widthChanged  = (previousWidth !== 0) && (previousWidth !== width)
    val heightChanged = (previousHeight !== 0) && (previousHeight !== height)
    if previousLines.isEmpty then
      val shouldClear = clearRequested
      clearRequested = false
      fullRender(frame, width, height, clear = shouldClear)
    else if widthChanged || heightChanged then
      clearRequested = false
      partialRender(frame, firstChanged = 0)
      previousLines = newLines
      previousWidth = width
      previousHeight = height
    else
      clearRequested = false
      val firstChanged = firstChangedLine(previousLines, newLines)
      if firstChanged >= 0 then partialRender(frame, firstChanged)
      else positionHardwareCursorOnly(frame.position)
      previousLines = newLines
      previousWidth = width
      previousHeight = height

  private def fullRender(
      frame: CursorMarker.ScanResult,
      width: Int,
      height: Int,
      clear: Boolean
  ): Unit =
    val builder = StringBuilder()
    appendRenderStart(builder)
    if clear then builder.append("\u001b[2J\u001b[H\u001b[3J")
    builder.append(frame.lines.mkString("\r\n"))
    appendHardwareCursorMove(builder, frame)
    appendRenderEnd(builder)
    terminal.write(builder.result())
    previousLines = frame.lines
    previousWidth = width
    previousHeight = height
    cursorRow = finalCursorRow(frame)

  private def partialRender(frame: CursorMarker.ScanResult, firstChanged: Int): Unit =
    val builder = StringBuilder()
    appendRenderStart(builder)
    appendVerticalMove(builder, fromRow = cursorRow, toRow = firstChanged)
    builder.append("\r\u001b[J")
    builder.append(frame.lines.drop(firstChanged).mkString("\r\n"))
    appendHardwareCursorMove(builder, frame)
    appendRenderEnd(builder)
    terminal.write(builder.result())
    cursorRow = finalCursorRow(frame)

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

  private def positionHardwareCursorOnly(position: Option[CursorMarker.Position]): Unit =
    if options.hardwareCursorPositioning then
      position.foreach { target =>
        val builder = StringBuilder()
        appendVerticalMove(builder, fromRow = cursorRow, toRow = target.row)
        builder.append("\r")
        appendMoveRight(builder, target.column)
        if builder.nonEmpty then
          terminal.write(builder.result())
          cursorRow = target.row
      }

  private def prepareFrame(lines: Vector[String], width: Int): CursorMarker.ScanResult =
    CursorMarker.stripAndLocate(applyLineResets(sanitizeLines(lines, width)))

  private def appendHardwareCursorMove(
      builder: StringBuilder,
      frame: CursorMarker.ScanResult
  ): Unit =
    if options.hardwareCursorPositioning then
      frame.position.foreach { target =>
        appendVerticalMove(
          builder,
          fromRow = math.max(0, frame.lines.length - 1),
          toRow = target.row
        )
        builder.append("\r")
        appendMoveRight(builder, target.column)
      }

  private def finalCursorRow(frame: CursorMarker.ScanResult): Int =
    if options.hardwareCursorPositioning then
      frame.position.map(_.row).getOrElse(math.max(0, frame.lines.length - 1))
    else math.max(0, frame.lines.length - 1)

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
      if lineWidth <= width then line
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

  private def applyLineResets(lines: Vector[String]): Vector[String] =
    lines.map(_ + LineReset)

object TUI:
  private final class OverlayEntry(
      val id: OverlayId,
      var component: Component,
      var options: OverlayOptions,
      val preFocus: Option[Component],
      var hidden: Boolean,
      var focusOrder: Long
  )

  private final class PendingQuery[A]:
    private var completed = false
    private var result    = Option.empty[A]

    def complete(value: A): Unit = synchronized {
      if !completed then
        result = Some(value)
        completed = true
        notifyAll()
    }

    def await(timeoutMillis: Long): Option[A] = synchronized {
      val deadline  = System.currentTimeMillis() + math.max(0L, timeoutMillis)
      var remaining = math.max(0L, timeoutMillis)
      while !completed && remaining > 0 do
        wait(remaining)
        remaining = deadline - System.currentTimeMillis()
      result
    }

  final case class RenderSanitization(
      lineIndex: Int,
      originalWidth: Int,
      targetWidth: Int,
      original: String,
      sanitized: String
  ) derives CanEqual

  val SyncStart: String   = "\u001b[?2026h"
  val SyncEnd: String     = "\u001b[?2026l"
  val AutoWrapOff: String = "\u001b[?7l"
  val AutoWrapOn: String  = "\u001b[?7h"
  val LineReset: String   = "\u001b[0m\u001b]8;;\u0007"

private val SyncStart   = TUI.SyncStart
private val SyncEnd     = TUI.SyncEnd
private val AutoWrapOff = TUI.AutoWrapOff
private val AutoWrapOn  = TUI.AutoWrapOn
private val LineReset   = TUI.LineReset
