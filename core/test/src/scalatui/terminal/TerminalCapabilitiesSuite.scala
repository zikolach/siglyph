package scalatui.terminal

class TerminalCapabilitiesSuite extends munit.FunSuite:
  test("detects kitty capabilities"):
    assertEquals(
      TerminalCapabilities.detect(Map("TERM_PROGRAM" -> "kitty")),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )

  test("detects warp capabilities"):
    assertEquals(
      TerminalCapabilities.detect(Map("TERM_PROGRAM" -> "WarpTerminal")),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )
    assertEquals(
      TerminalCapabilities.detect(Map("WARP_SESSION_ID" -> "abc")),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )
    assertEquals(
      TerminalCapabilities.detect(Map("WARP_TERMINAL_SESSION_UUID" -> "uuid")),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )

  test("tmux blocks warp images despite term hints"):
    assertEquals(
      TerminalCapabilities.detect(
        Map(
          "TMUX"         -> "/tmp/tmux-1000/default,1234,0",
          "TERM_PROGRAM" -> "WarpTerminal",
          "TERM"         -> "tmux-256color"
        ),
        tmuxForwardsHyperlinks = true
      ),
      TerminalCapabilities(trueColor = false, hyperlinks = true, images = None)
    )
    assertEquals(
      TerminalCapabilities.detect(
        Map(
          "TERM"         -> "screen-256color",
          "TERM_PROGRAM" -> "WarpTerminal"
        )
      ),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
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
