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

/** Options for protocol image rendering. */
final case class ImageRenderOptions(
    mimeType: String = "image/png",
    filename: Option[String] = None,
    maxWidthCells: Option[Int] = None,
    maxHeightCells: Option[Int] = None,
    imageId: Option[Int] = None,
    cellDimensions: ImageCellDimensions = ImageCellDimensions(),
    cellDimensionsSource: ImageCellDimensionsSource = ImageCellDimensionsSource.Fixed
) derives CanEqual

/** Protocol render result and row reservation metadata. */
final case class ImageRenderResult(sequence: String, rows: Int, imageId: Option[Int])
    derives CanEqual

/** Dependency-free Kitty/iTerm2 terminal image protocol helpers. */
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

  private var nextImageId = 1

  /** Allocate a stable Kitty image id for reuse/update flows. */
  def allocateImageId(): Int =
    val id = nextImageId
    nextImageId += 1
    id

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

  /** Render base64 image data according to terminal capabilities. */
  def renderBase64Image(
      base64Data: String,
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
            encodeKitty(base64Data, imageId, size.widthCells, size.heightCells),
            size.heightCells,
            Some(imageId)
          )
        case ImageProtocol.ITerm2 =>
          ImageRenderResult(
            encodeITerm2(base64Data, options.filename, size.widthCells, size.heightCells),
            size.heightCells,
            None
          )
    }

  /** Encode a Kitty graphics protocol placement sequence for already-base64 image data. */
  def encodeKitty(
      base64Data: String,
      imageId: Int,
      widthCells: Int,
      heightCells: Int
  ): String =
    s"\u001b_Ga=T,f=100,i=$imageId,c=$widthCells,r=$heightCells,C=1;$base64Data\u001b\\"

  /** Encode an iTerm2 inline image sequence for already-base64 image data. */
  def encodeITerm2(
      base64Data: String,
      filename: Option[String],
      widthCells: Int,
      heightCells: Int
  ): String =
    val name = filename.fold("")(value => s"name=$value;")
    s"\u001b]1337;File=${name}inline=1;width=${widthCells};height=${heightCells}:$base64Data\u0007"

  /** Kitty delete-image sequence, or `None` when the protocol does not support this operation. */
  def deleteImage(imageId: Int, capabilities: TerminalCapabilities): Option[String] =
    capabilities.images.collect { case ImageProtocol.Kitty =>
      s"\u001b_Ga=d,d=i,i=$imageId\u001b\\"
    }

  /** Kitty delete-all sequence, or `None` when unsupported. */
  def deleteAllImages(capabilities: TerminalCapabilities): Option[String] =
    capabilities.images.collect { case ImageProtocol.Kitty => "\u001b_Ga=d,d=A\u001b\\" }

  /** Human-readable fallback constrained to `width` visible columns. */
  def fallback(
      dimensions: ImageDimensions,
      mimeType: String,
      filename: Option[String],
      width: Int
  ): String =
    val name = filename.fold("image")(identity)
    Ansi.truncateToWidth(
      s"[image: $name, $mimeType, ${dimensions.widthPx}x${dimensions.heightPx}]",
      math.max(0, width),
      ""
    )
