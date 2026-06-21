package scalatui.terminal

class TerminalColorProtocolSuite extends munit.FunSuite:
  test("parses OSC 11 six-digit hex background color"):
    assertEquals(
      TerminalColorProtocol.parseOsc11BackgroundColor("\u001b]11;#112233\u0007"),
      Some(RgbColor(17, 34, 51))
    )

  test("parses OSC 11 twelve-digit hex background color"):
    assertEquals(
      TerminalColorProtocol.parseOsc11BackgroundColor("\u001b]11;#ffff80000000\u0007"),
      Some(RgbColor(255, 128, 0))
    )

  test("parses OSC 11 rgb channel background color"):
    assertEquals(
      TerminalColorProtocol.parseOsc11BackgroundColor("\u001b]11;rgb:ffff/0000/8000\u001b\\"),
      Some(RgbColor(255, 0, 128))
    )

  test("rejects OSC 11 channels longer than four hex digits"):
    assertEquals(
      TerminalColorProtocol.parseOsc11BackgroundColor("\u001b]11;rgb:fffff/0000/8000\u001b\\"),
      None
    )

  test("rejects invalid OSC 11 payload"):
    assertEquals(TerminalColorProtocol.isOsc11BackgroundColorResponse("\u001b]11;nope\u0007"), true)
    assertEquals(TerminalColorProtocol.parseOsc11BackgroundColor("\u001b]11;nope\u0007"), None)

  test("parses terminal color-scheme reports"):
    assertEquals(TerminalColorProtocol.isTerminalColorSchemeReport("\u001b[?997;1n"), true)
    assertEquals(
      TerminalColorProtocol.parseTerminalColorSchemeReport("\u001b[?997;1n"),
      Some(TerminalColorScheme.Dark)
    )
    assertEquals(TerminalColorProtocol.isTerminalColorSchemeReport("\u001b[?997;2n"), true)
    assertEquals(
      TerminalColorProtocol.parseTerminalColorSchemeReport("\u001b[?997;2n"),
      Some(TerminalColorScheme.Light)
    )
    assertEquals(TerminalColorProtocol.isTerminalColorSchemeReport("\u001b[?997;3n"), true)
    assertEquals(TerminalColorProtocol.parseTerminalColorSchemeReport("\u001b[?997;3n"), None)
    assertEquals(TerminalColorProtocol.isTerminalColorSchemeReport("\u001b[?997;n"), false)
    assertEquals(TerminalColorProtocol.isTerminalColorSchemeReport("\u001b[?997;xn"), false)

  test("color scheme values are lowercase"):
    assertEquals(TerminalColorScheme.Dark.value, "dark")
    assertEquals(TerminalColorScheme.Light.value, "light")
