package scalatui.terminal

import scalatui.ansi.Ansi

class TerminalImageProtocolSuite extends munit.FunSuite:
  test("renders Kitty and iTerm2 protocol paths when capabilities allow"):
    val dimensions = ImageDimensions(100, 50)
    val kitty      = TerminalImageProtocol.renderBase64Image(
      "AAAA",
      dimensions,
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      terminalWidth = 20,
      ImageRenderOptions(imageId = Some(7))
    ).get
    assert(kitty.sequence.startsWith("\u001b_G"), kitty.sequence)
    assert(kitty.sequence.contains("i=7"), kitty.sequence)
    assertEquals(kitty.imageId, Some(7))
    assert(kitty.rows >= 1)

    val iterm = TerminalImageProtocol.renderBase64Image(
      "AAAA",
      dimensions,
      TerminalCapabilities(
        trueColor = true,
        hyperlinks = true,
        images = Some(ImageProtocol.ITerm2)
      ),
      terminalWidth = 20,
      ImageRenderOptions(filename = Some("pic.png"))
    ).get
    assert(iterm.sequence.startsWith("\u001b]1337;File="), iterm.sequence)
    assert(iterm.sequence.contains("name=pic.png"), iterm.sequence)

  test("unknown terminals return no protocol render and no cleanup"):
    val caps = TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
    assertEquals(
      TerminalImageProtocol.renderBase64Image(
        "AAAA",
        ImageDimensions(10, 10),
        caps,
        terminalWidth = 10
      ),
      None
    )
    assertEquals(TerminalImageProtocol.deleteImage(1, caps), None)
    assertEquals(TerminalImageProtocol.deleteAllImages(caps), None)

  test("fallback is readable and width-safe"):
    val fallback = TerminalImageProtocol.fallback(
      ImageDimensions(800, 600),
      "image/png",
      Some("very-long-file-name.png"),
      width = 16
    )
    assert(Ansi.visibleWidth(fallback) <= 16, fallback)
