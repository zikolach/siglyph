package scalatui.core

import scalatui.terminal.{ImageCellDimensions, TerminalCapabilities, TerminalCapabilityOverrides}
import scala.compiletime.testing.typeChecks

class RenderServicesSuite extends munit.FunSuite:
  test("render services have conservative standalone defaults without runtime controls"):
    val services = new RenderServices {}
    assertEquals(services.imageCellDimensions, ImageCellDimensions())
    assertEquals(services.terminalCapabilities, TerminalCapabilities.Conservative)
    assertEquals(services.terminalCapabilityOverrides, TerminalCapabilityOverrides())
    assert(!typeChecks("(new scalatui.core.RenderServices {}).requestExit()"))
    assert(!typeChecks("(new scalatui.core.RenderServices {}).requestRender()"))
    assert(!typeChecks("(new scalatui.core.RenderServices {}).overlays"))

  test("legacy contexts retain their concrete render environment methods"):
    val context                  = new TUIContext:
      override def requestRender(force: Boolean): Unit         = ()
      override def flushRender(): Unit                         = ()
      override def requestExit(): Unit                         = ()
      override def setFocus(component: Component | Null): Unit = ()
      override def overlays: OverlayHost                       = throw UnsupportedOperationException()
    val services: RenderServices = context
    assertEquals(services.imageCellDimensions, context.imageCellDimensions)
    assertEquals(services.terminalCapabilities, context.terminalCapabilities)
