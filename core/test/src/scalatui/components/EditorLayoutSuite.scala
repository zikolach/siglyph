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

  test("keeps focused Unicode clusters atomic at wrap and cursor boundaries"):
    val clusters = Vector(
      "\u1100\u1161\u11a8",
      "\u0915\u094d\u0915",
      "e\u0301",
      "👩‍💻",
      "🇦🇹"
    )

    clusters.foreach { cluster =>
      val buffer = EditorBuffer.fromText("a" + cluster + "b", EditorCursor(0, 2))
      Vector(1, 2, 3, 4).foreach { width =>
        val layout   = EditorLayout.fromBuffer(buffer, width)
        val expected = if Ansi.visibleWidth(cluster) <= width then "a" + cluster + "b" else "ab"
        assertEquals(layout.lines.map(_.text).mkString, expected, cluster)
        assert(layout.lines.forall(_.width <= width), layout.lines.toString)
        assert(layout.cursor.row >= 0, cluster)
      }
    }

  test("flushes buffered zero-width content before an over-wide cluster"):
    val zeroWidth = "\u0301"
    val buffer    = EditorBuffer.fromText(zeroWidth + "界a", EditorCursor(0, 0))
    val before    = EditorLayout.fromBuffer(buffer, width = 1)

    assertEquals(
      before.lines,
      Vector(
        EditorVisualLine(0, 0, 1, zeroWidth, 0),
        EditorVisualLine(0, 1, 2, "", 0),
        EditorVisualLine(0, 2, 3, "a", 1)
      )
    )
    assertEquals(before.cursor, EditorVisualCursor(0, 0))

    buffer.setCursor(EditorCursor(0, 1))
    assertEquals(EditorLayout.fromBuffer(buffer, 1).cursor, EditorVisualCursor(1, 0))
    buffer.setCursor(EditorCursor(0, 2))
    assertEquals(EditorLayout.fromBuffer(buffer, 1).cursor, EditorVisualCursor(2, 0))

  test("places cursor at end of final visual line"):
    val buffer = EditorBuffer.fromText("abcd", EditorCursor(0, 4))
    val layout = EditorLayout.fromBuffer(buffer, width = 2)

    assertEquals(layout.lines.map(_.text), Vector("ab", "cd"))
    assertEquals(layout.cursor, EditorVisualCursor(row = 1, column = 2))

  test("omits a sole oversized cluster while retaining logical ownership"):
    val cluster = "界"
    val buffer  = EditorBuffer.fromText(cluster, EditorCursor(0, 0))
    val layout  = EditorLayout.fromBuffer(buffer, width = 1)

    assertEquals(layout.lines, Vector(EditorVisualLine(0, 0, 1, "", 0)))
    assertEquals(layout.cursor, EditorVisualCursor(0, 0))
    assertEquals(buffer.text, cluster)

    buffer.setCursor(EditorCursor(0, 1))
    val after = EditorLayout.fromBuffer(buffer, width = 1)
    assertEquals(after.lines, Vector(EditorVisualLine(0, 0, 1, "", 0)))
    assertEquals(after.cursor, EditorVisualCursor(0, 0))
    assertEquals(buffer.text, cluster)

  test("keeps exact ownership around an omitted oversized cluster"):
    val cluster = "👩‍💻"
    val buffer  = EditorBuffer.fromText("a" + cluster + "b", EditorCursor(0, 1))
    val before  = EditorLayout.fromBuffer(buffer, width = 1)

    assertEquals(
      before.lines.map(line => (line.startColumn, line.endColumn)),
      Vector((0, 1), (1, 2), (2, 3))
    )
    assertEquals(before.lines.map(_.text), Vector("a", "", "b"))
    assert(before.lines.forall(_.width <= 1), before.lines.toString)
    assertEquals(before.cursor, EditorVisualCursor(1, 0))

    buffer.setCursor(EditorCursor(0, 2))
    val after = EditorLayout.fromBuffer(buffer, width = 1)
    assertEquals(after.cursor, EditorVisualCursor(2, 0))
    assertEquals(buffer.text, "a" + cluster + "b")

  test("wraps sanitized rejected-control expansions without truncation loss"):
    val values = Vector(
      "\u0000",
      "\t",
      "\u007f",
      "\u0085",
      "\u001bPpayload\u001b\\",
      "\u001bPpayload",
      "\u001b_payload\u001b\\",
      "\u001b_payload",
      "\u001b]52;c;payload\u0007",
      "\u001b]52;c;payload",
      "\u0090payload\u009c",
      "\u0090payload",
      "\u009dpayload\u009c",
      "\u009dpayload"
    )

    values.foreach { value =>
      val buffer = EditorBuffer(value)
      val layout = EditorLayout.fromBuffer(buffer, width = 3)
      assert(layout.lines.forall(_.width <= 3), value)
      assertEquals(
        layout.lines.map(line => Ansi.strip(line.text)).mkString,
        Ansi.sanitize(value),
        value
      )
      assertEquals(buffer.text, value, value)
      assert(layout.lines.forall(line =>
        line.startColumn >= 0 && line.endColumn <= buffer.clustersForLine(0).length
      ))
    }

  test("replays and closes supported SGR and OSC 8 across projected editor wraps"):
    val red    = "\u001b[31m"
    val open   = "\u001b]8;;https://example.com\u001b\\"
    val close  = "\u001b]8;;\u001b\\"
    val source = red + open + "abcd" + close + Ansi.Reset
    val layout = EditorLayout.fromBuffer(EditorBuffer(source), width = 2)

    assertEquals(layout.lines.map(line => Ansi.strip(line.text)), Vector("ab", "cd"))
    assertEquals(
      layout.lines.map(_.text),
      Vector(
        red + open + "ab" + close + Ansi.Reset,
        red + open + "cd" + close + Ansi.Reset
      )
    )

  test("maps every rejected expansion row to its exact source owner range"):
    val buffer = EditorBuffer("\t")
    val layout = EditorLayout.fromBuffer(buffer, width = 2)

    assertEquals(layout.lines.map(line => Ansi.strip(line.text)), Vector("\\u", "00", "09"))
    assertEquals(
      layout.lines.map(line => (line.startColumn, line.endColumn)),
      Vector.fill(3)((0, 1))
    )
    buffer.setCursor(EditorCursor(0, 0))
    assertEquals(EditorLayout.fromBuffer(buffer, 2).cursor, EditorVisualCursor(0, 0))
    buffer.setCursor(EditorCursor(0, 1))
    assertEquals(EditorLayout.fromBuffer(buffer, 2).cursor, EditorVisualCursor(2, 2))
