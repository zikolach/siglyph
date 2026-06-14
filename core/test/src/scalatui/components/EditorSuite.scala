package scalatui.components

import scalatui.ansi.Ansi
import scalatui.autocomplete.*
import scalatui.core.{CursorMarker, InputResult, OverlayOptions, OverlaySize, TUI}
import scalatui.editing.EditorCursor
import scalatui.terminal.{KeyDescriptor, KeybindingManager, KeyModifiers, TerminalInput, TerminalKey, VirtualTerminal}

class EditorSuite extends munit.FunSuite:
  test("renders focused fake cursor on character and hides it when unfocused"):
    val editor = Editor("abc")
    editor.setCursor(EditorCursor(0, 1))

    assertEquals(editor.render(10).head, "abc")

    editor.focused = true
    assertEquals(editor.render(10).head, s"a${CursorMarker.Sequence}\u001b[7mb\u001b[27mc")

  test("renders inverse space at line end and keeps output within width"):
    val editor = Editor("abcd")
    editor.focused = true

    val lines = editor.render(2)

    assertEquals(lines.map(Ansi.strip), Vector("ab", "cd"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 2), lines.toString)

    val roomy = Editor("ab")
    roomy.focused = true
    assertEquals(roomy.render(5).head, s"ab${CursorMarker.Sequence}\u001b[7m \u001b[27m")

  test("editor suppresses cursor marker while autocomplete owns input"):
    val editor = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(SlashCommandAutocompleteProvider(Vector(SlashCommand("help")))),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )
    editor.focused = true

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)

    val rendered = editor.render(20).head
    assert(!rendered.contains(CursorMarker.Sequence), rendered)

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

  test("large paste renders compact marker and submits expanded text"):
    val pasted    = (1 to 11).map(i => s"line$i").mkString("\n")
    var submitted = ""
    val editor    = Editor("ab", EditorOptions(onSubmit = text => submitted = text))
    editor.setCursor(EditorCursor(0, 1))
    editor.focused = true

    assertEquals(editor.handleInputResult(TerminalInput.Paste(pasted)), InputResult.Render)
    assertEquals(editor.text, "a[paste #1 +11 lines]b")
    assert(editor.render(80).head.contains("[paste #1 +11 lines]"))

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(submitted, s"a${pasted}b")

    editor.expandPasteMarkers()
    assertEquals(editor.text, s"a${pasted}b")

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
            "tui.input.submit" -> Vector(KeyDescriptor(TerminalKey.Character("x"), KeyModifiers(ctrl = true)))
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
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(ctrl = true))),
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
            "tui.editor.cursorLeft" -> Vector(KeyDescriptor(TerminalKey.Character("z"), KeyModifiers(alt = true))),
            "tui.editor.deleteCharBackward" -> Vector(KeyDescriptor(TerminalKey.Character("x"), KeyModifiers(ctrl = true))),
            "tui.input.newLine" -> Vector(KeyDescriptor(TerminalKey.Character("n"), KeyModifiers(ctrl = true))),
            "tui.input.submit" -> Vector(KeyDescriptor(TerminalKey.Character("s"), KeyModifiers(ctrl = true)))
          )
        ),
        onSubmit = text => submitted = text
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(alt = true)))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(ctrl = true)))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("n"), KeyModifiers(ctrl = true)))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("s"), KeyModifiers(ctrl = true)))

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
            "tui.select.down" -> Vector(KeyDescriptor(TerminalKey.Character("j"), KeyModifiers(alt = true)))
          )
        ),
        onSubmit = text => submitted = text
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("/")))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("j"), KeyModifiers(alt = true)))
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

  test("page up and page down preserve wrapped visual columns"):
    val editor = Editor("abcdefghijklmnopqrst")
    editor.render(1)
    editor.setCursor(EditorCursor(0, 10))
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.PageUp)), InputResult.Render)
    assertEquals(editor.cursor, EditorCursor(0, 5))
    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.PageDown)), InputResult.Render)
    assertEquals(editor.cursor, EditorCursor(0, 10))

  test("jump keybinds jump to typed target characters"):
    val editor = Editor("abracadabra")
    editor.render(1)
    editor.setCursor(EditorCursor(0, 0))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("]"), KeyModifiers(ctrl = true))),
      InputResult.NoRender
    )
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 2))

    editor.setCursor(EditorCursor(0, 3))
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("]"), KeyModifiers(ctrl = true, alt = true))),
      InputResult.NoRender
    )
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(editor.cursor, EditorCursor(0, 2))

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

    assertEquals(editor.handleInputResult(TerminalInput.Raw("?")), InputResult.Ignored)
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

  test("editor autocomplete ignores stale async suggestions and cancels pending handles"):
    final class ManualProvider extends AutocompleteProvider:
      var callback  = Option.empty[AutocompleteCallback]
      var cancelled = 0
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle =
        this.callback = Some(callback)
        () => cancelled += 1

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

    val provider = ManualProvider()
    val editor   = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )

    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    provider.callback.foreach(_.complete(Some(AutocompleteSuggestions(
      Vector(AutocompleteItem("help", "help")),
      "/"
    ))))

    assertEquals(provider.cancelled, 1)
    assertEquals(editor.text, "/x")

  test("editor autocomplete closes empty suggestions and provider replacement cancels request"):
    final class EmptyProvider extends AutocompleteProvider:
      var cancelled = 0
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle =
        callback.complete(None)
        () => cancelled += 1

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

    val provider = EmptyProvider()
    val editor   = Editor("/", EditorOptions(autocompleteProvider = Some(provider)))

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    editor.setAutocompleteProvider(None)

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

  private def helpProvider: AutocompleteProvider =
    SlashCommandAutocompleteProvider(Vector(SlashCommand("help", Some("Show help"))))

  private def visibleFrameLines(output: String): Vector[String] =
    val lines = Ansi.strip(output).replace(
      "\r\n",
      "\n"
    ).replace('\r', '\n').split("\n", -1).toVector.map(_.trim)
    lines.dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse
