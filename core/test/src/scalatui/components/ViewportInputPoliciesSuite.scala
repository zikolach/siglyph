package scalatui.components

import scalatui.core.*
import scalatui.terminal.*

class ViewportInputPoliciesSuite extends munit.FunSuite:
  private class Lines extends Component:
    override def render(width: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(40)(i => s"line $i"))

  test("focused select and settings pages do not page the transcript, including bounds"):
    val select   = SelectList(Vector.tabulate(12)(i => SelectItem(s"$i", s"item $i")), 3)
    val settings = SettingsList(
      Vector.tabulate(12)(i => SettingItem(s"$i", s"item $i", "x")),
      SettingsListOptions(maxVisible = 3)
    )
    Vector[Component](select, settings).foreach { list =>
      val transcript = ScrollView(Lines(), primary = true)
      val root       = VStack(Seq(
        StackEntry(transcript, StackEntryOptions(grow = 1, minSize = 1)),
        StackEntry(list, StackEntryOptions(basis = Some(3), minSize = 3, maxSize = Some(3)))
      ))
      val terminal   = VirtualTerminal(30, 10)
      val tui        = TUI.fullscreen(terminal, root)
      tui.setFocus(list)
      tui.start()
      try
        terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
        list match
          case value: SelectList   => assertEquals(value.selected.map(_.value), Some("3"))
          case value: SettingsList => assertEquals(value.selected.map(_.id), Some("3"))
          case _                   => fail("unexpected list")
        terminal.sendInput(TerminalInput.Key(TerminalKey.Up))
        terminal.sendInput(TerminalInput.Key(TerminalKey.PageUp))
        terminal.sendInput(TerminalInput.Key(TerminalKey.PageUp))
        assertEquals(transcript.offset, 0)
        tui.setFocus(null)
        terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
        assert(transcript.offset > 0)
      finally tui.stop()
    }

  test("filtered and empty lists consume page commands"):
    val list  = SelectList(
      Vector.tabulate(10)(i => SelectItem(s"$i", s"item $i")),
      SelectListOptions(maxVisible = 3, filteringEnabled = true)
    )
    list.handleInput(TerminalInput.Key(TerminalKey.Character("9")))
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.PageDown)),
      InputResult.NoRender
    )
    assertEquals(list.selected.map(_.value), Some("9"))
    val empty = SettingsList(Vector.empty)
    assertEquals(
      empty.handleInputResult(TerminalInput.Key(TerminalKey.PageUp)),
      InputResult.NoRender
    )

  test("wheel fixed defaults, Alt, burst, pause, reversal and backwards time are deterministic"):
    val fixed    = WheelScrollController()
    assertEquals(fixed.next(1, 0), 1)
    assertEquals(fixed.next(-1, 20, alt = true), -5)
    val auto     = WheelScrollController(WheelScrollPolicy(accelerate = true))
    assertEquals(auto.next(1, 0), 1)
    assertEquals(auto.next(1, 20), 5)
    assertEquals(auto.next(1, 22), 1)
    assertEquals(auto.next(1, 42), 5)
    assertEquals(auto.next(1, 500), 1)
    assertEquals(auto.next(-1, 520), -1)
    assertEquals(auto.next(-1, 510), -1)
    assertEquals(auto.next(-1, 515), -6)
    val disabled =
      WheelScrollController(WheelScrollPolicy(accelerate = true, terminalAccelerated = true))
    assertEquals(Vector(0L, 20L, 40L).map(disabled.next(1, _)), Vector(1, 1, 1))

  test("normalized wheel remainder chains without a second acceleration and contain stops it"):
    val location   = MouseEventLocation(0, 0, 0, 0, LayoutBounds(0, 0, 10, 3))
    val event      = MouseEvent.Wheel(location, MouseWheelDirection.Down, KeyModifiers())
    val controller = WheelScrollController(WheelScrollPolicy(fixedLines = 5))
    val inner      = ScrollView(Lines())
    val outer      = ScrollView(Lines())
    ViewportLayoutEngine.layout(inner, 10, 3)
    ViewportLayoutEngine.layout(outer, 10, 3)
    inner.scrollTo(35)
    val result     = inner.handleWheelAt(event, 0L, controller)
    assertEquals(result.wheelRemainder, Some(3))
    outer.handleMouseEvent(event.copy(lineDelta = result.wheelRemainder.get))
    assertEquals(outer.offset, 3)
    val contained  = ScrollView(Lines(), overscrollPolicy = OverscrollPolicy.Contain)
    ViewportLayoutEngine.layout(contained, 10, 3)
    contained.jumpToEnd()
    assertEquals(contained.handleWheelAt(event, 20L, controller).wheelRemainder, Some(0))

  test("path policy joins configured punctuation without splitting graphemes or crossing spaces"):
    val text     = "~/cafe\u0301/a.b\\c+d end"
    val document = ViewportSelectionDocument(Vector(text), 100, 1000)
    val default  = document.wordRange(0, 9).get
    assertEquals(document.selection(default.start, default.end).map(_.text), Some("b"))
    val path     = document.wordRange(0, 9, WordSelectionPolicy.PathFriendly).get
    assertEquals(
      document.selection(path.start, path.end).map(_.text),
      Some("~/cafe\u0301/a.b\\c+d")
    )
    val bounded  = ViewportSelectionDocument(Vector(text), 4, 1000)
    assertEquals(bounded.length, 4)
    intercept[IllegalArgumentException](WordSelectionPolicy(" "))
    intercept[IllegalArgumentException](WordSelectionPolicy("." * 257))

  test("wheel timestamp overflow cannot retain an old acceleration gesture"):
    val wheel = WheelScrollController(WheelScrollPolicy(accelerate = true))
    assertEquals(wheel.next(1, Long.MaxValue - 90L), 1)
    assertEquals(wheel.next(1, Long.MaxValue - 70L), 5)
    assertEquals(wheel.next(1, Long.MinValue + 10L), 1)
    assertEquals(wheel.next(1, Long.MinValue + 30L), 5)
