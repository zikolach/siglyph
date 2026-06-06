package scalatui.image

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  ImageDimensions,
  ImageRenderOptions,
  TerminalCapabilities,
  TerminalImageProtocol
}

/** Theme hooks for the optional terminal image component. */
final case class ImageTheme(fallbackStyle: String => String = identity)

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
