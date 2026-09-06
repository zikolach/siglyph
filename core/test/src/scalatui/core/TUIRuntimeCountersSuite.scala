package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol, VirtualTerminal}

class TUIRuntimeCountersSuite extends munit.FunSuite:
  private final class Mutable(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  test("runtime counters record rendering work without retaining application text"):
    val secret   = "counter-secret-application-text"
    val control  = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("YQ==").toOption.get,
      imageId = 903,
      widthCells = 1,
      heightCells = 1
    )
    val content  = Mutable(ComponentRender(
      Vector(secret, "tail"),
      Vector(TerminalControlPlacement(0, 0, control)),
      Vector.empty
    ))
    val terminal = VirtualTerminal(80, 5)
    val tui      = TUI(terminal)
    tui.addChild(content)

    tui.start()

    val initial = tui.testRuntimeCounters.snapshot
    assertEquals(initial.componentRenders, 1L)
    assertEquals(initial.paintedRows, 2L)
    assertEquals(initial.terminalWrites, 2L)
    assertEquals(initial.controlEncodes, 1L)
    assertEquals(initial.searchScans, 0L)
    assert(!initial.toString.contains(secret))

    content.frame = ComponentRender.text(Vector(secret, "changed"))
    tui.requestRender()
    tui.flushRender()
    tui.testRuntimeCounters.recordSearchScans(7)

    val updated = tui.testRuntimeCounters.snapshot
    assertEquals(updated.componentRenders - initial.componentRenders, 1L)
    assertEquals(updated.paintedRows - initial.paintedRows, 2L)
    assertEquals(updated.terminalWrites - initial.terminalWrites, 1L)
    assertEquals(updated.controlEncodes - initial.controlEncodes, 1L)
    assertEquals(updated.searchScans, 7L)
    assert(!updated.toString.contains(secret))
    tui.stop()

  test("runtime counters include overlay component renders"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(Mutable(ComponentRender.text("base")))
    tui.showOverlay(
      Mutable(ComponentRender.text("overlay")),
      OverlayOptions(focusCapturing = false)
    )

    tui.start()

    assertEquals(tui.testRuntimeCounters.snapshot.componentRenders, 2L)
    tui.stop()
