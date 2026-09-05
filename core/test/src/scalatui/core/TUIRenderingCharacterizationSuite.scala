package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  Terminal,
  TerminalImageProtocol,
  TerminalInput,
  TerminalRenderControlEncoder,
  VirtualTerminal
}

class TUIRenderingCharacterizationSuite extends munit.FunSuite:
  private final class MutableFrame(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  private final class CleanupRecordingFailingTerminal extends Terminal:
    val writes                           = scala.collection.mutable.ArrayBuffer.empty[String]
    var showCursorCalled                 = false
    var stopCalled                       = false
    private var failedSynchronizedRender = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()
    override def stop(): Unit                                                      = stopCalled = true
    override def write(data: String): Unit                                         =
      if data.contains(TUI.SyncStart) && !failedSynchronizedRender then
        failedSynchronizedRender = true
        throw RuntimeException("characterized render failure")
      writes += data
    override def columns: Int                                                      = 20
    override def rows: Int                                                         = 5
    override def moveBy(lines: Int): Unit                                          = ()
    override def hideCursor(): Unit                                                = ()
    override def showCursor(): Unit                                                = showCursorCalled = true
    override def clearLine(): Unit                                                 = ()
    override def clearFromCursor(): Unit                                           = ()
    override def clearScreen(): Unit                                               = ()

  test("normal screen first render and differential tail output remain byte-characterized"):
    val terminal  = VirtualTerminal(20, 5)
    val component = MutableFrame(ComponentRender.text(Vector("first", "second")))
    val tui       = TUI(terminal)
    tui.addChild(component)

    tui.start()

    assert(terminal.output.contains(TUI.SyncStart + TUI.AutoWrapOff), terminal.output)
    assert(terminal.output.contains("first" + TUI.LineReset + "\r\nsecond" + TUI.LineReset))
    assert(terminal.output.endsWith(TUI.SyncEnd + TUI.AutoWrapOn), terminal.output)
    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)
    terminal.clearWrites()

    component.frame = ComponentRender.text(Vector("first", "changed"))
    tui.requestRender()
    tui.flushRender()

    assert(terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\r\u001b[J"))
    assert(!terminal.output.contains("first" + TUI.LineReset), terminal.output)
    assert(terminal.output.contains("changed" + TUI.LineReset), terminal.output)
    tui.stop()

  test("normal screen resize keeps both configured clear policies"):
    Vector(
      NormalResizeClearPolicy.ClearScrollback    -> TUI.NormalScreenClear,
      NormalResizeClearPolicy.PreserveScrollback -> TUI.NormalScreenViewportClear
    ).foreach { (policy, expectedClear) =>
      val terminal = VirtualTerminal(20, 5)
      val tui      = TUI(terminal, TUIOptions(normalResizeClearPolicy = policy))
      tui.addChild(MutableFrame(ComponentRender.text("resized")))
      tui.start()
      terminal.clearWrites()

      terminal.resize(30, 4)

      assert(
        terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + expectedClear),
        terminal.output
      )
      assert(terminal.output.contains("resized" + TUI.LineReset), terminal.output)
      assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)
      tui.stop()
    }

  test("normal screen overlays, cursor placement, and typed controls remain characterized"):
    val control  = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("YQ==").toOption.get,
      imageId = 901,
      widthCells = 1,
      heightCells = 1
    )
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableFrame(ComponentRender(
      Vector("base", "cursor"),
      Vector(TerminalControlPlacement(0, 0, control)),
      Vector(CursorPlacement(1, 3))
    )))
    tui.showOverlay(
      MutableFrame(ComponentRender.text("top")),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(3)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(2)),
        focusCapturing = false
      )
    )

    tui.start()

    assert(terminal.output.contains("top"), terminal.output)
    assert(terminal.output.contains(TerminalRenderControlEncoder.encode(control)), terminal.output)
    assert(terminal.output.contains(s"\r\u001b[3C${TUI.SyncEnd}"), terminal.output)
    tui.stop()

  test("normal screen append-only output republishes the retained frame"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(
      terminal,
      TUIOptions(normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback)
    )
    tui.addChild(MutableFrame(ComponentRender.text("live")))
    tui.start()
    terminal.clearWrites()
    var result   = Option.empty[AppendResult]

    tui.appendToScrollback(
      MutableFrame(ComponentRender.text("history")),
      completed => result = Some(completed)
    )

    assertEquals(result, Some(AppendResult.Published(1, 0)))
    assert(terminal.output.contains("history" + TUI.LineReset + "\r\nlive" + TUI.LineReset))
    assertEquals(terminal.screenLines.filter(_.nonEmpty).takeRight(2), Vector("history", "live"))
    tui.stop()

  test("normal screen stop parks below content and restores the cursor"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableFrame(ComponentRender.text(Vector("first", "second"))))
    tui.start()
    terminal.clearWrites()

    tui.stop()

    assert(terminal.output.contains("\r\n\u001b[?25h"), terminal.output)
    assert(!terminal.output.contains(TUI.AlternateScreenExit), terminal.output)
    assert(!terminal.isRunning)

  test("normal screen render failure restores autowrap, cursor, and terminal lifecycle"):
    val terminal = CleanupRecordingFailingTerminal()
    val tui      = TUI(terminal)
    tui.addChild(MutableFrame(ComponentRender.text("failure")))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "characterized render failure")
    assert(terminal.writes.contains(TUI.AutoWrapOn), terminal.writes.toVector)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("width-only alternate screen enters before an unbounded first full redraw"):
    val terminal = VirtualTerminal(20, 2)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(ComponentRender.text(Vector("one", "two", "three", "four"))))

    tui.start()

    assert(terminal.output.startsWith(TUI.AlternateScreenEnter), terminal.output)
    assert(
      terminal.output.contains(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      terminal.output
    )
    assert(terminal.output.contains("one" + TUI.LineReset), terminal.output)
    assert(terminal.output.contains("four" + TUI.LineReset), terminal.output)
    terminal.clearWrites()

    tui.requestRender(force = true)
    tui.flushRender()

    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[3A\r"),
      terminal.output
    )
    assert(!terminal.output.contains(TUI.AlternateScreenClear), terminal.output)
    assert(terminal.output.contains("four" + TUI.LineReset), terminal.output)
    tui.stop()

  test("width-only alternate screen resize clears only the active viewport"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(ComponentRender.text("resized")))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 4)

    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      terminal.output
    )
    assert(!terminal.output.contains("\u001b[3J"), terminal.output)
    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)
    assert(terminal.output.contains("resized" + TUI.LineReset), terminal.output)
    tui.stop()

  test("width-only alternate screen preserves overlays and typed controls"):
    val control  = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("YQ==").toOption.get,
      imageId = 902,
      widthCells = 1,
      heightCells = 1
    )
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(ComponentRender(
      Vector("base"),
      Vector(TerminalControlPlacement(0, 0, control)),
      Vector.empty
    )))
    tui.showOverlay(
      MutableFrame(ComponentRender.text("top")),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(3)),
        row = Some(OverlaySize.Absolute(1)),
        col = Some(OverlaySize.Absolute(2)),
        focusCapturing = false
      )
    )

    tui.start()

    assert(terminal.output.contains(TerminalRenderControlEncoder.encode(control)), terminal.output)
    assert(terminal.output.contains("top"), terminal.output)
    tui.stop()

  test("width-only alternate screen cleanup exits once without normal-screen parking"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(ComponentRender.text(Vector("first", "second"))))
    tui.start()
    terminal.clearWrites()

    tui.stop()
    tui.stop()

    assertEquals(
      terminal.output.sliding(TUI.AlternateScreenExit.length).count(_ === TUI.AlternateScreenExit),
      1
    )
    assert(terminal.output.contains("\u001b[?25h"), terminal.output)
    assert(!terminal.output.contains("\r\n"), terminal.output)
    assert(!terminal.isRunning)
