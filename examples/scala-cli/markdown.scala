#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.7.1
//> using dep io.github.zikolach::siglyph-markdown:0.7.1

import scalatui.components.*
import scalatui.core.TUI
import scalatui.markdown.Markdown
import scalatui.terminal.StreamTerminal

@main def markdownDemo(): Unit =
  val tui = TUI(StreamTerminal(output = System.out, initialColumns = 72, initialRows = 24))
  tui.addChild(Markdown(
    """
      |# siglyph Markdown demo
      |
      |siglyph includes a dependency-free baseline Markdown renderer.
      |
      |- headings
      |- paragraphs and lists
      |- `inline code`
      |- links like [siglyph](https://github.com/zikolach/siglyph)
      |
      |> Components render within the requested terminal width.
      |
      || Module | Purpose |
      || --- | --- |
      || core | components and terminal abstractions |
      || markdown | Markdown component |
      |""".stripMargin.trim,
    paddingX = 1,
    paddingY = 1
  ))
  tui.start()
  tui.stop()
