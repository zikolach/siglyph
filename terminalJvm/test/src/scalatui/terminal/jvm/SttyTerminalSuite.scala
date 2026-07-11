package scalatui.terminal.jvm

import scalatui.terminal.{KittyKeyboardProtocol, KittyKeyboardProtocolState, Terminal}

import java.io.{ByteArrayOutputStream, InputStream}

class SttyTerminalSuite extends munit.FunSuite:
  test("title and progress support writes terminal protocol sequences"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    assertEquals(Terminal.setTitle(terminal, "safe\u001btitle"), true)
    assertEquals(Terminal.setProgress(terminal, active = true), true)
    assertEquals(Terminal.setProgress(terminal, active = false), true)

    val written = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(written.contains("\u001b]0;safetitle\u0007"), written)
    assert(written.contains(Terminal.ProgressActiveSequence), written)
    assert(written.contains(Terminal.ProgressClearSequence), written)

  test("direct title support sanitizes control characters"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.setTitle("safe\u001btitle\u0007")

    assertEquals(
      output.toString(java.nio.charset.StandardCharsets.UTF_8),
      "\u001b]0;safetitle\u0007"
    )

  test("Kitty keyboard negotiation writes enable sequence after accepted response"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.requestKittyKeyboardProtocol(timeoutMillis = 1000)
    assertEquals(
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      ),
      true
    )

    val written = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(written.contains(KittyKeyboardProtocol.QuerySequence), written)
    assert(written.contains(KittyKeyboardProtocol.EnableSequence), written)
    assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Active(3))

  test("Kitty keyboard state expires pending negotiation on state read"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.requestKittyKeyboardProtocol(timeoutMillis = 0)

    val deadline = System.currentTimeMillis() + 1000L
    while
      terminal.keyboardProtocolState match
        case KittyKeyboardProtocolState.Pending(_, _) => System.currentTimeMillis() < deadline
        case _                                        => false
    do Thread.sleep(1)

    assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Inactive)

  test("interactive start and output-side operations do not synchronously deliver callbacks"):
    val terminal        = SttyTerminal()
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
