package scalatui.core

import scalatui.ansi.Ansi
import scalatui.components.{
  Editor,
  ScrollView,
  ScrollbarMode,
  SelectItem,
  SelectList,
  StackEntry,
  StackEntryOptions,
  VStack
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyDescriptor,
  KeyModifiers,
  KeybindingCommand,
  KeybindingManager,
  MouseAction,
  MouseButton,
  MouseButtonState,
  MouseWheelDirection,
  TerminalInput,
  TerminalKey,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions,
  VirtualTerminal
}

class ViewportAffordanceSuite extends munit.FunSuite:
  private final class Lines(
      val values: Vector[String],
      markers: Vector[DocumentMarker] = Vector.empty
  ) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender(
      values,
      Vector.empty,
      Vector.empty,
      DocumentMetadata(markers)
    )

  test("typed prompt commands navigate retained metadata and ignore OSC-looking text"):
    val marked = ScrollView(
      Lines(
        Vector.tabulate(12)(index => s"line $index"),
        Vector(
          PromptStart(DocumentPosition(1)),
          PromptStart(DocumentPosition(5)),
          PromptStart(DocumentPosition(9))
        )
      ),
      primary = true
    )
    ViewportLayoutEngine.layout(marked, width = 12, height = 3)
    marked.scrollTo(7)
    marked.scrollToPrompt(-1)
    assertEquals(marked.offset, 5)
    marked.scrollToPrompt(1)
    assertEquals(marked.offset, 9)

    val ordinary = ScrollView(
      Lines(Vector("\u001b]133;Aprompt", "detail", "tail", "end")),
      primary = true
    )
    ViewportLayoutEngine.layout(ordinary, width = 20, height = 2)
    ordinary.scrollTo(1)
    ordinary.scrollToPrompt(-1)
    assertEquals(ordinary.offset, 1)

  test("viewport commands preserve editor handling and fall back to the primary scroll view"):
    val transcript = ScrollView(
      Lines(Vector.tabulate(20)(index => s"line $index")),
      primary = true
    )
    val editor     = Editor("one\ntwo\nthree")
    val root       = VStack(Seq(
      StackEntry(transcript, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(editor, StackEntryOptions(basis = Some(2), minSize = 2, maxSize = Some(2)))
    ))
    val terminal   = VirtualTerminal(12, 6)
    val tui        = TUI.fullscreen(terminal, root)
    tui.setFocus(editor)
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
    assertEquals(transcript.offset, 0)

    tui.setFocus(null)
    terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
    assertEquals(transcript.offset, 1)
    tui.stop()

  test("focused overlay precedes primary fallback and viewport handler can stage search commands"):
    var overlayInputs  = 0
    var searchCommands = Vector.empty[KeybindingCommand]
    val transcript     = ScrollView(
      Lines(Vector.tabulate(12)(index => s"line $index")),
      primary = true
    )
    val root           = new Component with ViewportLayoutProvider with ViewportCommandHandler:
      override def viewportLayout: ViewportLayout                                 = ViewportStackLayout(
        StackAxis.Vertical,
        Vector(ViewportStackEntry(transcript, grow = 1))
      )
      override def render(width: Int): ComponentRender                            = transcript.render(width)
      override def handleViewportCommand(command: KeybindingCommand): InputResult =
        searchCommands :+= command
        if command === KeybindingCommand.ViewportSearchToggle then InputResult.NoRender
        else InputResult.Ignored
    val overlay        = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text("overlay")
      override def handleInputResult(input: TerminalInput): InputResult =
        overlayInputs += 1
        InputResult.NoRender
    val terminal       = VirtualTerminal(12, 4)
    val tui            = TUI.fullscreen(terminal, root)
    tui.start()
    val handle         = tui.showOverlay(overlay)

    terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
    assertEquals(overlayInputs, 1)
    assertEquals(transcript.offset, 0)

    handle.hide()
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("f"),
      KeyModifiers(ctrl = true, shift = true)
    ))
    assert(searchCommands.contains(KeybindingCommand.ViewportSearchToggle))
    tui.stop()

  test("scrollbar geometry renders proportionally and survives narrow width and resize"):
    val view    = ScrollView(
      Lines(Vector.tabulate(20)(index => s"line $index")),
      scrollbar = ScrollbarMode.Always
    )
    val initial = ViewportLayoutEngine.layout(view, width = 6, height = 5)
    assertEquals(
      view.scrollbarGeometry,
      Some(scalatui.components.ScrollbarGeometry(5, 5, 0, 2, 15))
    )
    assert(initial.lines.exists(line => Ansi.strip(line).contains("█")))

    view.scrollTo(15)
    val moved = ViewportLayoutEngine.layout(view, width = 1, height = 3)
    assertEquals(view.scrollbarGeometry.map(_.column), Some(0))
    assertEquals(moved.lines.length, 3)

    ViewportLayoutEngine.layout(view, width = 8, height = 10)
    assertEquals(view.scrollbarGeometry.map(_.trackHeight), Some(10))
    assertEquals(view.offset, 10)

  test("automatic and hidden scrollbar modes follow overflow"):
    val hidden    = ScrollView(
      Lines(Vector("one", "two", "three")),
      scrollbar = ScrollbarMode.Hidden
    )
    val automatic = ScrollView(
      Lines(Vector("one", "two", "three")),
      scrollbar = ScrollbarMode.Automatic
    )
    ViewportLayoutEngine.layout(hidden, width = 6, height = 2)
    ViewportLayoutEngine.layout(automatic, width = 6, height = 3)
    assertEquals(hidden.scrollbarGeometry, None)
    assertEquals(automatic.scrollbarGeometry, None)
    ViewportLayoutEngine.layout(automatic, width = 6, height = 2)
    assert(automatic.scrollbarGeometry.nonEmpty)

  test("scrollbar track press and captured drag use semantic mouse contracts"):
    val view     = ScrollView(
      Lines(Vector.tabulate(50)(index => s"line $index")),
      primary = true,
      scrollbar = ScrollbarMode.Always
    )
    val terminal = VirtualTerminal(10, 10)
    val tui      = TUI.fullscreen(
      terminal,
      view,
      TUIOptions(mouseTracking =
        Some(TerminalMouseTrackingOptions(
          TerminalMouseTrackingMode.Drag
        ))
      )
    )
    tui.start()

    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), 5, 9))
    assertEquals(view.offset, 20)
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      20,
      9
    ))
    assertEquals(view.offset, 40)
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), 20, 9))
    tui.stop()

  test("jump-to-end indicator activates on semantic click and restores following"):
    val view     = ScrollView(
      Lines(Vector.tabulate(12)(index => s"line $index")),
      followEnd = true,
      primary = true,
      jumpToEndIndicator = Some(() => "Jump to end")
    )
    val terminal = VirtualTerminal(20, 4)
    val tui      = TUI.fullscreen(terminal, view, TUIOptions(mouseInput = true))
    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Wheel(MouseWheelDirection.Up),
      row = 1,
      col = 0
    ))

    val bounds = view.jumpToEndIndicatorBounds.get
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Press(MouseButton.Left),
      bounds.row,
      bounds.column
    ))
    assertEquals(view.isFollowingEnd, false)
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Release(MouseButton.Left),
      bounds.row,
      bounds.column
    ))
    assertEquals(view.isFollowingEnd, true)
    assertEquals(view.offset, 8)
    tui.stop()

  test("Alt wheel accelerates pointer-targeted scrolling without selector hover mutation"):
    val view     = ScrollView(
      Lines(Vector.tabulate(20)(index => s"line $index")),
      primary = true
    )
    val terminal = VirtualTerminal(10, 4)
    val tui      = TUI.fullscreen(terminal, view, TUIOptions(mouseInput = true))
    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Wheel(MouseWheelDirection.Down),
      row = 1,
      col = 0,
      modifiers = KeyModifiers(alt = true)
    ))
    assertEquals(view.offset, 5)
    tui.stop()

    val selector         = SelectList(Vector(SelectItem("one", "one"), SelectItem("two", "two")))
    val selectorTerminal = VirtualTerminal(10, 3)
    val selectorTui      = TUI.fullscreen(
      selectorTerminal,
      ScrollView(selector),
      TUIOptions(mouseTracking =
        Some(TerminalMouseTrackingOptions(
          TerminalMouseTrackingMode.AllMotion,
          allowAllMotionInMultiplexer = true
        ))
      )
    )
    selectorTui.start()
    val before           = selector.selected
    selectorTerminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Move(MouseButtonState.Released),
      row = 1,
      col = 1
    ))
    assertEquals(selector.selected, before)
    selectorTui.stop()

  test("custom line binding moves the primary viewport and updates its thumb"):
    val key      = KeyDescriptor(TerminalKey.Character("e"), KeyModifiers(ctrl = true))
    val bindings = KeybindingManager(Map(KeybindingCommand.ViewportLineDown -> Vector(key)))
    val view     = ScrollView(
      Lines(Vector.tabulate(20)(index => s"line $index")),
      primary = true,
      scrollbar = ScrollbarMode.Always
    )
    val terminal = VirtualTerminal(8, 4)
    val tui      = TUI.fullscreen(terminal, view, TUIOptions(keybindings = bindings))
    tui.start()
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("e"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(view.offset, 1)
    assert(view.scrollbarGeometry.nonEmpty)
    tui.stop()
