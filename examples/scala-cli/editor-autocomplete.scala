#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.1.1
//> using dep io.github.zikolach::siglyph-terminal-jvm:0.1.1

import scalatui.autocomplete.*
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def editorAutocompleteDemo(): Unit =
  val tui = TUI(SttyTerminal())
  val output = Text("Submitted: (none)")
  val editor = Editor(options = EditorOptions(
    autocompleteProvider = Some(SlashCommandAutocompleteProvider(Vector(
      SlashCommand("help", Some("Show available commands")),
      SlashCommand("clear", Some("Clear submitted text")),
      SlashCommand("quit", Some("Exit the demo"))
    ))),
    onSubmit = text =>
      text.trim match
        case "/quit" => tui.requestExit()
        case "/clear" => output.text = "Submitted: (none)"
        case value if value.nonEmpty => output.text = s"Submitted: $value"
        case _ => ()
  ))

  tui.addChild(Text("Editor autocomplete demo — type / then Tab"))
  tui.addChild(Spacer(1))
  tui.addChild(output)
  tui.addChild(Spacer(1))
  tui.addChild(editor)
  tui.setFocus(editor)
  tui.exitsOnEscape = true
  tui.run()
