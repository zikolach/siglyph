package scalatui.terminal

import scalatui.syntax.Equality.*

import java.io.{InputStream, OutputStream}

/**
 * Stream-backed terminal for non-interactive output and tests.
 *
 * It does not configure raw mode. Dimensions are provided explicitly or read from COLUMNS/LINES
 * environment variables.
 */
class StreamTerminal(
    input: InputStream = InputStream.nullInputStream(),
    output: OutputStream = OutputStream.nullOutputStream(),
    initialColumns: Int = StreamTerminal.envInt("COLUMNS").getOrElse(80),
    initialRows: Int = StreamTerminal.envInt("LINES").getOrElse(24)
) extends Terminal,
      TerminalInputDrainSupport:
  @volatile private var inputHandler: TerminalInput => Unit = _ => ()
  @volatile private var running                             = false
  private var inputThread: Thread | Null                    = null
  private var flushThread: Thread | Null                    = null
  private val inputBuffer                                   = TerminalInputBuffer()
  private val inputLock                                     = Object()

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    inputHandler = onInput
    running = true
    val thread  = Thread(() => readLoop(), "scala-tui-stream-terminal-input")
    val flusher = Thread(() => flushLoop(), "scala-tui-stream-terminal-flush")
    thread.setDaemon(true)
    flusher.setDaemon(true)
    inputThread = thread
    flushThread = flusher
    thread.start()
    flusher.start()

  override def stop(): Unit =
    if running || (inputThread ne null) then
      running = false
      Option(inputThread).foreach(_.interrupt())
      Option(flushThread).foreach(_.interrupt())
      inputThread = null
      flushThread = null
      inputLock.synchronized(inputBuffer.clear())

  override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
    inputLock.synchronized(inputBuffer.clear())

  override def write(data: String): Unit =
    val bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    output.write(bytes)
    output.flush()

  override def columns: Int = initialColumns
  override def rows: Int    = initialRows

  override def moveBy(lines: Int): Unit =
    if lines > 0 then write(s"\u001b[${lines}B")
    else if lines < 0 then write(s"\u001b[${-lines}A")

  override def hideCursor(): Unit      = write("\u001b[?25l")
  override def showCursor(): Unit      = write("\u001b[?25h")
  override def clearLine(): Unit       = write("\u001b[K")
  override def clearFromCursor(): Unit = write("\u001b[J")
  override def clearScreen(): Unit     = write("\u001b[2J\u001b[H")

  private def readLoop(): Unit =
    val buffer = Array.ofDim[Byte](4096)
    while running do
      try
        val read = input.read(buffer)
        if read < 0 then running = false
        else if read === 0 then flushPending()
        else
          val data   = String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8)
          val inputs = inputLock.synchronized(inputBuffer.process(data))
          inputs.foreach(inputHandler)
      catch case _: InterruptedException => running = false

  private def flushLoop(): Unit =
    while running do
      try
        Thread.sleep(StreamTerminal.IncompleteEscapeFlushMillis)
        flushPending()
      catch case _: InterruptedException => running = false

  private def flushPending(): Unit =
    val inputs = inputLock.synchronized(inputBuffer.flush())
    inputs.foreach(inputHandler)

object StreamTerminal:
  private val IncompleteEscapeFlushMillis = 75L

  private[terminal] def envInt(name: String): Option[Int] =
    Option(System.getenv(name)).flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0)
