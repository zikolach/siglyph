package scalatui.autocomplete

import scalatui.components.{Editor, EditorAutocompleteTrigger, EditorOptions}
import scalatui.core.InputResult
import scalatui.editing.EditorCursor
import scalatui.terminal.{TerminalInput, TerminalKey}

class AutocompleteSuite extends munit.FunSuite:
  test("combined provider returns deterministic slash and path suggestions"):
    val provider = CombinedAutocompleteProvider(
      commands = Vector(SlashCommand("help", Some("Show help"))),
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(
          PathCompletion("/tmp", "/tmp", isDirectory = true)
        )
      ))
    )

    val suggestions =
      requestSync(provider, AutocompleteRequest(Vector("/"), EditorCursor(0, 1), force = true))

    assertEquals(suggestions.map(_.prefix), Some("/"))
    assertEquals(
      suggestions.map(_.items.map(item => (item.value, item.kind))),
      Some(Vector(
        ("help", AutocompleteItemKind.SlashCommand),
        ("/tmp", AutocompleteItemKind.FilePath)
      ))
    )

  test("combined completion applies slash commands and paths by item kind"):
    val slashResult = AutocompleteProvider.defaultCompletion(CompletionRequest(
      Vector("/"),
      EditorCursor(0, 1),
      AutocompleteItem("help", "help", kind = AutocompleteItemKind.SlashCommand),
      "/"
    ))
    assertEquals(slashResult.lines, Vector("/help "))
    assertEquals(slashResult.cursor, EditorCursor(0, 6))

    val pathResult = AutocompleteProvider.defaultCompletion(CompletionRequest(
      Vector("open /"),
      EditorCursor(0, 6),
      AutocompleteItem("/tmp", "/tmp", kind = AutocompleteItemKind.FilePath),
      "/"
    ))
    assertEquals(pathResult.lines, Vector("open /tmp"))
    assertEquals(pathResult.cursor, EditorCursor(0, 9))

  test("path prefix parser handles quotes attachments and delimiters"):
    assertEquals(
      CompletionPrefixParser.parsePathPrefix("open \"src/ma", force = false),
      Some(PathCompletionPrefix("\"src/ma", "src/ma", isAttachment = false, isQuoted = true))
    )
    assertEquals(
      CompletionPrefixParser.parsePathPrefix("attach @\"docs/re", force = false),
      Some(PathCompletionPrefix("@\"docs/re", "docs/re", isAttachment = true, isQuoted = true))
    )
    assertEquals(
      CompletionPrefixParser.parsePathPrefix("--file=./src", force = false),
      Some(PathCompletionPrefix("./src", "./src", isAttachment = false, isQuoted = false))
    )

  test("quoted and attachment path completion preserves replacement token"):
    val provider    = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(
          PathCompletion("docs/read me.md", "read me.md")
        )
      ))
    )
    val request     =
      AutocompleteRequest(Vector("attach @\"docs/re tail"), EditorCursor(0, 16), force = false)
    val suggestions = requestSync(provider, request).get
    assertEquals(suggestions.prefix, "@\"docs/re")
    assertEquals(suggestions.items.head.value, "@\"docs/read me.md\"")

    val result = provider.applyCompletion(CompletionRequest(
      request.lines,
      request.cursor,
      suggestions.items.head,
      suggestions.prefix
    ))
    assertEquals(result.lines, Vector("attach @\"docs/read me.md\" tail"))

  test("editor cancels stale combined path requests and ignores old responses"):
    final class ManualPathProvider extends PathCompletionProvider:
      var callback  = Option.empty[PathCompletionProvider.Callback]
      var cancelled = 0
      override def requestPathSuggestions(
          request: PathCompletionRequest,
          callback: PathCompletionProvider.Callback
      ): AutocompleteRequestHandle =
        this.callback = Some(callback)
        () => cancelled += 1

    val path     = ManualPathProvider()
    val provider = CombinedAutocompleteProvider(pathProvider = Some(path))
    val editor   = Editor(
      "./",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )

    assertEquals(editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Render)
    assertEquals(
      editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x"))),
      InputResult.Render
    )
    path.callback.foreach(_.complete(Vector(PathCompletion("./old", "old"))))

    assertEquals(path.cancelled, 1)
    assertEquals(editor.text, "./x")

  private def requestSync(
      provider: AutocompleteProvider,
      request: AutocompleteRequest
  ): Option[AutocompleteSuggestions] =
    var result = Option.empty[AutocompleteSuggestions]
    var failed = Option.empty[Throwable]
    provider.requestSuggestions(
      request,
      new AutocompleteCallback:
        override def complete(value: Option[AutocompleteSuggestions]): Unit = result = value
        override def fail(error: Throwable): Unit                           = failed = Some(error)
    )
    failed.foreach(throw _)
    result
