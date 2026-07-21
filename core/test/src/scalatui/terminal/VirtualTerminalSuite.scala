package scalatui.terminal

import scalatui.syntax.Equality.*

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

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

  test("OSC and string-control output does not advance the emulated cursor"):
    val terminal = VirtualTerminal(80, 24)
    var inputs   = Vector.empty[TerminalInput]
    val reported = CountDownLatch(1)
    val onInput  = (input: TerminalInput) =>
      inputs :+= input
      reported.countDown()
    terminal.start(onInput, () => ())

    terminal.write("abc")
    terminal.setTitle("title")
    terminal.setProgress(active = true)
    terminal.write("\u001b_ignored-apc\u001b\\")
    terminal.write(TerminalCursorProtocol.CursorPositionQuery)

    assert(reported.await(5, TimeUnit.SECONDS), "cursor position report was not delivered")
    val report = inputs.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten
    assertEquals(
      String(report.toArray, java.nio.charset.StandardCharsets.UTF_8),
      "\u001b[1;4R"
    )
    terminal.stop()

  test("start and output-side operations do not synchronously deliver registered callbacks"):
    val terminal        = VirtualTerminal(80, 24)
    val caller          = Thread.currentThread()
    val inlineCallbacks = AtomicInteger(0)
    val cursorReported  = CountDownLatch(1)
    val inputThread     = AtomicReference[Thread](null)
    val onInput         = (_: TerminalInput) =>
      inputThread.set(Thread.currentThread())
      if Thread.currentThread() eq caller then inlineCallbacks.incrementAndGet()
      cursorReported.countDown()
    terminal.start(
      onInput,
      () => if Thread.currentThread() eq caller then inlineCallbacks.incrementAndGet()
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
    terminal.write(TerminalCursorProtocol.CursorPositionQuery)

    assert(cursorReported.await(5, TimeUnit.SECONDS), "cursor position report was not delivered")
    assert(inputThread.get() ne caller, "cursor position report was delivered on the write caller")
    assertEquals(inlineCallbacks.get(), 0)
    terminal.stop()

  test("modeled viewport advances by grapheme display width"):
    val terminal = VirtualTerminal(6, 2)

    terminal.write("e\u0301界🙂")

    assertEquals(terminal.screenLines, Vector("e\u0301界🙂", ""))
    assertEquals(terminal.cursorPosition, (0, 5))

  test("modeled viewport applies DEC autowrap and scrolling"):
    val terminal = VirtualTerminal(4, 2)

    terminal.write("abcd")
    assertEquals(terminal.cursorPosition, (0, 3))
    terminal.write("e")
    assertEquals(terminal.screenLines, Vector("abcd", "e"))
    assertEquals(terminal.cursorPosition, (1, 1))

    terminal.write("\u001b[?7l\u001b[1;4HXY")
    assertEquals(terminal.isAutowrapEnabled, false)
    assertEquals(terminal.screenLines, Vector("abcY", "e"))
    assertEquals(terminal.cursorPosition, (0, 3))

  test("modeled viewport supports cursor addressing and erase operations"):
    val terminal = VirtualTerminal(6, 3)

    terminal.write("abcdef\u001b[2;1Hghijkl\u001b[3;1Hmnopqr")
    terminal.write("\u001b[2;3H\u001b[K")
    assertEquals(terminal.screenLines, Vector("abcdef", "gh", "mnopqr"))

    terminal.write("\u001b[2;2H\u001b[J")
    assertEquals(terminal.screenLines, Vector("abcdef", "g", ""))
    assertEquals(terminal.cursorPosition, (1, 1))
