package scalatui.terminal

import scala.collection.mutable.ArrayBuffer

/** Test terminal that records writes and can deliver scripted input/resize events. */
final class VirtualTerminal(initialColumns: Int = 80, initialRows: Int = 24) extends Terminal:
  private var inputHandler: TerminalInput => Unit = _ => ()
  private var resizeHandler: () => Unit           = () => ()
  private val writesBuffer                        = ArrayBuffer.empty[String]
  private var running                             = false
  private var currentColumns                      = initialColumns
  private var currentRows                         = initialRows

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    inputHandler = onInput
    resizeHandler = onResize
    running = true

  override def stop(): Unit =
    running = false
    inputHandler = _ => ()
    resizeHandler = () => ()

  override def write(data: String): Unit = writesBuffer += data

  override def columns: Int = currentColumns
  override def rows: Int    = currentRows

  override def moveBy(lines: Int): Unit =
    if lines > 0 then write(s"\u001b[${lines}B")
    else if lines < 0 then write(s"\u001b[${-lines}A")

  override def hideCursor(): Unit      = write("\u001b[?25l")
  override def showCursor(): Unit      = write("\u001b[?25h")
  override def clearLine(): Unit       = write("\u001b[K")
  override def clearFromCursor(): Unit = write("\u001b[J")
  override def clearScreen(): Unit     = write("\u001b[2J\u001b[H")

  def isRunning: Boolean = running

  def writes: Vector[String] = writesBuffer.toVector
  def output: String         = writesBuffer.mkString
  def clearWrites(): Unit    = writesBuffer.clear()

  def sendInput(input: TerminalInput): Unit = inputHandler(input)

  def resize(columns: Int, rows: Int): Unit =
    currentColumns = columns
    currentRows = rows
    resizeHandler()

  /**
   * Minimal plain-text viewport for early tests. ANSI sequences are stripped and output is split on
   * CR/LF. A fuller terminal emulator will replace this once the renderer is implemented.
   */
  def viewportLines: Vector[String] =
    val plain    = stripAnsi(output).replace("\r\n", "\n").replace('\r', '\n')
    val allLines = plain.split("\n", -1).toVector
    allLines.takeRight(currentRows)

  private def stripAnsi(value: String): String =
    value.replaceAll("\\u001b\\[[0-9;?]*[A-Za-z]", "")
