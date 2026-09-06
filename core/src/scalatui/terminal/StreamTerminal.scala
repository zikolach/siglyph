package scalatui.terminal

import scalatui.syntax.Equality.*

import java.io.{InputStream, OutputStream}

/**
 * Stream-backed terminal for non-interactive output and tests.
 *
 * It does not configure raw mode. Dimensions are provided explicitly or read from COLUMNS/LINES
 * environment variables. After [[stop]], [[start]] requires the prior input reader and periodic
 * flush worker to have terminated; a callback or input stream that ignores interruption must
 * unblock before this terminal can restart.
 */
class StreamTerminal(
    input: InputStream = InputStream.nullInputStream(),
    output: OutputStream = OutputStream.nullOutputStream(),
    initialColumns: Int = StreamTerminal.envInt("COLUMNS").getOrElse(80),
    initialRows: Int = StreamTerminal.envInt("LINES").getOrElse(24)
) extends Terminal,
      TerminalInputDrainSupport:
  @volatile private var inputHandler: TerminalInput => Unit                  = _ => ()
  @volatile private var running                                              = false
  private var inputThread: Thread | Null                                     = null
  private var flushThread: Thread | Null                                     = null
  private val inputBuffer                                                    = TerminalInputBuffer()
  private val inputDelivery                                                  = OrderedInputDelivery()
  private var inputGeneration                                                = 0L
  private var inputFailureGeneration                                         = -1L
  private var flushFailureGeneration                                         = -1L
  private[terminal] var workerFailureForTesting: String => Option[Throwable] = _ => None
  private[terminal] var workerFailureClaimedForTesting: String => Unit       = _ => ()

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    start(onInput, onResize, _ => ())

  override def start(
      onInput: TerminalInput => Unit,
      onResize: () => Unit,
      onFailure: Throwable => Unit
  ): Unit = synchronized {
    if running then throw IllegalStateException("StreamTerminal is already running")
    reapInputThread()
    reapFlushThread()
    if inputThread ne null then
      throw IllegalStateException(
        "cannot restart StreamTerminal while the previous input reader is still alive"
      )
    if flushThread ne null then
      throw IllegalStateException(
        "cannot restart StreamTerminal while the previous flush worker is still alive"
      )
    inputHandler = onInput
    inputGeneration = inputDelivery.start(inputBuffer.clear())
    running = true
    val generation = inputGeneration
    val thread     = Thread(
      () => readLoop(generation, onFailure),
      "siglyph-stream-terminal-input"
    )
    val flusher    = Thread(
      () => flushLoop(generation, onFailure),
      "siglyph-stream-terminal-flush"
    )
    thread.setDaemon(true)
    flusher.setDaemon(true)
    inputThread = thread
    flushThread = flusher
    thread.start()
    flusher.start()
  }

  override def stop(): Unit = synchronized {
    if running || (inputThread ne null) || (flushThread ne null) then
      running = false
      inputDelivery.stop(inputGeneration, inputBuffer.clear())
      Option(inputThread).foreach(_.interrupt())
      Option(flushThread).foreach(_.interrupt())
      reapInputThread()
      reapFlushThread()
  }

  override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
    inputDelivery.clear(inputBuffer.clear())

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

  private def readLoop(generation: Long, onFailure: Throwable => Unit): Unit =
    try
      val buffer = Array.ofDim[Byte](4096)
      while inputDelivery.isActive(generation) do
        workerFailureForTesting("input").foreach(throw _)
        val read = input.read(buffer)
        if read < 0 then
          flushPending(generation)
          terminateGeneration(generation)
        else if read === 0 then flushPending(generation)
        else
          val chunk = TerminalInputChunk(buffer, 0, read)
          parseAndDeliver(generation, inputBuffer.process(chunk))
    catch case error: Throwable => reportFailure(generation, "input", error, onFailure)
    finally
      terminateGeneration(generation)
      clearInputThread(Thread.currentThread())

  private def flushLoop(generation: Long, onFailure: Throwable => Unit): Unit =
    try
      while inputDelivery.isActive(generation) do
        Thread.sleep(StreamTerminal.IncompleteEscapeFlushMillis)
        workerFailureForTesting("flush").foreach(throw _)
        flushPending(generation)
    catch case error: Throwable => reportFailure(generation, "flush", error, onFailure)
    finally clearFlushThread(Thread.currentThread())

  private def reportFailure(
      generation: Long,
      worker: String,
      error: Throwable,
      onFailure: Throwable => Unit
  ): Unit =
    if claimWorkerFailure(generation, worker) then
      try workerFailureClaimedForTesting(worker)
      catch case _: Throwable => ()
      try onFailure(error)
      catch case _: Throwable => ()

  private def claimWorkerFailure(generation: Long, worker: String): Boolean = synchronized {
    if !running || (inputGeneration !== generation) then false
    else
      worker match
        case "input" if inputFailureGeneration !== generation =>
          inputFailureGeneration = generation
          true
        case "flush" if flushFailureGeneration !== generation =>
          flushFailureGeneration = generation
          true
        case _                                                => false
  }

  private def flushPending(generation: Long): Unit =
    parseAndDeliver(generation, inputBuffer.flush())

  private def parseAndDeliver(generation: Long, parse: => Vector[TerminalInput]): Unit =
    inputDelivery.parseAndDeliver(generation, parse)(inputHandler)

  private def terminateGeneration(generation: Long): Unit =
    val terminate = synchronized {
      if running && (inputGeneration === generation) then
        running = false
        true
      else false
    }
    if terminate then inputDelivery.stop(generation, inputBuffer.clear())

  private def reapInputThread(): Unit =
    if Option(inputThread).exists(!_.isAlive) then inputThread = null

  private def reapFlushThread(): Unit =
    if Option(flushThread).exists(!_.isAlive) then flushThread = null

  private def clearInputThread(thread: Thread): Unit = synchronized {
    if inputThread eq thread then inputThread = null
  }

  private def clearFlushThread(thread: Thread): Unit = synchronized {
    if flushThread eq thread then flushThread = null
  }

object StreamTerminal:
  private val IncompleteEscapeFlushMillis = 75L

  private[terminal] def envInt(name: String): Option[Int] =
    Option(System.getenv(name)).flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0)
