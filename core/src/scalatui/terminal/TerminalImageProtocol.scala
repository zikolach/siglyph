package scalatui.terminal

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

/** Pixel dimensions for an image supplied by the application or optional image helpers. */
final case class ImageDimensions(widthPx: Int, heightPx: Int) derives CanEqual

/** Terminal cell dimensions used to reserve image output rows. */
final case class ImageCellDimensions(widthPx: Int = 9, heightPx: Int = 18) derives CanEqual

/** Cell size selected for protocol rendering. */
final case class ImageCellSize(widthCells: Int, heightCells: Int) derives CanEqual

/** Source for terminal cell dimensions used by protocol image rendering. */
enum ImageCellDimensionsSource derives CanEqual:
  /** Use caller-supplied [[ImageRenderOptions.cellDimensions]] exactly. */
  case Fixed

  /** Use the last valid runtime terminal cell-size reply, or fallback dimensions before a reply. */
  case Runtime

/**
 * Options for protocol image rendering.
 *
 * Low-level protocol sizing is deterministic by default: `cellDimensionsSource` defaults to
 * [[ImageCellDimensionsSource.Fixed]] and uses `cellDimensions` exactly. Use
 * [[ImageCellDimensionsSource.Runtime]] only when sizing should read the runtime cell-size cache
 * maintained by [[TerminalImageProtocol]].
 */
final case class ImageRenderOptions(
    mimeType: String = "image/png",
    filename: Option[String] = None,
    maxWidthCells: Option[Int] = None,
    maxHeightCells: Option[Int] = None,
    imageId: Option[Int] = None,
    cellDimensions: ImageCellDimensions = ImageCellDimensions(),
    cellDimensionsSource: ImageCellDimensionsSource = ImageCellDimensionsSource.Fixed
) derives CanEqual:
  require(imageId.forall(_ > 0), "Configured Kitty image ID must be positive")

/**
 * Typed JVM/Native image output and explicit display geometry.
 *
 * For values returned by [[TerminalImageProtocol.renderBase64Image]], `width` and `rows` match the
 * control footprint, and `imageId` identifies Kitty image reuse and cleanup when present. A
 * component reserves `rows` ordinary frame lines and places `control` within that geometry. The TUI
 * validates the placement and encodes the control only while assembling final synchronized output;
 * this value contains no raw protocol string.
 */
final case class ImageRenderResult(
    /** Closed semantic image control. */
    control: TerminalRenderControl,
    /** Display-cell width reserved for the image. */
    width: Int,
    /** Frame rows reserved for the image. */
    rows: Int,
    /** Kitty image identity, or `None` for protocols without that identity. */
    imageId: Option[Int]
) derives CanEqual

/**
 * Dependency-free Kitty/iTerm2 terminal image protocol helpers for JVM and Scala Native.
 *
 * Image payload entry points require [[Base64ImagePayload]] and do not validate image format,
 * dimensions, size, terminal capability claims, or payload content beyond that type's contract.
 * Helpers return closed [[TerminalRenderControl]] values, never arbitrary trusted strings. The TUI
 * validates their frame geometry and encodes them only at final output. Direct terminal backend
 * writes remain outside this component-output contract.
 */
object TerminalImageProtocol:
  private val defaultCellDimensions   = ImageCellDimensions(widthPx = 9, heightPx = 18)
  private val defaultCellSizeResponse = "^\u001b\\[6;(\\d+);(\\d+)t$".r
  private var currentCellDimensions   = defaultCellDimensions

  /** Query terminal for terminal cell dimensions in pixels. */
  val QueryCellDimensions: String = "\u001b[16t"

  /** Last known terminal cell dimensions used for image layout decisions. */
  def cellDimensions: ImageCellDimensions = synchronized(currentCellDimensions)

  /** Parse a terminal cell-size report into validated pixel dimensions. */
  def parseCellSizeResponse(data: String): Option[ImageCellDimensions] = data match
    case defaultCellSizeResponse(heightText, widthText) =>
      for
        widthPx  <- positiveInt(widthText)
        heightPx <- positiveInt(heightText)
      yield ImageCellDimensions(widthPx = widthPx, heightPx = heightPx)
    case _                                              => None

  /** Return true when data matches the terminal cell-size report format. */
  def isCellSizeResponse(data: String): Boolean = defaultCellSizeResponse.matches(data)

  /**
   * Update cached terminal cell dimensions for image layout, returning the normalized value when
   * valid.
   */
  def setCellDimensions(widthPx: Int, heightPx: Int): Option[ImageCellDimensions] =
    Option.when(widthPx > 0 && heightPx > 0) {
      val updated = ImageCellDimensions(widthPx, heightPx)
      synchronized { currentCellDimensions = updated }
      updated
    }

  /** Reset cached dimensions to the built-in deterministic fallback. */
  def resetCellDimensions(): Unit = synchronized { currentCellDimensions = defaultCellDimensions }

  private val imageIdAllocator = KittyImageIdAllocator()

  /** Allocate a stable Kitty image id for reuse/update flows. */
  def allocateImageId(): Int = imageIdAllocator.allocate()

  /** Calculate image cell size preserving aspect ratio within requested cell bounds. */
  def calculateCellSize(
      dimensions: ImageDimensions,
      options: ImageRenderOptions,
      terminalWidth: Int
  ): ImageCellSize =
    val maxWidth         =
      math.max(1, math.min(terminalWidth, options.maxWidthCells.getOrElse(terminalWidth)))
    val sourceWidth      = math.max(1, dimensions.widthPx)
    val sourceHeight     = math.max(1, dimensions.heightPx)
    val cell             = effectiveCellDimensions(options)
    val maxHeight        = options.maxHeightCells.map(math.max(1, _))
    val widthFromHeight  = maxHeight.map { heightCap =>
      math.max(
        1,
        math.floor(
          (heightCap.toDouble * cell.heightPx.toDouble * sourceWidth.toDouble) / (cell.widthPx.toDouble * sourceHeight.toDouble)
        ).toInt
      )
    }
    var widthCells       = math.min(maxWidth, widthFromHeight.getOrElse(maxWidth))
    var heightFromAspect = heightForWidth(widthCells, sourceWidth, sourceHeight, cell)
    maxHeight.foreach { heightCap =>
      while heightFromAspect > heightCap && widthCells > 1 do
        widthCells -= 1
        heightFromAspect = heightForWidth(widthCells, sourceWidth, sourceHeight, cell)
    }
    ImageCellSize(
      widthCells,
      maxHeight.fold(heightFromAspect)(heightCap => math.min(heightFromAspect, heightCap))
    )

  private def heightForWidth(
      widthCells: Int,
      sourceWidth: Int,
      sourceHeight: Int,
      cell: ImageCellDimensions
  ): Int =
    math.max(
      1,
      math.ceil(
        (widthCells.toDouble * cell.widthPx.toDouble * sourceHeight.toDouble) / (cell.heightPx.toDouble * sourceWidth.toDouble)
      ).toInt
    )

  private def effectiveCellDimensions(options: ImageRenderOptions): ImageCellDimensions =
    options.cellDimensionsSource match
      case ImageCellDimensionsSource.Fixed   => options.cellDimensions
      case ImageCellDimensionsSource.Runtime => cellDimensions

  private def positiveInt(value: String): Option[Int] = value.toIntOption.filter(_ > 0)

  /**
   * Render a validated base64 payload according to terminal capabilities.
   *
   * Payload validation is performed by [[Base64ImagePayload]] before this helper can be called.
   * `None` means that no supported image protocol is available. A returned result contains the
   * typed control and exact geometry that a component must reserve; protocol bytes are not encoded
   * here.
   */
  def renderBase64Image(
      payload: Base64ImagePayload,
      dimensions: ImageDimensions,
      capabilities: TerminalCapabilities,
      terminalWidth: Int,
      options: ImageRenderOptions = ImageRenderOptions()
  ): Option[ImageRenderResult] =
    capabilities.images.map { protocol =>
      val size = calculateCellSize(dimensions, options, terminalWidth)
      protocol match
        case ImageProtocol.Kitty  =>
          val imageId = options.imageId.getOrElse(allocateImageId())
          ImageRenderResult(
            encodeKitty(payload, imageId, size.widthCells, size.heightCells),
            size.widthCells,
            size.heightCells,
            Some(imageId)
          )
        case ImageProtocol.ITerm2 =>
          ImageRenderResult(
            encodeITerm2(payload, options.filename, size.widthCells, size.heightCells),
            size.widthCells,
            size.heightCells,
            None
          )
    }

  /**
   * Construct a typed Kitty image control that retains validated payload text unchanged.
   *
   * `imageId`, `widthCells`, and `heightCells` must be positive; invalid values throw
   * [[IllegalArgumentException]]. The control gains authority through this typed helper and is
   * encoded only by final TUI output.
   */
  def encodeKitty(
      payload: Base64ImagePayload,
      imageId: Int,
      widthCells: Int,
      heightCells: Int
  ): TerminalRenderControl =
    TerminalRenderControl.kittyImage(payload, imageId, widthCells, heightCells)

  /**
   * Construct a typed iTerm2 inline image control with unchanged validated payload text.
   *
   * A present filename is standard-base64 encoded from UTF-8 bytes. An absent filename emits no
   * `name=` field. `widthCells` and `heightCells` must be positive; invalid values throw
   * [[IllegalArgumentException]]. Encoding occurs only in final TUI output.
   */
  def encodeITerm2(
      payload: Base64ImagePayload,
      filename: Option[String],
      widthCells: Int,
      heightCells: Int
  ): TerminalRenderControl =
    TerminalRenderControl.iTerm2Image(payload, filename, widthCells, heightCells)

  /**
   * Return a typed Kitty uppercase-I delete-image control, or `None` when Kitty cleanup is
   * unsupported. Final encoding is `a=d,d=I,i=<positive id>`, which removes the targeted image data
   * and placements before retransmission. Encoding occurs only in final TUI output.
   */
  def deleteImage(
      imageId: Int,
      capabilities: TerminalCapabilities
  ): Option[TerminalRenderControl] =
    capabilities.images.collect { case ImageProtocol.Kitty =>
      TerminalRenderControl.kittyCleanup(Some(imageId))
    }

  /**
   * Return a typed Kitty delete-all control, or `None` when Kitty cleanup is unsupported. Encoding
   * occurs only in final TUI output.
   */
  def deleteAllImages(capabilities: TerminalCapabilities): Option[TerminalRenderControl] =
    capabilities.images.collect { case ImageProtocol.Kitty =>
      TerminalRenderControl.kittyCleanup(None)
    }

  /** Human-readable fallback constrained to `width` visible columns. */
  def fallback(
      dimensions: ImageDimensions,
      mimeType: String,
      filename: Option[String],
      width: Int
  ): String =
    val name = Ansi.visibleControlText(filename.fold("image")(identity))
    val mime = Ansi.visibleControlText(mimeType)
    Ansi.truncateToWidth(
      s"[image: $name, $mime, ${dimensions.widthPx}x${dimensions.heightPx}]",
      math.max(0, width),
      ""
    )

/** Bounded Kitty image identity state that never wraps into a non-positive value. */
private[terminal] final class KittyImageIdAllocator(initialNextId: Int = 1):
  require(initialNextId > 0, "Initial Kitty image ID must be positive")

  private var nextId = Option(initialNextId)

  /** Allocate the next positive ID, or fail after `Int.MaxValue` has been allocated. */
  def allocate(): Int = synchronized {
    val id = nextId.getOrElse(throw IllegalStateException("Kitty image ID allocator exhausted"))
    nextId = Option.when(id < Int.MaxValue)(id + 1)
    id
  }
