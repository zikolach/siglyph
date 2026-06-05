package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.terminal.{Terminal, TerminalInput, TerminalKey}

import scala.collection.mutable.ArrayBuffer

/** Main terminal UI runtime with a small differential renderer. */
final class TUI(val terminal: Terminal) extends TUIContext, OverlayHost:
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
        terminal.hideCursor()
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
      if previousLines.nonEmpty then terminal.write("\r\n")
      terminal.showCursor()
      terminal.stop()
      lifecycleLock.notifyAll()
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

  private def handleInput(input: TerminalInput): Unit = lifecycleLock.synchronized {
    if handlesControlC && isCtrl(input, "c") then requestExit()
    else if exitsOnEscape && (input === TerminalInput.Key(TerminalKey.Escape)) then requestExit()
    else
      inputTarget.map(_.handleInputResult(input)).foreach {
        case InputResult.Ignored               => ()
        case InputResult.Handled(shouldRender) =>
          if shouldRender then
            requestRender()
            flushRender()
        case InputResult.Exit                  => requestExit()
      }
  }

  private def inputTarget: Option[Component] =
    topCapturingOverlay.map(_.component).orElse(focusedComponent)

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
    case TerminalInput.Key(TerminalKey.Character(value), modifiers) =>
      (value === char) && modifiers.ctrl
    case _                                                          => false

  private def renderNow(): Unit =
    val width        = positiveDimension(terminal.columns)
    val height       = positiveDimension(terminal.rows)
    val rawLines     = root.render(width)
    val overlayLines = renderOverlays(rawLines, width, height)
    val newLines     = applyLineResets(sanitizeLines(overlayLines, width))

    val widthChanged  = (previousWidth !== 0) && (previousWidth !== width)
    val heightChanged = (previousHeight !== 0) && (previousHeight !== height)
    if previousLines.isEmpty then
      val shouldClear = clearRequested
      clearRequested = false
      fullRender(newLines, width, height, clear = shouldClear)
    else if widthChanged || heightChanged then
      clearRequested = false
      redrawFromFrameStart(newLines, width, height)
    else
      clearRequested = false
      val firstChanged = firstChangedLine(previousLines, newLines)
      if firstChanged >= 0 then partialRender(newLines, firstChanged)
      previousLines = newLines
      previousWidth = width
      previousHeight = height

  private def fullRender(lines: Vector[String], width: Int, height: Int, clear: Boolean): Unit =
    val builder = StringBuilder()
    builder.append(SyncStart)
    if clear then builder.append("\u001b[2J\u001b[H\u001b[3J")
    builder.append(lines.mkString("\r\n"))
    builder.append(SyncEnd)
    terminal.write(builder.result())
    previousLines = lines
    previousWidth = width
    previousHeight = height
    cursorRow = math.max(0, lines.length - 1)

  private def redrawFromFrameStart(lines: Vector[String], width: Int, height: Int): Unit =
    val builder = StringBuilder()
    builder.append(SyncStart)
    if cursorRow > 0 then builder.append(s"\u001b[${cursorRow}A")
    builder.append("\r\u001b[J")
    builder.append(lines.mkString("\r\n"))
    builder.append(SyncEnd)
    terminal.write(builder.result())
    previousLines = lines
    previousWidth = width
    previousHeight = height
    cursorRow = math.max(0, lines.length - 1)

  private def partialRender(lines: Vector[String], firstChanged: Int): Unit =
    val moveUp  = math.max(0, cursorRow - firstChanged)
    val builder = StringBuilder()
    builder.append(SyncStart)
    if moveUp > 0 then builder.append(s"\u001b[${moveUp}A")
    builder.append("\r\u001b[J")
    builder.append(lines.drop(firstChanged).mkString("\r\n"))
    builder.append(SyncEnd)
    terminal.write(builder.result())
    cursorRow = math.max(0, lines.length - 1)

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

  final case class RenderSanitization(
      lineIndex: Int,
      originalWidth: Int,
      targetWidth: Int,
      original: String,
      sanitized: String
  ) derives CanEqual

  val SyncStart: String = "\u001b[?2026h"
  val SyncEnd: String   = "\u001b[?2026l"
  val LineReset: String = "\u001b[0m\u001b]8;;\u0007"

private val SyncStart = TUI.SyncStart
private val SyncEnd   = TUI.SyncEnd
private val LineReset = TUI.LineReset
