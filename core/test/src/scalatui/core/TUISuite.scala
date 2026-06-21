package scalatui.core

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
  TerminalInput,
  TerminalKey,
  VirtualTerminal
}

import java.util.concurrent.atomic.AtomicReference

class TUISuite extends munit.FunSuite:
  final class MutableLine(var value: String) extends Component:
    override def render(width: Int): Vector[String] = Vector(value)

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

  test("width change redraws frame in place without clearing scrollback"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assert(output.contains("\r\u001b[J"), output)
    assert(!output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("over-wide lines are sanitized instead of failing"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcd"))

    tui.start()

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)
    assertEquals(tui.lastSanitizedLine.map(_.originalWidth), Some(4))

  test("height change redraws frame in place without clearing scrollback"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(20, 3)

    val output = terminal.output
    assert(output.contains("\r\u001b[J"), output)
    assert(!output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
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
    assertEquals(tui.removeChild(loader), true)
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

  test("TUI background color query writes OSC 11 and resolves valid response"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    terminal.clearWrites()

    var result = Option.empty[RgbColor]
    val thread = Thread(() => result = tui.queryTerminalBackgroundColor(timeoutMillis = 1000))
    thread.start()
    awaitOutput(terminal, TerminalColorProtocol.BackgroundColorQuery)
    terminal.sendInput(TerminalInput.Raw("\u001b]11;#112233\u0007"))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assertEquals(result, Some(RgbColor(17, 34, 51)))

  test("TUI background color query times out without valid response"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    terminal.clearWrites()

    assertEquals(tui.queryTerminalBackgroundColor(timeoutMillis = 1), None)
    assert(terminal.output.contains(TerminalColorProtocol.BackgroundColorQuery), terminal.output)

  test("TUI background color query returns none without writing when stopped"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)

    assertEquals(tui.queryTerminalBackgroundColor(timeoutMillis = 1000), None)
    assertEquals(terminal.output, "")

    tui.start()
    tui.stop()
    terminal.clearWrites()

    assertEquals(tui.queryTerminalBackgroundColor(timeoutMillis = 1000), None)
    assertEquals(terminal.output, "")

  test("TUI background color query removes pending query when write fails"):
    val terminal = QueryFailingTerminal(TerminalColorProtocol.BackgroundColorQuery)
    val tui      = TUI(terminal)
    tui.start()

    intercept[RuntimeException](tui.queryTerminalBackgroundColor(timeoutMillis = 1000))

    assertEquals(pendingQueryCount(tui, "pendingBackgroundColorQueries"), 0)

  test("TUI color-scheme query writes DSR and resolves valid response"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    terminal.clearWrites()

    var result = Option.empty[TerminalColorScheme]
    val thread = Thread(() => result = tui.queryTerminalColorScheme(timeoutMillis = 1000))
    thread.start()
    awaitOutput(terminal, TerminalColorProtocol.ColorSchemeQuery)
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;2n"))
    thread.join(1000)

    assertEquals(thread.isAlive, false)
    assertEquals(result, Some(TerminalColorScheme.Light))

  test("TUI color-scheme query returns none without writing when stopped"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)

    assertEquals(tui.queryTerminalColorScheme(timeoutMillis = 1000), None)
    assertEquals(terminal.output, "")

    tui.start()
    tui.stop()
    terminal.clearWrites()

    assertEquals(tui.queryTerminalColorScheme(timeoutMillis = 1000), None)
    assertEquals(terminal.output, "")

  test("TUI color-scheme query removes pending query when write fails"):
    val terminal = QueryFailingTerminal(TerminalColorProtocol.ColorSchemeQuery)
    val tui      = TUI(terminal)
    tui.start()

    intercept[RuntimeException](tui.queryTerminalColorScheme(timeoutMillis = 1000))

    assertEquals(pendingQueryCount(tui, "pendingColorSchemeQueries"), 0)

  test("color-scheme notifications can be enabled disabled and unsubscribed"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var schemes  = Vector.empty[TerminalColorScheme]
    val remove   = tui.onTerminalColorSchemeChange(scheme => schemes :+= scheme)

    tui.start()
    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;1n"))
    assertEquals(schemes, Vector.empty)

    tui.setTerminalColorSchemeNotifications(enabled = true)
    assert(
      terminal.output.contains(TerminalColorProtocol.EnableColorSchemeNotifications),
      terminal.output
    )
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;1n"))
    assertEquals(schemes, Vector(TerminalColorScheme.Dark))

    terminal.clearWrites()
    remove()
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;2n"))
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
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;1n"))

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

    terminal.sendInput(TerminalInput.Raw("\u001b]11;not-a-color\u0007"))
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;1n"))
    terminal.sendInput(TerminalInput.Raw("\u001b[?997;3n"))
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

    terminal.sendInput(TerminalInput.Raw("\u001b[6;12;24t"))
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

    terminal.sendInput(TerminalInput.Raw("\u001b[6;10;20t"))

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

    terminal.sendInput(TerminalInput.Raw("\u001b[6;0;24t"))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  private def visibleOutputLines(output: String): Vector[String] =
    Ansi.strip(output).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector

  private def awaitOutput(terminal: VirtualTerminal, expected: String): Unit =
    val deadline = System.currentTimeMillis() + 1000L
    while !terminal.output.contains(expected) && System.currentTimeMillis() < deadline do
      Thread.sleep(1)
    assert(terminal.output.contains(expected), terminal.output)

  private def pendingQueryCount(tui: TUI, fieldName: String): Int =
    val field = classOf[TUI].getDeclaredField(fieldName)
    field.setAccessible(true)
    field.get(tui).asInstanceOf[scala.collection.mutable.ArrayBuffer[?]].length

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

  test("overlay layout is recomputed after resize"):
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

    assert(terminal.output.contains("\r\u001b[J"), terminal.output)
    assert(!terminal.output.contains("\u001b[2J\u001b[H\u001b[3J"), terminal.output)
    assert(visibleOutputLines(terminal.output).exists(_.contains("wi")), terminal.output)
