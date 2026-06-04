package scalatui.unicode

class UnicodeSuite extends munit.FunSuite:
  test("records generated Unicode version"):
    assertEquals(Unicode.version, "17.0.0")

  test("measures ascii, cjk, combining marks, emoji, and regional indicators"):
    assertEquals(Unicode.stringWidth("hello"), 5)
    assertEquals(Unicode.stringWidth("表"), 2)
    assertEquals(Unicode.stringWidth("e\u0301"), 1)
    assertEquals(Unicode.stringWidth("🙂"), 2)
    assertEquals(Unicode.stringWidth("🇦🇹"), 2)

  test("groups basic grapheme clusters"):
    assertEquals(Unicode.graphemeClusters("e\u0301x"), Vector("e\u0301", "x"))
    assertEquals(Unicode.graphemeClusters("🇦🇹x"), Vector("🇦🇹", "x"))
