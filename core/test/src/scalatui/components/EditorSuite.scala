package scalatui.components

import scalatui.TestInputStreams

import scalatui.ansi.Ansi
import scalatui.autocomplete.*
import scalatui.core.{CursorPlacement, InputResult, OverlayOptions, OverlaySize, TUI, TUIOptions}
import scalatui.syntax.Equality.*
import scalatui.editing.EditorCursor
import scalatui.terminal.{
  KeyDescriptor,
  KeybindingManager,
  KeyModifiers,
  MouseAction,
  MouseInputContext,
  MouseWheelDirection,
  TerminalInput,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalKey,
  VirtualTerminal
}

class EditorSuite extends munit.FunSuite:
  test("renders focused fake cursor on character and hides it when unfocused"):
    val editor = Editor("abc")
    editor.setCursor(EditorCursor(0, 1))

    assertEquals(editor.render(10), scalatui.core.ComponentRender.text("abc"))

    editor.focused = true
    val focused = editor.render(10)
    assertEquals(focused.lines.head, "a\u001b[7mb\u001b[27mc")
    assertEquals(focused.cursorPlacements, Vector(CursorPlacement(0, 1)))

  test("omits oversized cursor clusters without mutating editor text"):
    val clusters = Vector("界", "\u1100\u1161\u11a8", "\u0915\u094d\u0915", "👩‍💻", "🇦🇹")

    clusters.foreach { cluster =>
      Vector(false, true).foreach { focused =>
        val editor   = Editor(cluster)
        editor.setCursor(EditorCursor(0, 0))
        editor.focused = focused
        val rendered = editor.render(1)

        assert(rendered.lines.forall(Ansi.visibleWidth(_) <= 1), rendered.toString)
        assertEquals(rendered.lines.map(Ansi.strip), Vector(""), cluster)
        assert(!rendered.lines.exists(_.contains("�")), rendered.toString)
        assertEquals(editor.text, cluster)
        if focused then
          assertEquals(rendered.cursorPlacements, Vector(CursorPlacement(0, 0)), cluster)
        else assertEquals(rendered.cursorPlacements, Vector.empty, cluster)
      }
    }

  test("preserves normal-width fake cursor rendering for complete clusters"):
    val cluster = "👩‍💻"
    val editor  = Editor(cluster)
    editor.setCursor(EditorCursor(0, 0))
    editor.focused = true

    val rendered = editor.render(2)
    assertEquals(Ansi.strip(rendered.lines.head), cluster)
    assertEquals(rendered.lines.head, s"\u001b[7m$cluster\u001b[27m")
    assertEquals(rendered.cursorPlacements, Vector(CursorPlacement(0, 0)))
    assertEquals(editor.text, cluster)

  test("renders inverse space at line end and keeps output within width"):

    val editor = Editor("abcd")
    editor.focused = true

    val rendered = editor.render(2)

    assertEquals(rendered.lines.map(Ansi.strip), Vector("ab", "cd"))
    assert(rendered.lines.forall(line => Ansi.visibleWidth(line) <= 2), rendered.toString)
    assertEquals(rendered.cursorPlacements, Vector.empty)

    val roomy       = Editor("ab")
    roomy.focused = true
    val roomyRender = roomy.render(5)
    assertEquals(roomyRender.lines.head, "ab\u001b[7m \u001b[27m")
    assertEquals(roomyRender.cursorPlacements, Vector(CursorPlacement(0, 2)))

  test("editor suppresses cursor metadata while autocomplete owns input"):
    val editor = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(SlashCommandAutocompleteProvider(Vector(SlashCommand("help")))),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )
    editor.focused = true

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)

    val rendered = editor.render(20)
    assertEquals(rendered.cursorPlacements, Vector.empty)

  test("pasted former cursor APC and ESC or C1 string candidates stay inert"):
    val formerApc  = "\u001b_" + "pi" + ":c\u001b\\"
    val candidates = Vector(
      formerApc,
      "\u001b_payload",
      "\u009fpayload\u009c",
      "\u009fpayload"
    )

    candidates.foreach { candidate =>
      val editor   = Editor()
      TestInputStreams.paste(candidate + "x").foreach(editor.handleInputResult)
      val expected = Ansi.sanitize(candidate + "x")

      val unfocused = editor.render(200)
      assertEquals(Ansi.strip(unfocused.lines.head), expected, candidate)
      assertEquals(unfocused.cursorPlacements, Vector.empty, candidate)

      editor.focused = true
      val focused = editor.render(200)
      assertEquals(Ansi.strip(focused.lines.head), expected + " ", candidate)
      assertEquals(
        focused.cursorPlacements,
        Vector(CursorPlacement(0, Ansi.visibleWidth(expected))),
        candidate
      )
    }

  test("inserts printable input and multiline paste via editor buffer"):
    var changed = Vector.empty[String]
    val editor  = Editor(options = EditorOptions(onChange = text => changed :+= text))

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assert(TestInputStreams.paste("界\ne\u0301").exists(editor.handleInputResult(_) match
      case InputResult.Handled(true) => true
      case _                         => false))

    assertEquals(editor.text, "a界\ne\u0301")
    assertEquals(editor.lines, Vector("a界", "e\u0301"))
    assertEquals(changed.lastOption, Some("a界\ne\u0301"))

  test("large paste renders compact marker and submits expanded text"):
    val pasted    = (1 to 11).map(i => s"line$i").mkString("\n")
    var submitted = ""
    val editor    = Editor("ab", EditorOptions(onSubmit = text => submitted = text))
    editor.setCursor(EditorCursor(0, 1))
    editor.focused = true

    assert(TestInputStreams.paste(pasted).exists(editor.handleInputResult(_) match
      case InputResult.Handled(true) => true
      case _                         => false))
    assertEquals(editor.text, "a[paste #1 +11 lines]b")
    assert(editor.render(80).lines.head.contains("[paste #1 +11 lines]"))

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(submitted, s"a${pasted}b")

    editor.expandPasteMarkers()
    assertEquals(editor.text, s"a${pasted}b")

  test("real bracketed paste bytes commit one normalized Unicode edit"):
    val pasted  = "A\r\ne\u0301\r👨‍👩‍👧‍👦\n🇦🇹"
    var changed = Vector.empty[String]
    val editor  = Editor("ab", EditorOptions(onChange = value => changed :+= value))
    editor.setCursor(EditorCursor(0, 1))

    val results = feedTerminalBytes(editor, bracketedPasteBytes(pasted), chunkSize = 1)

    assertEquals(editor.text, "aA\ne\u0301\n👨‍👩‍👧‍👦\n🇦🇹b")
    assertEquals(editor.cursor, EditorCursor(3, 1))
    assertEquals(changed, Vector("aA\ne\u0301\n👨‍👩‍👧‍👦\n🇦🇹b"))
    assertEquals(results.count { case InputResult.Render => true; case _ => false }, 1)
    assertEquals(editor.undo(), true)
    assertEquals(editor.text, "ab")
    assertEquals(editor.undo(), false)

  test("streamed paste applies aggregate line and grapheme marker thresholds"):
    val elevenLines = (1 to 11).map(index => s"line$index").mkString("\r\n")
    val lineEditor  = Editor()
    feedTerminalBytes(lineEditor, bracketedPasteBytes(elevenLines), chunkSize = 3)
    assertEquals(lineEditor.text, "[paste #1 +11 lines]")

    val graphemes      = "x" * 1001
    val graphemeEditor = Editor()
    feedTerminalBytes(graphemeEditor, bracketedPasteBytes(graphemes), chunkSize = 7)
    assertEquals(graphemeEditor.text, "[paste #1 1001 chars]")

  test("streamed large paste submission and expansion recover normalized content"):
    val pasted    = (1 to 11).map(index => s"line$index").mkString("\r\n")
    val expected  = pasted.replace("\r\n", "\n")
    var submitted = ""
    val editor    = Editor(options = EditorOptions(onSubmit = value => submitted = value))

    feedTerminalBytes(editor, bracketedPasteBytes(pasted), chunkSize = 2)
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(submitted, expected)
    editor.expandPasteMarkers()
    assertEquals(editor.text, expected)

  test("empty paste is unchanged and interrupted paste commits before later input"):
    var changes = 0
    val editor  = Editor("base", EditorOptions(onChange = _ => changes += 1))
    val empty   = feedTerminalBytes(editor, bracketedPasteBytes(""), chunkSize = 1)
    assertEquals(editor.text, "base")
    assertEquals(changes, 0)
    assertEquals(empty.count { case InputResult.Render => true; case _ => false }, 0)
    assertEquals(editor.undo(), false)

    editor.handleInputResult(TerminalInput.PasteStart)
    editor.handleInputResult(
      TerminalInput.PasteChunk(TerminalInputChunk("x\r\ny".getBytes("UTF-8")))
    )
    assertEquals(editor.text, "base")
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("!"))),
      InputResult.Render
    )
    assertEquals(editor.text, "basex\ny!")
    assertEquals(changes, 2)
    assertEquals(editor.undo(), true)
    assertEquals(editor.text, "basex\ny")
    assertEquals(editor.undo(), true)
    assertEquals(editor.text, "base")

  test("streamed paste renders and refreshes active autocomplete once at completion"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)

    val terminal = VirtualTerminal(40, 8)
    var renders  = 0
    val wrapper  = new scalatui.core.Component:
      override def render(width: Int): scalatui.core.ComponentRender    =
        renders += 1
        editor.render(width)
      override def handleInputResult(input: TerminalInput): InputResult =
        editor.handleInputResult(input)
    val tui      = TUI(terminal)
    tui.addChild(wrapper)
    tui.setFocus(wrapper)
    tui.start()
    renders = 0

    val parser = TerminalInputBuffer()
    bracketedPasteBytes("abc").foreach { byte =>
      parser.process(TerminalInputChunk(Array(byte))).foreach(terminal.sendInput)
    }

    assertEquals(editor.text, "/abc")
    assertEquals(renders, 1)
    assertEquals(scheduled.length, 1)
    tui.stop()

  test("insertAtCursor inserts text normalizes newlines and creates one undo step"):
    var changed = Vector.empty[String]
    val editor  = Editor("ab", EditorOptions(onChange = text => changed :+= text))
    editor.setCursor(EditorCursor(0, 1))

    editor.insertAtCursor("x\r\ny\rz")

    assertEquals(editor.text, "ax\ny\nzb")
    assertEquals(editor.cursor, EditorCursor(2, 1))
    assertEquals(changed, Vector("ax\ny\nzb"))

    assertEquals(editor.undo(), true)
    assertEquals(editor.text, "ab")

  test("typed programmatic and streamed insertion cursor follows final neighbor joins"):
    val cases = Vector(
      ("AB", 1, "\u0301", "B"),
      ("AB", 1, "\u0600", "A"),
      ("\u1100\u11a8", 1, "\u1161", ""),
      ("\u0915\u0915", 1, "\u094d", ""),
      ("👩💻", 1, "\u200d", ""),
      ("🇹", 0, "🇦", "")
    )

    cases.foreach { case (initial, column, inserted, afterBackspace) =>
      (0 to 2).foreach { mode =>
        val editor = Editor(initial)
        editor.setCursor(EditorCursor(0, column))
        mode match
          case 0 => editor.handleInputResult(TerminalInput.Key(TerminalKey.Character(inserted)))
          case 1 => editor.insertAtCursor(inserted)
          case _ => TestInputStreams.paste(inserted).foreach(editor.handleInputResult)
        editor.handleInputResult(TerminalInput.Key(TerminalKey.Backspace))
        assertEquals(editor.text, afterBackspace, s"$inserted mode=$mode")
      }
    }

  private def wheel(direction: MouseWheelDirection): MouseInputContext =
    MouseInputContext(
      TerminalInput.Mouse(MouseAction.Wheel(direction), row = 0, col = 0),
      boundsRow = 0,
      boundsCol = 0,
      boundsWidth = 20,
      boundsHeight = 5,
      localRow = 0,
      localCol = 0
    )

  test("editor wheel uses page movement without mutating text"):
    val editor = Editor((1 to 10).map(i => s"line$i").mkString("\n"))
    editor.render(20)
    editor.setCursor(EditorCursor(0, 0))
    val before = editor.text

    assertEquals(editor.handleMouse(wheel(MouseWheelDirection.Down)), InputResult.Render)
    assertEquals(editor.cursor.line, 5)
    assertEquals(editor.text, before)
    assertEquals(editor.handleMouse(wheel(MouseWheelDirection.Up)), InputResult.Render)
    assertEquals(editor.cursor.line, 0)
    assertEquals(editor.handleMouse(wheel(MouseWheelDirection.Up)), InputResult.NoRender)
    assertEquals(editor.text, before)

  test("insertAtCursor requests render when attached and preserves large paste markers"):
    val terminal = VirtualTerminal(80, 8)
    val pasted   = (1 to 11).map(i => s"line$i").mkString("\n")
    val editor   = Editor("ab")
    editor.setCursor(EditorCursor(0, 1))
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.start()
    terminal.clearWrites()

    editor.insertAtCursor(pasted)
    tui.flushRender()

    assertEquals(editor.text, "a[paste #1 +11 lines]b")
    assert(terminal.output.contains("[paste #1 +11 lines]"), terminal.output)

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

  test("editor supports pi-tui undo-only kill-ring yank and yank-pop"):
    val editor = Editor("hello world again")
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("w"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("w"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "hello ")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("y"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "hello world again")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("-"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "hello ")

    val cycle = Editor("one two")
    cycle.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("w"),
      KeyModifiers(ctrl = true)
    ))
    cycle.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("a"),
      KeyModifiers(ctrl = true)
    ))
    cycle.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("k"),
      KeyModifiers(ctrl = true)
    ))
    cycle.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("y"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(cycle.text, "one ")
    cycle.handleInputResult(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(alt = true)))
    assertEquals(cycle.text, "two")

  test("editor supports pi-tui word movement and forward word deletion bindings"):
    val editor = Editor("hello, 世界 again")
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("a"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    assertEquals(editor.cursor, EditorCursor(0, 5))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    assertEquals(editor.cursor, EditorCursor(0, 6))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("d"),
      KeyModifiers(alt = true)
    ))
    assertEquals(editor.text, "hello, again")

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

  test("editor submit keybinding is configurable"):
    var submitted = ""
    val editor    = Editor(
      options = EditorOptions(
        enterBehavior = EditorEnterBehavior.NewlineOnEnter(),
        keybindings = KeybindingManager.fromRawBindings(
          Map(
            "tui.input.submit" -> Vector(KeyDescriptor(
              TerminalKey.Character("x"),
              KeyModifiers(ctrl = true)
            ))
          )
        ),
        onSubmit = text => submitted = text
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a")))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)),
      InputResult.Render
    )
    assertEquals(editor.text, "a\n")

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(
        TerminalKey.Character("x"),
        KeyModifiers(ctrl = true)
      )),
      InputResult.Render
    )
    assertEquals(submitted, "a\n")

  test("editor supports custom movement, deletion, newline, and submit bindings"):
    var submitted = ""
    val editor    = Editor(
      "abc",
      EditorOptions(
        keybindings = KeybindingManager.fromRawBindings(
          Map(
            "tui.editor.cursorLeft"         -> Vector(KeyDescriptor(
              TerminalKey.Character("z"),
              KeyModifiers(alt = true)
            )),
            "tui.editor.deleteCharBackward" -> Vector(KeyDescriptor(
              TerminalKey.Character("x"),
              KeyModifiers(ctrl = true)
            )),
            "tui.input.newLine"             -> Vector(KeyDescriptor(
              TerminalKey.Character("n"),
              KeyModifiers(ctrl = true)
            )),
            "tui.input.submit"              -> Vector(KeyDescriptor(
              TerminalKey.Character("s"),
              KeyModifiers(ctrl = true)
            ))
          )
        ),
        onSubmit = text => submitted = text
      )
    )

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("z"),
      KeyModifiers(alt = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("x"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("n"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("s"),
      KeyModifiers(ctrl = true)
    ))

    assertEquals(editor.text, "a\nc")
    assertEquals(submitted, "a\nc")

  test("editor custom autocomplete select bindings and slash confirm semantics"):
    var submitted = ""
    val editor    = Editor(
      options = EditorOptions(
        autocompleteProvider = Some(
          SlashCommandAutocompleteProvider(
            Vector(
              SlashCommand("help"),
              SlashCommand("quit")
            )
          )
        ),
        keybindings = KeybindingManager.fromRawBindings(
          Map(
            "tui.select.down" -> Vector(KeyDescriptor(
              TerminalKey.Character("j"),
              KeyModifiers(alt = true)
            ))
          )
        ),
        onSubmit = text => submitted = text
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("/")))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("j"),
      KeyModifiers(alt = true)
    ))
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(editor.text, "/quit ")
    assertEquals(submitted, "/quit ")

  test("autocomplete overlays consume selection commands before history while active"):
    val alwaysProvider =
      AutocompleteProvider.sync(_ =>
        Some(AutocompleteSuggestions(Vector(AutocompleteItem("help", "help")), "/"))
      )

    val editor = Editor(options = EditorOptions(autocompleteProvider = Some(alwaysProvider)))
    editor.addToHistory("first")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.Render)
    assertEquals(editor.text, "")

  test("supports command history browsing and restoration"):
    val editor = Editor()
    editor.addToHistory("second")
    editor.addToHistory("first")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.Render)
    assertEquals(editor.text, "first")

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    assertEquals(editor.text, "xfirst")
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Down))
    assertEquals(editor.text, "xfirst")

    val clean = Editor()
    clean.addToHistory("second")
    clean.addToHistory("first")
    assertEquals(clean.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.Render)
    assertEquals(clean.text, "first")
    assertEquals(clean.handleInputResult(TerminalInput.Key(TerminalKey.Down)), InputResult.Render)
    assertEquals(clean.text, "")

  test("history ignores empty and consecutive duplicate entries"):
    val editor = Editor()
    editor.addToHistory("")
    editor.addToHistory("   ")
    editor.addToHistory("same")
    editor.addToHistory("same")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.Render)
    assertEquals(editor.text, "same")
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.NoRender)
    assertEquals(editor.text, "same")

  test("history keeps non-consecutive duplicates and caps at one hundred entries"):
    val duplicate = Editor()
    duplicate.addToHistory("first")
    duplicate.addToHistory("second")
    duplicate.addToHistory("first")

    duplicate.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(duplicate.text, "first")
    duplicate.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(duplicate.text, "second")
    duplicate.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(duplicate.text, "first")

    val capped = Editor()
    (0 until 105).foreach(index => capped.addToHistory(s"prompt $index"))
    (0 until 100).foreach(_ => capped.handleInputResult(TerminalInput.Key(TerminalKey.Up)))

    assertEquals(capped.text, "prompt 5")
    assertEquals(capped.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.NoRender)
    assertEquals(capped.text, "prompt 5")

  test("setText exits history browsing"):
    val editor = Editor()
    editor.addToHistory("first")
    editor.addToHistory("second")

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(editor.text, "second")

    editor.setText("")
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Up))
    assertEquals(editor.text, "second")

  test("large paste marker is edited as one logical unit"):
    val pasted = (1 to 11).map(index => s"line$index").mkString("\n")
    val editor = Editor("ab")
    editor.setCursor(EditorCursor(0, 1))
    TestInputStreams.paste(pasted).foreach(editor.handleInputResult)

    assert(editor.text.contains("[paste #1 +11 lines]"), editor.text)
    assertEquals(editor.cursor, EditorCursor(0, 2))

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Backspace))
    assertEquals(editor.text, "ab")
    assertEquals(editor.cursor, EditorCursor(0, 1))

  test("page up and page down preserve wrapped visual columns"):
    val editor = Editor("abcdefghijklmnopqrst")
    editor.render(1).lines
    editor.setCursor(EditorCursor(0, 10))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.PageUp)),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 5))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.PageDown)),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 10))

  test("jump keybinds jump to typed target characters"):
    val editor = Editor("abracadabra")
    editor.render(1).lines
    editor.setCursor(EditorCursor(0, 0))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(
        TerminalKey.Character("]"),
        KeyModifiers(ctrl = true)
      )),
      InputResult.NoRender
    )
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 2))

    editor.setCursor(EditorCursor(0, 3))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(
        TerminalKey.Character("]"),
        KeyModifiers(ctrl = true, alt = true)
      )),
      InputResult.NoRender
    )
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 2))

  test("submit clears undo history"):
    var submitted = ""
    val editor    = Editor(options = EditorOptions(onSubmit = text => submitted = text))

    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)

    assertEquals(submitted, "a")
    assertEquals(editor.undo(), false)
    assertEquals(editor.text, "a")

  test("submit resets yank-pop chain"):
    val editor = Editor("one two")

    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("w"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("a"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("k"),
      KeyModifiers(ctrl = true)
    ))
    editor.handleInputResult(TerminalInput.Key(
      TerminalKey.Character("y"),
      KeyModifiers(ctrl = true)
    ))
    assertEquals(editor.text, "one ")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(
        TerminalKey.Character("y"),
        KeyModifiers(alt = true)
      )),
      InputResult.NoRender
    )
    assertEquals(editor.text, "one ")

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
      InputResult.Render
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
      InputResult.Render
    )
    assertEquals(submitted, "hi\n")

  test("reports ignored and no-render handled inputs accurately"):
    val editor = Editor()

    TestInputStreams.raw("?").foreach(input =>
      assertEquals(editor.handleInputResult(input), InputResult.Ignored)
    )
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Left)),
      InputResult.NoRender
    )

  test("editor slash command autocomplete renders overlay and applies completion"):
    val provider = SlashCommandAutocompleteProvider(Vector(
      SlashCommand("help", Some("Show help")),
      SlashCommand("quit", Some("Exit"))
    ))
    val terminal = VirtualTerminal(40, 8)
    val editor   = Editor(options = EditorOptions(autocompleteProvider = Some(provider)))
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))

    assert(Ansi.strip(terminal.output).contains("help"), terminal.output)
    assert(Ansi.strip(terminal.output).contains("quit"), terminal.output)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(editor.text, "/quit ")

  test("autocomplete overlay wheel changes selected suggestion without changing editor text"):
    val provider = SlashCommandAutocompleteProvider(Vector(
      SlashCommand("help", Some("Show help")),
      SlashCommand("quit", Some("Exit"))
    ))
    val terminal = VirtualTerminal(40, 8)
    val editor   = Editor(options = EditorOptions(autocompleteProvider = Some(provider)))
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    val before = editor.text
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Wheel(MouseWheelDirection.Down),
      row = 1,
      col = 0
    ))

    assertEquals(editor.text, before)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(editor.text, "/quit ")

  test("forced autocomplete auto-applies a single suggestion when enabled"):
    val provider = AutocompleteProvider.sync(_ =>
      Some(AutocompleteSuggestions(Vector(AutocompleteItem("only", "only")), ""))
    )
    val editor   = Editor(
      options = EditorOptions(
        autocompleteProvider = Some(provider),
        autoApplySingleForcedCompletion = true
      )
    )

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)

    assertEquals(editor.text, "only")

  test("forced autocomplete keeps explicit selection by default"):
    val provider = AutocompleteProvider.sync(_ =>
      Some(AutocompleteSuggestions(Vector(AutocompleteItem("only", "only")), ""))
    )
    val editor   = Editor(options = EditorOptions(autocompleteProvider = Some(provider)))

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    assertEquals(editor.text, "")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(editor.text, "only")

  test("forced autocomplete with auto-apply shows selection UI for multiple suggestions"):
    val provider = AutocompleteProvider.sync(_ =>
      Some(AutocompleteSuggestions(
        Vector(AutocompleteItem("one", "one"), AutocompleteItem("two", "two")),
        ""
      ))
    )
    val editor   = Editor(
      options = EditorOptions(
        autocompleteProvider = Some(provider),
        autoApplySingleForcedCompletion = true
      )
    )

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    assertEquals(editor.text, "")

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Down)), InputResult.Render)
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(editor.text, "two")

  test("forced autocomplete empty and stale single suggestions do not mutate editor"):
    var events   = Vector.empty[AutocompleteProbeEvent]
    val provider = recordingAutocompleteProvider(event => events :+= event)
    val empty    = AutocompleteProvider.sync(_ => None)
    val noMatch  = Editor(
      options = EditorOptions(
        autocompleteProvider = Some(empty),
        autoApplySingleForcedCompletion = true
      )
    )

    assertEquals(noMatch.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    assertEquals(noMatch.text, "")

    val editor = Editor(
      options = EditorOptions(
        autocompleteProvider = Some(provider),
        autoApplySingleForcedCompletion = true
      )
    )
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    requested(events).head.callback.complete(Some(autocomplete("late", "")))

    assertEquals(editor.text, "x")

  test("editor autocomplete debounces rapid typing and cancels stale in-flight work"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val debouncer = recordingDebouncer(action => scheduled :+= action)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = debouncer
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("b")))

    assertEquals(requested(events).map(_.request.lines.mkString("\n")), Vector("/"))
    assertEquals(cancelled(events), Vector(0))
    assertEquals(scheduled.length, 2)

    requested(events).head.callback.complete(Some(autocomplete("/old", "/")))
    assertEquals(editor.text, "/ab")

    scheduled.head()
    assertEquals(requested(events).map(_.request.lines.mkString("\n")), Vector("/"))

    scheduled.last()
    assertEquals(requested(events).map(_.request.lines.mkString("\n")), Vector("/", "/ab"))

    requested(events)(1).callback.complete(Some(autocomplete("/abacus", "/ab")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    assertEquals(editor.text, "/abacus")

  test("editor explicit tab bypasses pending debounced refresh for current text"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))

    assertEquals(requested(events).map(_.request.lines.mkString("\n")), Vector("/", "/x"))
    assertEquals(requested(events).last.request.force, true)
    assertEquals(cancelled(events), Vector(0))
    assertEquals(scheduled.length, 1)

    scheduled.last()
    assertEquals(requested(events).map(_.request.lines.mkString("\n")), Vector("/", "/x"))

  test("editor autocomplete ignores stale failure callbacks after newer requests"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    scheduled.last()
    requested(events)(1).callback.complete(Some(autocomplete("/xray", "/x")))
    requested(events).head.callback.fail(RuntimeException("late"))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))

    assertEquals(cancelled(events), Vector(0))
    assertEquals(editor.text, "/xray")

  test("editor autocomplete keeps visible suggestions while refresh is pending"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val terminal  = VirtualTerminal(40, 8)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )
    val tui       = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Tab))
    requested(events).head.callback.complete(Some(autocomplete("/help", "/")))
    tui.flushRender()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("h")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    assert(visibleFrameLines(terminal.output).exists(_.contains("help")), terminal.output)

    terminal.clearWrites()
    scheduled.last()
    requested(events)(1).callback.complete(Some(autocomplete("/hello", "/h")))
    tui.flushRender()

    val refreshed = visibleFrameLines(terminal.output)
    assert(refreshed.exists(_.contains("hello")), terminal.output)
    assert(!refreshed.exists(_.contains("help")), terminal.output)

  test("editor autocomplete does not apply visible stale suggestions during refresh"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    requested(events).head.callback.complete(Some(autocomplete("/help", "/")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("h")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))

    assertEquals(editor.text, "/h")
    assertEquals(scheduled.length, 1)
    val requestCountAfterTab = requested(events).length
    scheduled.last()
    assertEquals(requested(events).length, requestCountAfterTab)

  test("editor autocomplete closes visible suggestions when refresh has no replacement"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val terminal  = VirtualTerminal(40, 8)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )
    val tui       = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Tab))
    requested(events).head.callback.complete(Some(autocomplete("/help", "/")))
    tui.flushRender()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("z")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    assert(visibleFrameLines(terminal.output).exists(_.contains("help")), terminal.output)

    terminal.clearWrites()
    scheduled.last()
    requested(events)(1).callback.complete(None)
    tui.flushRender()

    assert(!visibleFrameLines(terminal.output).exists(_.contains("help")), terminal.output)

  test("editor autocomplete closes initial empty suggestions"):
    var events   = Vector.empty[AutocompleteProbeEvent]
    val provider = recordingAutocompleteProvider(event => events :+= event)
    val terminal = VirtualTerminal(40, 8)
    val editor   = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Tab))
    requested(events).head.callback.complete(None)
    tui.flushRender()

    assert(!visibleFrameLines(terminal.output).exists(_.contains("help")), terminal.output)

  test("editor provider replacement cancels pending autocomplete request"):
    var events   = Vector.empty[AutocompleteProbeEvent]
    val provider = recordingAutocompleteProvider(event => events :+= event)
    val editor   = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    editor.setAutocompleteProvider(None)

    assertEquals(cancelled(events), Vector(0))
    assertEquals(editor.autocompleteProvider, None)

  test("editor provider replacement cancels pending debounced autocomplete refresh"):
    var events    = Vector.empty[AutocompleteProbeEvent]
    var scheduled = Vector.empty[() => Unit]
    val provider  = recordingAutocompleteProvider(event => events :+= event)
    val editor    = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly,
        autocompleteDebouncer = recordingDebouncer(action => scheduled :+= action)
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    editor.setAutocompleteProvider(None)
    val requestsBeforeScheduledAction = requested(events).length
    scheduled.last()

    assertEquals(cancelled(events), Vector(0))
    assertEquals(requested(events).length, requestsBeforeScheduledAction)
    assertEquals(editor.autocompleteProvider, None)

  test("editor default autocomplete placement appears after single-line editor"):
    val terminal = VirtualTerminal(40, 10)
    val editor   = Editor(options = EditorOptions(autocompleteProvider = Some(helpProvider)))
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val lines = visibleFrameLines(terminal.output)
    assertEquals(lines.indexWhere(_.contains("/")), 0)
    assertEquals(lines.indexWhere(_.contains("help")), 1)
    assertEquals(lines.length, 2)

  test("editor adjacent autocomplete placement tracks multiline editor height"):
    val terminal = VirtualTerminal(40, 10)
    val editor   = Editor("first\n/", EditorOptions(autocompleteProvider = Some(helpProvider)))
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Tab))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val lines = visibleFrameLines(terminal.output)
    assertEquals(lines.indexWhere(_.contains("first")), 0)
    assertEquals(lines.indexWhere(_.contains("/")), 1)
    assertEquals(lines.indexWhere(_.contains("help")), 2)

  test("editor custom autocomplete placement override is preserved"):
    val terminal = VirtualTerminal(40, 10)
    val editor   = Editor(options =
      EditorOptions(
        autocompleteProvider = Some(helpProvider),
        autocompletePlacement = EditorAutocompletePlacement.Custom(OverlayOptions(
          width = Some(OverlaySize.Absolute(10)),
          row = Some(OverlaySize.Absolute(5)),
          col = Some(OverlaySize.Absolute(0))
        ))
      )
    )
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val lines = visibleFrameLines(terminal.output)
    assertEquals(lines.indexWhere(_.contains("help")), 5)

  private def bracketedPasteBytes(value: String): Array[Byte] =
    s"\u001b[200~$value\u001b[201~".getBytes(java.nio.charset.StandardCharsets.UTF_8)

  private def feedTerminalBytes(
      editor: Editor,
      bytes: Array[Byte],
      chunkSize: Int
  ): Vector[InputResult] =
    val parser = TerminalInputBuffer()
    bytes.grouped(chunkSize).flatMap { chunk =>
      parser.process(TerminalInputChunk(chunk)).map(editor.handleInputResult)
    }.toVector

  private def autocomplete(value: String, prefix: String): AutocompleteSuggestions =
    AutocompleteSuggestions(Vector(AutocompleteItem(value, value)), prefix)

  private sealed trait AutocompleteProbeEvent derives CanEqual
  private final case class Requested(
      id: Int,
      request: AutocompleteRequest,
      callback: AutocompleteCallback
  ) extends AutocompleteProbeEvent
  private final case class Cancelled(id: Int) extends AutocompleteProbeEvent

  private def recordingAutocompleteProvider(
      record: AutocompleteProbeEvent => Unit
  ): AutocompleteProvider =
    var nextId = 0
    new AutocompleteProvider:
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle =
        val id = nextId
        nextId += 1
        record(Requested(id, request, callback))
        () => record(Cancelled(id))

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

  private def recordingDebouncer(record: (() => Unit) => Unit): EditorAutocompleteDebouncer =
    action =>
      var cancelled = false
      record(() => if !cancelled then action())
      EditorAutocompleteDebouncer.Pending(() => cancelled = true)

  private def requested(events: Vector[AutocompleteProbeEvent]): Vector[Requested] =
    events.collect { case event: Requested => event }

  private def cancelled(events: Vector[AutocompleteProbeEvent]): Vector[Int] =
    events.collect { case Cancelled(id) => id }

  private def helpProvider: AutocompleteProvider =
    SlashCommandAutocompleteProvider(Vector(SlashCommand("help", Some("Show help"))))

  private def visibleFrameLines(output: String): Vector[String] =
    val trustedText = "\u001b\\[[0-?]*[ -/]*[@-~]".r.replaceAllIn(output, "")
    val lines       = trustedText.replace("\r\n", "\n").replace('\r', '\n').split(
      "\n",
      -1
    ).toVector.map(_.trim)
    lines.dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse

  test("editor emits no cursor placement at zero or negative widths"):
    Vector(0, -1, -20).foreach { width =>
      val editor   = Editor("abc")
      editor.focused = true
      val rendered = editor.render(width)

      assertEquals(rendered.cursorPlacements, Vector.empty, width.toString)
      assertEquals(rendered.lines.forall(_.isEmpty), true, width.toString)
      assertEquals(rendered.validate(width), Right(()), width.toString)
      assertEquals(editor.text, "abc", width.toString)
    }

  test("default-padded width-one Box validates a focused impossible-width editor"):
    val editor = Editor("界")
    editor.focused = true
    val box    = Box()
    box.addChild(editor)

    val rendered = box.render(1)
    assertEquals(rendered.lines.map(Ansi.strip), Vector(" "))
    assertEquals(rendered.cursorPlacements, Vector.empty)
    assertEquals(rendered.validate(1), Right(()))
    assertEquals(editor.text, "界")

  test("focused editor keeps positive impossible-width cursor ownership at column zero"):
    val editor = Editor("界")
    editor.focused = true

    val rendered = editor.render(1)
    assertEquals(rendered.lines, Vector(""))
    assertEquals(rendered.cursorPlacements, Vector(CursorPlacement(0, 0)))
    assertEquals(rendered.validate(1), Right(()))

  test("focused cursor never divides supported SGR or OSC 8 metadata"):
    val red        = "\u001b[31m"
    val open       = "\u001b]8;;https://example.com\u001b\\"
    val close      = "\u001b]8;;\u001b\\"
    val value      = "a" + red + open + "b" + close + Ansi.Reset + "c"
    val boundaries = Vector(
      1,
      2,
      scalatui.unicode.Unicode.graphemeClusters("a" + red).length,
      scalatui.unicode.Unicode.graphemeClusters("a" + red + open).length,
      scalatui.unicode.Unicode.graphemeClusters("a" + red + open + "b").length,
      scalatui.unicode.Unicode.graphemeClusters(value).length
    ).distinct

    boundaries.foreach { boundary =>
      val editor   = Editor(value)
      editor.setCursor(EditorCursor(0, boundary))
      editor.focused = true
      val rendered = editor.render(80)
      val line     = rendered.lines.head

      assertEquals(Ansi.strip(line).trim, "abc", boundary.toString)
      assertEquals(line.sliding(red.length).count(_ === red), 1, boundary.toString)
      assertEquals(line.sliding(open.length).count(_ === open), 1, boundary.toString)
      assertEquals(line.sliding(close.length).count(_ === close), 1, boundary.toString)
      assertEquals(rendered.cursorPlacements.length, 1, boundary.toString)
      assertEquals(rendered.validate(80), Right(()), boundary.toString)
    }

  test("focused and unfocused editor emit complete rejected expansions across wraps"):
    val source   = "a\t\u0000\u007f\u0085\u001bPpayload\u001b\\z"
    val expected = Ansi.sanitize(source)

    Vector(false, true).foreach { focused =>
      val editor   = Editor(source)
      editor.setCursor(EditorCursor(0, 0))
      editor.focused = focused
      val rendered = editor.render(4)

      assert(rendered.lines.forall(Ansi.visibleWidth(_) <= 4), rendered.toString)
      assertEquals(Ansi.strip(rendered.lines.mkString), expected, focused.toString)
      if focused then assertEquals(rendered.cursorPlacements, Vector(CursorPlacement(0, 0)))
      else assertEquals(rendered.cursorPlacements, Vector.empty)
      assertEquals(editor.text, source)
    }

  test("editor projected layout keeps unlimited application content exact"):
    val source   = "x".repeat(100000) + "\t"
    val editor   = Editor(source)
    val rendered = editor.render(80)

    assertEquals(Ansi.strip(rendered.lines.mkString), Ansi.sanitize(source))
    assertEquals(editor.text, source)
    assert(rendered.lines.forall(Ansi.visibleWidth(_) <= 80), rendered.lines.length.toString)
