package scalatui.demo

import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def interactiveJvmDemo(): Unit =
  val tui = TUI(SttyTerminal())
  InteractiveDemo.install(tui)
  tui.run()
