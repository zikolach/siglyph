package scalatui.terminal.native

import scalatui.terminal.{KittyKeyboardProtocol, Terminal}

class PosixTerminalSuite extends munit.FunSuite:
  test("output-side capabilities do not require or invoke callback delivery"):
    val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)

    terminal.write("")
    terminal.hideCursor()
    terminal.showCursor()
    terminal.clearLine()
    terminal.clearFromCursor()
    terminal.clearScreen()
    terminal.moveBy(1)
    assertEquals(Terminal.setTitle(terminal, "test"), true)
    assertEquals(Terminal.setProgress(terminal, active = true), true)
    terminal.requestKittyKeyboardProtocol(timeoutMillis = 100)

    assertEquals(
      terminal.keyboardProtocolState.isInstanceOf[
        scalatui.terminal.KittyKeyboardProtocolState.Pending
      ],
      true
    )
    assert(KittyKeyboardProtocol.QuerySequence.nonEmpty)

  test("interactive start and output-side operations do not synchronously deliver callbacks"):
    val terminal        = PosixTerminal(initialColumns = 80, initialRows = 24)
    val caller          = Thread.currentThread()
    var inlineCallbacks = 0
    terminal.start(
      _ => if Thread.currentThread() eq caller then inlineCallbacks += 1,
      () => if Thread.currentThread() eq caller then inlineCallbacks += 1
    )
    try
      terminal.write("")
      terminal.hideCursor()
      terminal.showCursor()
      terminal.clearLine()
      terminal.clearFromCursor()
      terminal.clearScreen()
      terminal.moveBy(1)
      terminal.drainInput()
      Terminal.setTitle(terminal, "test")
      Terminal.setProgress(terminal, active = true)
      terminal.requestKittyKeyboardProtocol(timeoutMillis = 100)
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      )
      terminal.disableKittyKeyboardProtocol()
      assertEquals(inlineCallbacks, 0)
    finally terminal.stop()
