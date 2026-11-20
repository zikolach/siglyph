package scalatui.components

import scalatui.core.InputResult
import scalatui.syntax.Equality.*
import scalatui.editing.{EditorBuffer, EditorCursor}
import scalatui.terminal.{TerminalInput, TerminalInputChunk, TerminalKey}

class EditorNavigationSuite extends munit.FunSuite:
  private def key(editor: Editor, key: TerminalKey): InputResult =
    editor.handleInputResult(TerminalInput.Key(key))

  private def position(editor: Editor, width: Int): EditorVisualCursor =
    EditorLayout.fromBuffer(EditorBuffer.fromText(editor.text, editor.cursor), width).cursor

  test("arrows traverse wrapped rows rather than logical lines"):
    val editor = Editor("abcdefghij")
    editor.render(4)
    editor.setCursor(EditorCursor(0, 2))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(0, 6))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(0, 10))
    key(editor, TerminalKey.Up)
    assertEquals(editor.cursor, EditorCursor(0, 6))
    key(editor, TerminalKey.Up)
    assertEquals(editor.cursor, EditorCursor(0, 2))

  test("preferred display column survives short logical rows and boundary no-ops"):
    val editor = Editor("abcdef\nx\nabcdef")
    editor.render(20)
    editor.setCursor(EditorCursor(0, 5))
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(1, 1))
    editor.render(20)
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 5))
    key(editor, TerminalKey.Down)
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Up)
    assertEquals(editor.cursor, EditorCursor(0, 5))

  test("full wrapped target clamps before the boundary owned by the next row"):
    val editor = Editor("abcdefgh\n123456")
    editor.render(4)
    editor.setCursor(EditorCursor(1, 4))
    key(editor, TerminalKey.Up)
    assertEquals(position(editor, 4), EditorVisualCursor(2, 0))
    editor.setCursor(EditorCursor(1, 6))
    key(editor, TerminalKey.Up)
    assertEquals(position(editor, 4), EditorVisualCursor(2, 2))
    val full   = Editor("abcdefgh")
    full.render(4)
    key(full, TerminalKey.Up)
    assertEquals(full.cursor, EditorCursor(0, 3))
    assertEquals(position(full, 4), EditorVisualCursor(0, 3))
    key(full, TerminalKey.Down)
    assertEquals(full.cursor, EditorCursor(0, 8))

  test("wide and combining graphemes use display columns and floor within wide units"):
    val editor = Editor("abcd\na界e\ne\nab界e\ne\u0301🙂xy")
    editor.render(20)
    editor.setCursor(EditorCursor(0, 2))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(1, 1))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 1))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(3, 2))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(4, 1))
    assertEquals(editor.text, "abcd\na界e\ne\nab界e\ne\u0301🙂xy")

  test("ANSI metadata and rejected controls navigate only reachable source boundaries"):
    val sources = Vector(
      "a\u001b[31m界e\u0301\u001b[0mz",
      "a\tbc",
      "a\u001b]8;;https://example.com\u001b\\界\u001b]8;;\u001b\\z",
      "界🙂a"
    )
    for source <- sources; width <- Vector(1, 2, 3, 5) do
      val editor   = Editor(source + "\nlast")
      editor.render(width)
      val original = editor.text
      var previous = position(editor, width).row
      var result   = key(editor, TerminalKey.Up)
      while result === InputResult.Render do
        val next = position(editor, width).row
        assert(next < previous, s"$source width=$width: $previous -> $next")
        previous = next
        result = key(editor, TerminalKey.Up)
      assertEquals(editor.text, original)

  test("resize resets preferred column to the newly projected cursor"):
    val editor = Editor("abcdef\nx\nabcdef")
    editor.render(10)
    editor.setCursor(EditorCursor(0, 5))
    key(editor, TerminalKey.Down)
    editor.render(8)
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 1))

  test("horizontal commands and explicit placement reset preferred column"):
    val editor = Editor("abcdef\nxy\nabcdef")
    editor.render(10)
    editor.setCursor(EditorCursor(0, 5))
    key(editor, TerminalKey.Down)
    key(editor, TerminalKey.Right)
    key(editor, TerminalKey.Up)
    assertEquals(editor.cursor, EditorCursor(1, 0))
    editor.setCursor(EditorCursor(1, 1))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 1))

  test("nonempty history entry is deliberate and restores exact draft cursor"):
    val editor = Editor("draft\nsecond")
    editor.addToHistory("old")
    editor.render(4)
    editor.setCursor(EditorCursor(0, 1))
    assertEquals(key(editor, TerminalKey.Up), InputResult.NoRender)
    assertEquals(editor.text, "draft\nsecond")
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    assertEquals(editor.text, "old")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "draft\nsecond")
    assertEquals(editor.cursor, EditorCursor(0, 0))

  test("history restores a nonzero source cursor at an ANSI-projected display start"):
    val draft  = "\u001b[31mdraft\u001b[0m\nsecond"
    val editor = Editor(draft)
    editor.render(20)
    editor.addToHistory("old")
    editor.setCursor(EditorCursor(0, 5))
    assertEquals(position(editor, 20), EditorVisualCursor(0, 0))
    key(editor, TerminalKey.Up)
    assertEquals(editor.text, "old")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, draft)
    assertEquals(editor.cursor, EditorCursor(0, 5))

  test("history traverses wrapped single-line entries before returning the draft"):
    val editor = Editor("draft")
    editor.addToHistory("older")
    editor.addToHistory("abcdefghij")
    editor.render(4)
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "abcdefghij")
    assertEquals(editor.cursor, EditorCursor(0, 4))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(0, 8))
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "draft")
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Up)
    assertEquals(editor.text, "older")
    key(editor, TerminalKey.Up)
    assertEquals(editor.text, "older")

  test("ignored history additions preserve browsing while actual additions end it"):
    val editor = Editor("draft")
    editor.render(20)
    editor.addToHistory("old")
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    editor.addToHistory("  ")
    editor.addToHistory(" old ")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "draft")
    key(editor, TerminalKey.Up)
    editor.addToHistory("new")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "old")

  test("empty history is a no-op and empty draft is restored"):
    val editor = Editor()
    assertEquals(key(editor, TerminalKey.Up), InputResult.NoRender)
    assertEquals(key(editor, TerminalKey.Down), InputResult.NoRender)
    assert(!editor.undo())
    editor.render(20)
    editor.addToHistory("old")
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "")
    assertEquals(editor.cursor, EditorCursor(0, 0))

  test("history restoration with equal text repaints cursor without change callback"):
    var changes = Vector.empty[String]
    val editor  = Editor("same", EditorOptions(onChange = value => changes :+= value))
    editor.addToHistory("same")
    editor.render(10)
    editor.setCursor(EditorCursor(0, 0))
    assertEquals(key(editor, TerminalKey.Up), InputResult.NoRender)
    key(editor, TerminalKey.Right)
    assertEquals(key(editor, TerminalKey.Down), InputResult.Render)
    assertEquals(editor.cursor, EditorCursor(0, 0))
    assertEquals(changes, Vector.empty)

  test("history entry is one undo step and editing abandons the original draft"):
    val editor = Editor("draft")
    editor.addToHistory("old")
    editor.render(20)
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    assert(editor.undo())
    assertEquals(editor.text, "draft")
    key(editor, TerminalKey.Up)
    editor.insertAtCursor("X")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "Xold")
    assert(editor.undo())
    assertEquals(editor.text, "old")
    assert(editor.undo())
    assertEquals(editor.text, "draft")

  test("history draft retains large paste registry through submission"):
    val payload   = "界" * 1001
    var submitted = ""
    val editor    = Editor(options = EditorOptions(onSubmit = value => submitted = value))
    editor.addToHistory("old")
    editor.handleInput(TerminalInput.PasteStart)
    editor.handleInput(TerminalInput.PasteChunk(TerminalInputChunk(payload.getBytes("UTF-8"))))
    editor.handleInput(TerminalInput.PasteEnd)
    val draft     = editor.text
    editor.render(40)
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, draft)
    assertEquals(editor.cursor, EditorCursor(0, 0))
    key(editor, TerminalKey.Enter)
    assertEquals(submitted, payload)

  test("unfinished paste commits before navigation and undo restores pre-paste text"):
    val editor = Editor("abc\nx\nabc")
    editor.render(20)
    editor.setCursor(EditorCursor(0, 2))
    key(editor, TerminalKey.Down)
    editor.handleInput(TerminalInput.PasteStart)
    editor.handleInput(TerminalInput.PasteChunk(TerminalInputChunk("z".getBytes("UTF-8"))))
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 2))
    assertEquals(editor.text, "abc\nxz\nabc")
    assert(editor.undo())
    assertEquals(editor.text, "abc\nx\nabc")
    assertEquals(editor.cursor, EditorCursor(1, 1))

  test("history restoration retains paste counters and does not add undo steps"):
    val editor = Editor()
    editor.render(40)
    editor.addToHistory("old")
    val first  = "a" * 1001
    val second = "b" * 1001
    editor.insertAtCursor(first + "\n")
    val draft  = editor.text
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Down)
    key(editor, TerminalKey.End)
    editor.insertAtCursor(second + "\n")
    assert(editor.text.contains("paste #2"))
    editor.expandPasteMarkers()
    assertEquals(editor.text, first + "\n" + second + "\n")
    assert(editor.undo())
    assertEquals(editor.text, draft)
    assert(editor.undo())
    assertEquals(editor.text, draft)
    assertEquals(editor.cursor, EditorCursor(0, 0))
    assert(editor.undo())
    assertEquals(editor.text, "")

  test("mutation and undo reset the preferred column"):
    val editor = Editor("abcdef\nx\nabcdef")
    editor.render(20)
    editor.setCursor(EditorCursor(0, 5))
    key(editor, TerminalKey.Down)
    editor.insertAtCursor("y")
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 2))
    assert(editor.undo())
    key(editor, TerminalKey.Down)
    assertEquals(editor.cursor, EditorCursor(2, 1))

  test("explicit cursor and text replacement abandon a retained history draft"):
    val editor = Editor("draft")
    editor.render(20)
    editor.addToHistory("old")
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    editor.setCursor(EditorCursor(0, 1))
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "old")
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    editor.setText("replacement")
    key(editor, TerminalKey.Down)
    assertEquals(editor.text, "replacement")

  test("history transitions emit exactly one callback per text change"):
    var changes = Vector.empty[String]
    val editor  = Editor("draft", EditorOptions(onChange = value => changes :+= value))
    editor.render(20)
    editor.addToHistory("old")
    editor.setCursor(EditorCursor(0, 0))
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Up)
    key(editor, TerminalKey.Right)
    key(editor, TerminalKey.Down)
    assertEquals(changes, Vector("old", "draft"))

  test("resize to nonpositive widths keeps navigation safe and retained source exact"):
    val editor = Editor("界e\u0301🙂\nabc")
    val text   = editor.text
    for width <- Vector(0, -2, 1, 4) do
      editor.render(width)
      key(editor, TerminalKey.Up)
      key(editor, TerminalKey.Down)
      assertEquals(editor.text, text)
      assert(editor.cursor.line >= 0)
      assert(editor.cursor.column >= 0)
