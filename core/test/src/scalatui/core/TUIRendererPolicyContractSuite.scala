package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  RgbColor,
  TerminalColorProtocol,
  TerminalImageProtocol,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalRenderControlEncoder,
  VirtualTerminal
}

class TUIRendererPolicyContractSuite extends munit.FunSuite:
  private final class FixedFrame(frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  test("default constructors select identical normal-screen policy behavior"):
    val implicitDefault = VirtualTerminal(20, 5)
    val explicitDefault = VirtualTerminal(20, 5)
    val first           = TUI(implicitDefault)
    val second          = TUI(explicitDefault, TUIOptions())
    first.addChild(FixedFrame(ComponentRender.text(Vector("one", "two"))))
    second.addChild(FixedFrame(ComponentRender.text(Vector("one", "two"))))

    first.start()
    second.start()
    first.stop()
    second.stop()

    assertEquals(implicitDefault.output, explicitDefault.output)
    assert(!implicitDefault.output.contains(TUI.AlternateScreenEnter), implicitDefault.output)

  test("screen policies preserve shared query ordering and reentrant callback isolation"):
    screenModes.foreach { mode =>
      val terminal = VirtualTerminal(20, 5)
      val tui      = TUI(terminal, TUIOptions(screenMode = mode))
      val events   = scala.collection.mutable.ArrayBuffer.empty[String]
      tui.addChild(FixedFrame(ComponentRender.text("base")))
      tui.start()
      tui.queryTerminalBackgroundColor { result =>
        events += s"first:$result"
        tui.requestRender()
        tui.flushRender()
      }
      tui.queryTerminalBackgroundColor(result => events += s"second:$result")

      sendRaw(terminal, "\u001b]11;#112233\u0007")

      val success = TerminalQueryResult.Success(RgbColor(17, 34, 51))
      assertEquals(events.toVector, Vector(s"first:$success", s"second:$success"), mode.toString)
      assertEquals(
        terminal.writes.count(_.contains(TerminalColorProtocol.BackgroundColorQuery)),
        1,
        mode.toString
      )
      tui.stop()
    }

  test("screen policies preserve overlays and typed-control authority"):
    val ordinaryControl = "\u001b]1337;File=inline=1;width=1;height=1:AAAA\u0007"
    val typedControl    = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("YQ==").toOption.get,
      imageId = 951,
      widthCells = 1,
      heightCells = 1
    )
    screenModes.foreach { mode =>
      val terminal = VirtualTerminal(80, 5)
      val tui      = TUI(terminal, TUIOptions(screenMode = mode))
      tui.addChild(FixedFrame(ComponentRender(
        Vector(ordinaryControl, "base"),
        Vector(TerminalControlPlacement(1, 0, typedControl)),
        Vector.empty
      )))
      tui.showOverlay(
        FixedFrame(ComponentRender.text("top")),
        OverlayOptions(
          width = Some(OverlaySize.Absolute(3)),
          row = Some(OverlaySize.Absolute(2)),
          col = Some(OverlaySize.Absolute(2)),
          focusCapturing = false
        )
      )

      tui.start()

      assert(terminal.output.contains("top"), mode.toString)
      assert(
        terminal.output.contains(TerminalRenderControlEncoder.encode(typedControl)),
        mode.toString
      )
      assert(terminal.output.contains(Ansi.visibleControlText(ordinaryControl)), mode.toString)
      assert(!terminal.output.contains(ordinaryControl), mode.toString)
      tui.stop()
    }

  test("screen policies preserve cleanup order and buffer-specific cursor parking"):
    screenModes.foreach { mode =>
      val terminal = VirtualTerminal(20, 5)
      val tui      = TUI(terminal, TUIOptions(screenMode = mode))
      tui.addChild(FixedFrame(ComponentRender.text(Vector("one", "two"))))
      tui.start()
      terminal.clearWrites()

      tui.stop()
      tui.stop()

      mode match
        case TUIScreenMode.Normal    =>
          assertEquals(terminal.output, "\r\n\u001b[?25h")
        case TUIScreenMode.Alternate =>
          assertEquals(terminal.output, "\u001b[?25h" + TUI.AlternateScreenExit)
    }

  private def sendRaw(terminal: VirtualTerminal, value: String): Unit =
    val parser = TerminalInputBuffer()
    parser
      .process(TerminalInputChunk(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      .foreach(terminal.sendInput)

  private val screenModes = Vector(TUIScreenMode.Normal, TUIScreenMode.Alternate)
