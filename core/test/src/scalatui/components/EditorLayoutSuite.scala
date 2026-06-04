package scalatui.components

import scalatui.ansi.Ansi
import scalatui.editing.{EditorBuffer, EditorCursor}

class EditorLayoutSuite extends munit.FunSuite:
  test("wraps long logical lines and maps cursor to wrapped visual row"):
    val buffer = EditorBuffer.fromText("abcdef", EditorCursor(0, 5))
    val layout = EditorLayout.fromBuffer(buffer, width = 3)

    assertEquals(layout.lines.map(_.text), Vector("abc", "def"))
    assertEquals(
      layout.lines.map(line => (line.startColumn, line.endColumn)),
      Vector((0, 3), (3, 6))
    )
    assertEquals(layout.cursor, EditorVisualCursor(row = 1, column = 2))

  test("keeps empty logical lines visible and places cursor at column zero"):
    val buffer = EditorBuffer.fromText("a\n\nb", EditorCursor(1, 0))
    val layout = EditorLayout.fromBuffer(buffer, width = 10)

    assertEquals(layout.lines.map(_.text), Vector("a", "", "b"))
    assertEquals(layout.cursor, EditorVisualCursor(row = 1, column = 0))

  test("uses display width for CJK wrapping and cursor boundaries"):
    val buffer = EditorBuffer.fromText("a界b", EditorCursor(0, 2))
    val layout = EditorLayout.fromBuffer(buffer, width = 3)

    assertEquals(layout.lines.map(_.text), Vector("a界", "b"))
    assertEquals(layout.lines.map(_.width), Vector(3, 1))
    assertEquals(layout.cursor, EditorVisualCursor(row = 1, column = 0))

  test("treats combining marks and emoji as grapheme clusters"):
    val buffer = EditorBuffer.fromText("e\u0301🙂x", EditorCursor(0, 2))
    val layout = EditorLayout.fromBuffer(buffer, width = 3)

    assert(layout.lines.forall(line => Ansi.visibleWidth(line.text) <= 3), layout.lines.toString)
    assertEquals(layout.cursor.row, 1)
    assertEquals(layout.cursor.column, 0)

  test("places cursor at end of final visual line"):
    val buffer = EditorBuffer.fromText("abcd", EditorCursor(0, 4))
    val layout = EditorLayout.fromBuffer(buffer, width = 2)

    assertEquals(layout.lines.map(_.text), Vector("ab", "cd"))
    assertEquals(layout.cursor, EditorVisualCursor(row = 1, column = 2))
