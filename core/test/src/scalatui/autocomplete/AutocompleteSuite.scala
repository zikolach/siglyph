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

  test("trigger prefix validation rejects parser token delimiters"):
    assertEquals(TriggerPrefix.from("#"), Right(triggerPrefix("#")))
    assertEquals(TriggerPrefix.from("="), Left(TriggerPrefixError.ContainsDelimiter))
    assertEquals(TriggerPrefix.from("#="), Left(TriggerPrefixError.ContainsDelimiter))
    assertEquals(TriggerPrefix.from("("), Left(TriggerPrefixError.ContainsDelimiter))
    assertEquals(TriggerPrefix.from("# tag"), Left(TriggerPrefixError.ContainsWhitespace))

  test("trigger prefix parser detects active natural trigger tokens"):
    val source = triggerSource("#", _ => Some(Vector.empty))

    assertEquals(
      CompletionPrefixParser.parseTriggerPrefix("open #mo", Vector(source))
        .map(prefix => (prefix.replacementPrefix, prefix.query)),
      Some(("#mo", "mo"))
    )
    assertEquals(
      CompletionPrefixParser.parseTriggerPrefix("open (#mo", Vector(source))
        .map(prefix => (prefix.replacementPrefix, prefix.query)),
      Some(("#mo", "mo"))
    )
    assertEquals(
      CompletionPrefixParser.parseTriggerPrefix("open abc#mo", Vector(source)),
      None
    )

  test("combined provider dispatches configured natural trigger source"):
    var seenQuery = Option.empty[String]
    val provider  = CombinedAutocompleteProvider(
      triggerSources = Vector(triggerSource(
        "#",
        query =>
          seenQuery = Some(query)
          Some(Vector(AutocompleteItem("model", "model")))
      ))
    )

    val suggestions =
      requestSync(provider, AutocompleteRequest(Vector("choose #mo"), EditorCursor(0, 10)))

    assertEquals(seenQuery, Some("mo"))
    assertEquals(suggestions.map(_.prefix), Some("#mo"))
    assertEquals(suggestions.map(_.items.map(_.value)), Some(Vector("model")))

  test("combined provider chooses longest stacked trigger prefix"):
    var hashCalls    = 0
    var stackedQuery = Option.empty[String]
    val provider     = CombinedAutocompleteProvider(
      triggerSources = Vector(
        triggerSource(
          "#",
          _ =>
            hashCalls += 1
            Some(Vector(AutocompleteItem("hash", "hash")))
        ),
        triggerSource(
          "##",
          query =>
            stackedQuery = Some(query)
            Some(Vector(AutocompleteItem("model", "model")))
        )
      )
    )

    val suggestions =
      requestSync(provider, AutocompleteRequest(Vector("open ##mo"), EditorCursor(0, 9)))

    assertEquals(hashCalls, 0)
    assertEquals(stackedQuery, Some("mo"))
    assertEquals(suggestions.map(_.prefix), Some("##mo"))
    assertEquals(suggestions.map(_.items.map(_.value)), Some(Vector("model")))

  test("trigger completion inserts replacement text without command execution semantics"):
    val provider = CombinedAutocompleteProvider(
      triggerSources = Vector(triggerSource(
        "#",
        _ => Some(Vector(AutocompleteItem("model", "model")))
      ))
    )
    val request  = AutocompleteRequest(Vector("choose #mo now"), EditorCursor(0, 10))
    val item     = requestSync(provider, request).get.items.head

    val result = provider.applyCompletion(CompletionRequest(
      request.lines,
      request.cursor,
      item,
      "#mo"
    ))

    assertEquals(result.lines, Vector("choose model now"))
    assertEquals(result.cursor, EditorCursor(0, 12))

  test("forced active trigger completion skips path provider"):
    var pathCalls = 0
    val provider  = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync { _ =>
        pathCalls += 1
        Vector(PathCompletion("#path", "#path"))
      }),
      triggerSources = Vector(triggerSource(
        "#",
        _ => Some(Vector(AutocompleteItem("model", "model")))
      ))
    )

    val suggestions = requestSync(
      provider,
      AutocompleteRequest(Vector("choose #mo"), EditorCursor(0, 10), force = true)
    )

    assertEquals(pathCalls, 0)
    assertEquals(suggestions.map(_.prefix), Some("#mo"))
    assertEquals(suggestions.map(_.items.map(_.value)), Some(Vector("model")))

  test("forced active trigger without suggestions still skips path provider"):
    var pathCalls = 0
    val provider  = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync { _ =>
        pathCalls += 1
        Vector(PathCompletion("#path", "#path"))
      }),
      triggerSources = Vector(triggerSource(
        "#",
        _ => None
      ))
    )

    val suggestions = requestSync(
      provider,
      AutocompleteRequest(Vector("choose #mo"), EditorCursor(0, 10), force = true)
    )

    assertEquals(pathCalls, 0)
    assertEquals(suggestions, None)

  test("combined provider falls back when text is not an active trigger token"):
    var triggerCalls = 0
    val provider     = CombinedAutocompleteProvider(
      commands = Vector(SlashCommand("help")),
      triggerSources = Vector(triggerSource(
        "#",
        _ =>
          triggerCalls += 1
          Some(Vector(AutocompleteItem("model", "model")))
      ))
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("abc#mo"), EditorCursor(0, 6))),
      None
    )
    assertEquals(
      requestSync(
        provider,
        AutocompleteRequest(Vector("/h"), EditorCursor(0, 2))
      ).map(_.items.map(_.value)),
      Some(Vector("help"))
    )
    assertEquals(triggerCalls, 0)

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

  test("quoted path completion escapes backslashes in accepted values"):
    val provider = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(PathCompletion("docs/back\\slash.md", "back\\slash.md"))
      ))
    )

    val suggestions = requestSync(
      provider,
      AutocompleteRequest(Vector("open docs/back"), EditorCursor(0, 14), force = false)
    ).get

    assertEquals(suggestions.items.map(_.value), Vector("\"docs/back\\\\slash.md\""))

  test("escaped quoted path prefixes unescape raw provider prefix and preserve replacement text"):
    var seenPrefix = Option.empty[PathCompletionPrefix]
    val provider   = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync { request =>
        seenPrefix = Some(request.prefix)
        Vector(PathCompletion("docs/quote\"name.md", "quote\"name.md"))
      })
    )

    val suggestions = requestSync(
      provider,
      AutocompleteRequest(Vector("open \"docs/quote\\\"n"), EditorCursor(0, 19), force = false)
    ).get

    assertEquals(
      seenPrefix,
      Some(PathCompletionPrefix(
        "\"docs/quote\\\"n",
        "docs/quote\"n",
        isAttachment = false,
        isQuoted = true
      ))
    )
    assertEquals(suggestions.prefix, "\"docs/quote\\\"n")
    assertEquals(suggestions.items.map(_.value), Vector("\"docs/quote\\\"name.md\""))

  test("slash fuzzy ranking is disabled by default"):
    val provider = SlashCommandAutocompleteProvider(Vector(
      SlashCommand("markdown"),
      SlashCommand("my-model"),
      SlashCommand("model")
    ))

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("/m"), EditorCursor(0, 2)))
        .map(_.items.map(_.label)),
      Some(Vector("markdown", "my-model", "model"))
    )

  test("path fuzzy ranking is disabled by default"):
    val provider = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(
          PathCompletion("/my-model", "my-model"),
          PathCompletion("/model", "model")
        )
      ))
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("open /model"), EditorCursor(0, 11)))
        .map(_.items.map(_.label)),
      Some(Vector("my-model", "model"))
    )

  test("trigger fuzzy ranking is disabled by default"):
    val provider = CombinedAutocompleteProvider(
      triggerSources = Vector(triggerSource(
        "#",
        _ =>
          Some(Vector(
            AutocompleteItem("my-model", "my-model"),
            AutocompleteItem("model", "model")
          ))
      ))
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("#model"), EditorCursor(0, 6)))
        .map(_.items.map(_.label)),
      Some(Vector("my-model", "model"))
    )

  test("slash fuzzy ranking can be enabled"):
    val provider = SlashCommandAutocompleteProvider(
      Vector(SlashCommand("my-model"), SlashCommand("model"), SlashCommand("markdown")),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("/model"), EditorCursor(0, 6)))
        .map(_.items.map(_.label)),
      Some(Vector("model", "my-model"))
    )

  test("path fuzzy ranking can be enabled"):
    val provider = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(
          PathCompletion("/my-model", "my-model"),
          PathCompletion("/model", "model"),
          PathCompletion("/markdown", "markdown")
        )
      )),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("open /model"), EditorCursor(0, 11)))
        .map(_.items.map(_.label)),
      Some(Vector("model", "my-model"))
    )

  test("trigger fuzzy ranking can be enabled"):
    val provider = CombinedAutocompleteProvider(
      triggerSources = Vector(triggerSource(
        "#",
        _ =>
          Some(Vector(
            AutocompleteItem("my-model", "my-model"),
            AutocompleteItem("model", "model"),
            AutocompleteItem("markdown", "markdown")
          ))
      )),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("#model"), EditorCursor(0, 6)))
        .map(_.items.map(_.label)),
      Some(Vector("model", "my-model"))
    )

  test("slash argument fuzzy ranking can be enabled"):
    val provider = SlashCommandAutocompleteProvider(
      Vector(SlashCommand(
        "cmd",
        argumentCompletions = _ =>
          Some(Vector(
            AutocompleteItem("my-model", "my-model"),
            AutocompleteItem("model", "model"),
            AutocompleteItem("markdown", "markdown")
          ))
      )),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("/cmd model"), EditorCursor(0, 10)))
        .map(_.items.map(_.label)),
      Some(Vector("model", "my-model"))
    )

  test("slash fuzzy ranking preserves equal-score provider order"):
    val provider = SlashCommandAutocompleteProvider(
      Vector(
        SlashCommand("same", Some("first")),
        SlashCommand("same", Some("second"))
      ),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("/same"), EditorCursor(0, 5)))
        .map(_.items.flatMap(_.description)),
      Some(Vector("first", "second"))
    )

  test("path fuzzy ranking preserves equal-score provider order"):
    val provider = CombinedAutocompleteProvider(
      pathProvider = Some(PathCompletionProvider.sync(_ =>
        Vector(
          PathCompletion("/first", "same"),
          PathCompletion("/second", "same")
        )
      )),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("open /same"), EditorCursor(0, 10)))
        .map(_.items.map(_.value)),
      Some(Vector("/first", "/second"))
    )

  test("trigger fuzzy ranking preserves equal-score provider order"):
    val provider = CombinedAutocompleteProvider(
      triggerSources = Vector(triggerSource(
        "#",
        _ =>
          Some(Vector(
            AutocompleteItem("first", "same"),
            AutocompleteItem("second", "same")
          ))
      )),
      fuzzyRanking = AutocompleteFuzzyRanking.Enabled
    )

    assertEquals(
      requestSync(provider, AutocompleteRequest(Vector("#same"), EditorCursor(0, 5)))
        .map(_.items.map(_.value)),
      Some(Vector("first", "second"))
    )

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

  private def triggerPrefix(prefix: String): TriggerPrefix =
    TriggerPrefix.from(prefix).fold(error => sys.error(error.message), identity)

  private def triggerSource(
      prefix: String,
      completions: String => Option[Vector[AutocompleteItem]]
  ): TriggerCompletionSource =
    TriggerCompletionSource.fromPrefix(
      prefix,
      completions
    ).fold(error => sys.error(error.message), identity)

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
