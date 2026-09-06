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
  ): Option[TerminalControlPlacement] = placement.control.details match
    case _: TerminalRenderControlDetails.KittyImage | _: TerminalRenderControlDetails.KittyPlacement |
        _: TerminalRenderControlDetails.ITerm2Image =>
      val top     = math.max(placement.row.toLong, clip.row.toLong)
      val left    = math.max(placement.column.toLong, clip.col.toLong)
      val bottom  = math.min(
        placement.row.toLong + placement.control.rows.toLong,
        clip.row.toLong + clip.height.toLong
      )
      val right   = math.min(
        placement.column.toLong + placement.control.width.toLong,
        clip.col.toLong + clip.width.toLong
      )
      val rows    = math.max(0L, bottom - top).toInt
      val columns = math.max(0L, right - left).toInt
      TerminalImageProtocol
        .clipImage(
          placement.control,
          clippedTop = math.min(
            placement.control.rows.toLong,
            math.max(0L, top - placement.row.toLong)
          ).toInt,
          clippedLeft = math.min(
            placement.control.width.toLong,
            math.max(0L, left - placement.column.toLong)
          ).toInt,
          visibleWidth = columns,
          visibleRows = rows
        )
        .map(control => TerminalControlPlacement(top.toInt, left.toInt, control))
    case _: TerminalRenderControlDetails.KittyCleanup |
        _: TerminalRenderControlDetails.KittyPlacementCleanup =>
      val bottom = clip.row.toLong + clip.height.toLong
      val right  = clip.col.toLong + clip.width.toLong
      Option.when(
        placement.row.toLong >= clip.row.toLong && placement.row.toLong < bottom &&
          placement.column.toLong >= clip.col.toLong && placement.column.toLong <= right
      )(placement)
