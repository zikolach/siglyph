package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.terminal.{Terminal, TerminalInput, TerminalKey}

/** Main terminal UI runtime with a small differential renderer. */
final class TUI(val terminal: Terminal):
  private val root                                = Container()
  private val lifecycleLock                       = Object()
  private var previousLines                       = Vector.empty[String]
  private var previousWidth                       = 0
  private var previousHeight                      = 0
  private var cursorRow                           = 0
  private var focusedComponent: Option[Component] = None
  private var running                             = false
  private var exitRequested                       = false
  private var renderRequested                     = false
  private var sanitizationCount                   = 0
  private var lastSanitization                    = Option.empty[TUI.RenderSanitization]
  private var runtimeFailure                      = Option.empty[Throwable]

  var handlesControlC: Boolean = true
  var exitsOnEscape: Boolean   = false

  def addChild(component: Component): Unit       = root.addChild(component)
  def removeChild(component: Component): Boolean = root.removeChild(component)
  def clear(): Unit                              = root.clear()
  def children: Vector[Component]                = root.children

  /** Number of final rendered lines sanitized because they exceeded terminal width. */
  def sanitizedLineCount: Int = lifecycleLock.synchronized(sanitizationCount)

  /** Most recent final rendered line sanitization diagnostic, if any occurred. */
  def lastSanitizedLine: Option[TUI.RenderSanitization] =
    lifecycleLock.synchronized(lastSanitization)

  def setFocus(component: Component | Null): Unit =
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = false
      case _                    => ()
    }
    focusedComponent = Option(component)
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = true
      case _                    => ()
    }

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
              requestRender(force = true)
              flushRender()
            }
        )
        terminal.hideCursor()
        requestRender(force = true)
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

  def requestExit(): Unit = lifecycleLock.synchronized {
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

  def requestRender(force: Boolean = false): Unit = lifecycleLock.synchronized {
    if force then
      previousLines = Vector.empty
      previousWidth = -1
      previousHeight = -1
      cursorRow = 0
    renderRequested = true
  }

  def flushRender(): Unit = lifecycleLock.synchronized {
    if renderRequested then
      renderRequested = false
      renderNow()
  }

  private def handleInput(input: TerminalInput): Unit = lifecycleLock.synchronized {
    if handlesControlC && isCtrl(input, "c") then requestExit()
    else if exitsOnEscape && (input === TerminalInput.Key(TerminalKey.Escape)) then requestExit()
    else
      focusedComponent.map(_.handleInputResult(input)).foreach {
        case InputResult.Ignored               => ()
        case InputResult.Handled(shouldRender) =>
          if shouldRender then
            requestRender()
            flushRender()
        case InputResult.Exit                  => requestExit()
      }
  }

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
    val width    = positiveDimension(terminal.columns)
    val height   = positiveDimension(terminal.rows)
    val rawLines = root.render(width)
    val newLines = applyLineResets(sanitizeLines(rawLines, width))

    val widthChanged  = (previousWidth !== 0) && (previousWidth !== width)
    val heightChanged = (previousHeight !== 0) && (previousHeight !== height)
    if previousLines.isEmpty || widthChanged || heightChanged then
      fullRender(newLines, width, height, clear = widthChanged || heightChanged)
    else
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
