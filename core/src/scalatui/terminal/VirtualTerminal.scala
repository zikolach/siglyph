package scalatui.terminal

import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic test terminal that records writes and can deliver scripted input/resize events.
 *
 * Its screen model intentionally covers only renderer-critical behavior: grapheme display width,
 * wide-cell advance, DEC autowrap, cursor movement/reporting, and erase operations. It is not a
 * general-purpose terminal emulator.
 */
final class VirtualTerminal(initialColumns: Int = 80, initialRows: Int = 24)
    extends Terminal,
      TerminalMouseProtocolSupport,
      TerminalTitleSupport,
      TerminalProgressSupport:
  private var inputHandler: TerminalInput => Unit = _ => ()
  private var resizeHandler: () => Unit           = () => ()
  private val writesBuffer                        = ArrayBuffer.empty[String]
  private var running                             = false
  private var currentColumns                      = math.max(0, initialColumns)
  private var currentRows                         = math.max(0, initialRows)
  private var cursorRow                           = 0
  private var cursorCol                           = 0
  private var autowrapEnabled                     = true
  private var wrapPending                         = false
  private val screen                              = ArrayBuffer.fill(currentRows)(blankRow())
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
    cursorRow = clamp(row, currentRows)
    cursorCol = clamp(col, currentColumns)
    wrapPending = false

  /** Current zero-based cursor position in the modeled viewport. */
  def cursorPosition: (Int, Int) = (cursorRow, cursorCol)

  /** Whether DEC autowrap mode is enabled in the modeled viewport. */
  def isAutowrapEnabled: Boolean = autowrapEnabled

  /** Modeled viewport rows with continuation cells omitted and trailing blank cells trimmed. */
  def screenLines: Vector[String] = screen.toVector.map(renderScreenRow)

  def sendInput(input: TerminalInput): Unit = inputHandler(input)

  def sendMouse(input: TerminalInput.Mouse): Unit = sendInput(input)

  def resize(columns: Int, rows: Int): Unit =
    val nextColumns = math.max(0, columns)
    val nextRows    = math.max(0, rows)
    val resized     = ArrayBuffer.fill(nextRows)(Array.fill(nextColumns)(VirtualTerminal.Blank))
    var row         = 0
    while row < math.min(currentRows, nextRows) do
      var col = 0
      while col < math.min(currentColumns, nextColumns) do
        resized(row)(col) = screen(row)(col)
        col += 1
      row += 1
    screen.clear()
    screen ++= resized
    currentColumns = nextColumns
    currentRows = nextRows
    cursorRow = clamp(cursorRow, currentRows)
    cursorCol = clamp(cursorCol, currentColumns)
    wrapPending = false
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
          wrapPending = false
          index += 1
        case '\n'                                                                           =>
          advanceLine()
          wrapPending = false
          index += 1
        case '\b'                                                                           =>
          cursorCol = math.max(0, cursorCol - 1)
          wrapPending = false
          index += 1
        case ch if ch >= ' '                                                                =>
          val end = printableEnd(data, index)
          Unicode.graphemeClusters(data.substring(index, end)).foreach(writeGrapheme)
          index = end
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
        case 'A'                  => setCursorPosition(cursorRow - csiAmount(body), cursorCol)
        case 'B'                  => setCursorPosition(cursorRow + csiAmount(body), cursorCol)
        case 'C'                  => setCursorPosition(cursorRow, cursorCol + csiAmount(body))
        case 'D'                  => setCursorPosition(cursorRow, cursorCol - csiAmount(body))
        case 'G'                  => setCursorPosition(cursorRow, csiPosition(body, 0) - 1)
        case 'd'                  => setCursorPosition(csiPosition(body, 0) - 1, cursorCol)
        case 'H' | 'f'            =>
          val positions = csiPositions(body)
          setCursorPosition(
            positions.headOption.getOrElse(1) - 1,
            positions.drop(1).headOption.getOrElse(1) - 1
          )
        case 'J'                  => eraseDisplay(csiEraseMode(body))
        case 'K'                  => eraseLine(csiEraseMode(body))
        case 'h' if body === "?7" =>
          autowrapEnabled = true
          wrapPending = false
        case 'l' if body === "?7" =>
          autowrapEnabled = false
          wrapPending = false
        case _                    => ()
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
    if digits.isEmpty then 1 else math.max(1, scala.util.Try(digits.toInt).getOrElse(1))

  private def csiPosition(body: String, index: Int): Int =
    csiPositions(body).lift(index).getOrElse(1)

  private def csiEraseMode(body: String): Int =
    if body.isEmpty then 0 else scala.util.Try(body.takeWhile(_.isDigit).toInt).getOrElse(0)

  private def csiPositions(body: String): Vector[Int] =
    val normalized = body.dropWhile(ch => ch === '?' || ch === '>')
    if normalized.isEmpty then Vector.empty
    else
      normalized.split(";", -1).toVector.map(value =>
        if value.isEmpty then 1 else scala.util.Try(value.toInt).getOrElse(1)
      )

  private def isCsiFinal(ch: Char): Boolean = ch >= 0x40 && ch <= 0x7e

  private def advanceLine(): Unit =
    if currentRows <= 0 then cursorRow = 0
    else if cursorRow >= currentRows - 1 then
      if screen.nonEmpty then
        screen.remove(0)
        screen += blankRow()
      cursorRow = currentRows - 1
    else cursorRow += 1

  private def writeGrapheme(grapheme: String): Unit =
    val width = Unicode.graphemeWidth(grapheme)
    if width <= 0 then appendZeroWidth(grapheme)
    else if currentColumns > 0 && currentRows > 0 then
      if wrapPending && autowrapEnabled then wrapToNextLine()
      else if wrapPending then wrapPending = false

      if width > currentColumns - cursorCol && cursorCol > 0 && autowrapEnabled then
        wrapToNextLine()

      val occupied = math.min(width, currentColumns - cursorCol)
      if occupied > 0 then
        clearCell(cursorRow, cursorCol)
        screen(cursorRow)(cursorCol) = grapheme
        var offset = 1
        while offset < occupied do
          clearCell(cursorRow, cursorCol + offset)
          screen(cursorRow)(cursorCol + offset) = VirtualTerminal.Continuation
          offset += 1

        if cursorCol + occupied >= currentColumns then
          cursorCol = currentColumns - 1
          wrapPending = autowrapEnabled
        else cursorCol += occupied

  private def appendZeroWidth(grapheme: String): Unit =
    if currentColumns > 0 && currentRows > 0 then
      var col = if wrapPending then cursorCol else cursorCol - 1
      while col > 0 && screen(cursorRow)(col) === VirtualTerminal.Continuation do col -= 1
      if col >= 0 && (screen(cursorRow)(col) !== VirtualTerminal.Blank) then
        screen(cursorRow)(col) = screen(cursorRow)(col) + grapheme

  private def wrapToNextLine(): Unit =
    cursorCol = 0
    wrapPending = false
    advanceLine()

  private def eraseDisplay(mode: Int): Unit = mode match
    case 0     =>
      eraseRange(cursorRow, cursorCol, currentColumns)
      var row = cursorRow + 1
      while row < currentRows do
        eraseRange(row, 0, currentColumns)
        row += 1
    case 1     =>
      var row = 0
      while row < cursorRow do
        eraseRange(row, 0, currentColumns)
        row += 1
      eraseRange(cursorRow, 0, cursorCol + 1)
    case 2 | 3 =>
      var row = 0
      while row < currentRows do
        eraseRange(row, 0, currentColumns)
        row += 1
    case _     => ()
  wrapPending = false

  private def eraseLine(mode: Int): Unit =
    mode match
      case 0 => eraseRange(cursorRow, cursorCol, currentColumns)
      case 1 => eraseRange(cursorRow, 0, cursorCol + 1)
      case 2 => eraseRange(cursorRow, 0, currentColumns)
      case _ => ()
    wrapPending = false

  private def eraseRange(row: Int, from: Int, until: Int): Unit =
    if row >= 0 && row < screen.length then
      var col = math.max(0, from)
      while col < math.min(currentColumns, until) do
        clearCell(row, col)
        col += 1

  private def clearCell(row: Int, col: Int): Unit =
    if row >= 0 && row < screen.length && col >= 0 && col < currentColumns then
      var start = col
      while start > 0 && screen(row)(start) === VirtualTerminal.Continuation do start -= 1
      screen(row)(start) = VirtualTerminal.Blank
      var next  = start + 1
      while next < currentColumns && screen(row)(next) === VirtualTerminal.Continuation do
        screen(row)(next) = VirtualTerminal.Blank
        next += 1

  private def printableEnd(data: String, start: Int): Int =
    var index = start
    while index < data.length && data.charAt(index) >= ' ' && (data.charAt(index) !== '\u001b') do
      index += Character.charCount(data.codePointAt(index))
    index

  private def blankRow(): Array[String] = Array.fill(currentColumns)(VirtualTerminal.Blank)

  private def renderScreenRow(row: Array[String]): String =
    val rendered = row.iterator.filterNot(_ === VirtualTerminal.Continuation).mkString
    var end      = rendered.length
    while end > 0 && rendered.charAt(end - 1) === ' ' do end -= 1
    rendered.substring(0, end)

  private def clamp(value: Int, bound: Int): Int =
    if bound <= 0 then 0 else math.max(0, math.min(bound - 1, value))

  private def stripAnsi(value: String): String =
    value.replaceAll("\u001b\\[[0-9;?]*[A-Za-z]", "")

object VirtualTerminal:
  private[terminal] val Blank: String        = " "
  private[terminal] val Continuation: String = "\u0000"
