package scalatui.editing

class EditorBufferSuite extends munit.FunSuite:
  test("initializes from text, clamps cursor, and exports text"):
    val buffer = EditorBuffer.fromText("hello\nworld", EditorCursor(5, 99))
    assertEquals(buffer.lines, Vector("hello", "world"))
    assertEquals(buffer.cursor, EditorCursor(1, 5))
    assertEquals(buffer.text, "hello\nworld")
    assertEquals(buffer.submitText, "hello\nworld")

  test("apply places cursor at end of text"):
    val buffer = EditorBuffer("one\ntwo")
    assertEquals(buffer.cursor, EditorCursor(1, 3))

  test("moves cursor by grapheme clusters and clamps vertically"):
    val buffer = EditorBuffer.fromText("a👨‍👩‍👧‍👦b\nx", EditorCursor(0, 2))
    buffer.moveLeft()
    assertEquals(buffer.cursor, EditorCursor(0, 1))
    buffer.moveRight()
    assertEquals(buffer.cursor, EditorCursor(0, 2))
    buffer.moveDown()
    assertEquals(buffer.cursor, EditorCursor(1, 1))
    buffer.moveDown()
    assertEquals(buffer.cursor, EditorCursor(1, 1))
    buffer.moveUp()
    assertEquals(buffer.cursor, EditorCursor(0, 1))

  test("inserts ascii cjk combining mark and emoji text"):
    val buffer = EditorBuffer.empty
    buffer.insert("a")
    buffer.insert("界")
    buffer.insert("e\u0301")
    buffer.insert("👨‍👩‍👧‍👦")
    assertEquals(buffer.text, "a界e\u0301👨‍👩‍👧‍👦")
    assertEquals(buffer.cursor, EditorCursor(0, 4))

  test("backspace and delete remove whole grapheme clusters"):
    val buffer = EditorBuffer.fromText("ae\u0301👨‍👩‍👧‍👦b", EditorCursor(0, 3))
    buffer.backspace()
    assertEquals(buffer.text, "ae\u0301b")
    assertEquals(buffer.cursor, EditorCursor(0, 2))
    buffer.setCursor(EditorCursor(0, 1))
    buffer.delete()
    assertEquals(buffer.text, "ab")
    assertEquals(buffer.cursor, EditorCursor(0, 1))

  test("newline splits current line"):
    val buffer = EditorBuffer.fromText("hello", EditorCursor(0, 2))
    buffer.insertNewline()
    assertEquals(buffer.lines, Vector("he", "llo"))
    assertEquals(buffer.cursor, EditorCursor(1, 0))

  test("backspace at line start merges with previous line"):
    val buffer = EditorBuffer.fromText("he\nllo", EditorCursor(1, 0))
    buffer.backspace()
    assertEquals(buffer.lines, Vector("hello"))
    assertEquals(buffer.cursor, EditorCursor(0, 2))

  test("delete at line end merges with next line"):
    val buffer = EditorBuffer.fromText("he\nllo", EditorCursor(0, 2))
    buffer.delete()
    assertEquals(buffer.lines, Vector("hello"))
    assertEquals(buffer.cursor, EditorCursor(0, 2))

  test("delete to end and word deletion"):
    val buffer = EditorBuffer.fromText("hello world", EditorCursor(0, 11))
    buffer.deleteWordBackwards()
    assertEquals(buffer.text, "hello ")
    assertEquals(buffer.cursor, EditorCursor(0, 6))
    buffer.insert("again")
    buffer.setCursor(EditorCursor(0, 5))
    buffer.deleteToEndOfLine()
    assertEquals(buffer.text, "hello")
    assertEquals(buffer.cursor, EditorCursor(0, 5))

  test("word navigation honors punctuation and unicode boundaries"):
    val buffer = EditorBuffer.fromText("hello, 世界 again", EditorCursor(0, 0))
    buffer.moveWordForwards()
    assertEquals(buffer.cursor, EditorCursor(0, 5))
    buffer.moveWordForwards()
    assertEquals(buffer.cursor, EditorCursor(0, 6))
    buffer.moveWordForwards()
    assertEquals(buffer.cursor, EditorCursor(0, 9))
    buffer.moveWordBackwards()
    assertEquals(buffer.cursor, EditorCursor(0, 7))

  test("multiline paste preserves line breaks and unicode"):
    val buffer = EditorBuffer.fromText("ab", EditorCursor(0, 1))
    buffer.insertPaste("X\n界e\u0301\nY")
    assertEquals(buffer.lines, Vector("aX", "界e\u0301", "Yb"))
    assertEquals(buffer.cursor, EditorCursor(2, 1))
    assertEquals(buffer.text, "aX\n界e\u0301\nYb")

  test("large paste inserts compact marker and submit expands logical text"):
    val pasted = (1 to 11).map(i => s"line$i").mkString("\n")
    val buffer = EditorBuffer.fromText("ab", EditorCursor(0, 1))
    buffer.insertPaste(pasted)

    assertEquals(buffer.lines, Vector("a[paste #1 +11 lines]b"))
    assertEquals(buffer.cursor, EditorCursor(0, 2))
    assertEquals(buffer.pasteMarkers.get(1), Some(pasted))
    assertEquals(buffer.submitText, s"a${pasted}b")

  test("large paste markers expand in buffer on demand"):
    val pasted = "x" * 1001
    val buffer = EditorBuffer.empty
    buffer.insertPaste(pasted)

    assertEquals(buffer.text, "[paste #1 1001 chars]")
    buffer.expandPasteMarkersInBuffer()
    assertEquals(buffer.text, pasted)
    assertEquals(buffer.pasteMarkers, Map.empty[Int, String])
