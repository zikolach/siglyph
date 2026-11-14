package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol, VirtualTerminal}

class GraphicsLifecycleSuite extends munit.FunSuite:
  private val image = TerminalImageProtocol.encodeKitty(
    Base64ImagePayload.from("AAAA").toOption.get,
    imageId = 12010,
    widthCells = 2,
    heightCells = 3
  )

  private final class Root extends Component:
    var covered                                      = ""
    override def render(width: Int): ComponentRender = ComponentRender(
      Vector("before", "", covered, "", "after"),
      Vector(TerminalControlPlacement(1, 0, image)),
      Vector.empty
    )

  private def fixture(run: (VirtualTerminal, TUI, Root) => Unit): Unit =
    val terminal = VirtualTerminal(20, 6)
    val root     = new Root
    val tui      = TUI.fullscreen(terminal, root)
    tui.start()
    try run(terminal, tui, root)
    finally tui.stop()

  test("initial image paints after covered-row erases and all text"):
    fixture { (terminal, _, _) =>
      val output = terminal.output
      assert(output.indexOf("\u001b_Ga=T") > output.lastIndexOf("\u001b[2K"), output)
      assert(output.indexOf("\u001b_Ga=T") > output.indexOf("after"), output)
    }

  test("changed row below unchanged image anchor restores image last"):
    fixture { (terminal, tui, root) =>
      terminal.clearWrites()
      root.covered = "changed"
      tui.requestRender()
      tui.flushRender()
      val output = terminal.output
      assert(output.indexOf("\u001b_Ga=p") > output.indexOf("changed"), output)
      assert(!output.contains("\u001b_Ga=T"), output)
    }

  test("forced clear retains uploads without ED2"):
    fixture { (terminal, tui, _) =>
      terminal.clearWrites()
      tui.requestRender(force = true)
      tui.flushRender()
      val output = terminal.output
      assert(output.contains("\u001b_Ga=p"), output)
      assert(!output.contains("\u001b_Ga=T"), output)
      assert(!output.contains("\u001b[2J"), output)
    }

  test("resize full clear retains uploads without ED2"):
    fixture { (terminal, _, _) =>
      terminal.clearWrites()
      terminal.resize(20, 8)
      val output = terminal.output
      assert(output.contains("\u001b_Ga=p"), output)
      assert(!output.contains("\u001b_Ga=T"), output)
      assert(!output.contains("\u001b[2J"), output)
      assert(terminal.screenLines.exists(_.trim === "after"))
    }

  test("overlay uncover restores retained placement without uploading"):
    fixture { (terminal, tui, _) =>
      val panel   = new Component:
        override def render(width: Int): ComponentRender = ComponentRender.text("PANEL")
      terminal.clearWrites()
      val overlay = tui.showOverlay(
        panel,
        OverlayOptions(
          width = Some(OverlaySize.Absolute(8)),
          row = Some(OverlaySize.Absolute(2)),
          col = Some(OverlaySize.Absolute(1)),
          focusCapturing = false
        )
      )
      tui.flushRender()
      assert(terminal.output.contains("\u001b_Ga=d"), terminal.output)
      assert(!terminal.output.contains("\u001b_Ga=p"), terminal.output)
      terminal.clearWrites()
      overlay.hide()
      tui.flushRender()
      assert(terminal.output.contains("\u001b_Ga=p"), terminal.output)
      assert(!terminal.output.contains("\u001b_Ga=T"), terminal.output)
    }
