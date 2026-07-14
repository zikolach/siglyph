package scalatui.image

import scalatui.ansi.Ansi
import scalatui.core.{Component, TUI}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  Base64ImagePayloadError,
  ImageCellDimensions,
  ImageDimensions,
  ImageProtocol,
  ImageRenderOptions,
  TerminalCapabilities,
  TerminalImageProtocol,
  VirtualTerminal
}

import java.nio.file.Files

class ImageSuite extends munit.FunSuite:
  test("raw base64 factory rejects invalid input before creating an image"):
    val result = Image.fromBase64(
      "AAAA\u001b\\",
      ImageDimensions(1, 1),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
    )

    assertEquals(result, Left(Base64ImagePayloadError.InvalidStandardBase64))

  test("raw base64 factory constructs and renders a valid image"):
    val result = Image.fromBase64(
      "TQ",
      ImageDimensions(1, 1),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    )

    assert(result.isRight, result.toString)
    assert(result.toOption.get.render(10).head.contains(";TQ\u001b\\"))

  test("image component emits protocol rows and tracks Kitty id"):
    val image = Image(
      payload("AAAA"),
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
      payload("AAAA"),
      ImageDimensions(800, 600),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None),
      ImageRenderOptions(mimeType = "image/png", filename = Some("diagram.png"))
    )

    val lines = image.render(18)
    assertEquals(lines.length, 1)
    assert(lines.head.contains("image"), lines.head)
    assert(Ansi.visibleWidth(lines.head) <= 18, lines.head)
    assertEquals(image.cleanupSequence, None)

  test("image fallback escapes controls before width truncation"):
    val image = Image(
      payload("AAAA"),
      ImageDimensions(800, 600),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None),
      ImageRenderOptions(mimeType = "image/\u0000png\u0085", filename = Some("pic\u001b\u007f.png"))
    )

    val full = image.render(80).head
    assert(full.contains("pic\\u001B\\u007F.png"), full)
    assert(full.contains("image/\\u0000png\\u0085"), full)
    assert(!full.exists(char =>
      char === '\u0000' || char === '\u001b' || char === '\u007f' || char === '\u0085'
    ))
    assert(Ansi.visibleWidth(image.render(12).head) <= 12)

    val exactFit = Image(
      payload("AAAA"),
      ImageDimensions(8, 6),
      TerminalCapabilities(trueColor = false, hyperlinks = false, images = None),
      ImageRenderOptions(mimeType = "image/png", filename = Some("\u001b"))
    ).render(14).head
    assertEquals(exactFit, s"[image: \\u001B${Ansi.Reset}")
    assert(!exactFit.stripSuffix(Ansi.Reset).exists(_ === '\u001b'), exactFit)

  test("image source helper loads PNG file into component source contract"):
    val path = Files.createTempFile("siglyph-image", ".png")
    try
      Files.write(path, pngBytes(width = 16, height = 32))

      val source = ImageSource.fromFile(path)

      assert(source.isRight, source.toString)
      assertEquals(source.toOption.map(_.mimeType), Some("image/png"))
      assertEquals(source.toOption.map(_.dimensions), Some(ImageDimensions(16, 32)))
      assert(source.toOption.exists(_.payload.value.nonEmpty), source.toString)
    finally Files.deleteIfExists(path)

  test("dimension sniffer detects supported image formats"):
    assertEquals(
      ImageDimensionsSniffer.sniff(pngBytes(width = 16, height = 32)),
      Right(ImageMetadata("image/png", ImageDimensions(16, 32)))
    )
    assertEquals(
      ImageDimensionsSniffer.sniff(gifBytes(width = 17, height = 33)),
      Right(ImageMetadata("image/gif", ImageDimensions(17, 33)))
    )
    assertEquals(
      ImageDimensionsSniffer.sniff(jpegBytes(width = 18, height = 34)),
      Right(ImageMetadata("image/jpeg", ImageDimensions(18, 34)))
    )
    assertEquals(
      ImageDimensionsSniffer.sniff(webpVp8xBytes(width = 19, height = 35)),
      Right(ImageMetadata("image/webp", ImageDimensions(19, 35)))
    )

  test("dimension sniffer validates PNG IHDR chunk before reading dimensions"):
    val bytes = pngBytes(width = 16, height = 32)
    bytes(12) = 'N'.toByte
    bytes(13) = 'O'.toByte
    bytes(14) = 'T'.toByte
    bytes(15) = 'H'.toByte

    assert(
      ImageDimensionsSniffer.sniff(bytes)
        .left
        .exists(_.isInstanceOf[ImageHelperError.InvalidImage]),
      bytes.toString
    )

  test("dimension sniffer reports invalid and unsupported bytes safely"):
    assert(ImageDimensionsSniffer.sniff(Array[Byte](1, 2, 3)).left.exists(
      _.isInstanceOf[ImageHelperError.InvalidImage]
    ))
    assert(ImageDimensionsSniffer.sniff("not an image".getBytes).left.exists(
      _.isInstanceOf[ImageHelperError.UnsupportedFormat]
    ))

  test("image source helper reports unsupported file without rendering"):
    val path = Files.createTempFile("siglyph-image", ".txt")
    try
      Files.write(path, "not an image".getBytes)

      assert(ImageSource.fromFile(path).left.exists(
        _.isInstanceOf[ImageHelperError.UnsupportedFormat]
      ))
    finally Files.deleteIfExists(path)

  test("image sizing helper respects portrait height cap"):
    val size = ImageSizing.calculateCellSize(
      ImageDimensions(widthPx = 100, heightPx = 1000),
      maxWidthCells = 20,
      maxHeightCells = Some(5),
      cellDimensions = ImageCellDimensions(widthPx = 10, heightPx = 20)
    )

    assertEquals(size.widthCells, 1)
    assertEquals(size.heightCells, 5)

  test("Kitty image component reserves rows from protocol result"):
    val dimensions = ImageDimensions(widthPx = 100, heightPx = 100)
    val options    = ImageRenderOptions(maxWidthCells = Some(1))
    val caps       = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(
        ImageProtocol.Kitty
      )
    )

    val expected = TerminalImageProtocol.renderBase64Image(
      payload("AAAA"),
      dimensions,
      caps,
      terminalWidth = 20,
      options
    ).get

    val image = Image(payload("AAAA"), dimensions, caps, options)
    val lines = image.render(20)

    assertEquals(lines.length, expected.rows)
    assertEquals(lines.length >= 1, true)

  test("iTerm2 image component reserves rows from protocol result"):
    val dimensions = ImageDimensions(widthPx = 100, heightPx = 100)
    val options    = ImageRenderOptions(maxWidthCells = Some(1))
    val caps       = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(
        ImageProtocol.ITerm2
      )
    )

    val expected = TerminalImageProtocol.renderBase64Image(
      payload("AAAA"),
      dimensions,
      caps,
      terminalWidth = 20,
      options
    ).get

    val image = Image(payload("AAAA"), dimensions, caps, options)
    val lines = image.render(20)

    assertEquals(lines.length, expected.rows)
    assertEquals(lines.length >= 1, true)

  test("content after Kitty image appears below reserved rows"):
    val terminal     = VirtualTerminal(20, 10)
    val dimensions   = ImageDimensions(100, 100)
    val options      = ImageRenderOptions(maxWidthCells = Some(1))
    val caps         = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(ImageProtocol.Kitty)
    )
    val image        = Image(payload("AAAA"), dimensions, caps, options)
    val after        = FixedLine("after")
    val expectedRows =
      TerminalImageProtocol.renderBase64Image(
        payload("AAAA"),
        dimensions,
        caps,
        terminalWidth = 20,
        options
      ).get.rows

    val tui = TUI(terminal)
    tui.addChild(image)
    tui.addChild(after)
    tui.start()

    val lines      = visibleOutputLines(terminal.output)
    val afterIndex = lines.indexWhere(_.contains("after"))

    assert(afterIndex >= 0)
    assertEquals(afterIndex, expectedRows)

  test("content after iTerm2 image appears below reserved rows"):
    val terminal     = VirtualTerminal(20, 10)
    val dimensions   = ImageDimensions(100, 100)
    val options      = ImageRenderOptions(maxWidthCells = Some(1))
    val caps         = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(ImageProtocol.ITerm2)
    )
    val image        = Image(payload("AAAA"), dimensions, caps, options)
    val after        = FixedLine("after")
    val expectedRows =
      TerminalImageProtocol.renderBase64Image(
        payload("AAAA"),
        dimensions,
        caps,
        terminalWidth = 20,
        options
      ).get.rows

    val tui = TUI(terminal)
    tui.addChild(image)
    tui.addChild(after)
    tui.start()

    val lines      = visibleOutputLines(terminal.output)
    val afterIndex = lines.indexWhere(_.contains("after"))

    assert(afterIndex >= 0)
    assertEquals(afterIndex, expectedRows)

  private final class FixedLine(var value: String) extends Component:
    override def render(width: Int): Vector[String] = Vector(value)

  private def visibleOutputLines(output: String): Vector[String] =
    Ansi.strip(output)
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split("\n", -1)
      .toVector

  private def pngBytes(width: Int, height: Int): Array[Byte] =
    Array[Byte](
      0x89.toByte,
      'P'.toByte,
      'N'.toByte,
      'G'.toByte,
      0x0d.toByte,
      0x0a.toByte,
      0x1a.toByte,
      0x0a.toByte,
      0,
      0,
      0,
      13,
      'I'.toByte,
      'H'.toByte,
      'D'.toByte,
      'R'.toByte
    ) ++ int32BE(width) ++ int32BE(height)

  private def gifBytes(width: Int, height: Int): Array[Byte] =
    "GIF89a".getBytes ++ int16LE(width) ++ int16LE(height)

  private def jpegBytes(width: Int, height: Int): Array[Byte] =
    Array[Byte](
      0xff.toByte,
      0xd8.toByte,
      0xff.toByte,
      0xe0.toByte,
      0,
      2,
      0xff.toByte,
      0xc0.toByte,
      0,
      17,
      8
    ) ++ int16BE(height) ++ int16BE(width) ++ Array.fill[Byte](10)(0)

  private def webpVp8xBytes(width: Int, height: Int): Array[Byte] =
    "RIFF".getBytes ++ Array[Byte](0, 0, 0, 0) ++ "WEBP".getBytes ++
      "VP8X".getBytes ++ Array[Byte](10, 0, 0, 0, 0, 0, 0, 0) ++
      uint24LE(width - 1) ++ uint24LE(height - 1)

  private def int32BE(value: Int): Array[Byte] =
    Array(
      ((value >> 24) & 0xff).toByte,
      ((value >> 16) & 0xff).toByte,
      ((value >> 8) & 0xff).toByte,
      (value & 0xff).toByte
    )

  private def int16BE(value: Int): Array[Byte] =
    Array(((value >> 8) & 0xff).toByte, (value & 0xff).toByte)

  private def int16LE(value: Int): Array[Byte] =
    Array((value & 0xff).toByte, ((value >> 8) & 0xff).toByte)

  private def uint24LE(value: Int): Array[Byte] =
    Array(
      (value & 0xff).toByte,
      ((value >> 8) & 0xff).toByte,
      ((value >> 16) & 0xff).toByte
    )

  private def payload(value: String): Base64ImagePayload =
    Base64ImagePayload.from(value).toOption.get
