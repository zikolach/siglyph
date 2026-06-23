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
  TerminalInputBuffer,
  TerminalProgressSupport,
  TerminalTitleSupport
}

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
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
 * queries dimensions through ioctl, and polls for live resize changes while running.
 */
final class PosixTerminal(
    initialColumns: Int = PosixTerminal.envInt("COLUMNS").getOrElse(80),
    initialRows: Int = PosixTerminal.envInt("LINES").getOrElse(24)
) extends Terminal,
      TerminalInputDrainSupport,
      KittyKeyboardProtocolTerminal,
      TerminalTitleSupport,
      TerminalProgressSupport:
  private type Winsize = CStruct4[CUnsignedShort, CUnsignedShort, CUnsignedShort, CUnsignedShort]
  @volatile private var running                             = false
  @volatile private var resizePolling                       = false
  @volatile private var inputHandler: TerminalInput => Unit = _ => ()
  @volatile private var resizeHandler: () => Unit           = () => ()
  @volatile private var currentColumns                      = initialColumns
  @volatile private var currentRows                         = initialRows
  private var inputThread: Thread | Null                    = null
  private var resizeThread: Thread | Null                   = null
  private var flushThread: Thread | Null                    = null
  private var savedState: Ptr[termios.termios]              = null
  private val inputBuffer                                   = TerminalInputBuffer()
  private val inputLock                                     = Object()
  private val keyboardProtocolNegotiator                    = KittyKeyboardProtocolNegotiator()

  override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
    if !running then
      if unistd.isatty(unistd.STDIN_FILENO) == 0 then
        throw IllegalStateException(
          "Scala Native POSIX backend requires stdin to be an interactive TTY"
        )
      inputHandler = onInput
      resizeHandler = onResize
      refreshSize(notify = false)
      try
        enableRawMode()
        write("\u001b[?2004h")
        running = true
        val thread  = Thread(() => readLoop(), "scala-tui-posix-terminal-input")
        val flusher = Thread(() => flushLoop(), "scala-tui-posix-terminal-flush")
        thread.setDaemon(true)
        flusher.setDaemon(true)
        inputThread = thread
        flushThread = flusher
        thread.start()
        flusher.start()
        startResizePolling()
      catch
        case e: Throwable =>
          stopResizePolling()
          write("\u001b[?2004l")
          restoreMode()
          throw e

  override def stop(): Unit =
    if running || savedState != null then
      disableKittyKeyboardProtocol()
      write("\u001b[?2004l")
      running = false
      stopResizePolling()
      Option(inputThread).foreach(_.interrupt())
      Option(flushThread).foreach(_.interrupt())
      inputThread = null
      flushThread = null
      inputLock.synchronized(inputBuffer.clear())
      restoreMode()

  override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
    inputLock.synchronized(inputBuffer.clear())

  override def write(data: String): Unit =
    System.out.print(data)
    System.out.flush()

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
    if accepted then write(KittyKeyboardProtocol.EnableSequence)
    accepted

  override def disableKittyKeyboardProtocol(): Unit =
    if keyboardProtocolState !== KittyKeyboardProtocolState.Inactive then
      write(KittyKeyboardProtocol.DisableSequence)
    keyboardProtocolNegotiator.disable()

  private def enableRawMode(): Unit =
    if savedState == null then
      val original = stdlib.malloc(sizeof[termios.termios]).asInstanceOf[Ptr[termios.termios]]
      if original == null then throw OutOfMemoryError("failed to allocate termios state")
      if termios.tcgetattr(unistd.STDIN_FILENO, original) != 0 then
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

      if termios.tcsetattr(unistd.STDIN_FILENO, TCSAFLUSH, raw) != 0 then
        stdlib.free(original.asInstanceOf[Ptr[Byte]])
        throw IllegalStateException("tcsetattr raw mode failed")
      savedState = original

  private def restoreMode(): Unit =
    if savedState != null then
      termios.tcsetattr(unistd.STDIN_FILENO, TCSAFLUSH, savedState)
      stdlib.free(savedState.asInstanceOf[Ptr[Byte]])
      savedState = null

  private def refreshSize(notify: Boolean): Boolean =
    val winsize = stackalloc[Winsize]()
    if ioctl.ioctl(
        unistd.STDOUT_FILENO,
        PosixTerminal.TIOCGWINSZ,
        winsize.asInstanceOf[Ptr[Byte]]
      ) == 0
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
      val thread = Thread(() => resizeLoop(), "scala-tui-posix-terminal-resize")
      thread.setDaemon(true)
      resizeThread = thread
      thread.start()

  private def stopResizePolling(): Unit =
    resizePolling = false
    Option(resizeThread).foreach(_.interrupt())
    resizeThread = null

  private def resizeLoop(): Unit =
    while resizePolling do
      try
        Thread.sleep(PosixTerminal.ResizePollMillis)
        refreshSize(notify = true)
      catch
        case _: InterruptedException => resizePolling = false
        case _: Throwable            => ()

  private def readLoop(): Unit =
    val buffer = stackalloc[CChar](4096)
    while running do
      val read = unistd.read(unistd.STDIN_FILENO, buffer, 4096.toUSize)
      if read < 0 then running = false
      else if read == 0 then ()
      else
        val bytes  = Array.ofDim[Byte](read)
        var i      = 0
        while i < read do
          bytes(i) = (!(buffer + i)).toByte
          i += 1
        val data   = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        val inputs = inputLock.synchronized(inputBuffer.process(data))
        inputs.foreach(inputHandler)

  private def flushLoop(): Unit =
    while running do
      try
        Thread.sleep(PosixTerminal.IncompleteEscapeFlushMillis)
        flushPending()
      catch
        case _: InterruptedException => running = false
        case _: Throwable            => ()

  private def flushPending(): Unit =
    val inputs = inputLock.synchronized(inputBuffer.flush())
    inputs.foreach(inputHandler)

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
