package scalatui.core

import scalatui.TestInputStreams
import scalatui.terminal.{ImageCellDimensions, TerminalImageProtocol, VirtualTerminal}

import java.util.concurrent.CountDownLatch

class ImageGeometrySuite extends munit.FunSuite:
  test("valid cell-size reply updates only its context and triggers repaint"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component with ContextualComponent:
      private var context                                        = Option.empty[TUIContext]
      override def tuiContext_=(value: Option[TUIContext]): Unit = context = value
      override def render(width: Int): ComponentRender           =
        val dimensions = context.map(_.imageCellDimensions).getOrElse(ImageCellDimensions())
        ComponentRender.text(s"${dimensions.widthPx}x${dimensions.heightPx}")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    TestInputStreams.parse("\u001b[6;10;20t").foreach(terminal.sendInput)

    assertEquals(tui.imageCellDimensions, ImageCellDimensions(20, 10))
    assert(terminal.output.contains("20x10"), terminal.output)

  test("cell-size replies remain isolated between concurrent TUI sessions"):
    val firstTerminal  = VirtualTerminal(20, 5)
    val secondTerminal = VirtualTerminal(20, 5)
    val firstTui       = TUI(firstTerminal)
    val secondTui      = TUI(secondTerminal)
    firstTui.start()
    secondTui.start()
    val ready          = CountDownLatch(1)
    val first          = Thread(() => {
      ready.await()
      TestInputStreams.parse("\u001b[6;10;20t").foreach(firstTerminal.sendInput)
    })
    val second         = Thread(() => {
      ready.await()
      TestInputStreams.parse("\u001b[6;12;24t").foreach(secondTerminal.sendInput)
    })

    first.start()
    second.start()
    ready.countDown()
    first.join(2000)
    second.join(2000)

    assertEquals(first.isAlive, false)
    assertEquals(second.isAlive, false)
    assertEquals(firstTui.imageCellDimensions, ImageCellDimensions(20, 10))
    assertEquals(secondTui.imageCellDimensions, ImageCellDimensions(24, 12))

  test("invalid cell-size reply preserves the receiving session fallback"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()

    TestInputStreams.parse("\u001b[6;0;24t").foreach(terminal.sendInput)

    assertEquals(tui.imageCellDimensions, TerminalImageProtocol.DefaultCellDimensions)
