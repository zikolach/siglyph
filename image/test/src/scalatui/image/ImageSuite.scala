package scalatui.image

import scalatui.ansi.Ansi
import scalatui.terminal.{
  ImageCellDimensions,
  ImageDimensions,
  ImageProtocol,
  ImageRenderOptions,
  TerminalCapabilities
}

import java.nio.file.Files

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

  test("image source helper loads PNG file into component source contract"):
    val path = Files.createTempFile("siglyph-image", ".png")
    try
      Files.write(path, pngBytes(width = 16, height = 32))

      val source = ImageSource.fromFile(path)

      assert(source.isRight, source.toString)
      assertEquals(source.toOption.map(_.mimeType), Some("image/png"))
      assertEquals(source.toOption.map(_.dimensions), Some(ImageDimensions(16, 32)))
      assert(source.toOption.exists(_.base64Data.nonEmpty), source.toString)
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
