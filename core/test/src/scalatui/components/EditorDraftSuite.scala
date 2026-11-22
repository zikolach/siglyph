package scalatui.components

import scalatui.autocomplete.*
import scalatui.editing.EditorCursor
import scalatui.terminal.{TerminalInput, TerminalInputChunk, TerminalKey}

class EditorDraftSuite extends munit.FunSuite:
  private def chunks(editor: Editor, value: String): Unit =
    value.getBytes("UTF-8").grouped(317).foreach { bytes =>
      editor.handleInput(TerminalInput.PasteChunk(TerminalInputChunk(bytes)))
    }

  private def paste(editor: Editor, value: String): Unit =
    editor.handleInput(TerminalInput.PasteStart)
    chunks(editor, value)
    editor.handleInput(TerminalInput.PasteEnd)

  test("submission projection validates logical length without mutating or submitting"):
    val editor    = Editor("prefix")
    var changes   = Vector.empty[String]
    var submits   = Vector.empty[String]
    editor.onChange = value => changes :+= value
    editor.onSubmit = value => submits :+= value
    paste(editor, "x" * 4100)
    val displayed = editor.text
    val cursor    = editor.cursor
    val before    = changes
    assert(displayed.length < 100)
    assertEquals(editor.submittedText.length, 4106)
    assertEquals(editor.submittedText, "prefix" + "x" * 4100)
    assertEquals(editor.text, displayed)
    assertEquals(editor.cursor, cursor)
    assertEquals(changes, before)
    assertEquals(submits, Vector.empty)
    assert(editor.undo())
    assertEquals(editor.text, "prefix")

  test("opaque drafts retain multiple Unicode pastes and exact cursor across reuse and new pastes"):
    val editor  = Editor("head\n")
    val first   = "界👩‍💻e\u0301" * 1100
    val second  = "row\n" * 12
    paste(editor, first)
    paste(editor, second)
    editor.setCursor(EditorCursor(1, 1))
    val shown   = editor.text
    val logical = editor.submittedText
    val saved   = editor.snapshotDraft()
    editor.setText("other")
    editor.insertAtCursor(" edit")
    editor.restoreDraft(saved)
    assertEquals(editor.text, shown)
    assertEquals(editor.submittedText, logical)
    assertEquals(editor.cursor, EditorCursor(1, 1))
    assert(!editor.undo())
    paste(editor, "z" * 4100)
    assertEquals(editor.submittedText, "head\n" + first + "z" * 4100 + second)
    editor.restoreDraft(saved)
    assertEquals(editor.submittedText, logical)
    val other   = Editor()
    other.restoreDraft(saved)
    assertEquals(other.text, shown)
    assertEquals(other.cursor, EditorCursor(1, 1))
    assertEquals(other.submittedText, logical)

  test("reads exclude but do not consume half-open paste; restore discards pending chunks"):
    val editor = Editor("base")
    editor.handleInput(TerminalInput.PasteStart)
    chunks(editor, "x" * 4100)
    assertEquals(editor.submittedText, "base")
    val saved  = editor.snapshotDraft()
    editor.handleInput(TerminalInput.PasteEnd)
    assertEquals(editor.submittedText, "base" + "x" * 4100)
    editor.handleInput(TerminalInput.PasteStart)
    chunks(editor, "discard")
    editor.restoreDraft(saved)
    chunks(editor, "also discard")
    editor.handleInput(TerminalInput.PasteEnd)
    assertEquals(editor.submittedText, "base")

  test("literal marker text copied into a fresh editor does not carry paste identity"):
    val editor = Editor()
    paste(editor, "x" * 4100)
    val copy   = Editor(editor.text)
    assertEquals(copy.submittedText, editor.text)
    assertEquals(Editor("[paste #42 4100 chars]").submittedText, "[paste #42 4100 chars]")

  test("queued edit save validates before mutation and preserves history roundtrip"):
    val editor    = Editor("draft\n")
    paste(editor, "x" * 4100)
    editor.setCursor(EditorCursor(0, 0))
    val draft     = editor.snapshotDraft()
    val expected  = editor.submittedText
    editor.render(80)
    editor.addToHistory("older")
    editor.handleInput(TerminalInput.Key(TerminalKey.Up))
    assertEquals(editor.text, "older")
    assertEquals(editor.submittedText, "older")
    editor.snapshotDraft()
    editor.handleInput(TerminalInput.Key(TerminalKey.Down))
    assertEquals(editor.submittedText, expected)
    assertEquals(editor.cursor, EditorCursor(0, 0))
    editor.setText("queued edit")
    val candidate = editor.submittedText
    assert(candidate.length < 4100)
    val queued    = editor.snapshotDraft()
    editor.restoreDraft(draft)
    assertEquals(editor.submittedText, expected)
    editor.restoreDraft(queued)
    assertEquals(editor.submittedText, candidate)

  test("invalid restore leaves draft, undo and pending paste untouched"):
    val editor = Editor("base")
    editor.insertAtCursor(" edit")
    editor.handleInput(TerminalInput.PasteStart)
    chunks(editor, " pending")
    val before = editor.text
    val cursor = editor.cursor
    intercept[IllegalArgumentException](editor.restoreDraft(null))
    assertEquals(editor.text, before)
    assertEquals(editor.cursor, cursor)
    editor.handleInput(TerminalInput.PasteEnd)
    assertEquals(editor.submittedText, "base edit pending")
    assert(editor.undo())
    assertEquals(editor.text, "base edit")
    assert(editor.undo())
    assertEquals(editor.text, "base")

  test("restore observes changed payload even when displayed marker text is identical"):
    val first   = Editor()
    val second  = Editor()
    paste(first, "a" * 4100)
    paste(second, "b" * 4100)
    assertEquals(first.text, second.text)
    var changes = Vector.empty[String]
    first.onChange = value => changes :+= value
    first.restoreDraft(second.snapshotDraft())
    assertEquals(first.submittedText, "b" * 4100)
    assertEquals(changes, Vector(second.text))

  test("reads preserve autocomplete work while restore cancels and rejects stale completion"):
    var pending       = Option.empty[AutocompleteCallback]
    var requests      = 0
    var cancellations = 0
    val provider      = new AutocompleteProvider:
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle                                               =
        requests += 1
        pending = Some(callback)
        () => cancellations += 1
      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)
    val editor        = Editor(options =
      EditorOptions(
        autocompleteProvider = Some(provider),
        autoApplySingleForcedCompletion = true
      )
    )
    editor.handleInput(TerminalInput.Key(TerminalKey.Tab))
    val saved         = editor.snapshotDraft()
    assertEquals(editor.submittedText, "")
    assertEquals(requests, 1)
    assertEquals(cancellations, 0)
    intercept[IllegalArgumentException](editor.restoreDraft(null))
    assertEquals(cancellations, 0)
    assertEquals(requests, 1)
    editor.restoreDraft(saved)
    assertEquals(cancellations, 1)
    assertEquals(requests, 1)
    pending.foreach(_.complete(Some(AutocompleteSuggestions(
      Vector(AutocompleteItem("stale", "stale")),
      ""
    ))))
    assertEquals(editor.text, "")

  test("restore callbacks are reentrant, omit cursor-only changes and never submit"):
    val editor  = Editor("a")
    val saved   = editor.snapshotDraft()
    var changes = Vector.empty[String]
    var submits = 0
    editor.onChange = value =>
      assertEquals(editor.text, value)
      changes :+= value
    editor.onSubmit = _ => submits += 1
    editor.setCursor(EditorCursor(0, 0))
    editor.restoreDraft(saved)
    assertEquals(changes, Vector.empty)
    editor.setText("b")
    editor.restoreDraft(saved)
    assertEquals(changes, Vector("a"))
    editor.restoreDraft(saved)
    assertEquals(changes, Vector("a"))
    assertEquals(submits, 0)
