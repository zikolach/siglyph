package scalatui.demo

import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.StreamTerminal

@main def mvpDemo(): Unit =
  val tui = TUI(StreamTerminal(output = System.out, initialColumns = 80, initialRows = 24))
  tui.addChild(Text("scala-tui MVP demo", paddingX = 1, paddingY = 1))
  val box = Box(paddingX = 2, paddingY = 1)
  box.addChild(Text("Text, Box, Spacer, SelectList, and Input are available in the first milestone.", paddingX = 0))
  tui.addChild(box)
  tui.addChild(Spacer(1))
  tui.addChild(SelectList(Vector(
    SelectItem("text", "Text"),
    SelectItem("box", "Box"),
    SelectItem("input", "Input")
  ), maxVisible = 3))
  tui.addChild(Spacer(1))
  val input = Input("type here")
  input.focused = true
  tui.addChild(input)
  tui.start()
  tui.stop()
