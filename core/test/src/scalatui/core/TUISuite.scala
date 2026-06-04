package scalatui.core

import scalatui.terminal.VirtualTerminal

class TUISuite extends munit.FunSuite:
  final class MutableLine(var value: String) extends Component:
    override def render(width: Int): Vector[String] = Vector(value)

  test("first render writes full frame with synchronized output"):
    val terminal = VirtualTerminal(20, 5)
    val tui = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.contains(TUI.SyncStart))
    assert(output.contains("hello" + TUI.LineReset))
    assert(output.contains(TUI.SyncEnd))

  test("partial render moves to first changed line and writes changed tail"):
    val terminal = VirtualTerminal(20, 5)
    val first = MutableLine("first")
    val second = MutableLine("second")
    val tui = TUI(terminal)
    tui.addChild(first)
    tui.addChild(second)
    tui.start()
    terminal.clearWrites()

    second.value = "changed"
    tui.requestRender()

    val output = terminal.output
    assert(output.startsWith(TUI.SyncStart), output)
    assert(output.contains("\u001b[J"), output)
    assert(output.contains("changed" + TUI.LineReset), output)
    assert(!output.contains("first" + TUI.LineReset), output)

  test("width change performs full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assert(output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("line overflow fails"):
    val terminal = VirtualTerminal(3, 5)
    val tui = TUI(terminal)
    tui.addChild(MutableLine("abcd"))

    intercept[IllegalArgumentException](tui.start())
