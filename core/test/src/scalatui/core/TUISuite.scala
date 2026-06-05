package scalatui.core

import scalatui.ansi.Ansi
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey, VirtualTerminal}

import java.util.concurrent.atomic.AtomicReference

class TUISuite extends munit.FunSuite:
  final class MutableLine(var value: String) extends Component:
    override def render(width: Int): Vector[String] = Vector(value)

  test("first render writes full frame with synchronized output"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.contains(TUI.SyncStart))
    assert(output.contains("hello" + TUI.LineReset))
    assert(output.contains(TUI.SyncEnd))

  test("partial render moves to first changed line and writes changed tail"):
    val terminal = VirtualTerminal(20, 5)
    val first    = MutableLine("first")
    val second   = MutableLine("second")
    val tui      = TUI(terminal)
    tui.addChild(first)
    tui.addChild(second)
    tui.start()
    terminal.clearWrites()

    second.value = "changed"
    tui.requestRender()
    tui.flushRender()

    val output = terminal.output
    assert(output.startsWith(TUI.SyncStart), output)
    assert(output.contains("\u001b[J"), output)
    assert(output.contains("changed" + TUI.LineReset), output)
    assert(!output.contains("first" + TUI.LineReset), output)

  test("width change performs full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assert(output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("over-wide lines are sanitized instead of failing"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcd"))

    tui.start()

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)
    assertEquals(tui.lastSanitizedLine.map(_.originalWidth), Some(4))

  test("height change performs full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(20, 3)

    val output = terminal.output
    assert(output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("zero terminal dimensions are clamped before component rendering"):
    val terminal      = VirtualTerminal(0, 0)
    var renderedWidth = 0
    val component     = new Component:
      override def render(width: Int): Vector[String] =
        renderedWidth = width
        Vector("x")
    val tui           = TUI(terminal)
    tui.addChild(component)

    tui.start()

    assertEquals(renderedWidth, 1)
    assert(terminal.output.contains("x" + TUI.LineReset), terminal.output)

  test("styled and unicode over-wide lines are sanitized by visible width"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("\u001b[31ma界b"))

    tui.start()

    val plainLines = visibleOutputLines(terminal.output)
    assert(plainLines.exists(_.contains("a界")), terminal.output)
    assert(plainLines.forall(_.length <= 3), plainLines.toString)
    assertEquals(tui.sanitizedLineCount, 1)

  test("input-triggered over-wide render is sanitized without uncaught exception"):
    val terminal  = VirtualTerminal(3, 5)
    val component = new Component:
      var value                                            = "ok"
      override def handleInput(input: TerminalInput): Unit = value = "abcdef"
      override def render(width: Int): Vector[String]      = Vector(value)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("unrecoverable render failure restores terminal state through run"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      var fail                                             = false
      override def handleInput(input: TerminalInput): Unit = fail = true
      override def render(width: Int): Vector[String]      =
        if fail then throw RuntimeException("boom")
        Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val failure   = AtomicReference[Throwable](null)
    val thread    = Thread(() =>
      try tui.run()
      catch case e: Throwable => failure.set(e)
    )
    thread.start()
    Thread.sleep(50)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assertEquals(Option(failure.get()).map(_.getMessage), Some("boom"))
    assert(terminal.output.contains("\u001b[?25h"), terminal.output)

  test("requestRender coalesces until flush"):
    val terminal = VirtualTerminal(20, 5)
    val line     = MutableLine("one")
    val tui      = TUI(terminal)
    tui.addChild(line)
    tui.start()
    terminal.clearWrites()

    line.value = "two"
    tui.requestRender()
    tui.requestRender()
    assertEquals(terminal.output, "")

    tui.flushRender()
    assert(terminal.output.contains("two" + TUI.LineReset), terminal.output)

  test("focused component receives input and rerenders"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      var value                                            = "empty"
      override def handleInput(input: TerminalInput): Unit = input match
        case TerminalInput.Key(TerminalKey.Character(text), _) => value = text
        case _                                                 => ()
      override def render(width: Int): Vector[String]      = Vector(value)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assert(terminal.output.contains("x" + TUI.LineReset), terminal.output)

  test("input result can report handled input without render"):
    val terminal  = VirtualTerminal(20, 5)
    var handled   = 0
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        handled += 1
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(handled, 1)
    assertEquals(terminal.output, "")

  test("input result can report ignored input"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult = InputResult.Ignored
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(terminal.output, "")

  test("input result can request exit"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult = InputResult.Exit
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val thread    = Thread(() => tui.run())
    thread.start()
    Thread.sleep(50)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assert(terminal.output.contains("\r\n\u001b[?25h"), terminal.output)

  private def visibleOutputLines(output: String): Vector[String] =
    Ansi.strip(output).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector

  test("run exits on ctrl+c and stop positions cursor below content"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    val thread   = Thread(() => tui.run())
    thread.start()
    Thread.sleep(50)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assert(terminal.output.contains("\r\n\u001b[?25h"), terminal.output)
