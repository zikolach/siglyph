import scalatui.components.{Input, Text}
import scalatui.core.TUI
import scalatui.terminal.VirtualTerminal
import scalatui.terminal.native.PosixTerminal

object NativeConsumer:
  def main(args: Array[String]): Unit =
    val input    = Input("native")
    assert(input.value.equals("native"))
    val terminal = VirtualTerminal(30, 3)
    val tui      = TUI.fullscreen(terminal, Text("native consumer", paddingX = 0, paddingY = 0))
    try
      tui.start()
      assert(terminal.screenLines.exists(_.contains("native consumer")))
    finally tui.stop()
    val backend  = PosixTerminal()
    assert(backend.columns > 0)
    println("Native consumer passed")
