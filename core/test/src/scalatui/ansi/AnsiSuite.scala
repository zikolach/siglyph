package scalatui.ansi

class AnsiSuite extends munit.FunSuite:
  test("visible width ignores ANSI escapes"):
    assertEquals(Ansi.visibleWidth("\u001b[31mhello\u001b[0m"), 5)

  test("strip removes CSI, OSC, and APC escapes"):
    val text =
      "\u001b[31mred\u001b[0m\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\\u001b_marker\u0007"
    assertEquals(Ansi.strip(text), "redlink")

  test("truncate preserves ANSI and appends reset/ellipsis"):
    val truncated = Ansi.truncateToWidth("\u001b[31mhello world", 8)
    assertEquals(Ansi.visibleWidth(truncated), 8)
    assert(truncated.contains("..."))
    assert(truncated.contains(Ansi.Reset))

  test("slice by columns respects wide characters"):
    assertEquals(Ansi.sliceByColumns("a表b", 0, 3), Ansi.Slice("a表", 3))
    assertEquals(Ansi.sliceByColumns("a表b", 3, 1), Ansi.Slice("b", 1))

  test("wrap text returns lines within width"):
    val lines = Ansi.wrapTextWithAnsi("abc表def", 4)
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)
