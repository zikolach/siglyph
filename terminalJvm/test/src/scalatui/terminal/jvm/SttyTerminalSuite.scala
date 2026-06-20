package scalatui.terminal.jvm

import scalatui.terminal.{KittyKeyboardProtocol, KittyKeyboardProtocolState}

import java.io.{ByteArrayOutputStream, InputStream}

class SttyTerminalSuite extends munit.FunSuite:
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
