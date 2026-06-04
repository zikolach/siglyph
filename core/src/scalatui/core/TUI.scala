package scalatui.core

import scalatui.ansi.Ansi
import scalatui.terminal.{Terminal, TerminalInput, TerminalKey}

/** Main terminal UI runtime with a small differential renderer. */
final class TUI(val terminal: Terminal):
  private val root = Container()
  private val lifecycleLock = Object()
  private var previousLines = Vector.empty[String]
  private var previousWidth = 0
  private var cursorRow = 0
  private var focusedComponent: Option[Component] = None
  private var running = false
  private var exitRequested = false
  private var renderRequested = false

  var handlesControlC: Boolean = true
  var exitsOnEscape: Boolean = false

  def addChild(component: Component): Unit = root.addChild(component)
  def removeChild(component: Component): Boolean = root.removeChild(component)
  def clear(): Unit = root.clear()
  def children: Vector[Component] = root.children

  def setFocus(component: Component | Null): Unit =
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = false
      case _ => ()
    }
    focusedComponent = Option(component)
    focusedComponent.foreach {
      case focusable: Focusable => focusable.focused = true
      case _ => ()
    }

  def start(): Unit =
    lifecycleLock.synchronized {
      if running then return
      exitRequested = false
      running = true
    }
    try
      terminal.start(handleInput, () =>
        requestRender(force = true)
        flushRender()
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

  def requestExit(): Unit = lifecycleLock.synchronized {
    exitRequested = true
    lifecycleLock.notifyAll()
  }

  def stop(): Unit = lifecycleLock.synchronized {
    if !running then return
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
      cursorRow = 0
    renderRequested = true
  }

  def flushRender(): Unit = lifecycleLock.synchronized {
    if renderRequested then
      renderRequested = false
      renderNow()
  }

  private def handleInput(input: TerminalInput): Unit = lifecycleLock.synchronized {
    if handlesControlC && isCtrl(input, "c") then
      requestExit()
      return
    if exitsOnEscape && input == TerminalInput.Key(TerminalKey.Escape) then
      requestExit()
      return
    focusedComponent.foreach(_.handleInput(input))
    requestRender()
    flushRender()
  }

  private def isCtrl(input: TerminalInput, char: String): Boolean = input match
    case TerminalInput.Key(TerminalKey.Character(value), modifiers) => value == char && modifiers.ctrl
    case _ => false

  private def renderNow(): Unit =
    val width = terminal.columns
    val rawLines = root.render(width)
    rawLines.foreach { line =>
      val lineWidth = Ansi.visibleWidth(line)
      if lineWidth > width then
        throw IllegalArgumentException(s"Rendered line width $lineWidth exceeds terminal width $width: $line")
    }
    val newLines = applyLineResets(rawLines)

    val widthChanged = previousWidth != 0 && previousWidth != width
    if previousLines.isEmpty || widthChanged then
      fullRender(newLines, clear = widthChanged)
    else
      val firstChanged = firstChangedLine(previousLines, newLines)
      if firstChanged >= 0 then partialRender(newLines, firstChanged)
      previousLines = newLines
      previousWidth = width

  private def fullRender(lines: Vector[String], clear: Boolean): Unit =
    val builder = StringBuilder()
    builder.append(SyncStart)
    if clear then builder.append("\u001b[2J\u001b[H\u001b[3J")
    builder.append(lines.mkString("\r\n"))
    builder.append(SyncEnd)
    terminal.write(builder.result())
    previousLines = lines
    previousWidth = terminal.columns
    cursorRow = math.max(0, lines.length - 1)

  private def partialRender(lines: Vector[String], firstChanged: Int): Unit =
    val moveUp = math.max(0, cursorRow - firstChanged)
    val builder = StringBuilder()
    builder.append(SyncStart)
    if moveUp > 0 then builder.append(s"\u001b[${moveUp}A")
    builder.append("\r\u001b[J")
    builder.append(lines.drop(firstChanged).mkString("\r\n"))
    builder.append(SyncEnd)
    terminal.write(builder.result())
    cursorRow = math.max(0, lines.length - 1)

  private def firstChangedLine(oldLines: Vector[String], newLines: Vector[String]): Int =
    val min = math.min(oldLines.length, newLines.length)
    var i = 0
    while i < min do
      if oldLines(i) != newLines(i) then return i
      i += 1
    if oldLines.length == newLines.length then -1 else min

  private def applyLineResets(lines: Vector[String]): Vector[String] =
    lines.map(_ + LineReset)

object TUI:
  val SyncStart: String = "\u001b[?2026h"
  val SyncEnd: String = "\u001b[?2026l"
  val LineReset: String = "\u001b[0m\u001b]8;;\u0007"

private val SyncStart = TUI.SyncStart
private val SyncEnd = TUI.SyncEnd
private val LineReset = TUI.LineReset
