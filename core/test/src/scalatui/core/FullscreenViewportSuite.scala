package scalatui.core

import scalatui.ansi.Ansi
import scalatui.components.{
  MouseRegion,
  OverscrollPolicy,
  ScrollView,
  StackEntry,
  StackEntryOptions,
  VStack
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  KeyModifiers,
  MouseAction,
  MouseWheelDirection,
  TerminalImageProtocol,
  TerminalInput,
  TerminalKey,
  TerminalRenderControlEncoder,
  VirtualTerminal
}

class FullscreenViewportSuite extends munit.FunSuite:
  private final class Lines(var values: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(values)

  test("explicit fullscreen construction paints exactly the positive terminal height"):
    val terminal = VirtualTerminal(initialColumns = 8, initialRows = 4)
    val tui      = TUI.fullscreen(terminal, Lines(Vector("one")))

    tui.start()

    assertEquals(terminal.screenLines, Vector("one", "", "", ""))
    assert(terminal.output.startsWith(TUI.AlternateScreenEnter), terminal.output)
    tui.stop()
    assertEquals(count(TUI.AlternateScreenExit, terminal.output), 1)

  test("vertical layout keeps an editor row sticky while transcript scrolls"):
    val transcript = ScrollView(Lines(Vector("one", "two", "three", "four")))
    val editor     = Lines(Vector("edit>"))
    val root       = VStack(Seq(
      StackEntry(transcript, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(editor, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1)))
    ))
    val terminal   = VirtualTerminal(8, 3)
    val tui        = TUI.fullscreen(terminal, root)

    tui.start()
    transcript.scrollBy(2)
    tui.flushRender()

    assertEquals(terminal.screenLines, Vector("three", "four", "edit>"))
    assertEquals(transcript.viewportExtent, 2)
    tui.stop()

  test("follow end suppression jump and geometry changes clamp offset"):
    val content = Lines(Vector("0", "1", "2", "3"))
    val view    = ScrollView(content, followEnd = true, primary = true)

    ViewportLayoutEngine.layout(view, width = 4, height = 2)
    assertEquals((view.offset, view.contentExtent, view.viewportExtent), (2, 4, 2))

    view.scrollBy(-1)
    content.values :+= "4"
    ViewportLayoutEngine.layout(view, width = 4, height = 2)
    assertEquals(view.offset, 1)
    assertEquals(view.isFollowingEnd, false)

    view.jumpToEnd()
    ViewportLayoutEngine.layout(view, width = 4, height = 3)
    assertEquals(view.offset, 2)
    assertEquals(view.isFollowingEnd, true)

    view.jumpToStart()
    assertEquals(view.offset, 0)
    content.values = Vector("only")
    ViewportLayoutEngine.layout(view, width = 4, height = 1)
    assertEquals(view.offset, 0)

  test("nested wheel routing starts deepest and chains only unconsumed delta"):
    val inner     = ScrollView(Lines(Vector("i0", "i1", "i2", "i3")))
    val outerBody = VStack(Seq(
      StackEntry(Lines(Vector("head")), StackEntryOptions(basis = Some(1))),
      StackEntry(inner, StackEntryOptions(basis = Some(2), maxSize = Some(2))),
      StackEntry(Lines(Vector("tail0", "tail1", "tail2")))
    ))
    val outer     = ScrollView(outerBody)
    val terminal  = VirtualTerminal(8, 3)
    val tui       = TUI.fullscreen(terminal, outer, TUIOptions(mouseInput = true))

    tui.start()
    wheelDown(terminal, row = 1)
    wheelDown(terminal, row = 1)
    wheelDown(terminal, row = 1)

    assertEquals(inner.offset, 2)
    assertEquals(outer.offset, 1)
    tui.stop()

  test("Alt wheel chains signed partial consumption in both directions"):
    def fixture(): (ScrollView, ScrollView, VirtualTerminal, TUI) =
      val inner     = ScrollView(Lines(Vector.tabulate(6)(index => s"i$index")))
      val outerBody = VStack(Seq(
        StackEntry(Lines(Vector("head")), StackEntryOptions(basis = Some(1))),
        StackEntry(inner, StackEntryOptions(basis = Some(2), maxSize = Some(2))),
        StackEntry(Lines(Vector.tabulate(10)(index => s"tail$index")))
      ))
      val outer     = ScrollView(outerBody)
      val terminal  = VirtualTerminal(8, 3)
      val tui       = TUI.fullscreen(terminal, outer, TUIOptions(mouseInput = true))
      tui.start()
      (inner, outer, terminal, tui)

    val (downInner, downOuter, downTerminal, downTui) = fixture()
    downInner.scrollTo(2)
    wheel(downTerminal, MouseWheelDirection.Down, row = 1, alt = true)
    assertEquals(downInner.offset, 4)
    assertEquals(downOuter.offset, 3)
    downTui.stop()

    val (upInner, upOuter, upTerminal, upTui) = fixture()
    upInner.scrollTo(2)
    upOuter.jumpToEnd()
    val outerBefore                           = upOuter.offset
    wheel(upTerminal, MouseWheelDirection.Up, row = 1, alt = true)
    assertEquals(upInner.offset, 0)
    assertEquals(upOuter.offset, outerBefore - 3)
    upTui.stop()

  test("a generic nested handler consumes the signed wheel remainder"):
    val inner         = ScrollView(Lines(Vector("i0", "i1", "i2")))
    var receivedDelta = Option.empty[Int]
    val region        = MouseRegion(
      inner,
      {
        case event: MouseEvent.Wheel =>
          receivedDelta = Some(event.lineDelta)
          MouseHandlerResult.Handled
        case _                       => MouseHandlerResult.Ignored
      }
    )
    val outer         = ScrollView(VStack(Seq(
      StackEntry(region, StackEntryOptions(basis = Some(1), maxSize = Some(1))),
      StackEntry(Lines(Vector("tail0", "tail1", "tail2")))
    )))
    val terminal      = VirtualTerminal(8, 2)
    val tui           = TUI.fullscreen(terminal, outer, TUIOptions(mouseInput = true))
    tui.start()
    inner.jumpToEnd()

    wheel(terminal, MouseWheelDirection.Down, row = 0, alt = true)

    assertEquals(receivedDelta, Some(5))
    assertEquals(outer.offset, 0)
    tui.stop()

  test("contained inner overscroll does not move its outer scroll view"):
    val inner     = ScrollView(
      Lines(Vector("i0", "i1", "i2")),
      overscrollPolicy = OverscrollPolicy.Contain
    )
    val outerBody = VStack(Seq(
      StackEntry(inner, StackEntryOptions(basis = Some(1), maxSize = Some(1))),
      StackEntry(Lines(Vector("tail0", "tail1", "tail2")))
    ))
    val outer     = ScrollView(outerBody)
    val terminal  = VirtualTerminal(8, 2)
    val tui       = TUI.fullscreen(terminal, outer, TUIOptions(mouseInput = true))

    tui.start()
    wheelDown(terminal, row = 0)
    wheelDown(terminal, row = 0)
    wheelDown(terminal, row = 0)

    assertEquals(inner.offset, 2)
    assertEquals(outer.offset, 0)
    tui.stop()

  test("range renderer formats only visible scroll rows and translates typed metadata"):
    var fullRenders = 0
    var ranges      = Vector.empty[(Int, Int)]
    val large       = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 10000
      override def render(width: Int): ComponentRender                                    =
        fullRenders += 1
        ComponentRender.text(Vector.fill(10000)("full"))
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        ranges :+= startRow -> rowCount
        ComponentRender(
          Vector.tabulate(rowCount)(index => s"${startRow + index}"),
          Vector.empty,
          Vector.empty,
          DocumentMetadata(Vector(PromptStart(DocumentPosition(0))))
        )
    val view        = ScrollView(large)

    ViewportLayoutEngine.layout(view, width = 8, height = 3)
    view.scrollBy(500)
    val frame = ViewportLayoutEngine.layout(view, width = 8, height = 3)

    assertEquals(fullRenders, 0)
    assertEquals(ranges, Vector(0 -> 3, 500 -> 3))
    assertEquals(frame.lines.map(Ansi.strip), Vector("500", "501", "502"))
    assertEquals(
      frame.documentMetadata,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(500))))
    )
    assertEquals(
      frame.render.documentMetadata,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(0))))
    )

  test("fullscreen shares overlays cursor controls resize and cleanup services"):
    val control  = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("AAAA").toOption.get,
      imageId = 700,
      widthCells = 1,
      heightCells = 1
    )
    val root     = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("base", "line"),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector(CursorPlacement(1, 2))
      )
    val terminal = VirtualTerminal(8, 3)
    val tui      = TUI.fullscreen(
      terminal,
      root,
      TUIOptions(hardwareCursorPositioning = true)
    )
    tui.showOverlay(
      Lines(Vector("top")),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(3)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(2)),
        focusCapturing = false
      )
    )

    tui.start()
    assertEquals(terminal.screenLines, Vector("batop", "line", ""))
    assert(terminal.output.contains(TerminalRenderControlEncoder.encode(control)))
    assertEquals(terminal.cursorPosition, 1 -> 2)

    terminal.resize(8, 2)
    assertEquals(terminal.screenLines.length, 2)
    tui.stop()
    assertEquals(count(TUI.AlternateScreenExit, terminal.output), 1)

  test("resize during render rejects the stale fullscreen frame"):
    val terminal = VirtualTerminal(8, 2)
    var renders  = 0
    val root     = new Component:
      override def render(width: Int): ComponentRender =
        renders += 1
        if renders === 1 then terminal.resize(8, 3)
        ComponentRender.text(if renders === 1 then "stale" else "current")
    val tui      = TUI.fullscreen(terminal, root)

    tui.start()

    assert(renders >= 2)
    assert(!terminal.output.contains("stale"), terminal.output)
    assertEquals(terminal.screenLines, Vector("current", "", ""))
    tui.stop()

  test("fullscreen root keeps direct rendering unbounded and shares focus and context cleanup"):
    var focusedState = false
    var inputs       = 0
    var contexts     = Vector.empty[Option[TUIContext]]
    val content      = new Component with Focusable with ContextualComponent:
      override def render(width: Int): ComponentRender                  =
        ComponentRender.text(Vector("one", "two", "three"))
      override def focused: Boolean                                     = focusedState
      override def focused_=(value: Boolean): Unit                      = focusedState = value
      override def tuiContext_=(value: Option[TUIContext]): Unit        = contexts :+= value
      override def handleInputResult(input: TerminalInput): InputResult =
        inputs += 1
        InputResult.Handled(requestRender = false)
    val view         = ScrollView(content)
    val terminal     = VirtualTerminal(8, 2)
    val tui          = TUI.fullscreen(terminal, view)
    tui.setFocus(content)

    tui.start()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(view.render(8).lines, Vector("one", "two", "three"))
    assertEquals(inputs, 1)
    assertEquals(focusedState, true)
    tui.stop()
    assertEquals(contexts, Vector(Some(tui), None))

  test("normal and width-only alternate construction remain isolated"):
    val normalTerminal    = VirtualTerminal(8, 2)
    val alternateTerminal = VirtualTerminal(8, 2)
    val normal            = TUI(normalTerminal)
    val alternate         = TUI(
      alternateTerminal,
      TUIOptions(screenMode = TUIScreenMode.Alternate)
    )
    normal.addChild(Lines(Vector("one")))
    alternate.addChild(Lines(Vector("one")))

    normal.start()
    alternate.start()

    assert(!normalTerminal.output.contains(TUI.AlternateScreenEnter))
    assertEquals(alternate.testRuntimeCounters.snapshot.paintedRows, 1L)
    normal.stop()
    alternate.stop()

  private def wheelDown(terminal: VirtualTerminal, row: Int): Unit =
    wheel(terminal, MouseWheelDirection.Down, row)

  private def wheel(
      terminal: VirtualTerminal,
      direction: MouseWheelDirection,
      row: Int,
      alt: Boolean = false
  ): Unit =
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Wheel(direction),
      row,
      col = 0,
      modifiers = KeyModifiers(alt = alt)
    ))

  private def count(value: String, in: String): Int = in.sliding(value.length).count(_ === value)
