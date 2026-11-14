package scalatui.components

import scalatui.core.*
import scalatui.terminal.*

class ViewportWordPolicySuite extends munit.FunSuite:
  test("ScrollView double-click and word drag share path policy"):
    val content  = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(Vector("a.b c+d"))
    val view     = ScrollView(
      content,
      primary = true,
      wordSelectionPolicy = WordSelectionPolicy.PathFriendly
    )
    ViewportLayoutEngine.layout(view, 20, 2)
    val location = MouseEventLocation(0, 0, 0, 0, LayoutBounds(0, 0, 20, 2))
    view.handleMouseEvent(MouseEvent.Click(location, MouseButton.Left, 2, KeyModifiers()))
    assertEquals(view.selection.map(_.text), Some("a.b"))
    view.handleMouseEvent(MouseEvent.Drag(
      location.copy(localCol = 4),
      MouseButton.Left,
      KeyModifiers()
    ))
    assertEquals(view.selection.map(_.text), Some("a.b c+d"))

  test("configured multi-codepoint graphemes remain atomic"):
    val emoji    = "👩‍💻"
    val document = ViewportSelectionDocument(Vector("a" + emoji + "b"), 100, 1000)
    val range    = document.wordRange(0, 2, WordSelectionPolicy(emoji)).get
    assertEquals(document.selection(range.start, range.end).map(_.text), Some("a" + emoji + "b"))
    assertEquals(document.length, 3)

  test("Unicode nonbreaking spaces cannot be configured as word joiners"):
    for value <- Vector("\u00a0", "\u2007", "\u202f") do
      intercept[IllegalArgumentException](WordSelectionPolicy(value))
