package scalatui.core

import scalatui.components.{ScrollView, Text}
import scalatui.syntax.Equality.*
import java.util.concurrent.{CountDownLatch, TimeUnit}

import scalatui.terminal.{
  Base64ImagePayload,
  KeyDescriptor,
  KeyModifiers,
  KeybindingCommand,
  KeybindingManager,
  MouseAction,
  MouseButton,
  MouseButtonState,
  TerminalImageProtocol,
  TerminalInput,
  TerminalKey,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions,
  VirtualTerminal
}

class ViewportSelectionSuite extends munit.FunSuite:
  private final class TestClock(var now: Long = 0L) extends MonotonicClock:
    override def nanoTime(): Long = now

  private final class Lines(var values: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(values)

  private def mouseOptions(
      clock: MonotonicClock = MonotonicClock.System,
      host: Option[HostClipboard] = None,
      keybindings: KeybindingManager = KeybindingManager()
  ): TUIOptions = TUIOptions(
    mouseTracking = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.Drag)),
    mouseGestures = MouseGestureOptions(
      clickMaxDurationMillis = 100,
      multiClickMaxDelayMillis = 200,
      maxCellDistance = 0,
      clock = clock
    ),
    keybindings = keybindings,
    hostClipboard = host
  )

  private def sendMouse(
      terminal: VirtualTerminal,
      action: MouseAction,
      row: Int,
      col: Int
  ): Unit = terminal.sendMouse(TerminalInput.Mouse(action, row, col, KeyModifiers.empty))

  private def click(
      terminal: VirtualTerminal,
      clock: TestClock,
      row: Int,
      col: Int
  ): Unit =
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row, col)
    clock.now += 1000000L
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row, col)
    clock.now += 1000000L

  test("cell mapping selects complete Unicode graphemes without ANSI or typed controls"):
    val clock    = TestClock()
    val control  = TerminalImageProtocol.encodeITerm2(
      Base64ImagePayload.from("U0VDUkVU").toOption.get,
      filename = Some("private.txt"),
      widthCells = 1,
      heightCells = 1
    )
    val content  = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("A e\u0301 \u001b[31m界\u001b[0m Z\u001b]52;c;SECRET\u0007"),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector.empty
      )
    val view     = ScrollView(content, primary = true)
    val terminal = VirtualTerminal(12, 2)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions(clock))

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 0, col = 5)
    assertEquals(tui.viewportSelection.map(_.text), Some("界"))
    assert(!tui.viewportSelection.exists(_.text.contains("31m")))
    assert(!tui.viewportSelection.exists(_.text.contains("SECRET")))
    assert(!tui.viewportSelection.exists(_.text.contains("private.txt")))
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row = 0, col = 5)
    click(terminal, clock, row = 0, col = 5)
    click(terminal, clock, row = 0, col = 5)
    assertEquals(tui.viewportSelection.map(_.text), Some("A e\u0301 界 Z"))
    tui.stop()
    assertEquals(tui.viewportSelection, None)

  test("drag selection crosses wrapped rows and projects normalized offsets after resize"):
    val view     = ScrollView(Text("alpha beta gamma", paddingX = 0), primary = true)
    val terminal = VirtualTerminal(8, 3)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions())

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 0, col = 6)
    sendMouse(
      terminal,
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      row = 1,
      col = 1
    )
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row = 1, col = 1)
    assertEquals(tui.viewportSelection.map(_.text), Some("be\nta"))

    terminal.resize(20, 3)
    assertEquals(tui.viewportSelection.map(_.text), Some("beta"))
    tui.stop()

  test("drag edge scrolling advances at most one bounded row per motion event"):
    val view     = ScrollView(Lines(Vector.tabulate(12)(index => s"row$index")), primary = true)
    val terminal = VirtualTerminal(8, 3)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions())

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 1, col = 0)
    sendMouse(
      terminal,
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      row = 2,
      col = 4
    )
    assertEquals(view.offset, 1)
    sendMouse(
      terminal,
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      row = 2,
      col = 4
    )
    assertEquals(view.offset, 2)
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row = 2, col = 4)
    assert(tui.viewportSelection.nonEmpty)
    tui.stop()

  test("double click keeps path and kebab-case joiners and triple click selects one line"):
    val clock    = TestClock()
    val view     = ScrollView(Lines(Vector("open /tmp/my-file now", "second line")), primary = true)
    val terminal = VirtualTerminal(24, 3)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions(clock))

    tui.start()
    click(terminal, clock, row = 0, col = 10)
    click(terminal, clock, row = 0, col = 10)
    assertEquals(tui.viewportSelection.map(_.text), Some("/tmp/my-file"))
    click(terminal, clock, row = 0, col = 10)
    assertEquals(tui.viewportSelection.map(_.text), Some("open /tmp/my-file now"))
    tui.stop()

  test("content replacement clears selection and its mapping"):
    val view     = ScrollView(Lines(Vector("old content")), primary = true)
    val terminal = VirtualTerminal(16, 2)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions())

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 0, col = 0)
    assert(tui.viewportSelection.nonEmpty)
    view.replaceContent(Lines(Vector("new content")))
    tui.flushRender()
    assertEquals(tui.viewportSelection, None)
    tui.stop()

  test("an overlay blocks selection of covered primary content"):
    val view     = ScrollView(Lines(Vector("underlay")), primary = true)
    val terminal = VirtualTerminal(12, 2)
    val tui      = TUI.fullscreen(terminal, view, mouseOptions())
    tui.showOverlay(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text("overlay"))

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 0, col = 0)
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row = 0, col = 0)
    assertEquals(tui.viewportSelection, None)
    tui.stop()

  test("copy reports unsupported host success rejection and callback failure"):
    val view      = ScrollView(Lines(Vector("copy me")), primary = true)
    val terminal  = VirtualTerminal(12, 2)
    var copied    = ""
    var reentrant = Option.empty[ViewportSelection]
    var tuiRef    = Option.empty[TUI]
    val host      = new HostClipboard:
      override def copy(value: String): Boolean =
        copied = value
        reentrant = tuiRef.flatMap(_.viewportSelection)
        true
    val tui       = TUI.fullscreen(terminal, view, mouseOptions(host = Some(host)))
    tuiRef = Some(tui)

    tui.start()
    assertEquals(tui.clipboardSupport(ClipboardTarget.Host), ClipboardTargetSupport.Supported)
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), row = 0, col = 0)
    sendMouse(
      terminal,
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      row = 0,
      col = 6
    )
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), row = 0, col = 6)
    assertEquals(tui.copySelection(), ClipboardCopyResult.Success(ClipboardTarget.Host))
    assertEquals(copied, "copy me")
    assert(reentrant.nonEmpty)
    tui.stop()

    val unsupported =
      TUI.fullscreen(VirtualTerminal(8, 1), ScrollView(Lines(Vector("x")), primary = true))
    assertEquals(
      unsupported.copySelection(),
      ClipboardCopyResult.Unsupported(ClipboardTarget.Host)
    )

    val rejected = TUI.fullscreen(
      VirtualTerminal(8, 1),
      ScrollView(Lines(Vector("x")), primary = true),
      mouseOptions(host =
        Some(new HostClipboard:
          override def copy(value: String): Boolean = false)
      )
    )
    rejected.start()
    sendMouse(
      rejected.terminal.asInstanceOf[VirtualTerminal],
      MouseAction.Press(MouseButton.Left),
      0,
      0
    )
    assertEquals(rejected.copySelection(), ClipboardCopyResult.Failure(ClipboardTarget.Host))
    rejected.stop()

    val failure = RuntimeException("clipboard failed")
    val failed  = TUI.fullscreen(
      VirtualTerminal(8, 1),
      ScrollView(Lines(Vector("x")), primary = true),
      mouseOptions(host =
        Some(new HostClipboard:
          override def copy(value: String): Boolean = throw failure)
      )
    )
    failed.start()
    sendMouse(
      failed.terminal.asInstanceOf[VirtualTerminal],
      MouseAction.Press(MouseButton.Left),
      0,
      0
    )
    assertEquals(
      failed.copySelection(),
      ClipboardCopyResult.Failure(ClipboardTarget.Host, Some(failure))
    )
    failed.stop()

  test("copy command honors focused overlay precedence and host-only output"):
    val copyKey  = KeyDescriptor(TerminalKey.Character("y"), KeyModifiers(ctrl = true))
    val clearKey = KeyDescriptor(TerminalKey.Character("x"), KeyModifiers(ctrl = true))
    val bindings = KeybindingManager(Map(
      KeybindingCommand.ViewportCopySelection  -> Vector(copyKey),
      KeybindingCommand.ViewportClearSelection -> Vector(clearKey)
    ))
    var copies   = 0
    val host     = new HostClipboard:
      override def copy(value: String): Boolean =
        copies += 1
        true
    val view     = ScrollView(Lines(Vector("copy")), primary = true)
    val terminal = VirtualTerminal(8, 2)
    val tui      =
      TUI.fullscreen(terminal, view, mouseOptions(host = Some(host), keybindings = bindings))
    val overlay  = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text("overlay")
      override def handleInputResult(input: TerminalInput): InputResult = InputResult.NoRender

    tui.start()
    sendMouse(terminal, MouseAction.Press(MouseButton.Left), 0, 0)
    sendMouse(terminal, MouseAction.Release(MouseButton.Left), 0, 0)
    val handle = tui.showOverlay(overlay, OverlayOptions(focusCapturing = true))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
    assertEquals(copies, 0)
    handle.hide()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
    assertEquals(copies, 1)
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("f"),
      KeyModifiers(ctrl = true, shift = true)
    ))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
    assertEquals(copies, 2)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(ctrl = true)))
    assertEquals(tui.viewportSelection, None)
    assert(!terminal.output.contains("\u001b]52;"))
    tui.stop()

  test("selection click reads a range document in bounded chunks from row zero"):
    var fullRenders = 0
    var ranges      = Vector.empty[(Int, Int)]
    val content     = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 100000
      override def render(width: Int): ComponentRender                                    =
        fullRenders += 1
        ComponentRender.text(Vector.fill(100000)("x"))
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        ranges :+= startRow -> rowCount
        ComponentRender.text(Vector.fill(rowCount)("x"))
    val view        = ScrollView(content, primary = true, maxSelectionGraphemes = 5)
    val terminal    = VirtualTerminal(8, 2)
    val tui         = TUI.fullscreen(terminal, view, mouseOptions())
    tui.start()
    ranges = Vector.empty

    sendMouse(terminal, MouseAction.Press(MouseButton.Left), 0, 0)

    assertEquals(fullRenders, 0)
    assertEquals(ranges.headOption, Some(0 -> 64))
    assertEquals(ranges.count(_ === (0 -> 64)), 1)
    assert(ranges.forall(_._1 === 0))
    assert(view.lastSelectionGraphemeScans <= 6)
    tui.stop()

  test("blocked old range render cannot commit selection after content replacement"):
    val entered              = CountDownLatch(1)
    val release              = CountDownLatch(1)
    @volatile var blockRange = false
    val oldContent           = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 1
      override def render(width: Int): ComponentRender                                    = ComponentRender.text("old")
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        if blockRange then
          entered.countDown()
          release.await()
        ComponentRender.text(Option.when(startRow === 0)("old").toVector)
    val view                 = ScrollView(oldContent, primary = true)
    val terminal             = VirtualTerminal(8, 1)
    val tui                  = TUI.fullscreen(terminal, view, mouseOptions())
    tui.start()
    blockRange = true
    val selecting            = Thread(() =>
      sendMouse(terminal, MouseAction.Press(MouseButton.Left), 0, 0)
    )
    selecting.start()
    assert(entered.await(2, TimeUnit.SECONDS))

    view.replaceContent(Lines(Vector("new")))
    release.countDown()
    selecting.join(2000)
    tui.flushRender()

    assertEquals(selecting.isAlive, false)
    assertEquals(tui.viewportSelection, None)
    tui.stop()

  test("oversized selection rows stop ANSI-safe grapheme work at the configured bound"):
    val oversized = "a".repeat(100000) + "\u001b]52;c;SECRET\u0007"
    val content   = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 1
      override def render(width: Int): ComponentRender                                    = ComponentRender.text(oversized)
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        ComponentRender.text(Option.when(startRow === 0 && rowCount > 0)(oversized).toVector)
    val view      = ScrollView(
      content,
      primary = true,
      maxSelectionGraphemes = 4,
      maxSelectionUtf8Bytes = 100
    )
    val terminal  = VirtualTerminal(8, 1)
    val tui       = TUI.fullscreen(terminal, view, mouseOptions())
    tui.start()

    sendMouse(terminal, MouseAction.Press(MouseButton.Left), 0, 0)

    assertEquals(view.lastSelectionGraphemeScans, 4)
    assert(!tui.viewportSelection.exists(_.text.contains("SECRET")))
    tui.stop()

  test("selection retention bounds clipboard payload without splitting graphemes"):
    val retainedCount = 9
    val content       = "a".repeat(retainedCount) + "😀tail"
    var copied        = ""
    val host          = new HostClipboard:
      override def copy(value: String): Boolean =
        copied = value
        true
    val clock         = TestClock()
    val view          = ScrollView(
      Lines(Vector(content)),
      primary = true,
      maxSelectionGraphemes = 100,
      maxSelectionUtf8Bytes = retainedCount + 1
    )
    val terminal      = VirtualTerminal(24, 1)
    val tui           = TUI.fullscreen(terminal, view, mouseOptions(clock, Some(host)))

    tui.start()
    click(terminal, clock, 0, 0)
    click(terminal, clock, 0, 0)
    assertEquals(tui.copySelection(), ClipboardCopyResult.Success(ClipboardTarget.Host))
    assertEquals(scalatui.unicode.Unicode.graphemeClusters(copied).length, retainedCount)
    assertEquals(copied.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, retainedCount)
    assert(!copied.contains("😀"))
    tui.stop()
