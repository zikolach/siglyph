package scalatui.core

import scalatui.TestInputStreams

import scalatui.ansi.Ansi
import scalatui.components.{
  Editor,
  EditorOptions,
  Loader,
  LoaderIndicatorOptions,
  LoaderOptions,
  Text
}
import scalatui.terminal.{
  ImageCellDimensions,
  ImageDimensions,
  ImageProtocol,
  ImageRenderOptions,
  KeyEventType,
  KeyModifiers,
  RgbColor,
  TerminalCapabilities,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalImageProtocol,
  TerminalInputDrainSupport,
  TerminalInput,
  TerminalKey,
  VirtualTerminal
}

import java.util.concurrent.atomic.AtomicReference

class TUISuite extends munit.FunSuite:
  final class MutableLine(var value: String) extends Component:
    override def render(width: Int): Vector[String] = Vector(value)

  final class MutableFrame(var values: Vector[String]) extends Component:
    override def render(width: Int): Vector[String] = values

  final class AsyncStartupTerminal(inputs: Vector[TerminalInput], publishResize: Boolean = false)
      extends Terminal:
    private val writesBuffer = scala.collection.mutable.ArrayBuffer.empty[String]
    private var inputHandler = Option.empty[TerminalInput => Unit]

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      inputHandler = Some(onInput)
      val publisher = Thread(() => {
        if publishResize then onResize()
        inputs.foreach(onInput)
      })
      publisher.start()
      publisher.join()

    def emit(input: TerminalInput): Unit = inputHandler.foreach(handler => handler(input))

    override def stop(): Unit = ()

    override def write(data: String): Unit = writesBuffer.synchronized(writesBuffer += data)

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit =
      if lines > 0 then write(s"\u001b[${lines}B")
      else if lines < 0 then write(s"\u001b[${-lines}A")

    override def hideCursor(): Unit      = write("\u001b[?25l")
    override def showCursor(): Unit      = write("\u001b[?25h")
    override def clearLine(): Unit       = write("\u001b[K")
    override def clearFromCursor(): Unit = write("\u001b[J")
    override def clearScreen(): Unit     = write("\u001b[2J\u001b[H")

    def output: String = writesBuffer.synchronized(writesBuffer.mkString)

  final class StartFailingTerminal extends Terminal:
    private val writesBuffer = scala.collection.mutable.ArrayBuffer.empty[String]
    var stopCalled           = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      throw RuntimeException("start failed")

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit = writesBuffer += data

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = write("\u001b[?25l")
    override def showCursor(): Unit       = write("\u001b[?25h")
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = write("\u001b[2J\u001b[H")

    def output: String = writesBuffer.mkString

  final class QueryFailingTerminal(failingWrite: String) extends Terminal:
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = ()

    override def write(data: String): Unit =
      if data.equals(failingWrite) then throw RuntimeException("write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = ()
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class StopCleanupFailingTerminal(failingWrite: String) extends Terminal:
    @volatile var showCursorCalled = false
    @volatile var stopCalled       = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit =
      if data.equals(failingWrite) then throw RuntimeException("write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = showCursorCalled = true
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class PartialAutoWrapRenderFailingTerminal extends Terminal:
    val writes                     = scala.collection.mutable.ArrayBuffer.empty[String]
    @volatile var showCursorCalled = false
    @volatile var stopCalled       = false
    private var failedRenderWrite  = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit =
      if data.contains(TUI.AutoWrapOff) && !failedRenderWrite then
        failedRenderWrite = true
        writes += TUI.AutoWrapOff
        throw RuntimeException("render write failed")
      writes += data

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = showCursorCalled = true
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class RenderAndStopFailingTerminal extends Terminal:
    private var failedRenderWrite = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = throw RuntimeException("stop failed")

    override def write(data: String): Unit =
      if data.contains(TUI.AutoWrapOff) && !failedRenderWrite then
        failedRenderWrite = true
        throw RuntimeException("render write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = ()
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class DrainingTerminal extends Terminal, TerminalInputDrainSupport:
    var drainCalls     = 0
    var stopCalls      = 0
    var lastMaxMillis  = Option.empty[Long]
    var lastIdleMillis = Option.empty[Long]

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalls += 1

    override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
      assert(maxMillis >= 0L)
      assert(idleMillis >= 0L)
      lastMaxMillis = Some(maxMillis)
      lastIdleMillis = Some(idleMillis)
      drainCalls += 1

    override def write(data: String): Unit = ()
    override def columns: Int              = 20
    override def rows: Int                 = 5
    override def moveBy(lines: Int): Unit  = ()
    override def hideCursor(): Unit        = ()
    override def showCursor(): Unit        = ()
    override def clearLine(): Unit         = ()
    override def clearFromCursor(): Unit   = ()
    override def clearScreen(): Unit       = ()

  test("first render writes full frame with synchronized output"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.contains(TUI.SyncStart))
    assert(output.contains("hello" + TUI.LineReset))
    assert(output.contains(TUI.SyncEnd))
    assert(!output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assertNoAlternateScreen(output)

  test("default mode stop does not emit alternate screen"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    tui.stop()

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(output.contains("\r\n\u001b[?25h"), output)

  test("alternate-screen mode enters before first rendered frame"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.startsWith(TUI.AlternateScreenEnter), output)
    assert(output.indexOf(TUI.AlternateScreenEnter) < output.indexOf(TUI.SyncStart), output)
    assert(
      output.contains(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("alternate-screen mode handles independent resize publication during terminal start"):
    val terminal = AsyncStartupTerminal(Vector.empty, publishResize = true)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.startsWith(TUI.AlternateScreenEnter), output)
    assert(output.indexOf(TUI.AlternateScreenEnter) < output.indexOf(TUI.SyncStart), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("independent startup input publication preserves input order"):
    val terminal  = AsyncStartupTerminal(Vector(
      TerminalInput.Key(TerminalKey.Character("a")),
      TerminalInput.Key(TerminalKey.Character("b")),
      TerminalInput.Key(TerminalKey.Character("c"))
    ))
    val received  = scala.collection.mutable.ArrayBuffer.empty[String]
    val component = new Component:
      override def render(width: Int): Vector[String] = Vector(received.mkString)

      override def handleInputResult(input: TerminalInput): InputResult = input match
        case TerminalInput.Key(TerminalKey.Character(value), _) =>
          received += value
          InputResult.Render
        case _                                                  => InputResult.Ignored
    val tui       = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(component)
    tui.setFocus(component)

    tui.start()

    assertEquals(received.toVector, Vector("a", "b", "c"))

  test("input emitted while startup input drains preserves order"):
    val terminal  = AsyncStartupTerminal(Vector(
      TerminalInput.Key(TerminalKey.Character("a"))
    ))
    val received  = scala.collection.mutable.ArrayBuffer.empty[String]
    val component = new Component:
      override def render(width: Int): Vector[String] = Vector(received.mkString)

      override def handleInputResult(input: TerminalInput): InputResult = input match
        case TerminalInput.Key(TerminalKey.Character("a"), _)   =>
          terminal.emit(TerminalInput.Key(TerminalKey.Character("b")))
          received += "a"
          InputResult.Render
        case TerminalInput.Key(TerminalKey.Character(value), _) =>
          received += value
          InputResult.Render
        case _                                                  => InputResult.Ignored
    val tui       = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(component)
    tui.setFocus(component)

    tui.start()

    assertEquals(received.toVector, Vector("a", "b"))

  test("alternate-screen mode does not enter when terminal start fails"):
    val terminal = StartFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    interceptMessage[RuntimeException]("start failed"):
      tui.start()

    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)
    assert(terminal.stopCalled)

  test("alternate-screen mode exits on stop without cursor parking"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(Vector("first", "second")))
    tui.start()
    terminal.clearWrites()

    tui.stop()

    val output = terminal.output
    assert(output.contains("\u001b[?25h"), output)
    assert(output.contains(TUI.AlternateScreenExit), output)
    assertEquals(countOccurrences(output, TUI.AlternateScreenExit), 1)
    assert(!output.contains("\r\n"), output)

  test("alternate-screen mode stop is idempotent"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    tui.stop()
    tui.stop()

    assertEquals(countOccurrences(terminal.output, TUI.AlternateScreenExit), 1)

  test("alternate-screen render write failure restores terminal state"):
    val terminal = PartialAutoWrapRenderFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    val output = terminal.writes.mkString
    assertEquals(failure.getMessage, "render write failed")
    assert(output.contains(TUI.AlternateScreenEnter), output)
    assert(output.contains(TUI.AutoWrapOff), output)
    assert(output.contains(TUI.AutoWrapOn), output)
    assert(output.contains(TUI.AlternateScreenExit), output)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("startup failure preserves original exception when cleanup fails"):
    val terminal = RenderAndStopFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "render write failed")
    assertEquals(failure.getSuppressed.toVector.map(_.getMessage), Vector("stop failed"))

  test("render write failure restores autowrap during cleanup"):
    val terminal = PartialAutoWrapRenderFailingTerminal()
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "render write failed")
    assert(terminal.writes.contains(TUI.AutoWrapOff), terminal.writes.toVector)
    assert(terminal.writes.contains(TUI.AutoWrapOn), terminal.writes.toVector)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("hardware cursor positioning is disabled by default and strips markers"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine(s"ab${CursorMarker.Sequence}\u001b[7mc\u001b[27m"))

    tui.start()

    assert(!terminal.output.contains(CursorMarker.Sequence), terminal.output)
    assert(terminal.output.contains("ab\u001b[7mc\u001b[27m" + TUI.LineReset), terminal.output)

  test("hardware cursor positioning moves cursor to marker when enabled"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableLine(s"ab${CursorMarker.Sequence}\u001b[7mc\u001b[27m"))

    tui.start()

    assert(!terminal.output.contains(CursorMarker.Sequence), terminal.output)
    assert(terminal.output.contains(s"\r\u001b[2C${TUI.SyncEnd}"), terminal.output)

  test("hardware cursor positioning preserves no-marker render behavior"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableLine("hello"))

    tui.start()

    assert(terminal.output.contains("hello" + TUI.LineReset + TUI.SyncEnd), terminal.output)
    assert(!terminal.output.contains("\u001b[2C"), terminal.output)

  test("overlay composition determines surviving cursor marker"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableLine(s"a${CursorMarker.Sequence}\u001b[7mb\u001b[27m"))
    tui.showOverlay(
      MutableLine("zz"),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(2)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(0)),
        focusCapturing = false
      )
    )

    tui.start()

    assert(!terminal.output.contains(CursorMarker.Sequence), terminal.output)
    assert(!terminal.output.contains("\u001b[1C"), terminal.output)
    assert(Ansi.strip(terminal.output).contains("zz"), terminal.output)

  test("differential renderer compares marker-stripped frames"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine(s"a${CursorMarker.Sequence}\u001b[7mb\u001b[27m"))
    tui.start()
    terminal.clearWrites()

    tui.requestRender()
    tui.flushRender()

    assertEquals(terminal.output, "")

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

  test("width change uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("width change redraws with autowrap disabled to avoid resize reflow"):
    val terminal = VirtualTerminal(5, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcde"))
    tui.start()

    val initialOutput = terminal.output
    assert(initialOutput.contains(TUI.AutoWrapOff + "abcde" + TUI.LineReset), initialOutput)
    assert(initialOutput.endsWith(TUI.SyncEnd + TUI.AutoWrapOn), initialOutput)
    terminal.clearWrites()

    terminal.resize(3, 5)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("abc" + Ansi.Reset + TUI.LineReset), output)
    assert(output.endsWith(TUI.SyncEnd + TUI.AutoWrapOn), output)

  test("font-size-like resize uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(8, 4)
    val tui      = TUI(terminal)
    tui.addChild(MutableFrame(Vector("abcdefgh", "ijklmnop", "qrstuvwx")))
    tui.start()

    val initialOutput = terminal.output
    assert(initialOutput.contains("abcdefgh" + TUI.LineReset), initialOutput)
    terminal.clearWrites()

    terminal.resize(5, 2)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("abcde" + Ansi.Reset + TUI.LineReset), output)
    assert(output.contains("ijklm" + Ansi.Reset + TUI.LineReset), output)
    assert(output.contains("qrstu" + Ansi.Reset + TUI.LineReset), output)

  test("alternate-screen resize redraw clears viewport without scrollback clear"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assert(!output.contains(TUI.AlternateScreenEnter), output)
    assert(!output.contains("\u001b[3J"), output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("over-wide lines are sanitized instead of failing"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcd"))

    tui.start()

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)
    assertEquals(tui.lastSanitizedLine.map(_.originalWidth), Some(4))

  test("height change uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(20, 3)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
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

  test("one-column render remains width safe"):
    val terminal = VirtualTerminal(1, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("界x"))

    tui.start()

    assert(visibleOutputLines(terminal.output).forall(Ansi.visibleWidth(_) <= 1), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("shrinking terminal to one column repaints width-safe output with pi-tui full clear"):
    val terminal = VirtualTerminal(4, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("界ab"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(1, 5)

    assertNoAlternateScreen(terminal.output)
    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      terminal.output
    )
    assert(visibleOutputLines(terminal.output).forall(Ansi.visibleWidth(_) <= 1), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("image protocol escapes remain in synchronized sanitized output"):
    val terminal = VirtualTerminal(3, 5)
    val sequence = TerminalImageProtocol.renderBase64Image(
      "AAAA",
      ImageDimensions(10, 10),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      terminalWidth = 3,
      ImageRenderOptions(imageId = Some(5))
    ).get.sequence
    val tui      = TUI(terminal)
    tui.addChild(MutableLine(sequence + "abcdef"))

    tui.start()

    assert(terminal.output.contains("\u001b_G"), terminal.output)
    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
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

  test("contextual loader state changes request coalesced renders and detach safely"):
    val terminal = VirtualTerminal(20, 5)
    val loader   = Loader(LoaderOptions(
      indicator = LoaderIndicatorOptions(Vector("a", "b")),
      leadingBlankLine = false
    ))
    val tui      = TUI(terminal)
    tui.addChild(loader)
    tui.start()
    terminal.clearWrites()

    loader.setMessage("Go")
    assertEquals(terminal.output, "")
    tui.flushRender()
    assert(terminal.output.contains("a Go"), terminal.output)

    terminal.clearWrites()
    loader.start()
    loader.tick()
    assertEquals(terminal.output, "")
    tui.flushRender()
    assert(terminal.output.contains("b Go"), terminal.output)

    terminal.clearWrites()
    tui.removeChild(loader)
    assert(!tui.children.exists(_ eq loader))
    loader.setMessage("detached")
    tui.flushRender()
    assertEquals(terminal.output, "")

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

  test("global input listener observes typed input before focused component"):
    val terminal     = VirtualTerminal(20, 5)
    var listenerLog  = Vector.empty[TerminalInput]
    var componentLog = Vector.empty[TerminalInput]
    val component    = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        componentLog :+= input
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui          = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.addInputListener { input =>
      listenerLog :+= input
      InputResult.Ignored
    }
    tui.start()

    val input = TerminalInput.Key(TerminalKey.Character("x"))
    terminal.sendInput(input)

    assertEquals(listenerLog, Vector(input))
    assertEquals(componentLog, Vector(input))

  test("global input listener handled result stops focused routing and can render"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = 0
    val line      = MutableLine("before")
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered += 1
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("component")
    val tui       = TUI(terminal)
    tui.addChild(line)
    tui.addChild(component)
    tui.setFocus(component)
    tui.addInputListener { _ =>
      line.value = "after"
      InputResult.Render
    }
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, 0)
    assert(terminal.output.contains("after" + TUI.LineReset), terminal.output)

  test("global input listener can be removed"):
    val terminal       = VirtualTerminal(20, 5)
    var listenerCalls  = 0
    var componentCalls = 0
    val component      = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        componentCalls += 1
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui            = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val remove         = tui.addInputListener { _ =>
      listenerCalls += 1
      InputResult.Ignored
    }
    tui.start()

    remove()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(listenerCalls, 0)
    assertEquals(componentCalls, 1)

  test("global input listener can request exit"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("stable"))
    tui.addInputListener(_ => InputResult.Exit)
    val thread   = Thread(() => tui.run())
    thread.start()
    Thread.sleep(50)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assert(terminal.output.contains("\u001b[?25h"), terminal.output)

  test("key release is ignored by default"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = 0
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered += 1
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    terminal.sendInput(TerminalInput.KeyEvent(
      TerminalKey.Character("x"),
      eventType = scalatui.terminal.KeyEventType.Release
    ))

    assertEquals(delivered, 0)

  test("key release is delivered to components that opt in"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Option.empty[TerminalInput]
    val component = new Component:
      override def wantsKeyRelease: Boolean                             = true
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered = Some(input)
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    val release = TerminalInput.KeyEvent(
      TerminalKey.Character("x"),
      eventType = scalatui.terminal.KeyEventType.Release
    )
    terminal.sendInput(release)

    assertEquals(delivered, Some(release))

  test("editor submit callback mutations rerender immediately"):
    val terminal = VirtualTerminal(40, 8)
    val output   = Text("Submitted: (none)")
    val editor   = Editor(
      "hello",
      EditorOptions(onSubmit = text => output.text = s"Submitted: $text")
    )
    val tui      = TUI(terminal)
    tui.addChild(output)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("Submitted: hello"), terminal.output)

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

  test("TUI title and progress helpers report virtual terminal support"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)

    assertEquals(tui.setTerminalTitle("demo"), true)
    assertEquals(tui.setTerminalProgress(active = true), true)
    assertEquals(tui.setTerminalProgress(active = false), true)

    assert(terminal.output.contains("\u001b]0;demo\u0007"), terminal.output)
    assert(terminal.output.contains("\u001b]9;4;3\u0007"), terminal.output)
    assert(terminal.output.contains("\u001b]9;4;0\u0007"), terminal.output)

  test("TUI background color query completes with success"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    terminal.clearWrites()
    var results  = Vector.empty[TerminalQueryResult[RgbColor]]

    tui.queryTerminalBackgroundColor(result => results :+= result)
    assert(terminal.output.contains(TerminalColorProtocol.BackgroundColorQuery), terminal.output)
    TestInputStreams.parse("\u001b]11;#112233\u0007").foreach(terminal.sendInput)

    assertEquals(results, Vector(TerminalQueryResult.Success(RgbColor(17, 34, 51))))

  test("TUI queries complete recognized malformed replies as invalid responses"):
    val terminal   = VirtualTerminal(20, 5)
    val tui        = TUI(terminal)
    tui.start()
    var background = Vector.empty[TerminalQueryResult[RgbColor]]
    var scheme     = Vector.empty[TerminalQueryResult[TerminalColorScheme]]

    tui.queryTerminalBackgroundColor(result => background :+= result)
    TestInputStreams.parse("\u001b]11;not-a-color\u0007").foreach(terminal.sendInput)
    tui.queryTerminalColorScheme(result => scheme :+= result)
    TestInputStreams.parse("\u001b[?997;3n").foreach(terminal.sendInput)

    assertEquals(background, Vector(TerminalQueryResult.InvalidResponse))
    assertEquals(scheme, Vector(TerminalQueryResult.InvalidResponse))

  test("TUI stopped query completes synchronously without writing"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var result   = Option.empty[TerminalQueryResult[RgbColor]]

    val cancel = tui.queryTerminalBackgroundColor(value => result = Some(value))

    assertEquals(result, Some(TerminalQueryResult.Stopped))
    assertEquals(terminal.output, "")
    cancel()
    cancel()

  test("TUI query cancellation is idempotent and silent"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    var calls    = 0

    val cancel = tui.queryTerminalColorScheme(_ => calls += 1)
    cancel()
    cancel()
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)

    assertEquals(calls, 0)

  test("TUI query emission failure completes with failed and stops"):
    val terminal = QueryFailingTerminal(TerminalColorProtocol.BackgroundColorQuery)
    val tui      = TUI(terminal)
    tui.start()
    var result   = Option.empty[TerminalQueryResult[RgbColor]]

    tui.queryTerminalBackgroundColor(value => result = Some(value))

    assert(result.exists {
      case TerminalQueryResult.Failed(cause) => cause.getMessage.equals("write failed")
      case _                                 => false
    })

  test("TUI color-scheme query completes with success"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    var results  = Vector.empty[TerminalQueryResult[TerminalColorScheme]]

    tui.queryTerminalColorScheme(result => results :+= result)
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)

    assertEquals(
      results,
      Vector(TerminalQueryResult.Success(TerminalColorScheme.Light))
    )

  test("color-scheme notifications can be enabled disabled and unsubscribed"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var schemes  = Vector.empty[TerminalColorScheme]
    val remove   = tui.onTerminalColorSchemeChange(scheme => schemes :+= scheme)

    tui.start()
    terminal.clearWrites()
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector.empty)

    tui.setTerminalColorSchemeNotifications(enabled = true)
    assert(
      terminal.output.contains(TerminalColorProtocol.EnableColorSchemeNotifications),
      terminal.output
    )
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector(TerminalColorScheme.Dark))

    terminal.clearWrites()
    remove()
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector(TerminalColorScheme.Dark))

    tui.setTerminalColorSchemeNotifications(enabled = false)
    assert(
      terminal.output.contains(TerminalColorProtocol.DisableColorSchemeNotifications),
      terminal.output
    )

  test("color-scheme listeners run outside lifecycle lock"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var joined   = false
    tui.onTerminalColorSchemeChange { _ =>
      val thread = Thread(() => tui.requestRender())
      thread.start()
      thread.join(1000)
      joined = !thread.isAlive
    }

    tui.start()
    tui.setTerminalColorSchemeNotifications(enabled = true)
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)

    assertEquals(joined, true)

  test("terminal protocol replies are not routed to focused component"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    tui.queryTerminalBackgroundColor(_ => ())
    TestInputStreams.parse("\u001b]11;not-a-color\u0007").foreach(terminal.sendInput)
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    TestInputStreams.parse("\u001b[?997;3n").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("terminal protocol replies are not routed to global input listeners"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val tui       = TUI(terminal)
    tui.addInputListener { input =>
      delivered :+= input
      InputResult.Ignored
    }
    tui.start()

    TestInputStreams.parse("\u001b[6;12;24t").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("cell-size reply is consumed before component input routing"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    TestInputStreams.parse("\u001b[6;12;24t").foreach(terminal.sendInput)
    assertEquals(delivered, Vector.empty)
    assertEquals(TerminalImageProtocol.cellDimensions, ImageCellDimensions(24, 12))

  test("valid cell-size reply triggers repaint"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      override def render(width: Int): Vector[String] =
        val dimensions = TerminalImageProtocol.cellDimensions
        Vector(s"${dimensions.widthPx}x${dimensions.heightPx}")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    TestInputStreams.parse("\u001b[6;10;20t").foreach(terminal.sendInput)

    assert(terminal.output.contains("20x10"), terminal.output)

  test("cell-size reply does not block following input"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    TestInputStreams.parse("\u001b[6;0;24t").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  private def visibleOutputLines(output: String): Vector[String] =
    Ansi.strip(output).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector

  private def assertNoAlternateScreen(output: String): Unit =
    assert(!output.contains(TUI.AlternateScreenEnter), output)
    assert(!output.contains(TUI.AlternateScreenExit), output)

  private def countOccurrences(value: String, needle: String): Int =
    if needle.isEmpty then 0
    else value.sliding(needle.length).count(_ == needle)

  test("typed escape exits when configured"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.exitsOnEscape = true
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Escape))
    tui.run()

    assertEquals(terminal.isRunning, false)

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

  test("stop restores cursor stops terminal and notifies run when cleanup write fails"):
    val terminal = StopCleanupFailingTerminal(TerminalColorProtocol.DisableColorSchemeNotifications)
    val tui      = TUI(terminal)
    tui.setTerminalColorSchemeNotifications(enabled = true)
    val failure  = AtomicReference[Throwable](null)
    val thread   = Thread(() =>
      try tui.run()
      catch case e: Throwable => failure.set(e)
    )
    thread.start()
    Thread.sleep(50)

    intercept[RuntimeException](tui.stop())
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assertEquals(failure.get(), null)
    assertEquals(terminal.showCursorCalled, true)
    assertEquals(terminal.stopCalled, true)

  test("stop drains supported terminal before terminal stop and remains idempotent"):
    val terminal = DrainingTerminal()
    val tui      = TUI(terminal)
    tui.start()

    tui.stop()
    tui.stop()

    assertEquals(terminal.drainCalls, 1)
    assertEquals(terminal.stopCalls, 1)

  test("terminal drain helper clamps negative timeout values"):
    val terminal = DrainingTerminal()

    val drained = Terminal.drainInput(terminal, maxMillis = -1L, idleMillis = -2L)

    assertEquals(drained, true)
    assertEquals(terminal.lastMaxMillis, Some(0L))
    assertEquals(terminal.lastIdleMillis, Some(0L))

  test("stop works without optional terminal drain support"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()

    tui.stop()

    assertEquals(terminal.isRunning, false)

  test("run does not exit on ctrl+c key release when component opts in"):
    val terminal  = VirtualTerminal(20, 5)
    val tui       = TUI(terminal)
    val component = new Component:
      override def wantsKeyRelease: Boolean                             = true
      override def handleInputResult(input: TerminalInput): InputResult =
        InputResult.NoRender
      override def render(width: Int): Vector[String]                   = Vector("stable")
    tui.addChild(component)
    tui.setFocus(component)
    val thread    = Thread(() => tui.run())
    thread.start()
    Thread.sleep(50)

    terminal.sendInput(TerminalInput.KeyEvent(
      TerminalKey.Character("c"),
      KeyModifiers(ctrl = true),
      KeyEventType.Release
    ))
    Thread.sleep(50)

    assertEquals(thread.isAlive, true)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    thread.join(1000)

    assertEquals(thread.isAlive, false)

  test("visible overlay is composited over base content"):
    val terminal = VirtualTerminal(10, 3)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcdef"))
    tui.showOverlay(
      MutableLine("XYZ"),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(3)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(1))
      )
    )

    tui.start()

    assert(visibleOutputLines(terminal.output).exists(_.contains("aXYZe")), terminal.output)

  test("non-capturing overlay preserves base input focus"):
    val terminal    = VirtualTerminal(10, 3)
    var baseHits    = 0
    var overlayHits = 0
    val base        = new Component:
      override def render(width: Int): Vector[String]                   = Vector("base")
      override def handleInputResult(input: TerminalInput): InputResult =
        baseHits += 1
        InputResult.NoRender
    val overlay     = new Component:
      override def render(width: Int): Vector[String]                   = Vector("over")
      override def handleInputResult(input: TerminalInput): InputResult =
        overlayHits += 1
        InputResult.NoRender
    val tui         = TUI(terminal)
    tui.addChild(base)
    tui.setFocus(base)
    tui.showOverlay(overlay, OverlayOptions(focusCapturing = false))
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(baseHits, 1)
    assertEquals(overlayHits, 0)

  test("topmost capturing overlay receives input and restores focus when hidden"):
    val terminal     = VirtualTerminal(10, 3)
    var baseFocused  = false
    var firstHits    = 0
    var secondHits   = 0
    val base         = new Component with Focusable:
      override def render(width: Int): Vector[String] = Vector("base")
      override def focused: Boolean                   = baseFocused
      override def focused_=(value: Boolean): Unit    = baseFocused = value
    val first        = new Component:
      override def render(width: Int): Vector[String]                   = Vector("one")
      override def handleInputResult(input: TerminalInput): InputResult =
        firstHits += 1
        InputResult.NoRender
    val second       = new Component:
      override def render(width: Int): Vector[String]                   = Vector("two")
      override def handleInputResult(input: TerminalInput): InputResult =
        secondHits += 1
        InputResult.NoRender
    val tui          = TUI(terminal)
    tui.addChild(base)
    tui.setFocus(base)
    val firstHandle  = tui.showOverlay(
      first,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    val secondHandle = tui.showOverlay(
      second,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    secondHandle.hide()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("y")))
    firstHandle.hide()

    assertEquals(secondHits, 1)
    assertEquals(firstHits, 1)
    assertEquals(baseFocused, true)

  test("overlay layout is recomputed after resize with pi-tui full clear"):
    val terminal = VirtualTerminal(20, 4)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("base"))
    tui.showOverlay(
      MutableLine("wide"),
      OverlayOptions(width = Some(OverlaySize.Percent(100)), anchor = OverlayAnchor.BottomLeft)
    )
    tui.start()
    terminal.clearWrites()

    terminal.resize(2, 2)

    assertNoAlternateScreen(terminal.output)
    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      terminal.output
    )
    assert(visibleOutputLines(terminal.output).exists(_.contains("wi")), terminal.output)
