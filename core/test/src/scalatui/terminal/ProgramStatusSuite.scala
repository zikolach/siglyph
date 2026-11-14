package scalatui.terminal

import scalatui.core.TUI
import scalatui.syntax.Equality.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.collection.mutable.ArrayBuffer

class ProgramStatusSuite extends munit.FunSuite:
  private def message(wire: String): String =
    val encoded = wire.split(":msg=")(1).dropRight(2)
    String(Base64.getDecoder.decode(encoded), UTF_8)

  test("formatter validates app, blocked reason, controls and UTF8 boundaries") {
    val status =
      ProgramStatus(ProgramStatusState.Blocked, Some("pi"), Some(ProgramStatusKind.Auth), "Allow?")
    assertEquals(
      ProgramStatusProtocol.format(status),
      "\u001b]7501;state=blocked:app=pi:kind=auth:msg=QWxsb3c/\u001b\\"
    )
    assertEquals(
      ProgramStatusProtocol.format(status.copy(
        state = ProgramStatusState.Working,
        app = Some("bad app"),
        message = "\n"
      )),
      "\u001b]7501;state=working\u001b\\"
    )
    assertEquals(
      message(ProgramStatusProtocol.format(status.copy(message =
        "first\nsecond\u001b[31m\u009bthird\t"
      ))),
      "first second [31m third"
    )
    for text <- Vector("é" * 2000, "🙂" * 1000, "a" * 2047 + "🙂") do
      val decoded = message(ProgramStatusProtocol.format(status.copy(message = text)))
      assert(decoded.getBytes(UTF_8).length <= 2048)
      assert(!decoded.contains("�"))
    assert(!ProgramStatusProtocol.format(status.copy(app = Some("a" * 33))).contains("app="))
    assert(ProgramStatusProtocol.format(status.copy(app = Some("a" * 32))).contains("app="))
  }

  test("handshake positive negative expired and late replies") {
    val writes  = ArrayBuffer.empty[String]
    val session = ProgramStatusSession(writes += _)
    assert(!session.report(ProgramStatus(ProgramStatusState.Idle)))
    assertEquals(writes.toVector, Vector.empty)
    session.request(100L, 10L)
    assert(!session.receive("\u001b]7501;no\u0007", 101L))
    assert(!session.report(ProgramStatus(ProgramStatusState.Working)))
    assert(session.receive("\u001b]7501;?version=2\u0007", 110L))
    assert(session.report(ProgramStatus(ProgramStatusState.Working)))
    session.reset()
    session.request(200L, 10L)
    assert(!session.receive(ProgramStatusProtocol.Query, 211L))
    assert(!session.report(ProgramStatus(ProgramStatusState.Idle)))
    assert(ProgramStatusProtocol.isReply(ProgramStatusProtocol.Query))
    assert(!ProgramStatusProtocol.isReply("\u001b]7501;?\u001b"))
    assert(!ProgramStatusProtocol.isReply("\u001b]7501;?" + "x" * 5000 + "\u0007"))
  }

  test("failed report and failed clear retain cleanup until successful retry") {
    var fail    = false
    val writes  = ArrayBuffer.empty[String]
    val session =
      ProgramStatusSession(data => if fail then throw RuntimeException("sink") else writes += data)
    session.request(0, 10)
    assert(session.receive(ProgramStatusProtocol.Query, 1))
    fail = true
    intercept[RuntimeException](session.report(ProgramStatus(ProgramStatusState.Done)))
    assert(session.cleanupPending)
    intercept[RuntimeException](session.reset())
    assert(session.cleanupPending)
    assert(!session.supported)
    fail = false
    session.reset()
    assert(!session.cleanupPending)
    val count   = writes.size
    session.reset()
    assertEquals(writes.size, count)
    session.request(20, 10)
    assert(session.receive(ProgramStatusProtocol.Query, 21))
  }

  private class StatusTerminal extends Terminal, TerminalProgramStatusSupport:
    val writes                                                            = ArrayBuffer.empty[String]
    var input: TerminalInput => Unit                                      = _ => ()
    var failClear                                                         = false
    def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = input = onInput
    def stop(): Unit                                                      = ()
    def write(data: String): Unit                                         =
      if failClear && data.contains("7501;state=clear") then throw RuntimeException("clear failed")
      writes += data
    def columns: Int                                                      = 80
    def rows: Int                                                         = 24
    def moveBy(lines: Int): Unit                                          = ()
    def hideCursor(): Unit                                                = ()
    def showCursor(): Unit                                                = ()
    def clearLine(): Unit                                                 = ()
    def clearFromCursor(): Unit                                           = ()
    def clearScreen(): Unit                                               = ()
    def reply(): Unit                                                     =
      TerminalInputBuffer().process(
        TerminalInputChunk(ProgramStatusProtocol.Query.getBytes(UTF_8))
      ).foreach(input)

  test("TUI negotiates consumes replies and clears with lifecycle retry") {
    val terminal = StatusTerminal()
    val tui      = TUI(terminal)
    tui.start()
    assert(terminal.writes.contains(ProgramStatusProtocol.Query))
    assert(!tui.setTerminalProgramStatus(ProgramStatus(ProgramStatusState.Working)))
    // Explicit long negotiation avoids wall-clock test timing assumptions.
    terminal.requestProgramStatus(60000)
    terminal.reply()
    assert(tui.setTerminalProgramStatus(ProgramStatus(ProgramStatusState.Working)))
    assert(terminal.writes.exists(_.contains("7501;state=working")))
    terminal.failClear = true
    intercept[RuntimeException](tui.stop())
    assert(terminal.programStatusCleanupPending)
    terminal.failClear = false
    tui.stop()
    assert(!terminal.programStatusCleanupPending)
    assert(!terminal.programStatusSupported)
  }

  test("unsupported TUI emits no reports and consumes valid late replies") {
    val terminal = VirtualTerminal()
    val tui      = TUI(terminal)
    val observed = ArrayBuffer.empty[TerminalInput]
    tui.addInputListener(input =>
      observed += input
      scalatui.core.InputResult.Ignored
    )
    tui.start()
    assert(!tui.setTerminalProgramStatus(ProgramStatus(ProgramStatusState.Done)))
    TerminalInputBuffer().process(TerminalInputChunk(ProgramStatusProtocol.Query.getBytes(UTF_8)))
      .foreach(terminal.sendInput)
    assertEquals(observed.toVector, Vector.empty)
    tui.stop()
  }

  test("profiles honor multiplexer ceilings Orca opt-in and leaked outer hints") {
    for profile <- Vector("herdr", "rex", "orca") do
      val env      = Map("TERM_PROGRAM" -> profile, "KITTY_WINDOW_ID" -> "outer")
      val detected = TerminalCapabilities.detect(env)
      assertEquals(detected.images.nonEmpty, profile !== "orca")
      assertEquals(TerminalCapabilities.detect(env + ("TMUX" -> "1")).images, None)
      assertEquals(
        TerminalCapabilities.detect(env + ("TERM" -> "screen-256color")).hyperlinks,
        false
      )
      assertEquals(
        TerminalCapabilities.resolve(
          detected,
          TerminalCapabilityOverrides(images = TerminalCapabilityOverride.Disabled)
        ).images,
        None
      )
    assertEquals(
      TerminalCapabilities.detect(Map(
        "TERM_PROGRAM"        -> "orca",
        "ORCA_IMAGE_PROTOCOL" -> "kitty"
      )).images,
      Some(ImageProtocol.Kitty)
    )
    for env <- Vector(Map("TERM" -> "alacritty"), Map("ALACRITTY_WINDOW_ID" -> "1")) do
      assertEquals(TerminalCapabilities.detect(env), TerminalCapabilities(true, true, None))
  }
