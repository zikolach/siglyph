package scalatui.core

import scalatui.ansi.Ansi
import scalatui.terminal.{Terminal, TerminalInput}

/** Main terminal UI runtime with a small differential renderer. */
final class TUI(val terminal: Terminal):
  private val root = Container()
  private var previousLines = Vector.empty[String]
  private var previousWidth = 0
  private var cursorRow = 0
  private var focusedComponent: Option[Component] = None
  private var running = false

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
    running = true
    terminal.start(handleInput, () => requestRender())
    terminal.hideCursor()
    requestRender(force = true)

  def stop(): Unit =
    running = false
    terminal.showCursor()
    terminal.stop()

  def requestRender(force: Boolean = false): Unit =
    if force then
      previousLines = Vector.empty
      previousWidth = 0
      cursorRow = 0
    renderNow()

  private def handleInput(input: TerminalInput): Unit =
    focusedComponent.foreach(_.handleInput(input))
    requestRender()

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
