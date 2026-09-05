package scalatui.core

import scalatui.components.{MouseRegion, StackEntry, StackEntryOptions, Text, VStack}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyModifiers,
  MouseAction,
  MouseButton,
  MouseButtonState,
  MouseWheelDirection,
  Terminal,
  TerminalInput,
  TerminalKey,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions,
  VirtualTerminal
}

class MouseFoundationSuite extends munit.FunSuite:
  private final class TestClock(var now: Long = 0L) extends MonotonicClock:
    override def nanoTime(): Long = now

  private final class MouseComponent(
      result: MouseEvent => MouseHandlerResult = _ => MouseHandlerResult.Handled
  ) extends Component,
        MouseEventHandler,
        Focusable:
    val events           = scala.collection.mutable.ArrayBuffer.empty[MouseEvent]
    var renders          = 0
    var keys             = 0
    private var hasFocus = false

    override def render(width: Int): ComponentRender =
      renders += 1
      ComponentRender.text("target")

    override def handleMouseEvent(event: MouseEvent): MouseHandlerResult =
      events += event
      result(event)

    override def handleInputResult(input: TerminalInput): InputResult =
      keys += 1
      InputResult.NoRender

    override def focused: Boolean                = hasFocus
    override def focused_=(value: Boolean): Unit = hasFocus = value

  private def raw(
      action: MouseAction,
      row: Int,
      col: Int,
      modifiers: KeyModifiers = KeyModifiers.empty
  ): TerminalInput.Mouse = TerminalInput.Mouse(action, row, col, modifiers)

  private def options(clock: MonotonicClock = MonotonicClock.System): TUIOptions = TUIOptions(
    mouseTracking = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.Drag)),
    mouseGestures = MouseGestureOptions(
      clickMaxDurationMillis = 100,
      multiClickMaxDelayMillis = 200,
      maxCellDistance = 1,
      clock = clock
    )
  )

  test("semantic click uses committed absolute local and nested layout geometry"):
    val events   = scala.collection.mutable.ArrayBuffer.empty[MouseEvent]
    val region   = MouseRegion(
      Text("child"),
      event =>
        events += event
        MouseHandlerResult.Handled
    )
    val root     = VStack(Seq(
      StackEntry(Text("header"), StackEntryOptions(basis = Some(1), shrink = 0)),
      StackEntry(region, StackEntryOptions(grow = 1))
    ))
    val terminal = VirtualTerminal(12, 3)
    val tui      = TUI.fullscreen(terminal, root, options(TestClock()))

    tui.start()
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 1, col = 2))
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 1, col = 2))

    val click = events.collectFirst { case value: MouseEvent.Click => value }.get
    assertEquals(click.clickCount, 1)
    assertEquals(click.location.absoluteRow, 1)
    assertEquals(click.location.absoluteCol, 2)
    assertEquals(click.location.localRow, 0)
    assertEquals(click.location.localCol, 2)
    assertEquals(click.location.bounds, LayoutBounds(1, 0, 12, 2))
    tui.stop()

  test("semantic uncaptured move and wheel preserve modifiers and button state"):
    val component = MouseComponent()
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, component, options())
    val modifiers = KeyModifiers(alt = true)

    tui.start()
    terminal.sendMouse(raw(
      MouseAction.Move(MouseButtonState.Released),
      row = 1,
      col = 2,
      modifiers
    ))
    terminal.sendMouse(raw(
      MouseAction.Wheel(MouseWheelDirection.Down),
      row = 1,
      col = 2,
      modifiers
    ))

    assertEquals(
      component.events.toVector,
      Vector(
        MouseEvent.Move(
          MouseEventLocation(1, 2, 1, 2, LayoutBounds(0, 0, 8, 2)),
          MouseButtonState.Released,
          modifiers
        ),
        MouseEvent.Wheel(
          MouseEventLocation(1, 2, 1, 2, LayoutBounds(0, 0, 8, 2)),
          MouseWheelDirection.Down,
          modifiers,
          lineDelta = 5
        )
      )
    )
    tui.stop()

  test("horizontal wheel reaches the deepest MouseRegion exactly once"):
    val directions = scala.collection.mutable.ArrayBuffer.empty[MouseWheelDirection]
    val region     = MouseRegion(
      Text("child"),
      {
        case wheel: MouseEvent.Wheel =>
          directions += wheel.direction
          MouseHandlerResult.Handled
        case _                       => MouseHandlerResult.Ignored
      }
    )
    val terminal   = VirtualTerminal(8, 1)
    val tui        = TUI.fullscreen(terminal, region, options())
    tui.start()

    terminal.sendMouse(raw(MouseAction.Wheel(MouseWheelDirection.Left), 0, 0))
    terminal.sendMouse(raw(MouseAction.Wheel(MouseWheelDirection.Right), 0, 0))

    assertEquals(directions.toVector, Vector(MouseWheelDirection.Left, MouseWheelDirection.Right))
    tui.stop()

  test("handled wheel with unchanged remainder does not enter legacy routing"):
    val component = MouseComponent {
      case wheel: MouseEvent.Wheel =>
        MouseHandlerResult(handled = true, wheelRemainder = Some(wheel.lineDelta))
      case _                       => MouseHandlerResult.Ignored
    }
    val terminal  = VirtualTerminal(8, 1)
    val tui       = TUI.fullscreen(terminal, component, options())
    tui.start()
    tui.setFocus(component)

    terminal.sendMouse(raw(MouseAction.Wheel(MouseWheelDirection.Down), 0, 0))

    assertEquals(component.events.count(_.isInstanceOf[MouseEvent.Wheel]), 1)
    assertEquals(component.keys, 0)
    tui.stop()

  test("capture routes drag and release outside bounds and release prevents click"):
    val component = MouseComponent {
      case _: MouseEvent.Press => MouseHandlerResult(
          handled = true,
          captureIntent = MouseCaptureIntent.Capture
        )
      case _                   => MouseHandlerResult.Handled
    }
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, component, options())

    tui.start()
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    terminal.sendMouse(raw(
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      row = 5,
      col = 9
    ))
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 5, col = 9))

    assertEquals(
      component.events.map(_.getClass.getSimpleName).toVector,
      Vector("Press", "Drag", "Release")
    )
    val release = component.events.last.asInstanceOf[MouseEvent.Release]
    assertEquals(release.location.localRow, 5)
    assertEquals(release.location.localCol, 9)
    tui.stop()

  test("handler render capture and focus intents use serialized runtime paths"):
    val component = MouseComponent {
      case _: MouseEvent.Press => MouseHandlerResult(
          handled = true,
          renderIntent = MouseRenderIntent.Render,
          captureIntent = MouseCaptureIntent.Capture,
          focusIntent = MouseFocusIntent.Request
        )
      case _                   => MouseHandlerResult.Handled
    }
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, component, options())

    tui.start()
    val initialRenders = component.renders
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 4, col = 4))

    assert(component.renders > initialRenders)
    assertEquals(component.focused, true)
    assertEquals(component.keys, 1)
    assert(component.events.last.isInstanceOf[MouseEvent.Release])
    tui.stop()

  test("injected monotonic clock classifies repeated clicks without a timer"):
    val clock     = TestClock()
    val component = MouseComponent()
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, component, options(clock))

    tui.start()
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    clock.now = 10000000L
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 0, col = 0))
    clock.now = 100000000L
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 1))
    clock.now = 110000000L
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 0, col = 1))

    assertEquals(
      component.events.collect { case click: MouseEvent.Click => click.clickCount }.toVector,
      Vector(1, 2)
    )
    tui.stop()

  test("committed removal clears capture before later release"):
    val component = MouseComponent {
      case _: MouseEvent.Press => MouseHandlerResult(
          handled = true,
          captureIntent = MouseCaptureIntent.Capture
        )
      case _                   => MouseHandlerResult.Handled
    }
    val root      = VStack(Seq(StackEntry(component, StackEntryOptions(grow = 1))))
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, root, options())

    tui.start()
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    root.removeChild(component)
    tui.requestRender()
    tui.flushRender()
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 0, col = 0))

    assertEquals(component.events.count(_.isInstanceOf[MouseEvent.Release]), 0)
    tui.stop()

  test("topmost overlay receives input and hiding it clears its capture"):
    val base     = MouseComponent()
    val overlay  = MouseComponent {
      case _: MouseEvent.Press => MouseHandlerResult(
          handled = true,
          captureIntent = MouseCaptureIntent.Capture
        )
      case _                   => MouseHandlerResult.Handled
    }
    val terminal = VirtualTerminal(8, 2)
    val tui      = TUI.fullscreen(terminal, base, options())
    val handle   = tui.showOverlay(
      overlay,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )

    tui.start()
    terminal.sendMouse(raw(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    handle.setHidden(true)
    terminal.sendMouse(raw(MouseAction.Release(MouseButton.Left), row = 0, col = 0))

    assert(overlay.events.head.isInstanceOf[MouseEvent.Press])
    assertEquals(overlay.events.count(_.isInstanceOf[MouseEvent.Release]), 0)
    assertEquals(base.events.count(_.isInstanceOf[MouseEvent.Release]), 1)
    tui.stop()

  test("MouseRegion preserves direct child render metadata and context"):
    var attached       = Option.empty[TUIContext]
    val expectedRender = ComponentRender(
      Vector("child"),
      controls = Vector.empty,
      cursorPlacements = Vector(CursorPlacement(0, 2)),
      documentMetadata = DocumentMetadata(Vector(PromptStart(DocumentPosition(0, 1))))
    )
    val child          = new Component with ContextualComponent:
      override def render(width: Int): ComponentRender           = expectedRender
      override def tuiContext_=(value: Option[TUIContext]): Unit = attached = value
    val region         = MouseRegion(child, _ => MouseHandlerResult.Ignored)
    val terminal       = VirtualTerminal(8, 2)
    val tui            = TUI.fullscreen(terminal, region, options())

    assertEquals(region.render(8), expectedRender)
    assertEquals(region.renderFrame(8).render, expectedRender)
    tui.start()
    assert(attached.contains(tui))
    tui.stop()
    assertEquals(attached, None)

  test("tracking resolution preserves legacy basic mode and conservative multiplexers"):
    assertEquals(
      TerminalMouseTrackingOptions.resolve(legacyMouseInput = true, None, Map.empty),
      TerminalMouseTrackingMode.Basic
    )
    val allMotion = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.AllMotion))
    assertEquals(
      TerminalMouseTrackingOptions.resolve(false, allMotion, Map("TMUX" -> "active")),
      TerminalMouseTrackingMode.Drag
    )
    assertEquals(
      TerminalMouseTrackingOptions.resolve(
        false,
        Some(TerminalMouseTrackingOptions(
          TerminalMouseTrackingMode.AllMotion,
          allowAllMotionInMultiplexer = true
        )),
        Map("TERM" -> "tmux-256color")
      ),
      TerminalMouseTrackingMode.AllMotion
    )

  test("VirtualTerminal tracks minimum mode and performs one cleanup obligation"):
    val cases = Vector(
      TerminalMouseTrackingMode.Basic     -> Terminal.MouseProtocol.EnableNormalTracking,
      TerminalMouseTrackingMode.Drag      -> Terminal.MouseProtocol.EnableButtonMotionTracking,
      TerminalMouseTrackingMode.AllMotion -> Terminal.MouseProtocol.EnableAllMotionTracking
    )
    cases.foreach { (mode, enable) =>
      val terminal = VirtualTerminal()
      Terminal.setMouseTracking(terminal, mode)
      terminal.start(_ => (), () => ())
      terminal.stop()
      terminal.stop()
      assert(terminal.output.contains(enable), terminal.output)
      assert(terminal.output.contains(Terminal.MouseProtocol.EnableSgrCoordinates), terminal.output)
      assertEquals(
        terminal.writes.count(_ === Terminal.MouseProtocol.disable(mode)),
        1
      )
    }
