package scalatui.terminal.jvm

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KittyKeyboardProtocol,
  KittyKeyboardProtocolNegotiator,
  KittyKeyboardProtocolState,
  KittyKeyboardProtocolTerminal,
  StreamTerminal,
  Terminal,
  TerminalMouseProtocolSupport,
  TerminalProgressSupport,
  TerminalTitleSupport
}

import java.io.{IOException, InputStream, OutputStream, PrintStream}
import scala.sys.process.*

/**
 * Unix JVM terminal backend using `stty` for interactive raw mode.
 *
 * After [[stop]], [[start]] requires the previous input reader, input flush worker, and resize
 * worker to have terminated. Start is also rejected while any cleanup obligation from the previous
 * run remains incomplete.
 */
final class SttyTerminal(
    input: InputStream = System.in,
    output: OutputStream = System.out,
    columnsOverride: Option[Int] = None,
    rowsOverride: Option[Int] = None,
    sizeQuery: () => Option[(Int, Int)] = () => SttyTerminal.querySize()
) extends StreamTerminal(
      input = input,
      output = output,
      initialColumns = columnsOverride.orElse(StreamTerminal.envInt("COLUMNS")).getOrElse(80),
      initialRows = rowsOverride.orElse(StreamTerminal.envInt("LINES")).getOrElse(24)
    ),
      KittyKeyboardProtocolTerminal,
      TerminalMouseProtocolSupport,
      TerminalTitleSupport,
      TerminalProgressSupport:
  @volatile private var savedState: Option[String]                       = None
  @volatile private var resizePolling                                    = false
  private var sttyRunning                                                = false
  private var inputCleanupPending                                        = false
  private var pasteCleanupPending                                        = false
  private var kittyCleanupPending                                        = false
  private[jvm] var cleanupFailureForTesting: String => Option[Throwable] = _ => None
  private[jvm] var sttyFailureForTesting: String => Option[Throwable]    = _ => None
  @volatile private var currentColumns                                   = columnsOverride
    .orElse(StreamTerminal.envInt("COLUMNS"))
    .getOrElse(80)
  @volatile private var currentRows                                      =
    rowsOverride.orElse(StreamTerminal.envInt("LINES")).getOrElse(24)
  private var resizeHandler: () => Unit                                  = () => ()
  private var resizeThread: Thread | Null                                = null
  private var mouseReportingEnabled                                      = false
  private var mouseCleanupPending                                        = false
  private val keyboardProtocolNegotiator                                 = KittyKeyboardProtocolNegotiator()

  override def mouseReportingEnabled_=(enabled: Boolean): Unit =
    mouseReportingEnabled = enabled

  override def start(onInput: scalatui.terminal.TerminalInput => Unit, onResize: () => Unit): Unit =
    synchronized {
      if sttyRunning then throw IllegalStateException("SttyTerminal is already running")
      reapResizeThread()
      if hasCleanupObligations then
        throw IllegalStateException(
          "cannot start SttyTerminal while cleanup from the previous generation remains incomplete"
        )
      if resizeThread ne null then
        throw IllegalStateException(
          "cannot restart SttyTerminal while the previous resize worker is still alive"
        )
      resizeHandler = onResize
      val state =
        try runStty("-g")
        catch
          case cause: Throwable =>
            throw IllegalStateException(
              "SttyTerminal requires accessible interactive /dev/tty; use StreamTerminal for non-interactive streams",
              cause
            )
      savedState = Some(state)
      try
        refreshSize(notify = false)
        runStty("raw -echo min 1 time 0")
        pasteCleanupPending = true
        write("\u001b[?2004h")
        if mouseReportingEnabled then
          mouseCleanupPending = true
          write(Terminal.MouseProtocol.Enable)
        inputCleanupPending = true
        super.start(onInput, onResize)
        sttyRunning = true
        startResizePolling()
      catch
        case e: Throwable =>
          sttyRunning = false
          stopResizePolling()
          cleanup().foreach(e.addSuppressed)
          throw e
    }

  override def stop(): Unit = synchronized {
    sttyRunning = false
    stopResizePolling()
    cleanup().foreach(throw _)
  }

  override def write(data: String): Unit =
    super.write(data)
    output match
      case stream: PrintStream if stream.checkError() =>
        throw IOException(
          "SttyTerminal output PrintStream reported a suppressed output failure; check the configured output destination"
        )
      case _                                          => ()

  override def columns: Int = currentColumns
  override def rows: Int    = currentRows

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

  private[jvm] def refreshSizeForTesting(): Boolean = refreshSize(notify = true)

  private def refreshSize(notify: Boolean): Boolean =
    sizeQuery().exists { (rows, columns) =>
      if rows <= 0 || columns <= 0 then false
      else
        val changed = (rows !== currentRows) || (columns !== currentColumns)
        currentRows = rows
        currentColumns = columns
        if changed && notify then resizeHandler()
        changed
    }

  private def startResizePolling(): Unit =
    if (resizeThread eq null) then
      resizePolling = true
      val thread = Thread(() => resizeLoop(), "siglyph-stty-terminal-resize")
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
          Thread.sleep(SttyTerminal.ResizePollMillis)
          refreshSize(notify = true)
        catch
          case _: InterruptedException => resizePolling = false
          case _: Throwable            => ()
    finally synchronized {
        if resizeThread eq Thread.currentThread() then resizeThread = null
      }

  private def hasCleanupObligations: Boolean =
    inputCleanupPending || kittyCleanupPending || mouseCleanupPending || pasteCleanupPending ||
      savedState.nonEmpty

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

    if kittyCleanupPending then attempt("kitty") { disableKittyKeyboardProtocol() }
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
    if inputCleanupPending then
      attempt("input") {
        super.stop()
        inputCleanupPending = false
      }
    savedState.foreach { state =>
      attempt("termios") {
        runStty(state)
        savedState = None
      }
    }
    failure

  private def runStty(args: String): String =
    sttyFailureForTesting(args).foreach(throw _)
    val command = Seq("sh", "-c", s"stty $args < /dev/tty")
    val output  = command.!!
    output.trim

object SttyTerminal:
  private val ResizePollMillis = 150L

  private[jvm] def querySize(): Option[(Int, Int)] =
    scala.util.Try {
      val parts = Seq("sh", "-c", "stty size < /dev/tty").!!.trim.split("\\s+").toVector
      if parts.length >= 2 then Some((parts(0).toInt, parts(1).toInt)) else None
    }.toOption.flatten
