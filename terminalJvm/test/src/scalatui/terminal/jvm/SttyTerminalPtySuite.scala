package scalatui.terminal.jvm

import scalatui.syntax.Equality.*

import java.io.{ByteArrayOutputStream, InputStream}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.sys.process.*

/** Real `/dev/tty` lifecycle checks, enabled only by `scripts/test-terminal-pty.sh`. */
class SttyTerminalPtySuite extends munit.FunSuite:
  private val PtyTestEnabled = "SIGLYPH_PTY_TEST"

  test("PTY lifecycle enters raw mode, orders writes, reports resize, and restores stty"):
    withPtyTest {
      val originalState                   = runStty("-g")
      val originalStableState             = stableSttyState()
      val (originalRows, originalColumns) = querySize()
      val targetRows                      = if originalRows > 0 then originalRows + 1 else 7
      val targetColumns                   = if originalColumns > 0 then originalColumns + 1 else 29
      val output                          = ByteArrayOutputStream()
      val resized                         = CountDownLatch(1)
      val terminal                        = SttyTerminal(
        input = InputStream.nullInputStream(),
        output = output
      )
      var stopped                         = false

      try
        terminal.start(_ => (), () => resized.countDown())
        val rawState = runStty("-a")
        assert(rawState.contains("-echo"), rawState)
        assert(rawState.contains("-icanon"), rawState)

        terminal.write("pty-marker")
        runStty(s"rows $targetRows cols $targetColumns")
        assert(resized.await(5, TimeUnit.SECONDS), "resize callback was not delivered within 5s")
        assertEquals(terminal.rows, targetRows)
        assertEquals(terminal.columns, targetColumns)

        terminal.stop()
        stopped = true

        val written  = output.toString(java.nio.charset.StandardCharsets.UTF_8)
        val enabled  = written.indexOf("\u001b[?2004h")
        val marker   = written.indexOf("pty-marker")
        val disabled = written.lastIndexOf("\u001b[?2004l")
        assert(enabled >= 0, written)
        assert(marker > enabled, written)
        assert(disabled > marker, written)
        assertEquals(stableSttyState(), originalStableState)
      finally
        if !stopped then
          terminal.cleanupFailureForTesting = _ => None
          scala.util.Try(terminal.stop())
        restoreStty(originalState, originalRows, originalColumns)
    }

  test("PTY cleanup retries an injected restoration failure and restores stty"):
    withPtyTest {
      val originalState                   = runStty("-g")
      val originalStableState             = stableSttyState()
      val (originalRows, originalColumns) = querySize()
      val terminal                        = SttyTerminal(
        input = InputStream.nullInputStream(),
        output = ByteArrayOutputStream()
      )
      var restored                        = false

      try
        terminal.start(_ => (), () => ())
        var injected = false
        terminal.cleanupFailureForTesting = name =>
          Option.when((name === "termios") && !injected) {
            injected = true
            RuntimeException("injected PTY restoration failure")
          }

        val failure = intercept[RuntimeException](terminal.stop())
        assertEquals(failure.getMessage, "injected PTY restoration failure")

        terminal.cleanupFailureForTesting = _ => None
        terminal.stop()
        restored = true
        assertEquals(stableSttyState(), originalStableState)
      finally
        terminal.cleanupFailureForTesting = _ => None
        if !restored then scala.util.Try(terminal.stop())
        restoreStty(originalState, originalRows, originalColumns)
    }

  private def withPtyTest(body: => Unit): Unit =
    if sys.env.get(PtyTestEnabled).contains("1") then body

  private def querySize(): (Int, Int) =
    val parts = runStty("size").split("\\s+").toVector
    if parts.lengthCompare(2) >= 0 then (parts(0).toInt, parts(1).toInt)
    else throw AssertionError(s"unexpected stty size output: ${parts.mkString(" ")}")

  private def restoreStty(state: String, rows: Int, columns: Int): Unit =
    runStty(s"rows $rows cols $columns")
    runStty(state)

  private def stableSttyState(): String =
    runStty("-a")
      .replaceAll("\\b\\d+ rows; \\d+ columns;", "")
      .replaceAll("\\brows \\d+; columns \\d+;", "")
      .replaceAll("(?<![A-Za-z])-?pendin(?![A-Za-z])", "")
      .replaceAll("\\s+", " ")
      .trim

  private def runStty(args: String): String =
    Seq("sh", "-c", s"stty $args < /dev/tty").!!.trim
