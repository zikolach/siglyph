package scalatui.terminal

import scalatui.syntax.Equality.*

import java.nio.charset.StandardCharsets
import scala.annotation.static

/**
 * Read-only semantic fields for a terminal render control on JVM and Scala Native.
 *
 * Applications may inspect or pattern-match these values. Constructing a details value does not
 * construct a [[TerminalRenderControl]] or grant terminal-control authority.
 */
enum TerminalRenderControlDetails derives CanEqual:
  /** Kitty image payload, identity, and display-cell footprint. */
  case KittyImage(
      payload: Base64ImagePayload,
      imageId: Int,
      widthCells: Int,
      heightCells: Int
  )

  /** iTerm2 image payload, optional filename, and display-cell footprint. */
  case ITerm2Image(
      payload: Base64ImagePayload,
      filename: Option[String],
      widthCells: Int,
      heightCells: Int
  )

  /** Kitty cleanup fields. `Some(id)` deletes one image; `None` deletes all images. */
  case KittyCleanup(imageId: Option[Int])

/**
 * A library-owned semantic terminal control shared by JVM and Scala Native component output.
 *
 * Applications can inspect [[details]] but cannot construct or subclass this value, add trusted
 * variants, or supply arbitrary escape strings. Controls are created by typed protocol helpers and
 * encoded only while the TUI assembles its final synchronized output. Image-looking bytes in an
 * ordinary [[scalatui.core.ComponentRender]] line do not become a control and gain no semantic
 * authority. Direct terminal backend writes remain outside this component-output trust boundary.
 */
final class TerminalRenderControl private (
    /** Read-only semantic fields for this control. */
    val details: TerminalRenderControlDetails
):
  /** Display-cell width occupied by this control; cleanup controls occupy zero columns. */
  val width: Int = details match
    case value: TerminalRenderControlDetails.KittyImage  => value.widthCells
    case value: TerminalRenderControlDetails.ITerm2Image => value.widthCells
    case _: TerminalRenderControlDetails.KittyCleanup    => 0

  /** Frame rows reserved by this control; cleanup controls use one anchor row. */
  val rows: Int = details match
    case value: TerminalRenderControlDetails.KittyImage  => value.heightCells
    case value: TerminalRenderControlDetails.ITerm2Image => value.heightCells
    case _: TerminalRenderControlDetails.KittyCleanup    => 1

  override def equals(other: Any): Boolean = other match
    case that: TerminalRenderControl => details === that.details
    case _                           => false

  override def hashCode(): Int = details.hashCode()

  /** Bounded diagnostic text that never includes image payload or filename content. */
  override def toString: String = details match
    case kitty: TerminalRenderControlDetails.KittyImage     =>
      s"TerminalRenderControl(kind=KittyImage,imageId=${kitty.imageId},width=${kitty.widthCells},rows=${kitty.heightCells})"
    case iterm: TerminalRenderControlDetails.ITerm2Image    =>
      s"TerminalRenderControl(kind=ITerm2Image,width=${iterm.widthCells},rows=${iterm.heightCells})"
    case cleanup: TerminalRenderControlDetails.KittyCleanup =>
      val imageId = cleanup.imageId.fold("")(value => s",imageId=$value")
      s"TerminalRenderControl(kind=KittyCleanup$imageId,width=0,rows=1)"

object TerminalRenderControl:
  /** Public alias for read-only semantic control details. */
  type Details = TerminalRenderControlDetails

  /** Public companion for inspecting and pattern-matching read-only semantic control details. */
  val Details: TerminalRenderControlDetails.type = TerminalRenderControlDetails

  @static private[terminal] def kittyImage(
      payload: Base64ImagePayload,
      imageId: Int,
      widthCells: Int,
      heightCells: Int
  ): TerminalRenderControl =
    require(imageId > 0, "Kitty image ID must be positive")
    require(widthCells > 0, "Kitty image width must be positive")
    require(heightCells > 0, "Kitty image rows must be positive")
    new TerminalRenderControl(Details.KittyImage(payload, imageId, widthCells, heightCells))

  @static private[terminal] def iTerm2Image(
      payload: Base64ImagePayload,
      filename: Option[String],
      widthCells: Int,
      heightCells: Int
  ): TerminalRenderControl =
    require(widthCells > 0, "iTerm2 image width must be positive")
    require(heightCells > 0, "iTerm2 image rows must be positive")
    new TerminalRenderControl(Details.ITerm2Image(payload, filename, widthCells, heightCells))

  @static private[terminal] def kittyCleanup(imageId: Option[Int]): TerminalRenderControl =
    require(imageId.forall(_ > 0), "Kitty cleanup image ID must be positive")
    new TerminalRenderControl(Details.KittyCleanup(imageId))

  /** Return the typed cleanup required before an old control placement is replaced or removed. */
  private[scalatui] def cleanupForReplacement(
      control: TerminalRenderControl
  ): Option[TerminalRenderControl] = control.details match
    case kitty: Details.KittyImage => Some(kittyCleanup(Some(kitty.imageId)))
    case _                         => None

/** Shared exhaustive raw encoder for core-owned semantic terminal controls. */
private[scalatui] object TerminalRenderControlEncoder:
  def encode(control: TerminalRenderControl): String = encodeDetails(control.details)

  private[terminal] def encodeDetails(details: TerminalRenderControlDetails): String =
    details match
      case kitty: TerminalRenderControlDetails.KittyImage     =>
        require(kitty.imageId > 0, "Kitty image ID must be positive")
        require(kitty.widthCells > 0, "Kitty image width must be positive")
        require(kitty.heightCells > 0, "Kitty image rows must be positive")
        s"\u001b_Ga=T,f=100,i=${kitty.imageId},c=${kitty.widthCells},r=${kitty.heightCells},C=1;${kitty.payload.value}\u001b\\"
      case iterm: TerminalRenderControlDetails.ITerm2Image    =>
        require(iterm.widthCells > 0, "iTerm2 image width must be positive")
        require(iterm.heightCells > 0, "iTerm2 image rows must be positive")
        val name = iterm.filename.fold("")(value =>
          s"name=${Base64ImagePayload.encode(value.getBytes(StandardCharsets.UTF_8)).value};"
        )
        s"\u001b]1337;File=${name}inline=1;width=${iterm.widthCells};height=${iterm.heightCells}:${iterm.payload.value}\u0007"
      case cleanup: TerminalRenderControlDetails.KittyCleanup =>
        cleanup.imageId match
          case Some(imageId) =>
            require(imageId > 0, "Kitty cleanup image ID must be positive")
            s"\u001b_Ga=d,d=I,i=$imageId\u001b\\"
          case None          => "\u001b_Ga=d,d=A\u001b\\"
