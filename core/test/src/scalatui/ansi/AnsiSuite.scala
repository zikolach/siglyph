package scalatui.ansi

class AnsiSuite extends munit.FunSuite:
  test("visible width ignores ANSI escapes"):
    assertEquals(Ansi.visibleWidth("\u001b[31mhello\u001b[0m"), 5)

  test("visible width counts tabs as three columns"):
    assertEquals(Ansi.visibleWidth("a\tb"), 5)

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

  test("slice by columns avoids partial wide cells at start and end boundaries"):
    assertEquals(Ansi.sliceByColumns("a表b", 2, 3), Ansi.Slice("b", 1))
    assertEquals(Ansi.sliceByColumns("a表b", 0, 2), Ansi.Slice("a", 1))

  test("slice by columns preserves ANSI style and resets emitted styled slices"):
    val sliced = Ansi.sliceByColumns("\u001b[31ma表b", 1, 2)

    assertEquals(Ansi.strip(sliced.text), "表")
    assertEquals(sliced.width, 2)
    assert(sliced.text.startsWith("\u001b[31m"), sliced.text)
    assert(sliced.text.endsWith(Ansi.Reset), sliced.text)

  test("slice by columns resets ANSI style when wide grapheme is clipped"):
    val sliced = Ansi.sliceByColumns("\u001b[31m界", 0, 1)

    assertEquals(Ansi.strip(sliced.text), "")
    assertEquals(sliced.width, 0)
    assert(sliced.text.startsWith("\u001b[31m"), sliced.text)
    assert(sliced.text.endsWith(Ansi.Reset), sliced.text)

  test("truncate accounts for tabs and clips oversized ellipsis"):
    val tabbed = Ansi.truncateToWidth("a\tb", 4, "")
    val narrow = Ansi.truncateToWidth("表abc", 1)

    assertEquals(Ansi.strip(tabbed), "a\t")
    assert(Ansi.visibleWidth(tabbed) <= 4, tabbed)
    assertEquals(Ansi.strip(narrow), ".")
    assert(Ansi.visibleWidth(narrow) <= 1, narrow)

  test("pad right accounts for tab width"):
    val padded = Ansi.padRight("a\t", 5)

    assertEquals(Ansi.visibleWidth(padded), 5)

  test("zero requested widths return safe empty or minimal output"):
    assertEquals(Ansi.sliceByColumns("abc", 0, 0), Ansi.Slice("", 0))
    assertEquals(Ansi.truncateToWidth("abc", 0), "")
    assertEquals(Ansi.wrapTextWithAnsi("abc", 0), Vector(""))

  test("wrap text returns lines within width"):
    val lines = Ansi.wrapTextWithAnsi("abc表def", 4)
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)

  test("wrap text accounts for tabs"):
    val lines = Ansi.wrapTextWithAnsi("a\tb", 4)

    assertEquals(lines.map(Ansi.strip), Vector("a\t", "b"))
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)
