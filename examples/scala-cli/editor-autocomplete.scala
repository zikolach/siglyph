#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.7.0
//> using dep io.github.zikolach::siglyph-terminal-jvm:0.7.0

import java.io.File
import java.util.concurrent.Executors

import scalatui.autocomplete.*
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def editorAutocompleteDemo(): Unit =
  val scheduler = Executors.newSingleThreadScheduledExecutor { runnable =>
    val thread = Thread(runnable, "siglyph-autocomplete-demo-debounce")
    thread.setDaemon(true)
    thread
  }
  val tags      = Vector("#bug", "#docs", "#demo", "#release-notes")
  TriggerCompletionSource.fromPrefix(
    "#",
    _ =>
      Some(tags.map(tag =>
        AutocompleteItem(tag.drop(1), tag, Some("application-owned tag"))
      ))
  ) match
    case Left(error)      =>
      System.err.println(s"Invalid demo trigger configuration: ${error.message}")
      scheduler.shutdownNow()
    case Right(tagSource) =>
      val tui    = TUI(SttyTerminal())
      val output = Text("Submitted: (none)")
      val editor = Editor(options =
        EditorOptions(
          autocompleteProvider = Some(CombinedAutocompleteProvider(
            commands = Vector(
              SlashCommand("help", Some("Show available commands")),
              SlashCommand("clear", Some("Clear submitted text")),
              SlashCommand("quit", Some("Exit the demo"))
            ),
            pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
              baseDirectory = File("."),
              maxResults = 20,
              includeHidden = false
            ))),
            triggerSources = Vector(tagSource),
            fuzzyRanking = AutocompleteFuzzyRanking.Enabled
          )),
          autocompleteDebouncer = EditorAutocompleteDebouncer.Delayed(scheduler, delayMillis = 40)
        )
      )
      editor.onSubmit = text =>
        text.trim match
          case "/quit"                 => tui.requestExit()
          case "/clear"                =>
            output.text = "Submitted: (none)"
            editor.setText("")
          case value if value.nonEmpty =>
            output.text = s"Submitted: $value"
            editor.setText("")
          case _                       => ()

      tui.addChild(Text("Autocomplete demo — try /he, ./, @\"README, or #dc then Tab"))
      tui.addChild(
        Text("Filesystem completions use Java NIO only; no fd/find/shell tools required.")
      )
      tui.addChild(Spacer(1))
      tui.addChild(output)
      tui.addChild(Spacer(1))
      tui.addChild(editor)
      tui.setFocus(editor)
      tui.exitsOnEscape = true
      try tui.run()
      finally scheduler.shutdownNow()
