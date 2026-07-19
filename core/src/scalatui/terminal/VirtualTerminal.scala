package scalatui.terminal

import scalatui.syntax.Equality.*

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.atomic.AtomicReference

/** Test terminal that records writes and can deliver scripted input/resize events. */
final class VirtualTerminal(initialColumns: Int = 80, initialRows: Int = 24)
    extends Terminal,
      TerminalMouseProtocolSupport,
      TerminalTitleSupport,
      TerminalProgressSupport:
  private var inputHandler: TerminalInput => Unit = _ => ()
  private var resizeHandler: () => Unit           = () => ()
  private val writesBuffer                        = ArrayBuffer.empty[String]
  private var running                             = false
  private var currentColumns                      = initialColumns
  private var currentRows                         = initialRows
  private var cursorRow                           = 0
  private var cursorCol                           = 0
  @volatile private var mouseReportingEnabled     = false

  override def mouseReportingEnabled_=(enabled: Boolean): Unit =
    mouseReportingEnabled = enabled

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    inputHandler = onInput
    resizeHandler = onResize
    running = true
    if mouseReportingEnabled then write(Terminal.MouseProtocol.Enable)

  override def stop(): Unit =
    if mouseReportingEnabled then write(Terminal.MouseProtocol.Disable)
    running = false
    inputHandler = _ => ()
    resizeHandler = () => ()

  override def write(data: String): Unit =
    writesBuffer += data
    processOutput(data)

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

  override def setTitle(title: String): Unit = write(Terminal.titleSequence(title))

  override def setProgress(active: Boolean): Unit =
    write(if active then Terminal.ProgressActiveSequence else Terminal.ProgressClearSequence)

  def isRunning: Boolean = running

  def writes: Vector[String] = writesBuffer.toVector
  def output: String         = writesBuffer.mkString
  def clearWrites(): Unit    = writesBuffer.clear()

  def setCursorPosition(row: Int, col: Int = 0): Unit =
    cursorRow = math.max(0, math.min(currentRows - 1, row))
    cursorCol = math.max(0, math.min(currentColumns - 1, col))

  def sendInput(input: TerminalInput): Unit = inputHandler(input)

  def sendMouse(input: TerminalInput.Mouse): Unit = sendInput(input)

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

  private def processOutput(data: String): Unit =
    var index = 0
    while index < data.length do
      data.charAt(index) match
        case '\u001b' if data.startsWith(TerminalCursorProtocol.CursorPositionQuery, index) =>
          val response = s"\u001b[${cursorRow + 1};${cursorCol + 1}R"
          val inputs   = TerminalInputBuffer()
            .process(TerminalInputChunk(response.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
          deliverInputOffCallerThread(inputs)
          index += TerminalCursorProtocol.CursorPositionQuery.length
        case '\u001b' if index + 1 < data.length && data.charAt(index + 1) === '['          =>
          index = processCsi(data, index)
        case '\u001b' if index + 1 < data.length && data.charAt(index + 1) === ']'          =>
          index = processStringControl(data, index, allowsBel = true)
        case '\u001b'
            if index + 1 < data.length && isStringControlIntroducer(data.charAt(index + 1)) =>
          index = processStringControl(data, index, allowsBel = false)
        case '\u001b' if index + 1 < data.length                                            =>
          index += 2
        case '\r'                                                                           =>
          cursorCol = 0
          index += 1
        case '\n'                                                                           =>
          advanceLine()
          index += 1
        case ch if ch >= ' '                                                                =>
          advanceColumn()
          index += Character.charCount(data.codePointAt(index))
        case _                                                                              => index += 1

  private def deliverInputOffCallerThread(inputs: Vector[TerminalInput]): Unit =
    if inputs.nonEmpty then
      val handler  = inputHandler
      val failure  = AtomicReference[Throwable](null)
      val delivery = Thread(
        () =>
          try inputs.foreach(handler)
          catch case error: Throwable => failure.set(error),
        "siglyph-virtual-terminal-input"
      )
      delivery.setDaemon(true)
      delivery.start()
      delivery.join()
      Option(failure.get()).foreach(throw _)

  private def processCsi(data: String, start: Int): Int =
    var index = start + 2
    while index < data.length && !isCsiFinal(data.charAt(index)) do index += 1
    if index >= data.length then data.length
    else
      val body      = data.substring(start + 2, index)
      val finalChar = data.charAt(index)
      finalChar match
        case 'A' => cursorRow = math.max(0, cursorRow - csiAmount(body))
        case 'B' => cursorRow = math.min(currentRows - 1, cursorRow + csiAmount(body))
        case 'C' => cursorCol = math.min(currentColumns - 1, cursorCol + csiAmount(body))
        case 'D' => cursorCol = math.max(0, cursorCol - csiAmount(body))
        case 'H' => setCursorPosition(0, 0)
        case _   => ()
      index + 1

  private def processStringControl(data: String, start: Int, allowsBel: Boolean): Int =
    var index    = start + 2
    var endIndex = data.length
    var complete = false
    while index < data.length && !complete do
      if allowsBel && data.charAt(index) === '\u0007' then
        endIndex = index + 1
        complete = true
      else if data.charAt(index) === '\u001b' && index + 1 < data.length &&
        data.charAt(index + 1) === '\\'
      then
        endIndex = index + 2
        complete = true
      else index += 1
    endIndex

  private def isStringControlIntroducer(ch: Char): Boolean =
    ch === 'P' || ch === '_' || ch === '^' || ch === 'X'

  private def csiAmount(body: String): Int =
    val digits = body.takeWhile(_.isDigit)
    if digits.isEmpty then 1 else scala.util.Try(digits.toInt).getOrElse(1)

  private def isCsiFinal(ch: Char): Boolean = ch >= 0x40 && ch <= 0x7e

  private def advanceColumn(): Unit =
    if cursorCol >= currentColumns - 1 then
      cursorCol = 0
      advanceLine()
    else cursorCol += 1

  private def advanceLine(): Unit =
    if cursorRow >= currentRows - 1 then cursorRow = currentRows - 1
    else cursorRow += 1

  private def stripAnsi(value: String): String =
    value.replaceAll("\u001b\\[[0-9;?]*[A-Za-z]", "")
