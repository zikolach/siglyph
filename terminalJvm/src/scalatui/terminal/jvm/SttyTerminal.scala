package scalatui.terminal.jvm

import scalatui.terminal.StreamTerminal

import java.io.{InputStream, OutputStream}
import scala.sys.process.*

/** Unix JVM terminal backend using `stty` for interactive raw mode. */
final class SttyTerminal(
    input: InputStream = System.in,
    output: OutputStream = System.out,
    columnsOverride: Option[Int] = None,
    rowsOverride: Option[Int] = None
) extends StreamTerminal(
      input = input,
      output = output,
      initialColumns = columnsOverride.orElse(StreamTerminal.envInt("COLUMNS")).getOrElse(80),
      initialRows = rowsOverride.orElse(StreamTerminal.envInt("LINES")).getOrElse(24)
    ):
  private var savedState: Option[String] = None

  override def start(onInput: scalatui.terminal.TerminalInput => Unit, onResize: () => Unit): Unit =
    if System.console() == null then
      throw IllegalStateException("JVM stty backend requires an interactive TTY; use StreamTerminal for non-interactive streams")
    savedState = Some(runStty("-g"))
    try runStty("raw -echo min 1 time 0")
    catch
      case e: Throwable =>
        savedState.foreach(state => scala.util.Try(runStty(state)))
        savedState = None
        throw e
    super.start(onInput, onResize)

  override def stop(): Unit =
    super.stop()
    savedState.foreach(state => scala.util.Try(runStty(state)))
    savedState = None

  private def runStty(args: String): String =
    val command = Seq("sh", "-c", s"stty $args < /dev/tty")
    val output = command.!!
    output.trim
