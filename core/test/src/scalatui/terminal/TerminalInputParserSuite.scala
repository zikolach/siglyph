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

  test("parses kitty csi-u printable key"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[97;5u"),
      TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true))
    )

  test("parses xterm modifyOtherKeys"):
    assertEquals(
      TerminalInputParser.parseOne("\u001b[27;3;120~"),
      TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(alt = true))
    )

  test("parses bracketed paste"):
    assertEquals(
      TerminalInputParser.parse("\u001b[200~hello\nworld\u001b[201~"),
      Vector(TerminalInput.Paste("hello\nworld"))
    )

  test("normalizes common raw control bytes"):
    assertEquals(TerminalInputParser.parseOne("\u0003"), TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u0001"), TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u0005"), TerminalInput.Key(TerminalKey.Character("e"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u0015"), TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u000b"), TerminalInput.Key(TerminalKey.Character("k"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u000c"), TerminalInput.Key(TerminalKey.Character("l"), KeyModifiers(ctrl = true)))
    assertEquals(TerminalInputParser.parseOne("\u0017"), TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))
