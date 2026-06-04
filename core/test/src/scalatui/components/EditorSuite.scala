package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.InputResult
import scalatui.editing.EditorCursor
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}

class EditorSuite extends munit.FunSuite:
  test("renders focused fake cursor on character and hides it when unfocused"):
    val editor = Editor("abc")
    editor.setCursor(EditorCursor(0, 1))

    assertEquals(editor.render(10).head, "abc")

    editor.focused = true
    assertEquals(editor.render(10).head, "a\u001b[7mb\u001b[27mc")

  test("renders inverse space at line end and keeps output within width"):
    val editor = Editor("abcd")
    editor.focused = true

    val lines = editor.render(2)

    assertEquals(lines.map(Ansi.strip), Vector("ab", "cd"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 2), lines.toString)

    val roomy = Editor("ab")
    roomy.focused = true
    assertEquals(roomy.render(5).head, "ab\u001b[7m \u001b[27m")

  test("inserts printable input and multiline paste via editor buffer"):
    var changed = Vector.empty[String]
    val editor  = Editor(options = EditorOptions(onChange = text => changed :+= text))

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.handleInputResult(TerminalInput.Paste("界\ne\u0301")), InputResult.Render)

    assertEquals(editor.text, "a界\ne\u0301")
    assertEquals(editor.lines, Vector("a界", "e\u0301"))
    assertEquals(changed.lastOption, Some("a界\ne\u0301"))

  test("handles deletion keys and readline-style movement"):
    val editor = Editor("hello world")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("w"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "hello ")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("a"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("X")))
    assertEquals(editor.text, "Xhello ")

    editor.handleInputResult(TerminalInput.Key(TerminalKey.End))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Backspace, KeyModifiers(ctrl = true)))
    assertEquals(editor.text, "")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("k"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "")

  test("moves cursor with arrows home and end"):
    val editor = Editor("ab\ncd")

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Home))
    assertEquals(editor.cursor, EditorCursor(1, 0))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(editor.cursor, EditorCursor(0, 0))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Right))
    assertEquals(editor.cursor, EditorCursor(0, 1))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Down))
    assertEquals(editor.cursor, EditorCursor(1, 1))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.End))
    assertEquals(editor.cursor, EditorCursor(1, 2))

  test("submit-on-enter mode submits plain enter and inserts newline on shift enter"):
    var submitted = ""
    val editor    = Editor(options = EditorOptions(onSubmit = text => submitted = text))

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter, KeyModifiers(shift = true))),
      InputResult.Render
    )
    assertEquals(editor.text, "\n")

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)),
      InputResult.NoRender
    )
    assertEquals(submitted, "\n")

  test("newline-on-enter mode inserts newline and submits on cmd enter"):
    var submitted = ""
    val editor    = Editor(
      "hi",
      EditorOptions(
        enterBehavior = EditorEnterBehavior.NewlineOnEnter(),
        onSubmit = text => submitted = text
      )
    )

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(editor.text, "hi\n")

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter, KeyModifiers(superKey = true))),
      InputResult.NoRender
    )
    assertEquals(submitted, "hi\n")

  test("reports ignored and no-render handled inputs accurately"):
    val editor = Editor()

    assertEquals(editor.handleInputResult(TerminalInput.Raw("?")), InputResult.Ignored)
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Left)),
      InputResult.NoRender
    )
