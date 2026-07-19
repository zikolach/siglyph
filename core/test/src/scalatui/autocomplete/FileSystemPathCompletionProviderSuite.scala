package scalatui.autocomplete

import java.nio.file.{Files, LinkOption, Path}

import scalatui.editing.EditorCursor

class FileSystemPathCompletionProviderSuite extends munit.FunSuite:
  test("filesystem provider bounds results and sorts directories before files"):
    withTempDirectory { root =>
      Files.createDirectory(root.resolve("b-dir"))
      Files.createDirectory(root.resolve("a-dir"))
      Files.writeString(root.resolve("b-file.txt"), "b")
      Files.writeString(root.resolve("a-file.txt"), "a")

      val result = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile,
          maxResults = 3
        )),
        ""
      )

      assertEquals(
        result.map(completion => (completion.path, completion.label, completion.isDirectory)),
        Vector(
          ("a-dir/", "a-dir/", true),
          ("b-dir/", "b-dir/", true),
          ("a-file.txt", "a-file.txt", false)
        )
      )
    }

  test("filesystem provider filters hidden and git entries unless configured"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("visible.txt"), "visible")
      Files.writeString(root.resolve(".hidden.txt"), "hidden")
      Files.createDirectory(root.resolve(".git"))

      val defaultResult = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(baseDirectory =
          root.toFile
        )),
        ""
      )
      assertEquals(defaultResult.map(_.path), Vector("visible.txt"))

      val includeHidden = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile,
          includeHidden = true
        )),
        ""
      )
      assertEquals(includeHidden.map(_.path), Vector(".hidden.txt", "visible.txt"))

      val includeHiddenAndGit = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile,
          includeHidden = true,
          includeGitEntries = true
        )),
        ""
      )
      assertEquals(includeHiddenAndGit.map(_.path), Vector(".git/", ".hidden.txt", "visible.txt"))

      val includeGitOnly = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile,
          includeGitEntries = true
        )),
        ""
      )
      assertEquals(includeGitOnly.map(_.path), Vector(".git/", "visible.txt"))
    }

  test("filesystem provider completes nested directories and marks directories and files"):
    withTempDirectory { root =>
      val src = Files.createDirectory(root.resolve("src"))
      Files.createDirectory(src.resolve("app"))
      Files.writeString(src.resolve("Main.scala"), "object Main")

      val result = request(
        FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(baseDirectory =
          root.toFile
        )),
        "src/"
      )

      assertEquals(
        result.map(completion => (completion.path, completion.label, completion.isDirectory)),
        Vector(
          ("src/app/", "app/", true),
          ("src/Main.scala", "Main.scala", false)
        )
      )
    }

  test("filesystem provider returns empty results for missing and non-directory paths"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("plain.txt"), "plain")
      val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile
      ))

      assertEquals(request(provider, "missing/"), Vector.empty)
      assertEquals(request(provider, "plain.txt/"), Vector.empty)
    }

  test("filesystem provider rejects unsafe control-character filenames"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("safe.txt"), "safe")
      Files.writeString(root.resolve("bad\u001b[31m.txt"), "bad")
      val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile
      ))

      assertEquals(request(provider, "").map(_.path), Vector("safe.txt"))
    }

  test("filesystem provider honors configured scan limit"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("a.txt"), "a")
      Files.writeString(root.resolve("b.txt"), "b")
      val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile,
        maxScannedEntries = 0
      ))

      assertEquals(request(provider, ""), Vector.empty)
    }

  test("filesystem provider reports dispatcher failures through callback"):
    val expected = RuntimeException("dispatch failed")
    val provider = FileSystemPathCompletionProvider(
      FileSystemPathCompletionOptions(),
      dispatch = _ => throw expected
    )
    var failed   = Option.empty[Throwable]

    provider.requestPathSuggestions(
      PathCompletionRequest(
        PathCompletionPrefix("", "", isAttachment = false, isQuoted = false),
        force = false
      ),
      new PathCompletionProvider.Callback:
        override def complete(result: Vector[PathCompletion]): Unit = ()
        override def fail(error: Throwable): Unit                   = failed = Some(error)
    )

    assertEquals(failed, Some(expected))

  test(
    "filesystem provider preserves quoted attachment completion syntax through combined provider"
  ):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("read me.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val requestValue = AutocompleteRequest(
        Vector("attach @\"docs/read tail"),
        EditorCursor(0, 18),
        force = false
      )
      val suggestions  = requestAutocomplete(provider, requestValue).get

      assertEquals(suggestions.prefix, "@\"docs/read")
      assertEquals(
        suggestions.items.map(item => (item.value, item.kind)),
        Vector(
          ("@\"docs/read me.md\"", AutocompleteItemKind.Attachment)
        )
      )
    }

  test("filesystem provider keeps plain path completions unquoted when safe"):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("plain.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val suggestions = requestAutocompleteAtEnd(provider, "open docs/pl").get

      assertEquals(suggestions.prefix, "docs/pl")
      assertEquals(suggestions.items.map(_.value), Vector("docs/plain.md"))
    }

  test("filesystem provider quotes whitespace path completions through combined provider"):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("read me.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val requestValue = AutocompleteRequest(
        Vector("open docs/read tail"),
        EditorCursor(0, 14),
        force = false
      )
      val suggestions  = requestAutocomplete(provider, requestValue).get

      assertEquals(suggestions.prefix, "docs/read")
      assertEquals(suggestions.items.map(_.value), Vector("\"docs/read me.md\""))
    }

  test("filesystem provider preserves existing plain quoted path completions"):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("plain.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val suggestions = requestAutocompleteAtEnd(provider, "open \"docs/pl").get

      assertEquals(suggestions.prefix, "\"docs/pl")
      assertEquals(suggestions.items.map(_.value), Vector("\"docs/plain.md\""))
    }

  test("filesystem provider escapes quotes inside quoted path completions"):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("quote\"name.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val suggestions = requestAutocompleteAtEnd(provider, "open docs/quote").get

      assertEquals(suggestions.prefix, "docs/quote")
      assertEquals(suggestions.items.map(_.value), Vector("\"docs/quote\\\"name.md\""))
    }

  test("filesystem provider quotes bare attachment path completions"):
    withTempDirectory { root =>
      val docs     = Files.createDirectory(root.resolve("docs"))
      Files.writeString(docs.resolve("read me.md"), "hello")
      val provider = CombinedAutocompleteProvider(
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile
        )))
      )

      val requestValue = AutocompleteRequest(
        Vector("attach @docs/re tail"),
        EditorCursor(0, 15),
        force = false
      )
      val suggestions  = requestAutocomplete(provider, requestValue).get

      assertEquals(suggestions.prefix, "@docs/re")
      assertEquals(suggestions.items.map(_.value), Vector("@\"docs/read me.md\""))
    }

  test("filesystem provider rejects path prefixes outside configured base by default"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("inside.txt"), "inside")
      val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile
      ))

      assertEquals(request(provider, "../"), Vector.empty)
      assertEquals(request(provider, root.toAbsolutePath.toString + "/"), Vector.empty)
      assertEquals(request(provider, "~/"), Vector.empty)
    }

  test("filesystem provider separates current directory from contained parent traversal"):
    withTempDirectory { root =>
      val work     = Files.createDirectory(root.resolve("work"))
      val shared   = Files.createDirectory(root.resolve("shared"))
      Files.writeString(shared.resolve("guide.md"), "guide")
      val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile,
        currentDirectory = Some(work.toFile),
        containmentRoots = Vector(root.toFile),
        allowParentTraversal = true
      ))

      assertEquals(request(provider, "../shared/").map(_.path), Vector("../shared/guide.md"))
      assertEquals(request(provider, "../../"), Vector.empty)

      val disabled = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile,
        currentDirectory = Some(work.toFile),
        containmentRoots = Vector(root.toFile)
      ))
      assertEquals(request(disabled, "../shared/"), Vector.empty)
    }

  test("filesystem provider gates contained absolute and home syntax independently"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("inside.txt"), "inside")
      val absolutePrefix = root.toAbsolutePath.toString + "/"
      val absolute       = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
        baseDirectory = root.toFile,
        containmentRoots = Vector(root.toFile),
        allowAbsolutePaths = true
      ))
      assertEquals(
        request(absolute, absolutePrefix).map(_.path),
        Vector(absolutePrefix + "inside.txt")
      )

      val previousHome = System.getProperty("user.home")
      try
        System.setProperty("user.home", root.toString)
        val home = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = root.toFile,
          containmentRoots = Vector(root.toFile),
          allowHomeExpansion = true
        ))
        assertEquals(request(home, "~/").map(_.path), Vector("~/inside.txt"))
      finally
        Option(previousHome) match
          case Some(value) => System.setProperty("user.home", value)
          case None        => System.clearProperty("user.home")
    }

  test("filesystem provider rejects symlink completions escaping configured base"):
    withTempDirectory { root =>
      withTempDirectory { outside =>
        Files.writeString(root.resolve("inside.txt"), "inside")
        Files.writeString(outside.resolve("outside.txt"), "outside")
        val link = root.resolve("outside-link.txt")
        try
          Files.createSymbolicLink(link, outside.resolve("outside.txt"))
          val provider = FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
            baseDirectory = root.toFile
          ))

          assertEquals(request(provider, "").map(_.path), Vector("inside.txt"))
        catch
          case _: UnsupportedOperationException => ()
          case _: java.io.IOException           => ()
          case _: SecurityException             => ()
      }
    }

  test("filesystem provider suppresses cancelled queued work"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("file.txt"), "file")
      var queued    = Vector.empty[Runnable]
      var completed = false
      val provider  = FileSystemPathCompletionProvider(
        FileSystemPathCompletionOptions(baseDirectory = root.toFile),
        dispatch = runnable => queued = queued :+ runnable
      )

      val handle = provider.requestPathSuggestions(
        PathCompletionRequest(
          PathCompletionPrefix("", "", isAttachment = false, isQuoted = false),
          force = false
        ),
        new PathCompletionProvider.Callback:
          override def complete(result: Vector[PathCompletion]): Unit = completed = true
          override def fail(error: Throwable): Unit                   = throw error
      )

      handle.cancel()
      queued.foreach(_.run())
      assertEquals(completed, false)
    }

  test("recursive attachment provider finds descendants and preserves combined syntax"):
    withTempDirectory { root =>
      val src       = Files.createDirectories(root.resolve("src/main"))
      Files.writeString(src.resolve("Main.scala"), "object Main")
      val recursive = RecursiveAttachmentCompletionProvider(
        RecursiveAttachmentCompletionOptions(
          paths = FileSystemPathCompletionOptions(baseDirectory = root.toFile),
          maxDepth = 3,
          maxVisitedEntries = 20,
          maxResults = 10
        )
      )

      assertEquals(
        requestAttachment(recursive, "Ma").map(_.path),
        Vector("src/main/Main.scala")
      )
      assertEquals(request(recursive, "Ma"), Vector.empty)

      val combined = CombinedAutocompleteProvider(pathProvider = Some(recursive))
      val result   = requestAutocompleteAtEnd(combined, "attach @\"Ma").get
      assertEquals(result.prefix, "@\"Ma")
      assertEquals(result.items.map(_.value), Vector("@\"src/main/Main.scala\""))
    }

  test("recursive attachment provider honors depth visited-entry and result bounds"):
    withTempDirectory { root =>
      val nested = Files.createDirectories(root.resolve("one/two"))
      Files.writeString(nested.resolve("target-a.txt"), "a")
      Files.writeString(nested.resolve("target-b.txt"), "b")

      def recursive(depth: Int, visited: Int, results: Int) =
        RecursiveAttachmentCompletionProvider(RecursiveAttachmentCompletionOptions(
          paths = FileSystemPathCompletionOptions(baseDirectory = root.toFile),
          maxDepth = depth,
          maxVisitedEntries = visited,
          maxResults = results
        ))

      assertEquals(requestAttachment(recursive(1, 20, 10), "target"), Vector.empty)
      assertEquals(requestAttachment(recursive(2, 0, 10), "target"), Vector.empty)
      assertEquals(requestAttachment(recursive(2, 20, 1), "target").length, 1)
      assertEquals(
        requestAttachment(recursive(2, 20, 10), "target").map(_.path),
        Vector("one/two/target-a.txt", "one/two/target-b.txt")
      )
    }

  test("recursive attachment provider rejects escaping symlinks"):
    withTempDirectory { root =>
      withTempDirectory { outside =>
        Files.writeString(outside.resolve("outside-secret.txt"), "secret")
        Files.writeString(root.resolve("inside.txt"), "inside")
        try
          Files.createSymbolicLink(root.resolve("escape"), outside)
          val recursive = RecursiveAttachmentCompletionProvider(
            RecursiveAttachmentCompletionOptions(
              paths = FileSystemPathCompletionOptions(baseDirectory = root.toFile)
            )
          )

          assertEquals(requestAttachment(recursive, "outside"), Vector.empty)
          assertEquals(requestAttachment(recursive, "inside").map(_.path), Vector("inside.txt"))
        catch
          case _: UnsupportedOperationException => ()
          case _: java.io.IOException           => ()
          case _: SecurityException             => ()
      }
    }

  test("recursive attachment provider suppresses cancelled queued traversal"):
    withTempDirectory { root =>
      Files.writeString(root.resolve("file.txt"), "file")
      var queued    = Vector.empty[Runnable]
      var completed = false
      val provider  = RecursiveAttachmentCompletionProvider(
        RecursiveAttachmentCompletionOptions(
          paths = FileSystemPathCompletionOptions(baseDirectory = root.toFile)
        ),
        dispatch = runnable => queued :+= runnable
      )
      val handle    = provider.requestPathSuggestions(
        PathCompletionRequest(
          PathCompletionPrefix("@f", "f", isAttachment = true, isQuoted = false),
          force = false
        ),
        new PathCompletionProvider.Callback:
          override def complete(result: Vector[PathCompletion]): Unit = completed = true
          override def fail(error: Throwable): Unit                   = throw error
      )

      handle.cancel()
      queued.foreach(_.run())

      assertEquals(completed, false)
    }

  private def request(provider: PathCompletionProvider, rawPrefix: String): Vector[PathCompletion] =
    request(provider, rawPrefix, isAttachment = false)

  private def requestAttachment(
      provider: PathCompletionProvider,
      rawPrefix: String
  ): Vector[PathCompletion] = request(provider, rawPrefix, isAttachment = true)

  private def request(
      provider: PathCompletionProvider,
      rawPrefix: String,
      isAttachment: Boolean
  ): Vector[PathCompletion] =
    var result = Vector.empty[PathCompletion]
    var failed = Option.empty[Throwable]
    provider.requestPathSuggestions(
      PathCompletionRequest(
        PathCompletionPrefix(rawPrefix, rawPrefix, isAttachment, isQuoted = false),
        force = false
      ),
      new PathCompletionProvider.Callback:
        override def complete(value: Vector[PathCompletion]): Unit = result = value
        override def fail(error: Throwable): Unit                  = failed = Some(error)
    )
    failed.foreach(throw _)
    result

  private def requestAutocompleteAtEnd(
      provider: AutocompleteProvider,
      text: String
  ): Option[AutocompleteSuggestions] =
    requestAutocomplete(provider, AutocompleteRequest(Vector(text), EditorCursor(0, text.length)))

  private def requestAutocomplete(
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

  private def withTempDirectory(test: Path => Unit): Unit =
    val root = Files.createTempDirectory("siglyph-path-completion")
    try test(root)
    finally deleteRecursively(root)

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path, LinkOption.NOFOLLOW_LINKS) then
      if Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) then
        val stream = Files.list(path)
        try stream.forEach(child => deleteRecursively(child))
        finally stream.close()
      Files.deleteIfExists(path)
