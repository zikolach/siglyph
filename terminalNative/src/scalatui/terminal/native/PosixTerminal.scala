package scalatui.terminal.native

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KittyKeyboardProtocol,
  KittyKeyboardProtocolNegotiator,
  KittyKeyboardProtocolState,
  KittyKeyboardProtocolTerminal,
  Terminal,
  TerminalInputDrainSupport,
  TerminalInput,
  TerminalInputChunk,
  TerminalMouseProtocolSupport,
  TerminalInputBuffer,
  OrderedInputDelivery,
  TerminalProgressSupport,
  TerminalTitleSupport
}

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import java.io.IOException

import scala.scalanative.posix.termios
import scala.scalanative.posix.termios.*
import scala.scalanative.posix.termiosOps.*
import scala.scalanative.posix.unistd
import scala.scalanative.posix.unistd.*
import scala.scalanative.posix.sys.ioctl
import scala.scalanative.libc.stdlib

/**
 * Scala Native POSIX terminal backend for macOS/Linux.
 *
 * It configures stdin raw mode via termios, reads stdin on a background thread, writes to stdout,
 * queries dimensions through ioctl, and polls for live resize changes while running. After
 * [[stop]], [[start]] requires the previous stdin reader, input flush worker, and resize worker to
 * have terminated. Start is also rejected while any cleanup obligation from the previous run
 * remains incomplete.
 */
final class PosixTerminal(
    initialColumns: Int = PosixTerminal.envInt("COLUMNS").getOrElse(80),
    initialRows: Int = PosixTerminal.envInt("LINES").getOrElse(24)
) extends Terminal,
      TerminalInputDrainSupport,
      TerminalMouseProtocolSupport,
      KittyKeyboardProtocolTerminal,
      TerminalTitleSupport,
      TerminalProgressSupport:
  private type Winsize = CStruct4[CUnsignedShort, CUnsignedShort, CUnsignedShort, CUnsignedShort]
  @volatile private var running                                             = false
  @volatile private var resizePolling                                       = false
  @volatile private var inputHandler: TerminalInput => Unit                 = _ => ()
  @volatile private var resizeHandler: () => Unit                           = () => ()
  @volatile private var currentColumns                                      = initialColumns
  @volatile private var currentRows                                         = initialRows
  private var inputThread: Thread | Null                                    = null
  private var resizeThread: Thread | Null                                   = null
  private var flushThread: Thread | Null                                    = null
  private var savedState: Ptr[termios.termios]                              = null
  private val inputBuffer                                                   = TerminalInputBuffer()
  private val inputDelivery                                                 = OrderedInputDelivery()
  private var inputGeneration                                               = 0L
  private var inputCleanupPending                                           = false
  private var pasteCleanupPending                                           = false
  private var kittyCleanupPending                                           = false
  @volatile private var mouseReportingEnabled                               = false
  private var mouseCleanupPending                                           = false
  private[native] var cleanupFailureForTesting: String => Option[Throwable] = _ => None
  private[native] var writeFailureForTesting: String => Option[Throwable]   = _ => None
  private val keyboardProtocolNegotiator                                    = KittyKeyboardProtocolNegotiator()

  override def mouseReportingEnabled_=(enabled: Boolean): Unit =
    mouseReportingEnabled = enabled

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = synchronized {
    if running then throw IllegalStateException("PosixTerminal is already running")
    else
      reapInputThread()
      reapFlushThread()
      reapResizeThread()
      if hasCleanupObligations then
        throw IllegalStateException(
          "cannot start PosixTerminal while cleanup from the previous generation remains incomplete"
        )
      if inputThread ne null then
        throw IllegalStateException(
          "cannot restart PosixTerminal while the previous stdin reader is still alive"
        )
      if flushThread ne null then
        throw IllegalStateException(
          "cannot restart PosixTerminal while the previous flush worker is still alive"
        )
      if resizeThread ne null then
        throw IllegalStateException(
          "cannot restart PosixTerminal while the previous resize worker is still alive"
        )
      if unistd.isatty(unistd.STDIN_FILENO) === 0 then
        throw IllegalStateException(
          "Scala Native POSIX backend requires stdin to be an interactive TTY"
        )
      inputHandler = onInput
      resizeHandler = onResize
      refreshSize(notify = false)
      try
        enableRawMode()
        pasteCleanupPending = true
        write("\u001b[?2004h")
        if mouseReportingEnabled then
          mouseCleanupPending = true
          write(Terminal.MouseProtocol.Enable)
        inputGeneration = inputDelivery.start(inputBuffer.clear())
        inputCleanupPending = true
        running = true
        val generation = inputGeneration
        val thread     = Thread(() => readLoop(generation), "siglyph-posix-terminal-input")
        val flusher    = Thread(() => flushLoop(generation), "siglyph-posix-terminal-flush")
        thread.setDaemon(true)
        flusher.setDaemon(true)
        inputThread = thread
        flushThread = flusher
        thread.start()
        flusher.start()
        startResizePolling()
      catch
        case e: Throwable =>
          running = false
          stopResizePolling()
          cleanup().foreach(e.addSuppressed)
          throw e

  }

  override def stop(): Unit = synchronized {
    if running || hasCleanupObligations || (inputThread ne null) || (flushThread ne null) then
      running = false
      stopResizePolling()
      cleanup().foreach(throw _)

  }

  override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
    inputDelivery.clear(inputBuffer.clear())

  override def write(data: String): Unit =
    val stream = System.out
    stream.print(data)
    writeFailureForTesting(data).foreach(throw _)
    stream.flush()
    if stream.checkError() then
      throw IOException(
        "PosixTerminal output PrintStream reported a suppressed output failure; check stdout"
      )

  override def columns: Int =
    refreshSize(notify = false)
    currentColumns

  override def rows: Int =
    refreshSize(notify = false)
    currentRows

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

  override def keyboardProtocolState: KittyKeyboardProtocolState =
    keyboardProtocolNegotiator.expire(System.currentTimeMillis())
    keyboardProtocolNegotiator.state

  override def requestKittyKeyboardProtocol(timeoutMillis: Long): Unit =
    keyboardProtocolNegotiator.begin(System.currentTimeMillis(), timeoutMillis)
    write(KittyKeyboardProtocol.QuerySequence)

  override def acceptKittyKeyboardProtocolResponse(
      response: String,
      nowMillis: Long
  ): Boolean =
    val accepted = keyboardProtocolNegotiator.receiveResponse(response, nowMillis)
    if accepted then
      kittyCleanupPending = true
      write(KittyKeyboardProtocol.EnableSequence)
    accepted

  override def disableKittyKeyboardProtocol(): Unit =
    if keyboardProtocolState !== KittyKeyboardProtocolState.Inactive then
      write(KittyKeyboardProtocol.DisableSequence)
    keyboardProtocolNegotiator.disable()
    kittyCleanupPending = false

  private def enableRawMode(): Unit =
    if savedState === null then
      val original = stdlib.malloc(sizeof[termios.termios]).asInstanceOf[Ptr[termios.termios]]
      if original === null then throw OutOfMemoryError("failed to allocate termios state")
      if termios.tcgetattr(unistd.STDIN_FILENO, original) !== 0 then
        stdlib.free(original.asInstanceOf[Ptr[Byte]])
        throw IllegalStateException("tcgetattr failed")

      val raw = stackalloc[termios.termios]()
      !raw = !original
      raw.c_iflag = clearFlags(raw.c_iflag, BRKINT | ICRNL | INPCK | ISTRIP | IXON)
      raw.c_oflag = clearFlags(raw.c_oflag, OPOST)
      raw.c_cflag = setFlags(raw.c_cflag, CS8)
      raw.c_lflag = clearFlags(raw.c_lflag, ECHO | ICANON | IEXTEN | ISIG)
      raw.c_cc(termios.VMIN) = 0.toUByte
      raw.c_cc(termios.VTIME) = 1.toUByte

      if termios.tcsetattr(unistd.STDIN_FILENO, TCSAFLUSH, raw) !== 0 then
        stdlib.free(original.asInstanceOf[Ptr[Byte]])
        throw IllegalStateException("tcsetattr raw mode failed")
      savedState = original

  private def restoreMode(): Unit =
    if savedState !== null then
      val state    = savedState
      val restored = termios.tcsetattr(unistd.STDIN_FILENO, TCSAFLUSH, state) === 0
      if restored then
        stdlib.free(state.asInstanceOf[Ptr[Byte]])
        savedState = null
      else throw IllegalStateException("tcsetattr restore mode failed")

  private def refreshSize(notify: Boolean): Boolean =
    val winsize = stackalloc[Winsize]()
    if ioctl.ioctl(
        unistd.STDOUT_FILENO,
        PosixTerminal.TIOCGWINSZ,
        winsize.asInstanceOf[Ptr[Byte]]
      ) === 0
    then
      val rows = winsize._1.toInt
      val cols = winsize._2.toInt
      if rows > 0 && cols > 0 then
        val changed = (rows !== currentRows) || (cols !== currentColumns)
        currentRows = rows
        currentColumns = cols
        if changed && notify then resizeHandler()
        changed
      else false
    else false

  private def startResizePolling(): Unit =
    if (resizeThread eq null) then
      resizePolling = true
      val thread = Thread(() => resizeLoop(), "siglyph-posix-terminal-resize")
      thread.setDaemon(true)
      resizeThread = thread
      thread.start()

  private def stopResizePolling(): Unit =
    resizePolling = false
    Option(resizeThread).foreach(_.interrupt())

  private def reapResizeThread(): Unit =
    if Option(resizeThread).exists(!_.isAlive) then resizeThread = null

  private def resizeLoop(): Unit =
    try
      while resizePolling do
        try
          Thread.sleep(PosixTerminal.ResizePollMillis)
          refreshSize(notify = true)
        catch
          case _: InterruptedException => resizePolling = false
          case _: Throwable            => ()
    finally clearResizeThread(Thread.currentThread())

  private def readLoop(generation: Long): Unit =
    try
      val buffer = stackalloc[CChar](4096)
      while inputDelivery.isActive(generation) do
        val read = unistd.read(unistd.STDIN_FILENO, buffer, 4096.toUSize)
        if read < 0 then terminateGeneration(generation)
        else if read.toLong === 0L then ()
        else
          val bytes = Array.ofDim[Byte](read)
          var i     = 0
          while i < read do
            bytes(i) = (!(buffer + i)).toByte
            i += 1
          val chunk = TerminalInputChunk(bytes)
          parseAndDeliver(generation, inputBuffer.process(chunk))
    finally
      terminateGeneration(generation)
      clearInputThread(Thread.currentThread())

  private def flushLoop(generation: Long): Unit =
    try
      while inputDelivery.isActive(generation) do
        try
          Thread.sleep(PosixTerminal.IncompleteEscapeFlushMillis)
          flushPending(generation)
        catch
          case _: InterruptedException => terminateGeneration(generation)
          case _: Throwable            => ()
    finally clearFlushThread(Thread.currentThread())

  private def flushPending(generation: Long): Unit =
    parseAndDeliver(generation, inputBuffer.flush())

  private def parseAndDeliver(generation: Long, parse: => Vector[TerminalInput]): Unit =
    inputDelivery.parseAndDeliver(generation, parse)(inputHandler)

  private def terminateGeneration(generation: Long): Unit =
    if inputDelivery.stop(generation, inputBuffer.clear()) then
      synchronized {
        if inputGeneration === generation then running = false
      }

  private def hasCleanupObligations: Boolean =
    inputCleanupPending || kittyCleanupPending || mouseCleanupPending || pasteCleanupPending ||
      (savedState !== null)

  private def cleanup(): Option[Throwable] =
    var failure                                      = Option.empty[Throwable]
    def attempt(name: String)(action: => Unit): Unit =
      try
        cleanupFailureForTesting(name).foreach(throw _)
        action
      catch
        case error: Throwable => failure match
            case Some(first) => first.addSuppressed(error)
            case None        => failure = Some(error)

    if inputCleanupPending then
      attempt("input") {
        inputDelivery.stop(inputGeneration, inputBuffer.clear())
        Option(inputThread).foreach(_.interrupt())
        Option(flushThread).foreach(_.interrupt())
        reapInputThread()
        reapFlushThread()
        inputCleanupPending = false
      }
    if kittyCleanupPending then attempt("kitty")(disableKittyKeyboardProtocol())
    if mouseCleanupPending then
      attempt("mouse") {
        write(Terminal.MouseProtocol.Disable)
        mouseCleanupPending = false
      }
    if pasteCleanupPending then
      attempt("paste") {
        write("\u001b[?2004l")
        pasteCleanupPending = false
      }
    if savedState !== null then attempt("termios")(restoreMode())
    failure

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

  private def clearResizeThread(thread: Thread): Unit = synchronized {
    if resizeThread eq thread then resizeThread = null
  }

  private[native] def retainFlushThreadForTesting(thread: Thread): Unit = synchronized {
    flushThread = thread
  }

  private def clearFlags(value: termios.tcflag_t, mask: Int): termios.tcflag_t =
    (value.toInt & ~mask).toUInt

  private def setFlags(value: termios.tcflag_t, mask: Int): termios.tcflag_t =
    (value.toInt | mask).toUInt

object PosixTerminal:
  private val ResizePollMillis            = 150L
  private val IncompleteEscapeFlushMillis = 75L

  private val TIOCGWINSZ: CLongInt =
    if Option(System.getProperty("os.name")).exists(_.toLowerCase.contains("mac")) then
      0x40087468L.toSize
    else 0x5413L.toSize

  private[native] def envInt(name: String): Option[Int] =
    Option(System.getenv(name)).flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0)
