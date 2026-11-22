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

  test("inserts moves and deletes focused Unicode clusters atomically at every position"):
    val clusters = Vector("\u1100\u1161\u11a8", "\u0915\u094d\u0915", "e\u0301", "👩‍💻", "🇦🇹")
    clusters.foreach { cluster =>
      Vector(0, 1, 2).foreach { position =>
        val buffer = EditorBuffer.fromText("AB", EditorCursor(0, position))
        buffer.insert(cluster)
        assertEquals(buffer.cursor, EditorCursor(0, position + 1), cluster)
        buffer.moveLeft()
        buffer.delete()
        assertEquals(buffer.text, "AB", cluster)
      }
    }

  test("insertion cursor follows final segmentation when both neighbors join"):
    val cases = Vector(
      ("AB", 1, "\u0301", "A\u0301B", EditorCursor(0, 1), "B"),
      ("AB", 1, "\u0600", "A\u0600B", EditorCursor(0, 2), "A"),
      ("\u1100\u11a8", 1, "\u1161", "\u1100\u1161\u11a8", EditorCursor(0, 1), ""),
      ("\u0915\u0915", 1, "\u094d", "\u0915\u094d\u0915", EditorCursor(0, 1), ""),
      ("👩💻", 1, "\u200d", "👩‍💻", EditorCursor(0, 1), ""),
      ("🇹", 0, "🇦", "🇦🇹", EditorCursor(0, 1), "")
    )

    cases.foreach { case (initial, column, inserted, expected, cursor, afterBackspace) =>
      val buffer = EditorBuffer.fromText(initial, EditorCursor(0, column))
      buffer.insert(inserted)
      assertEquals(buffer.text, expected, expected)
      assertEquals(buffer.cursor, cursor, expected)
      buffer.backspace()
      assertEquals(buffer.text, afterBackspace, expected)
    }

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

  test("chunked paste insertion preserves boundaries without a whole-paste string"):
    val buffer = EditorBuffer.fromText("ab", EditorCursor(0, 1))
    buffer.insertPasteChunks(Vector("e", "\u0301\n👨‍", "👩‍👧‍👦\n🇦", "🇹"))

    assertEquals(buffer.text, "ae\u0301\n👨‍👩‍👧‍👦\n🇦🇹b")
    assertEquals(buffer.cursor, EditorCursor(2, 1))

  test("submission expands registered markers once and preserves replacement metacharacters"):
    val buffer = EditorBuffer.empty
    val first  = "$1\\path [paste #2]"
    buffer.restore(EditorBuffer.Snapshot(
      Vector("[paste #1] / [paste #2]"),
      EditorCursor(0, 3),
      Map(1 -> first, 2 -> "second"),
      2
    ))
    assertEquals(buffer.submitText, first + " / second")

  test(
    "new paste identities avoid literal references in display and retained payloads after restore"
  ):
    val literal = "[paste #1 1001 chars] "
    val first   = "x" * 1001 + " [paste #2] [paste #4]"
    val buffer  = EditorBuffer(literal)
    buffer.insertPaste(first)
    assertEquals(buffer.pasteMarkers.keySet, Set(3))
    assertEquals(buffer.submitText, literal + first)
    val saved   = buffer.snapshot
    buffer.insertPaste("z" * 1001)
    buffer.restore(saved)
    buffer.insertPaste("y" * 1001)
    assertEquals(buffer.pasteMarkers.keySet, Set(3, 5))
    assertEquals(buffer.submitText, literal + first + "y" * 1001)

  test("out-of-range literal marker IDs remain ordinary text with registered pastes"):
    val literal = "[paste #999999999999999999999999 1001 chars] "
    val buffer  = EditorBuffer(literal)
    buffer.insertPaste("x" * 1001)
    assertEquals(buffer.submitText, literal + "x" * 1001)
    buffer.setCursor(EditorCursor(0, 1))
    assertEquals(buffer.cursor, EditorCursor(0, 1))

  test("noncanonical marker IDs do not alias a registered paste"):
    val buffer = EditorBuffer.empty
    buffer.restore(EditorBuffer.Snapshot(
      Vector("[paste #01] [paste #1]"),
      EditorCursor(0, 0),
      Map(1 -> "payload"),
      1
    ))
    assertEquals(buffer.submitText, "[paste #01] payload")

  test("exhausted paste IDs reject without modifying buffer state"):
    val buffer = EditorBuffer.empty
    buffer.restore(EditorBuffer.Snapshot(
      Vector("draft"),
      EditorCursor(0, 5),
      Map.empty,
      Int.MaxValue
    ))
    val before = buffer.snapshot
    intercept[IllegalStateException](buffer.insertPaste("x" * 1001))
    assertEquals(buffer.snapshot, before)

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
