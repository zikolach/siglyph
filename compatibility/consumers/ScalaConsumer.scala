import scalatui.components.Input
import scalatui.core.{Component, ComponentRender, TUI}
import scalatui.terminal.VirtualTerminal
import scalatui.terminal.jvm.interop.SiglyphJvm

object ScalaConsumer:
  def main(args: Array[String]): Unit =
    val input     = Input("seed")
    assert(input.value.equals("seed"))
    input.setValue("界")
    assert(input.value.equals("界"))
    val terminal  = VirtualTerminal(30, 3)
    val component = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(scalatui.ansi.Ansi.truncateToWidth("consumer", width, ""))
    val tui       = TUI.fullscreen(terminal, component)
    try
      tui.start()
      tui.flushRender()
      assert(terminal.screenLines.exists(_.contains("consumer")))
    finally tui.stop()
    val facade    = new SiglyphJvm()
    assert(facade.createInput("facade").value.equals("facade"))
    println("Scala consumer passed")
