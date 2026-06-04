package scalatui.terminal

class TerminalCapabilitiesSuite extends munit.FunSuite:
  test("detects kitty capabilities"):
    assertEquals(
      TerminalCapabilities.detect(Map("TERM_PROGRAM" -> "kitty")),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )

  test("tmux disables images and delegates hyperlinks"):
    assertEquals(
      TerminalCapabilities.detect(
        Map("TMUX" -> "1", "COLORTERM" -> "truecolor"),
        tmuxForwardsHyperlinks = true
      ),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)
    )

  test("unknown terminal is conservative"):
    assertEquals(
      TerminalCapabilities.detect(Map("TERM" -> "xterm-256color")),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
    )
