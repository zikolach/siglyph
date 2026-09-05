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
  case KittyImage, KittyPlacement, ITerm2Image, KittyCleanup, KittyPlacementCleanup

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
 * A shared JVM/Native validation failure for frame-relative control or cursor geometry.
 *
 * The TUI rejects this failure before writing the frame. It does not move, drop, partially encode,
 * or convert the control to ordinary text, and runtime failure cleanup follows the normal TUI path.
 * Error values and their default strings retain only bounded semantic kind, optional image ID,
 * geometry, frame dimensions, and duplicate coordinates. They never retain a control, payload,
 * filename, or application text.
 */
enum ComponentRenderValidationError derives CanEqual:
  /** A control extends below the ordinary rows that reserve its frame footprint. */
  case ControlOutsideRows(control: ComponentRenderControlDiagnostic, frameRows: Int)

  /** A control extends beyond the requested display width. */
  case ControlOutsideWidth(control: ComponentRenderControlDiagnostic, frameWidth: Int)

  /** A cursor candidate uses a row outside the returned frame rows. */
  case CursorOutsideRows(row: Int, column: Int, frameRows: Int)

  /** A cursor candidate uses a column outside the requested display width. */
  case CursorOutsideWidth(row: Int, column: Int, frameWidth: Int)

  /** A typed document marker uses a row outside the returned frame rows. */
  case DocumentPositionOutsideRows(row: Int, column: Int, frameRows: Int)

  /** A typed document marker uses a column beyond the requested display width. */
  case DocumentPositionOutsideWidth(row: Int, column: Int, frameWidth: Int)

  /** Two active Kitty image controls in one final frame use the same semantic image ID. */
  case DuplicateActiveKittyImageId(
      imageId: Int,
      first: ComponentRenderControlDiagnostic,
      duplicate: ComponentRenderControlDiagnostic
  )

/** A normalized position in rendered document rows and display-cell columns. */
final case class DocumentPosition(row: Int, column: Int = 0) derives CanEqual:
  require(row >= 0, "Document position row must be non-negative")
  require(column >= 0, "Document position column must be non-negative")

/** Typed semantic metadata attached to a rendered document position. */
sealed trait DocumentMarker derives CanEqual:
  def position: DocumentPosition

/** Marks the start of a shell prompt without granting authority to ordinary rendered strings. */
final case class PromptStart(position: DocumentPosition) extends DocumentMarker derives CanEqual

/** Optional typed document metadata retained independently from text and terminal controls. */
final case class DocumentMetadata(markers: Vector[DocumentMarker] = Vector.empty) derives CanEqual:
  /** Translate marker geometry while preserving marker types. */
  def translated(rowOffset: Int = 0, columnOffset: Int = 0): DocumentMetadata =
    DocumentMetadata(markers.map {
      case PromptStart(position) => PromptStart(DocumentPosition(
          position.row + rowOffset,
          position.column + columnOffset
        ))
    })

object DocumentMetadata:
  /** Empty typed document metadata. */
  val empty: DocumentMetadata = DocumentMetadata()

/**
 * Component output shared by JVM and Scala Native.
 *
 * `lines` contains ordinary application text. `controls` contains separate semantic terminal
 * controls. `cursorPlacements` contains hardware-cursor candidates. Both metadata channels use
 * frame-relative display-cell geometry and remain independent from ordinary strings. Every field is
 * required explicitly; text-only factories construct explicit empty metadata. Cursor candidates
 * must identify an existing row and use a column below `max(0, width)`. Control footprints must fit
 * completely within the returned rows and requested width. A composing parent must validate each
 * child against the child's own rows and requested width before translation or sibling composition.
 * Validation rejects invalid geometry rather than moving, dropping, or converting it to text. A
 * final frame may contain at most one active Kitty image control per semantic image ID; cleanup
 * controls are not active image placements. Arbitrary trusted strings and protocol-prefix inference
 * are not provided.
 */
final case class ComponentRender(
    /** Ordered ordinary application lines. Their contents grant no semantic control authority. */
    lines: Vector[String],
    /** Semantic controls anchored relative to `lines`. */
    controls: Vector[TerminalControlPlacement],
    /** Hardware-cursor candidates anchored relative to `lines`. */
    cursorPlacements: Vector[CursorPlacement],
    /** Optional semantic document markers anchored relative to `lines`. */
    documentMetadata: DocumentMetadata = DocumentMetadata.empty
) derives CanEqual:
  /**
   * Validate every cursor candidate and control footprint against this frame and `width`.
   *
   * Components remain responsible for fitting ordinary lines within the requested display width.
   * The TUI calls this validation before output and rejects `Left` as an
   * [[IllegalArgumentException]] before any frame bytes are written. Diagnostics retain bounded
   * geometry but no application text. Kitty IDs are compared as integers without rebuilding payload
   * strings.
   */
  def validate(width: Int): Either[ComponentRenderValidationError, Unit] =
    val frameWidth  = math.max(0, width)
    val cursorError = cursorPlacements.iterator
      .map { placement =>
        if placement.row >= lines.length then
          Left(ComponentRenderValidationError.CursorOutsideRows(
            placement.row,
            placement.column,
            lines.length
          ))
        else if placement.column >= frameWidth then
          Left(ComponentRenderValidationError.CursorOutsideWidth(
            placement.row,
            placement.column,
            frameWidth
          ))
        else Right(())
      }
      .collectFirst { case Left(error) => Left(error) }

    cursorError.orElse {
      documentMetadata.markers.iterator
        .map(_.position)
        .map { position =>
          if position.row >= lines.length then
            Left(ComponentRenderValidationError.DocumentPositionOutsideRows(
              position.row,
              position.column,
              lines.length
            ))
          else if position.column > frameWidth then
            Left(ComponentRenderValidationError.DocumentPositionOutsideWidth(
              position.row,
              position.column,
              frameWidth
            ))
          else Right(())
        }
        .collectFirst { case Left(error) => Left(error) }
    }.getOrElse {
      val activeKittyPlacements = mutable.HashMap.empty[Int, ComponentRenderControlDiagnostic]
      controls.iterator
        .map { placement =>
          validatePlacement(placement, width).flatMap { _ =>
            placement.control.details match
              case kitty: TerminalRenderControlDetails.KittyImage     =>
                validateActiveKitty(
                  kitty.imageId,
                  placement,
                  activeKittyPlacements
                )
              case kitty: TerminalRenderControlDetails.KittyPlacement =>
                validateActiveKitty(
                  kitty.imageId,
                  placement,
                  activeKittyPlacements
                )
              case _                                                  => Right(())
          }
        }
        .collectFirst { case Left(error) => Left(error) }
        .getOrElse(Right(()))
    }

  /**
   * Return this frame after validation, or reject invalid surviving metadata before output.
   */
  private[scalatui] def validated(width: Int): ComponentRender =
    validate(width) match
      case Right(())   => this
      case Left(error) => throw IllegalArgumentException(error.toString)

  /**
   * Translate every metadata placement without copying controls or payloads.
   *
   * Translation throws [[IllegalArgumentException]] if an offset makes any coordinate negative.
   */
  def translated(rowOffset: Int = 0, columnOffset: Int = 0): ComponentRender =
    copy(
      controls = controls.map(_.translated(rowOffset, columnOffset)),
      cursorPlacements = cursorPlacements.map(_.translated(rowOffset, columnOffset)),
      documentMetadata = documentMetadata.translated(rowOffset, columnOffset)
    )

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

  private def validateActiveKitty(
      imageId: Int,
      placement: TerminalControlPlacement,
      active: mutable.HashMap[Int, ComponentRenderControlDiagnostic]
  ): Either[ComponentRenderValidationError, Unit] =
    val diagnostic = controlDiagnostic(placement)
    active.get(imageId) match
      case Some(first) => Left(ComponentRenderValidationError.DuplicateActiveKittyImageId(
          imageId,
          first,
          diagnostic
        ))
      case None        =>
        active.put(imageId, diagnostic)
        Right(())

  private def controlDiagnostic(
      placement: TerminalControlPlacement
  ): ComponentRenderControlDiagnostic =
    val (kind, imageId) = placement.control.details match
      case kitty: TerminalRenderControlDetails.KittyImage              =>
        ComponentRenderControlKind.KittyImage -> Some(kitty.imageId)
      case kitty: TerminalRenderControlDetails.KittyPlacement          =>
        ComponentRenderControlKind.KittyPlacement -> Some(kitty.imageId)
      case _: TerminalRenderControlDetails.ITerm2Image                 =>
        ComponentRenderControlKind.ITerm2Image -> None
      case cleanup: TerminalRenderControlDetails.KittyCleanup          =>
        ComponentRenderControlKind.KittyCleanup -> cleanup.imageId
      case cleanup: TerminalRenderControlDetails.KittyPlacementCleanup =>
        ComponentRenderControlKind.KittyPlacementCleanup -> Some(cleanup.imageId)
    ComponentRenderControlDiagnostic(
      kind,
      imageId,
      placement.row,
      placement.column,
      placement.control.width,
      placement.control.rows
    )

object ComponentRender:
  /** Preserve the original three-argument positional constructor. */
  def apply(
      lines: Vector[String],
      controls: Vector[TerminalControlPlacement],
      cursorPlacements: Vector[CursorPlacement]
  ): ComponentRender =
    new ComponentRender(lines, controls, cursorPlacements, DocumentMetadata.empty)

  /** Preserve the original three-field pattern extractor. */
  def unapply(
      value: ComponentRender
  ): (Vector[String], Vector[TerminalControlPlacement], Vector[CursorPlacement]) =
    (value.lines, value.controls, value.cursorPlacements)

  /** Construct shared JVM/Native text-only output with no controls or cursor candidates. */
  def text(lines: Vector[String]): ComponentRender =
    ComponentRender(lines, Vector.empty, Vector.empty)

  /**
   * Construct one-line shared JVM/Native text-only output with no controls or cursor candidates.
   */
  def text(line: String): ComponentRender = text(Vector(line))

  /** Empty shared JVM/Native text-only component output. */
  val empty: ComponentRender = text(Vector.empty)
