package scalatui.image

import scalatui.ansi.Ansi
import scalatui.terminal.{ImageDimensions, ImageProtocol, ImageRenderOptions, TerminalCapabilities}

class ImageSuite extends munit.FunSuite:
  test("image component emits protocol rows and tracks Kitty id"):
    val image = Image(
      "AAAA",
      ImageDimensions(100, 50),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      ImageRenderOptions(imageId = Some(42), maxWidthCells = Some(10))
    )

    val lines = image.render(20)
    assert(lines.head.startsWith("\u001b_G"), lines.head)
    assert(lines.head.contains("i=42"), lines.head)
    assertEquals(image.currentImageId, Some(42))
    assertEquals(image.cleanupSequence.exists(_.contains("i=42")), true)

  test("image component renders fallback on unsupported terminals"):
    val image = Image(
      "AAAA",
      ImageDimensions(800, 600),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None),
      ImageRenderOptions(mimeType = "image/png", filename = Some("diagram.png"))
    )

    val lines = image.render(18)
    assertEquals(lines.length, 1)
    assert(lines.head.contains("image"), lines.head)
    assert(Ansi.visibleWidth(lines.head) <= 18, lines.head)
    assertEquals(image.cleanupSequence, None)
