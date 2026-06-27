package scalatui.terminal

class TerminalInputParserSuite extends munit.FunSuite:
  test("parses basic keys and printable unicode"):
    assertEquals(TerminalInputParser.parseOne("\r"), TerminalInput.Key(TerminalKey.Enter))
    assertEquals(TerminalInputParser.parseOne("\u001b[A"), TerminalInput.Key(TerminalKey.Up))
    assertEquals(TerminalInputParser.parseOne("ä"), TerminalInput.Key(TerminalKey.Character("ä")))

  test("parses modified arrows"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[1;5D"),
      TerminalInput.Key(TerminalKey.Left, KeyModifiers(ctrl = true))
    )

  test("parses legacy alt+arrow variants"):
    assertEquals(
      TerminalInputParser.parseOne("\u001bB"),
      TerminalInput.Key(TerminalKey.Left, KeyModifiers(alt = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001bF"),
      TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001bb"),
      TerminalInput.Key(TerminalKey.Left, KeyModifiers(alt = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001bf"),
      TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true))
    )

  test("parses kitty csi-u printable key"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[97;5u"),
      TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001b[106;5u"),
      TerminalInput.Key(TerminalKey.Character("j"), KeyModifiers(ctrl = true))
    )

  test("keeps bare line feed as plain enter"):
    assertEquals(TerminalInputParser.parseOne("\n"), TerminalInput.Key(TerminalKey.Enter))

  test("parses kitty csi-u event metadata and super modifier"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[97;9:2u"),
      TerminalInput.KeyEvent(
        TerminalKey.Character("a"),
        KeyModifiers(superKey = true),
        KeyEventType.Repeat
      )
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001b[97;1:3u"),
      TerminalInput.KeyEvent(TerminalKey.Character("a"), KeyModifiers.empty, KeyEventType.Release)
    )

  test("parses kitty csi-u shifted/base keypad-style edge case"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[65::97;2:1u"),
      TerminalInput.KeyEvent(
        TerminalKey.Character("a"),
        KeyModifiers(shift = true),
        KeyEventType.Press
      )
    )

  test("kitty keyboard protocol negotiation ignores stale or mismatched responses"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    val first      = negotiator.begin(nowMillis = 1000, timeoutMillis = 10)
    negotiator.expire(nowMillis = 1011)

    assertEquals(first.id, 1L)
    assertEquals(negotiator.receiveResponse("\u001b[?1u", nowMillis = 1011), false)
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)

    negotiator.begin(nowMillis = 2000, timeoutMillis = 10)
    assertEquals(negotiator.receiveResponse("\u001b[?3u", nowMillis = 2011), false)
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)

    negotiator.begin(nowMillis = 3000, timeoutMillis = 10)
    assertEquals(negotiator.receiveResponse("\u001b[?not-a-response", nowMillis = 3001), false)
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Pending(3L, 3010L))
    assertEquals(negotiator.receiveResponse("\u001b[?3u", nowMillis = 3002), true)
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Active(3))

  test("parses xterm modifyOtherKeys"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[27;3;120~"),
      TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(alt = true))
    )

  test("parses jump-forward keybinding escapes"):
    assertEquals(
      TerminalInputParser.parseOne("\u001d"),
      TerminalInput.Key(TerminalKey.Character("]"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001b\u001d"),
      TerminalInput.Key(TerminalKey.Character("]"), KeyModifiers(ctrl = true, alt = true))
    )

  test("parses bracketed paste"):
    assertEquals(
      TerminalInputParser.parse("\u001b[200~hello\nworld\u001b[201~"),
      Vector(TerminalInput.Paste("hello\nworld"))
    )

  test("parses page navigation keys"):
    assertEquals(TerminalInputParser.parseOne("\u001b[5~"), TerminalInput.Key(TerminalKey.PageUp))
    assertEquals(TerminalInputParser.parseOne("\u001b[6~"), TerminalInput.Key(TerminalKey.PageDown))

  test("parses insert key and modified insert key"):
    assertEquals(TerminalInputParser.parseOne("\u001b[2~"), TerminalInput.Key(TerminalKey.Insert))
    assertEquals(
      TerminalInputParser.parseOne("\u001b[2;5~"),
      TerminalInput.Key(TerminalKey.Insert, KeyModifiers(ctrl = true))
    )

  test("normalizes common raw control bytes"):
    assertEquals(
      TerminalInputParser.parseOne("\u0003"),
      TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0001"),
      TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0005"),
      TerminalInput.Key(TerminalKey.Character("e"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0015"),
      TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u000b"),
      TerminalInput.Key(TerminalKey.Character("k"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u000c"),
      TerminalInput.Key(TerminalKey.Character("l"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0014"),
      TerminalInput.Key(TerminalKey.Character("t"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0017"),
      TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u0019"),
      TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001f"),
      TerminalInput.Key(TerminalKey.Character("-"), KeyModifiers(ctrl = true))
    )
    assertEquals(
      TerminalInputParser.parseOne("\u001b\u007f"),
      TerminalInput.Key(TerminalKey.Backspace, KeyModifiers(alt = true))
    )
