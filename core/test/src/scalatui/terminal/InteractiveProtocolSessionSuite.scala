package scalatui.terminal

import scalatui.syntax.Equality.*

class InteractiveProtocolSessionSuite extends munit.FunSuite:
  test("paste and mouse retain obligations across failed enable and disable writes"):
    for mode <- Vector(
                  TerminalMouseTrackingMode.Basic,
                  TerminalMouseTrackingMode.Drag,
                  TerminalMouseTrackingMode.AllMotion
                )
    do
      var fail    = true
      var writes  = Vector.empty[String]
      val session = InteractiveProtocolSession { data =>
        writes :+= data
        if fail then throw new java.io.IOException("output")
      }
      intercept[java.io.IOException](session.enablePaste())
      assert(session.pasteCleanupPending)
      intercept[java.io.IOException](session.enableMouse(mode))
      assert(session.mouseCleanupPending)
      intercept[java.io.IOException](session.disableMouse())
      intercept[java.io.IOException](session.disablePaste())
      assert(session.hasCleanupObligations)
      fail = false
      session.disableMouse()
      session.disablePaste()
      assertEquals(
        writes.takeRight(2),
        Vector(Terminal.MouseProtocol.disable(mode), "\u001b[?2004l")
      )
      assert(!session.hasCleanupObligations)
      val count   = writes.size
      session.disableMouse()
      session.disablePaste()
      assertEquals(writes.size, count)

  test("Kitty enable and disable failures retain pop obligation and active state"):
    var fail    = false
    var writes  = Vector.empty[String]
    val session = InteractiveProtocolSession { data =>
      writes :+= data
      if fail then throw new java.io.IOException("output")
    }
    session.requestKittyKeyboardProtocol(0L, 100L)
    fail = true
    intercept[java.io.IOException](session.acceptKittyKeyboardProtocolResponse("\u001b[?1u", 1L))
    assert(session.kittyCleanupPending)
    intercept[java.io.IOException](session.disableKittyKeyboardProtocol())
    assertEquals(session.keyboardProtocolState(2L), KittyKeyboardProtocolState.Active(1))
    fail = false
    session.disableKittyKeyboardProtocol()
    assert(!session.hasCleanupObligations)
    assertEquals(session.keyboardProtocolState(2L), KittyKeyboardProtocolState.Inactive)
    val count   = writes.size
    session.disableKittyKeyboardProtocol()
    assertEquals(writes.size, count)

  test("pending Kitty cancellation emits no pop and disabled mouse emits nothing"):
    var writes  = Vector.empty[String]
    val session = InteractiveProtocolSession(data => writes :+= data)
    session.enableMouse(TerminalMouseTrackingMode.Disabled)
    session.requestKittyKeyboardProtocol(0L, 100L)
    session.cancelPendingKittyNegotiation()
    assert(!session.acceptKittyKeyboardProtocolResponse("\u001b[?1u", 1L))
    session.disableKittyKeyboardProtocol()
    assertEquals(writes, Vector(KittyKeyboardProtocol.QuerySequence))
