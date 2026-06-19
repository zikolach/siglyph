package scalatui.image

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  ImageDimensions,
  ImageCellDimensions,
  ImageCellSize,
  ImageRenderOptions,
  TerminalCapabilities,
  TerminalImageProtocol
}

import java.nio.file.{Files, Path}
import java.util.Base64

/** Theme hooks for the optional terminal image component. */
final case class ImageTheme(fallbackStyle: String => String = identity)

/** Loaded image data ready for the existing terminal image component contract. */
final case class ImageSource(
    base64Data: String,
    mimeType: String,
    dimensions: ImageDimensions,
    filename: Option[String] = None
) derives CanEqual

/** Typed failures returned by dependency-light image helpers. */
sealed trait ImageHelperError derives CanEqual:
  def message: String

object ImageHelperError:
  final case class UnsupportedFormat(message: String)          extends ImageHelperError
  final case class InvalidImage(message: String)               extends ImageHelperError
  final case class UnreadableFile(path: Path, message: String) extends ImageHelperError

/** Dependency-light file and byte helpers for terminal image sources. */
object ImageSource:
  def fromFile(path: Path): Either[ImageHelperError, ImageSource] =
    try
      if !Files.isRegularFile(path) || !Files.isReadable(path) then
        Left(ImageHelperError.UnreadableFile(path, s"Unreadable image file: $path"))
      else
        val bytes = Files.readAllBytes(path)
        fromBytes(bytes, Some(path.getFileName.toString))
    catch case e: Throwable => Left(ImageHelperError.UnreadableFile(path, e.getMessage))

  def fromBytes(
      bytes: Array[Byte],
      filename: Option[String] = None
  ): Either[ImageHelperError, ImageSource] =
    ImageDimensionsSniffer.sniff(bytes).map { metadata =>
      ImageSource(
        Base64.getEncoder.encodeToString(bytes),
        metadata.mimeType,
        metadata.dimensions,
        filename
      )
    }

/** Image metadata detected without decoding or rendering the full image. */
final case class ImageMetadata(mimeType: String, dimensions: ImageDimensions) derives CanEqual

/** Header-only dimension sniffer for common terminal image formats. */
object ImageDimensionsSniffer:
  def sniff(bytes: Array[Byte]): Either[ImageHelperError, ImageMetadata] =
    if bytes.length < 4 then Left(ImageHelperError.InvalidImage("Image data is too short"))
    else if isPng(bytes) then sniffPng(bytes)
    else if isGif(bytes) then sniffGif(bytes)
    else if isJpeg(bytes) then sniffJpeg(bytes)
    else if isWebP(bytes) then sniffWebP(bytes)
    else Left(ImageHelperError.UnsupportedFormat("Unsupported image format"))

  private def sniffPng(bytes: Array[Byte]): Either[ImageHelperError, ImageMetadata] =
    if bytes.length < 24 then Left(ImageHelperError.InvalidImage("PNG data is missing IHDR"))
    else
      Right(ImageMetadata(
        "image/png",
        ImageDimensions(readInt32BE(bytes, 16), readInt32BE(bytes, 20))
      ))

  private def sniffGif(bytes: Array[Byte]): Either[ImageHelperError, ImageMetadata] =
    if bytes.length < 10 then Left(ImageHelperError.InvalidImage("GIF data is missing dimensions"))
    else
      Right(ImageMetadata(
        "image/gif",
        ImageDimensions(readInt16LE(bytes, 6), readInt16LE(bytes, 8))
      ))

  private def sniffJpeg(bytes: Array[Byte]): Either[ImageHelperError, ImageMetadata] =
    var offset = 2
    while offset + 3 < bytes.length do
      while offset < bytes.length && (unsigned(bytes(offset)) !== 0xff) do offset += 1
      while offset < bytes.length && (unsigned(bytes(offset)) === 0xff) do offset += 1
      if offset >= bytes.length then
        return Left(ImageHelperError.InvalidImage("JPEG marker is incomplete"))
      val marker        = unsigned(bytes(offset))
      offset += 1
      if marker === 0xd9 || marker === 0xda then
        return Left(ImageHelperError.InvalidImage("JPEG dimensions were not found"))
      if offset + 1 >= bytes.length then
        return Left(ImageHelperError.InvalidImage("JPEG segment length is incomplete"))
      val segmentLength = readInt16BE(bytes, offset)
      if segmentLength < 2 || offset + segmentLength > bytes.length then
        return Left(ImageHelperError.InvalidImage("JPEG segment exceeds available data"))
      if isStartOfFrame(marker) then
        if segmentLength < 7 then
          return Left(ImageHelperError.InvalidImage("JPEG SOF is incomplete"))
        val height = readInt16BE(bytes, offset + 3)
        val width  = readInt16BE(bytes, offset + 5)
        return Right(ImageMetadata("image/jpeg", ImageDimensions(width, height)))
      offset += segmentLength
    Left(ImageHelperError.InvalidImage("JPEG dimensions were not found"))

  private def sniffWebP(bytes: Array[Byte]): Either[ImageHelperError, ImageMetadata] =
    if bytes.length < 20 then Left(ImageHelperError.InvalidImage("WebP data is incomplete"))
    else
      chunkType(bytes, 12) match
        case "VP8X" if bytes.length >= 30                                   =>
          val width  = readUInt24LE(bytes, 24) + 1
          val height = readUInt24LE(bytes, 27) + 1
          Right(ImageMetadata("image/webp", ImageDimensions(width, height)))
        case "VP8L" if bytes.length >= 25 && (unsigned(bytes(20)) === 0x2f) =>
          val b1     = unsigned(bytes(21))
          val b2     = unsigned(bytes(22))
          val b3     = unsigned(bytes(23))
          val b4     = unsigned(bytes(24))
          val width  = 1 + (((b2 & 0x3f) << 8) | b1)
          val height = 1 + (((b4 & 0x0f) << 10) | (b3 << 2) | ((b2 & 0xc0) >> 6))
          Right(ImageMetadata("image/webp", ImageDimensions(width, height)))
        case "VP8 "
            if bytes.length >= 30 && (unsigned(bytes(23)) === 0x9d) && (unsigned(
              bytes(24)
            ) === 0x01) && (unsigned(bytes(25)) === 0x2a) =>
          val width  = readInt16LE(bytes, 26) & 0x3fff
          val height = readInt16LE(bytes, 28) & 0x3fff
          Right(ImageMetadata("image/webp", ImageDimensions(width, height)))
        case _                                                              => Left(ImageHelperError.InvalidImage("WebP dimensions were not found"))

  private def isPng(bytes: Array[Byte]): Boolean =
    bytes.length >= 8 && bytes(0) === 0x89.toByte && bytes(1) === 'P'.toByte && bytes(
      2
    ) === 'N'.toByte && bytes(3) === 'G'.toByte && bytes(4) === 0x0d.toByte && bytes(
      5
    ) === 0x0a.toByte && bytes(6) === 0x1a.toByte && bytes(7) === 0x0a.toByte

  private def isGif(bytes: Array[Byte]): Boolean =
    bytes.length >= 6 && (ascii(bytes, 0, 6) === "GIF87a" || ascii(bytes, 0, 6) === "GIF89a")

  private def isJpeg(bytes: Array[Byte]): Boolean =
    bytes.length >= 2 && unsigned(bytes(0)) === 0xff && unsigned(bytes(1)) === 0xd8

  private def isWebP(bytes: Array[Byte]): Boolean =
    bytes.length >= 12 && ascii(bytes, 0, 4) === "RIFF" && ascii(bytes, 8, 4) === "WEBP"

  private def isStartOfFrame(marker: Int): Boolean =
    (marker >= 0xc0 && marker <= 0xc3) ||
      (marker >= 0xc5 && marker <= 0xc7) ||
      (marker >= 0xc9 && marker <= 0xcb) ||
      (marker >= 0xcd && marker <= 0xcf)

  private def chunkType(bytes: Array[Byte], offset: Int): String = ascii(bytes, offset, 4)

  private def ascii(bytes: Array[Byte], offset: Int, length: Int): String =
    String(bytes.slice(offset, offset + length), java.nio.charset.StandardCharsets.US_ASCII)

  private def readInt32BE(bytes: Array[Byte], offset: Int): Int =
    (unsigned(bytes(offset)) << 24) |
      (unsigned(bytes(offset + 1)) << 16) |
      (unsigned(bytes(offset + 2)) << 8) |
      unsigned(bytes(offset + 3))

  private def readInt16BE(bytes: Array[Byte], offset: Int): Int =
    (unsigned(bytes(offset)) << 8) | unsigned(bytes(offset + 1))

  private def readInt16LE(bytes: Array[Byte], offset: Int): Int =
    unsigned(bytes(offset)) | (unsigned(bytes(offset + 1)) << 8)

  private def readUInt24LE(bytes: Array[Byte], offset: Int): Int =
    unsigned(
      bytes(offset)
    ) | (unsigned(bytes(offset + 1)) << 8) | (unsigned(bytes(offset + 2)) << 16)

  private def unsigned(byte: Byte): Int = byte & 0xff

/** Public image sizing helpers for cell-bound planning before protocol output. */
object ImageSizing:
  def calculateCellSize(
      dimensions: ImageDimensions,
      maxWidthCells: Int,
      maxHeightCells: Option[Int] = None,
      cellDimensions: ImageCellDimensions = ImageCellDimensions()
  ): ImageCellSize =
    TerminalImageProtocol.calculateCellSize(
      dimensions,
      ImageRenderOptions(
        maxWidthCells = Some(maxWidthCells),
        maxHeightCells = maxHeightCells,
        cellDimensions = cellDimensions
      ),
      maxWidthCells
    )

/**
 * Optional image component that emits Kitty/iTerm2 protocol escapes when supported.
 *
 * The component accepts already-base64 encoded image data and caller-supplied dimensions so this
 * module stays dependency-free. File loading, header parsing, scaling, or transcoding can be added
 * by future optional helper modules without changing this component contract.
 */
final class Image(
    base64Data: String,
    dimensions: ImageDimensions,
    capabilities: TerminalCapabilities,
    options: ImageRenderOptions = ImageRenderOptions(),
    theme: ImageTheme = ImageTheme()
) extends Component:
  private var imageId = options.imageId

  /** Protocol image id used for Kitty render/reuse flows, if one has been allocated. */
  def currentImageId: Option[Int] = imageId

  override def render(width: Int): Vector[String] =
    val safeWidth = math.max(0, width)
    TerminalImageProtocol.renderBase64Image(
      base64Data,
      dimensions,
      capabilities,
      math.max(1, safeWidth),
      options.copy(imageId = imageId)
    ) match
      case Some(result) =>
        imageId = result.imageId.orElse(imageId)
        result.sequence +: Vector.fill(math.max(0, result.rows - 1))("")
      case None         =>
        Vector(theme.fallbackStyle(TerminalImageProtocol.fallback(
          dimensions,
          options.mimeType,
          options.filename,
          safeWidth
        )))

  /** Cleanup escape for the current image id when the active protocol supports one. */
  def cleanupSequence: Option[String] =
    imageId.flatMap(TerminalImageProtocol.deleteImage(_, capabilities))
