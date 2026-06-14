#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.1.1
//> using dep io.github.zikolach::siglyph-terminal-jvm:0.1.1

import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def helloSiglyph(): Unit =
  val tui = TUI(SttyTerminal())
  val status = Text("Type text and press Enter. Esc or Ctrl+C exits.")
  val input = Input()
  input.onSubmit = value =>
    status.text = s"Last submission: ${if value.trim.isEmpty then "(empty)" else value}"
    input.setValue("")

  tui.addChild(Text("siglyph Scala CLI demo"))
  tui.addChild(Spacer(1))
  tui.addChild(status)
  tui.addChild(Spacer(1))
  tui.addChild(input)
  tui.setFocus(input)
  tui.exitsOnEscape = true
  tui.run()
