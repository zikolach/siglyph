package scalatui.core

import scalatui.terminal.{TerminalRenderControl, TerminalRenderControlDetails}

import scala.collection.mutable

/**
 * A typed terminal-control placement shared by JVM and Scala Native.
 *
 * `row` and `column` are zero-based, frame-relative coordinates. `column` is a display-cell column,
 * not a string offset. Construction rejects a negative coordinate. The complete [[control]]
 * footprint must also fit within the containing [[ComponentRender]] rows and requested width;
 * [[ComponentRender.validate]] reports a footprint that does not fit.
 */
final case class TerminalControlPlacement(
    /** Zero-based frame row. */
    row: Int,
    /** Zero-based display column. */
    column: Int,
    /** Library-owned semantic control placed at this anchor. */
    control: TerminalRenderControl
) derives CanEqual:
  require(row >= 0, "Terminal control row must be non-negative")
  require(column >= 0, "Terminal control column must be non-negative")

  /**
   * Translate this placement without copying its semantic control or payload.
   *
   * Translation throws [[IllegalArgumentException]] if an offset makes either coordinate negative.
   */
  def translated(rowOffset: Int = 0, columnOffset: Int = 0): TerminalControlPlacement =
    TerminalControlPlacement(row + rowOffset, column + columnOffset, control)

/** Bounded semantic kind used in control-validation diagnostics. */
enum ComponentRenderControlKind derives CanEqual:
  case KittyImage, ITerm2Image, KittyCleanup

/**
 * Bounded control geometry retained by validation failures.
 *
 * This value never retains a terminal control, image payload, filename, or application text.
 */
final case class ComponentRenderControlDiagnostic(
    kind: ComponentRenderControlKind,
    imageId: Option[Int],
    row: Int,
    column: Int,
    width: Int,
    rows: Int
) derives CanEqual

/**
 * A shared JVM/Native validation failure for a semantic control footprint.
 *
 * The TUI rejects this failure before writing the frame. It does not move, drop, partially encode,
 * or convert the control to ordinary text, and runtime failure cleanup follows the normal TUI path.
 * Error values and their default strings retain only bounded semantic kind, optional image ID,
 * geometry, frame dimensions, and duplicate coordinates. They never retain a placement, control,
 * payload, filename, or application text.
 */
enum ComponentRenderValidationError derives CanEqual:
  /** A control extends below the ordinary rows that reserve its frame footprint. */
  case ControlOutsideRows(control: ComponentRenderControlDiagnostic, frameRows: Int)

  /** A control extends beyond the requested display width. */
  case ControlOutsideWidth(control: ComponentRenderControlDiagnostic, frameWidth: Int)

  /** Two active Kitty image controls in one final frame use the same semantic image ID. */
  case DuplicateActiveKittyImageId(
      imageId: Int,
      first: ComponentRenderControlDiagnostic,
      duplicate: ComponentRenderControlDiagnostic
  )

/**
 * Component output shared by JVM and Scala Native.
 *
 * `lines` contains ordinary application text. `controls` contains separate semantic terminal
 * controls with frame-relative geometry. Ordinary strings never gain control authority from their
 * contents. Control placements must fit completely within the returned rows and requested width;
 * validation rejects partial footprints rather than moving, dropping, or converting them to text. A
 * final frame may contain at most one active Kitty image control per semantic image ID; cleanup
 * controls are not active image placements. Arbitrary trusted strings, protocol-prefix inference,
 * and a legacy line-vector render contract are not provided.
 */
final case class ComponentRender(
    /** Ordered ordinary application lines. Their contents grant no semantic control authority. */
    lines: Vector[String],
    /** Semantic controls anchored relative to `lines`. */
    controls: Vector[TerminalControlPlacement] = Vector.empty
) derives CanEqual:
  /**
   * Validate every control footprint against this frame and `width`.
   *
   * This method validates control geometry and unique active Kitty image IDs. Components remain
   * responsible for fitting ordinary lines within the requested display width. The TUI calls this
   * validation before output and rejects `Left` as an [[IllegalArgumentException]] before any frame
   * bytes are written. Kitty IDs are compared as integers without rebuilding payload strings.
   */
  def validate(width: Int): Either[ComponentRenderValidationError, Unit] =
    val activeKittyPlacements = mutable.HashMap.empty[Int, ComponentRenderControlDiagnostic]
    controls.iterator
      .map { placement =>
        validatePlacement(placement, width).flatMap { _ =>
          placement.control.details match
            case kitty: TerminalRenderControlDetails.KittyImage =>
              val diagnostic = controlDiagnostic(placement)
              activeKittyPlacements.get(kitty.imageId) match
                case Some(first) => Left(
                    ComponentRenderValidationError.DuplicateActiveKittyImageId(
                      kitty.imageId,
                      first,
                      diagnostic
                    )
                  )
                case None        =>
                  activeKittyPlacements.put(kitty.imageId, diagnostic)
                  Right(())
            case _                                              => Right(())
        }
      }
      .collectFirst { case Left(error) => Left(error) }
      .getOrElse(Right(()))

  /**
   * Return this frame after validation, or reject an invalid surviving control before output.
   */
  private[scalatui] def validated(width: Int): ComponentRender =
    validate(width) match
      case Right(())   => this
      case Left(error) => throw IllegalArgumentException(error.toString)

  /**
   * Translate every placement without copying controls or payloads.
   *
   * Translation throws [[IllegalArgumentException]] if an offset makes any coordinate negative.
   */
  def translated(rowOffset: Int = 0, columnOffset: Int = 0): ComponentRender =
    copy(controls = controls.map(_.translated(rowOffset, columnOffset)))

  private def validatePlacement(
      placement: TerminalControlPlacement,
      width: Int
  ): Either[ComponentRenderValidationError, Unit] =
    val frameWidth = math.max(0, width)
    if placement.row > lines.length - placement.control.rows then
      Left(ComponentRenderValidationError.ControlOutsideRows(
        controlDiagnostic(placement),
        lines.length
      ))
    else if placement.column > frameWidth - placement.control.width then
      Left(ComponentRenderValidationError.ControlOutsideWidth(
        controlDiagnostic(placement),
        frameWidth
      ))
    else Right(())

  private def controlDiagnostic(
      placement: TerminalControlPlacement
  ): ComponentRenderControlDiagnostic =
    val (kind, imageId) = placement.control.details match
      case kitty: TerminalRenderControlDetails.KittyImage     =>
        ComponentRenderControlKind.KittyImage -> Some(kitty.imageId)
      case _: TerminalRenderControlDetails.ITerm2Image        =>
        ComponentRenderControlKind.ITerm2Image -> None
      case cleanup: TerminalRenderControlDetails.KittyCleanup =>
        ComponentRenderControlKind.KittyCleanup -> cleanup.imageId
    ComponentRenderControlDiagnostic(
      kind,
      imageId,
      placement.row,
      placement.column,
      placement.control.width,
      placement.control.rows
    )

object ComponentRender:
  /** Construct shared JVM/Native text-only component output with no terminal controls. */
  def text(lines: Vector[String]): ComponentRender = ComponentRender(lines)

  /** Construct one-line shared JVM/Native text-only component output with no terminal controls. */
  def text(line: String): ComponentRender = ComponentRender(Vector(line))

  /** Empty shared JVM/Native text-only component output. */
  val empty: ComponentRender = ComponentRender(Vector.empty)
