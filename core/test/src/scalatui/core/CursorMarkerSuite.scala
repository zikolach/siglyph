package scalatui.core

class CursorMarkerSuite extends munit.FunSuite:
  test("stripAndLocate handles plain text marker"):
    val result = CursorMarker.stripAndLocate(Vector(s"ab${CursorMarker.Sequence}cd"))

    assertEquals(result.lines, Vector("abcd"))
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 2)))

  test("stripAndLocate ignores ANSI SGR width before marker"):
    val result =
      CursorMarker.stripAndLocate(Vector(s"\u001b[31mred\u001b[0m${CursorMarker.Sequence}x"))

    assertEquals(result.lines, Vector("\u001b[31mred\u001b[0mx"))
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 3)))

  test("stripAndLocate ignores OSC hyperlink width before marker"):
    val line =
      s"\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\${CursorMarker.Sequence}!"

    val result = CursorMarker.stripAndLocate(Vector(line))

    assertEquals(
      result.lines,
      Vector("\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\!")
    )
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 4)))

  test("stripAndLocate uses display width for wide unicode and combining marks"):
    val result = CursorMarker.stripAndLocate(Vector(s"界e\u0301🙂${CursorMarker.Sequence}x"))

    assertEquals(result.lines, Vector("界e\u0301🙂x"))
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 5)))

  test("stripAndLocate locates marker before end-of-line fake cursor space"):
    val result =
      CursorMarker.stripAndLocate(Vector(s"ab${CursorMarker.Sequence}\u001b[7m \u001b[27m"))

    assertEquals(result.lines, Vector("ab\u001b[7m \u001b[27m"))
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 2)))

  test("stripAndLocate strips multiple markers and selects first row-major position"):
    val result = CursorMarker.stripAndLocate(Vector(
      s"a${CursorMarker.Sequence}b${CursorMarker.Sequence}",
      s"${CursorMarker.Sequence}c"
    ))

    assertEquals(result.lines, Vector("ab", "c"))
    assertEquals(result.position, Some(CursorMarker.Position(row = 0, column = 1)))
