package scalatui.terminal.jvm

import scalatui.syntax.Equality.*
import scalatui.terminal.StreamTerminal

import java.io.{InputStream, OutputStream}
import scala.sys.process.*

/** Unix JVM terminal backend using `stty` for interactive raw mode. */
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
    ):
  @volatile private var savedState: Option[String] = None
  @volatile private var resizePolling              = false
  @volatile private var currentColumns             = columnsOverride
    .orElse(StreamTerminal.envInt("COLUMNS"))
    .getOrElse(80)
  @volatile private var currentRows                =
    rowsOverride.orElse(StreamTerminal.envInt("LINES")).getOrElse(24)
  private var resizeHandler: () => Unit            = () => ()
  private var resizeThread: Thread | Null          = null

  override def start(onInput: scalatui.terminal.TerminalInput => Unit, onResize: () => Unit): Unit =
    if savedState.isEmpty then
      if System.console() == null then
        throw IllegalStateException(
          "JVM stty backend requires an interactive TTY; use StreamTerminal for non-interactive streams"
        )
      resizeHandler = onResize
      savedState = Some(runStty("-g"))
      try
        refreshSize(notify = false)
        runStty("raw -echo min 1 time 0")
        write("\u001b[?2004h")
        super.start(onInput, onResize)
        startResizePolling()
      catch
        case e: Throwable =>
          stopResizePolling()
          write("\u001b[?2004l")
          savedState.foreach(state => scala.util.Try(runStty(state)))
          savedState = None
          throw e

  override def stop(): Unit =
    stopResizePolling()
    if savedState.isEmpty then super.stop()
    else
      write("\u001b[?2004l")
      super.stop()
      savedState.foreach(state => scala.util.Try(runStty(state)))
      savedState = None

  override def columns: Int = currentColumns
  override def rows: Int    = currentRows

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
      val thread = Thread(() => resizeLoop(), "scala-tui-stty-terminal-resize")
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
        Thread.sleep(SttyTerminal.ResizePollMillis)
        refreshSize(notify = true)
      catch
        case _: InterruptedException => resizePolling = false
        case _: Throwable            => ()

  private def runStty(args: String): String =
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
