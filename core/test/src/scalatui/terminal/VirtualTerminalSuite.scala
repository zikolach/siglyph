package scalatui.terminal

class VirtualTerminalSuite extends munit.FunSuite:
  test("virtual terminal records writes and plain viewport"):
    val terminal = VirtualTerminal(initialColumns = 10, initialRows = 2)
    terminal.write("first\n")
    terminal.write("\u001b[31msecond\u001b[0m\nthird")

    assertEquals(terminal.output.contains("\u001b[31m"), true)
    assertEquals(terminal.viewportLines, Vector("second", "third"))

  test("virtual terminal viewport strips only CSI controls with the Native-compatible regex"):
    val terminal = VirtualTerminal(initialColumns = 80, initialRows = 1)
    terminal.write("plain\u001b[31mred\u001b[0m:\u001b[?25lhidden\u001b[2;3Hdone")

    assertEquals(terminal.viewportLines, Vector("plainred:hiddendone"))

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

  test("direct virtual terminal title support sanitizes control characters"):
    val terminal = VirtualTerminal(80, 24)

    terminal.setTitle("safe\u001btitle\u0007")

    assertEquals(terminal.output, "\u001b]0;safetitle\u0007")

  test("start and output-side operations do not synchronously deliver registered callbacks"):
    val terminal        = VirtualTerminal(80, 24)
    val caller          = Thread.currentThread()
    var inlineCallbacks = 0
    terminal.start(
      _ => if Thread.currentThread() eq caller then inlineCallbacks += 1,
      () => if Thread.currentThread() eq caller then inlineCallbacks += 1
    )

    terminal.write("output")
    terminal.moveBy(1)
    terminal.hideCursor()
    terminal.showCursor()
    terminal.clearLine()
    terminal.clearFromCursor()
    terminal.clearScreen()
    Terminal.setTitle(terminal, "title")
    Terminal.setProgress(terminal, active = true)

    assertEquals(inlineCallbacks, 0)
    terminal.stop()
