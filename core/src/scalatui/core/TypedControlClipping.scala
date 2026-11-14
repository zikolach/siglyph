package scalatui.core

import scalatui.terminal.{TerminalImageProtocol, TerminalRenderControlDetails}

/** Typed image-footprint clipping shared by viewport, stack, scroll, and overlay composition. */
private[core] object TypedControlClipping:
  /**
   * Return a complete or protocol-validated cropped image placement inside `clip`.
   *
   * Fully clipped images and partial footprints that the protocol helper cannot represent return
   * `None`. Cleanup controls are not image placements and pass through unchanged.
   */
  def clipPlacement(
      placement: TerminalControlPlacement,
      clip: ClipRect
  ): Option[TerminalControlPlacement] =
    clipTranslatedPlacement(placement, 0L, 0L, clip)

  /** Intersect in signed coordinates before constructing a non-negative public placement. */
  def clipTranslatedPlacement(
      placement: TerminalControlPlacement,
      rowOffset: Long,
      columnOffset: Long,
      clip: ClipRect
  ): Option[TerminalControlPlacement] =
    if rowOffset > Long.MaxValue - placement.row.toLong ||
      columnOffset > Long.MaxValue - placement.column.toLong
    then None
    else
      val row    = placement.row.toLong + rowOffset
      val column = placement.column.toLong + columnOffset
      if row < Int.MinValue || row > Int.MaxValue || column < Int.MinValue || column > Int.MaxValue
      then None
      else clipAt(placement, row, column, clip)

  private def clipAt(
      placement: TerminalControlPlacement,
      row: Long,
      column: Long,
      clip: ClipRect
  ): Option[TerminalControlPlacement] =
    placement.control.details match
      case _: TerminalRenderControlDetails.KittyImage | _: TerminalRenderControlDetails.KittyPlacement |
          _: TerminalRenderControlDetails.ITerm2Image =>
        val top     = math.max(row, clip.row.toLong)
        val left    = math.max(column, clip.col.toLong)
        val bottom  = math.min(
          row + placement.control.rows.toLong,
          clip.row.toLong + clip.height.toLong
        )
        val right   = math.min(
          column + placement.control.width.toLong,
          clip.col.toLong + clip.width.toLong
        )
        val rows    = math.max(0L, bottom - top).toInt
        val columns = math.max(0L, right - left).toInt
        TerminalImageProtocol
          .clipImage(
            placement.control,
            clippedTop = math.min(
              placement.control.rows.toLong,
              math.max(0L, top - row)
            ).toInt,
            clippedLeft = math.min(
              placement.control.width.toLong,
              math.max(0L, left - column)
            ).toInt,
            visibleWidth = columns,
            visibleRows = rows
          )
          .filter(_ => top >= 0L && left >= 0L && top <= Int.MaxValue && left <= Int.MaxValue)
          .map(control => TerminalControlPlacement(top.toInt, left.toInt, control))
      case _: TerminalRenderControlDetails.KittyCleanup |
          _: TerminalRenderControlDetails.KittyPlacementCleanup =>
        val bottom = clip.row.toLong + clip.height.toLong
        val right  = clip.col.toLong + clip.width.toLong
        Option.when(
          row >= clip.row.toLong && row < bottom &&
            column >= clip.col.toLong && column <= right
        )(())
          .filter(_ => row >= 0L && column >= 0L && row <= Int.MaxValue && column <= Int.MaxValue)
          .map(_ => TerminalControlPlacement(row.toInt, column.toInt, placement.control))
