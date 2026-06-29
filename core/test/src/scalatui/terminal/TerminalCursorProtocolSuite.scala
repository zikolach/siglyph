package scalatui.terminal

class TerminalCursorProtocolSuite extends munit.FunSuite:
  test("parses cursor-position reports as zero-based coordinates"):
    assertEquals(
      TerminalCursorProtocol.parseCursorPositionReport("\u001b[5;12R"),
      Some(TerminalCursorProtocol.CursorPosition(row = 4, col = 11))
    )

  test("rejects invalid cursor-position reports"):
    assertEquals(TerminalCursorProtocol.parseCursorPositionReport("\u001b[0;1R"), None)
    assertEquals(TerminalCursorProtocol.parseCursorPositionReport("\u001b[1;0R"), None)
    assertEquals(TerminalCursorProtocol.parseCursorPositionReport("\u001b[1;1H"), None)
