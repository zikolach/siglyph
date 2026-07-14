package scalatui.image

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender, TerminalControlPlacement}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  Base64ImagePayloadError,
  ImageDimensions,
  ImageCellDimensions,
  ImageCellDimensionsSource,
  ImageCellSize,
  ImageRenderOptions,
  TerminalCapabilities,
  TerminalImageProtocol,
  TerminalRenderControl
}

import java.io.IOException
import java.nio.file.{Files, Path}

/** Theme hooks for the optional terminal image component. */
final case class ImageTheme(fallbackStyle: String => String = identity)

/**
 * Loaded image data with a validated terminal payload, MIME type, dimensions, and optional name.
 *
 * The shared [[scalatui.terminal.Base64ImagePayload]] contract is identical on JVM and Scala Native
 * and does not validate the image format or dimensions.
 */
final case class ImageSource(
    payload: Base64ImagePayload,
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
  /** Load a supported image file and encode its bytes as a validated terminal payload. */
  def fromFile(path: Path): Either[ImageHelperError, ImageSource] =
    try
      if !Files.isRegularFile(path) || !Files.isReadable(path) then
        Left(ImageHelperError.UnreadableFile(path, s"Unreadable image file: $path"))
      else
        val bytes = Files.readAllBytes(path)
        fromBytes(bytes, Some(path.getFileName.toString))
    catch
      case e: IOException       =>
        val message = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        Left(ImageHelperError.UnreadableFile(path, message))
      case e: SecurityException =>
        val message = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        Left(ImageHelperError.UnreadableFile(path, message))

  def fromBytes(
      bytes: Array[Byte],
      filename: Option[String] = None
  ): Either[ImageHelperError, ImageSource] =
    ImageDimensionsSniffer.sniff(bytes).map { metadata =>
      ImageSource(
        Base64ImagePayload.encode(bytes),
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
    else if ascii(bytes, 12, 4) !== "IHDR" then
      Left(ImageHelperError.InvalidImage("PNG data is missing IHDR"))
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
 * Optional image component that emits typed Kitty/iTerm2 controls when supported.
 *
 * The component requires a [[scalatui.terminal.Base64ImagePayload]] and caller-supplied dimensions
 * so this module stays dependency-free. It does not decode, validate, scale, or transcode images.
 * Supported output reserves ordinary blank rows and carries one semantic control separately. The
 * TUI validates that control's geometry and encodes it only while assembling final synchronized
 * output. Unsupported capability returns readable ordinary text with no control.
 *
 * By default, this high-level component opts into runtime terminal cell dimensions because it is
 * rendered inside a [[scalatui.core.TUI]] lifecycle that sends the cell-size query on start. Pass
 * [[scalatui.terminal.ImageRenderOptions]] with
 * [[scalatui.terminal.ImageCellDimensionsSource.Fixed]] when component rendering must use
 * caller-supplied deterministic cell dimensions.
 */
final class Image(
    payload: Base64ImagePayload,
    dimensions: ImageDimensions,
    capabilities: TerminalCapabilities,
    options: ImageRenderOptions = ImageRenderOptions(
      cellDimensionsSource = ImageCellDimensionsSource.Runtime
    ),
    theme: ImageTheme = ImageTheme()
) extends Component:
  private var imageId = options.imageId

  /** Protocol image id used for Kitty render/reuse flows, if one has been allocated. */
  def currentImageId: Option[Int] = imageId

  override def render(width: Int): ComponentRender =
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then ComponentRender.text("")
    else
      TerminalImageProtocol.renderBase64Image(
        payload,
        dimensions,
        capabilities,
        safeWidth,
        options.copy(imageId = imageId)
      ) match
        case Some(result) =>
          imageId = result.imageId.orElse(imageId)
          ComponentRender(
            Vector.fill(result.rows)(""),
            Vector(TerminalControlPlacement(row = 0, column = 0, result.control))
          )
        case None         =>
          ComponentRender.text(theme.fallbackStyle(TerminalImageProtocol.fallback(
            dimensions,
            options.mimeType,
            options.filename,
            safeWidth
          )))

  /**
   * Typed cleanup control for the current image id when the active protocol supports one.
   *
   * This method returns no raw sequence. The control is encoded only when included in TUI-owned
   * final output.
   */
  def cleanupSequence: Option[TerminalRenderControl] =
    imageId.flatMap(TerminalImageProtocol.deleteImage(_, capabilities))

object Image:
  /**
   * Validate raw standard base64 before constructing an image component on JVM or Scala Native.
   *
   * Invalid input returns [[scalatui.terminal.Base64ImagePayloadError]] and creates no component or
   * terminal output. Validation transiently allocates and discards decoded bytes. Image format,
   * dimensions, payload size, terminal support, scaling, and transcoding are not validated here.
   */
  def fromBase64(
      base64Data: String,
      dimensions: ImageDimensions,
      capabilities: TerminalCapabilities,
      options: ImageRenderOptions = ImageRenderOptions(
        cellDimensionsSource = ImageCellDimensionsSource.Runtime
      ),
      theme: ImageTheme = ImageTheme()
  ): Either[Base64ImagePayloadError, Image] =
    Base64ImagePayload.from(base64Data).map(payload =>
      new Image(payload, dimensions, capabilities, options, theme)
    )
