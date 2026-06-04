package scalatui.demo

import scalatui.core.TUI
import scalatui.terminal.native.PosixTerminal

@main def interactiveNativeDemo(): Unit =
  val tui = TUI(PosixTerminal())
  InteractiveDemo.install(tui)
  tui.run()
