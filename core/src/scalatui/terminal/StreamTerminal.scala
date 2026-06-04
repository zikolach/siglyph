package scalatui.terminal

import java.io.{InputStream, OutputStream}

/** Stream-backed terminal for non-interactive output and tests.
  *
  * It does not configure raw mode. Dimensions are provided explicitly or read
  * from COLUMNS/LINES environment variables.
  */
class StreamTerminal(
    input: InputStream = InputStream.nullInputStream(),
    output: OutputStream = OutputStream.nullOutputStream(),
    initialColumns: Int = StreamTerminal.envInt("COLUMNS").getOrElse(80),
    initialRows: Int = StreamTerminal.envInt("LINES").getOrElse(24)
) extends Terminal:
  @volatile private var inputHandler: TerminalInput => Unit = _ => ()
  @volatile private var running = false
  private var inputThread: Thread | Null = null

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    inputHandler = onInput
    running = true
    val thread = Thread(() => readLoop(), "scala-tui-stream-terminal-input")
    thread.setDaemon(true)
    inputThread = thread
    thread.start()

  override def stop(): Unit =
    running = false
    Option(inputThread).foreach(_.interrupt())
    inputThread = null

  override def write(data: String): Unit =
    val bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    output.write(bytes)
    output.flush()

  override def columns: Int = initialColumns
  override def rows: Int = initialRows

  override def moveBy(lines: Int): Unit =
    if lines > 0 then write(s"\u001b[${lines}B")
    else if lines < 0 then write(s"\u001b[${-lines}A")

  override def hideCursor(): Unit = write("\u001b[?25l")
  override def showCursor(): Unit = write("\u001b[?25h")
  override def clearLine(): Unit = write("\u001b[K")
  override def clearFromCursor(): Unit = write("\u001b[J")
  override def clearScreen(): Unit = write("\u001b[2J\u001b[H")

  private def readLoop(): Unit =
    val buffer = Array.ofDim[Byte](4096)
    while running do
      try
        val read = input.read(buffer)
        if read < 0 then running = false
        else if read > 0 then
          val data = String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8)
          TerminalInputParser.parse(data).foreach(inputHandler)
      catch case _: InterruptedException => running = false

object StreamTerminal:
  private[terminal] def envInt(name: String): Option[Int] =
    Option(System.getenv(name)).flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0)
