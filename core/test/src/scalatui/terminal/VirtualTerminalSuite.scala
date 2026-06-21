package scalatui.terminal

class VirtualTerminalSuite extends munit.FunSuite:
  test("virtual terminal records writes and plain viewport"):
    val terminal = VirtualTerminal(initialColumns = 10, initialRows = 2)
    terminal.write("first\n")
    terminal.write("\u001b[31msecond\u001b[0m\nthird")

    assertEquals(terminal.output.contains("\u001b[31m"), true)
    assertEquals(terminal.viewportLines, Vector("second", "third"))

  test("virtual terminal delivers input and resize callbacks"):
    val terminal = VirtualTerminal(80, 24)
    var inputs   = Vector.empty[TerminalInput]
    var resizes  = 0

    terminal.start(input => inputs :+= input, () => resizes += 1)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    terminal.resize(100, 40)

    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Enter)))
    assertEquals(terminal.columns, 100)
    assertEquals(terminal.rows, 40)
    assertEquals(resizes, 1)

  test("virtual terminal supports title and progress assertions"):
    val terminal = VirtualTerminal(80, 24)

    assertEquals(Terminal.setTitle(terminal, "hello\u0007world"), true)
    assertEquals(Terminal.setProgress(terminal, active = true), true)
    assertEquals(Terminal.setProgress(terminal, active = false), true)

    assert(terminal.output.contains("\u001b]0;helloworld\u0007"), terminal.output)
    assert(terminal.output.contains(Terminal.ProgressActiveSequence), terminal.output)
    assert(terminal.output.contains(Terminal.ProgressClearSequence), terminal.output)
