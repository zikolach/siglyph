package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

/** Pure overlay layout and ANSI-aware compositing helpers used by [[TUI]]. */
object OverlayRenderer:
  def resolve(
      options: OverlayOptions,
      overlayHeight: Int,
      terminalWidth: Int,
      terminalHeight: Int
  ): ResolvedOverlay =
    val termWidth   = math.max(1, terminalWidth)
    val termHeight  = math.max(1, terminalHeight)
    val margin      = options.margin.normalized
    val availWidth  = math.max(1, termWidth - margin.left - margin.right)
    val availHeight = math.max(1, termHeight - margin.top - margin.bottom)

    val rawWidth = options.width.map(resolveSize(_, termWidth)).getOrElse(math.min(80, availWidth))
    val minWidth = options.minWidth.map(math.max(1, _)).getOrElse(1)
    val width    = math.max(1, math.min(math.max(rawWidth, minWidth), availWidth))

    val maxHeight       = options.maxHeight.map(size =>
      math.max(1, math.min(resolveSize(size, termHeight), availHeight))
    )
    val effectiveHeight = maxHeight match
      case Some(value) => math.min(math.max(0, overlayHeight), value)
      case None        => math.max(0, overlayHeight)

    val row = clamp(
      resolvePosition(
        explicit = options.row,
        reference = termHeight,
        available = availHeight,
        extent = effectiveHeight,
        marginStart = margin.top,
        anchorPosition = anchorRow(options.anchor, effectiveHeight, availHeight, margin.top)
      ) + options.offsetY,
      margin.top,
      math.max(margin.top, termHeight - margin.bottom - effectiveHeight)
    )

    val col = clamp(
      resolvePosition(
        explicit = options.col,
        reference = termWidth,
        available = availWidth,
        extent = width,
        marginStart = margin.left,
        anchorPosition = anchorCol(options.anchor, width, availWidth, margin.left)
      ) + options.offsetX,
      margin.left,
      math.max(margin.left, termWidth - margin.right - width)
    )

    ResolvedOverlay(width = width, row = row, col = col, maxHeight = maxHeight)

  /**
   * Composite validated frame-local metadata at this public pure boundary.
   *
   * The base and final frame use `terminalWidth`. Each overlay uses its resolved width before any
   * translation or occlusion. Invalid metadata fails with [[ComponentRenderValidationError]]'s
   * bounded redacted diagnostics, including when no overlays are supplied.
   */
  def composite(
      baseFrame: ComponentRender,
      overlays: Vector[(ComponentRender, ResolvedOverlay)],
      terminalWidth: Int,
      terminalHeight: Int
  ): ComponentRender =
    val validatedBase     = baseFrame.validated(terminalWidth)
    val validatedOverlays = overlays.map { case (frame, layout) =>
      frame.validated(layout.width) -> layout
    }
    val result            =
      if validatedOverlays.isEmpty then validatedBase
      else
        val termWidth        = math.max(1, terminalWidth)
        val termHeight       = math.max(1, terminalHeight)
        val minHeight        = validatedOverlays.foldLeft(validatedBase.lines.length) {
          case (height, (frame, layout)) => math.max(height, layout.row + frame.lines.length)
        }
        val lineBuffer       = validatedBase.lines.padTo(minHeight, "").toArray
        val viewportStart    = math.max(0, minHeight - termHeight)
        var controls         = validatedBase.controls
        var cursorPlacements = validatedBase.cursorPlacements

        validatedOverlays.foreach { case (frame, layout) =>
          val finalRow = viewportStart + layout.row
          controls = controls.filterNot(intersectsOverlay(_, finalRow, layout, frame.lines.length))
          controls ++= frame.controls.map(_.translated(finalRow, layout.col))
          cursorPlacements = cursorPlacements.filterNot(
            cursorIntersectsOverlay(_, finalRow, layout, frame.lines.length)
          )
          cursorPlacements ++= frame.cursorPlacements.map(_.translated(finalRow, layout.col))
          frame.lines.zipWithIndex.foreach { case (line, offset) =>
            val index = finalRow + offset
            if index >= 0 && index < lineBuffer.length then
              lineBuffer(index) =
                compositeLine(lineBuffer(index), line, layout.col, layout.width, termWidth)
          }
        }

        ComponentRender(lineBuffer.toVector, controls, cursorPlacements)
    result.validated(terminalWidth)

  private def cursorIntersectsOverlay(
      placement: CursorPlacement,
      overlayRow: Int,
      layout: ResolvedOverlay,
      overlayHeight: Int
  ): Boolean =
    placement.row >= overlayRow && placement.row < overlayRow + overlayHeight &&
      placement.column >= layout.col && placement.column < layout.col + layout.width

  private def intersectsOverlay(
      placement: TerminalControlPlacement,
      overlayRow: Int,
      layout: ResolvedOverlay,
      overlayHeight: Int
  ): Boolean =
    val controlBottom = placement.row + placement.control.rows
    val controlRight  = placement.column + placement.control.width
    val overlayBottom = overlayRow + overlayHeight
    val overlayRight  = layout.col + layout.width
    placement.control.width > 0 && placement.row < overlayBottom && controlBottom > overlayRow &&
    placement.column < overlayRight && controlRight > layout.col

  def compositeLine(
      baseLine: String,
      overlayLine: String,
      startCol: Int,
      overlayWidth: Int,
      terminalWidth: Int
  ): String =
    val safeTerminalWidth = math.max(1, terminalWidth)
    val safeStart         = clamp(startCol, 0, safeTerminalWidth)
    val safeOverlayWidth  = math.max(0, math.min(overlayWidth, safeTerminalWidth - safeStart))
    if safeOverlayWidth <= 0 then Ansi.truncateToWidth(baseLine, safeTerminalWidth, "")
    else
      val beforeSlice      = Ansi.sliceByColumns(baseLine, 0, safeStart)
      val before           = beforeSlice.text + " ".repeat(math.max(0, safeStart - beforeSlice.width))
      val overlay          = Ansi.sliceByColumns(overlayLine, 0, safeOverlayWidth)
      val paddedOverlay    = overlay.text + " ".repeat(math.max(0, safeOverlayWidth - overlay.width))
      val afterWidth       = math.max(0, safeTerminalWidth - safeStart - safeOverlayWidth)
      val afterStart       = safeStart + safeOverlayWidth
      val beforeAfterSlice = Ansi.sliceByColumns(baseLine, 0, afterStart)
      val afterGap         =
        if afterStart < Ansi.visibleWidth(baseLine) then
          math.max(0, afterStart - beforeAfterSlice.width)
        else 0
      val after            = Ansi.sliceByColumns(baseLine, afterStart, afterWidth).text
      before + Ansi.Reset + paddedOverlay + Ansi.Reset + " ".repeat(afterGap) + after

  private def resolvePosition(
      explicit: Option[OverlaySize],
      reference: Int,
      available: Int,
      extent: Int,
      marginStart: Int,
      anchorPosition: Int
  ): Int = explicit match
    case Some(OverlaySize.Absolute(value)) => value
    case Some(OverlaySize.Percent(value))  =>
      val maxPosition = math.max(0, available - extent)
      marginStart + math.floor(maxPosition * (value / 100.0)).toInt
    case None                              => anchorPosition

  private def resolveSize(size: OverlaySize, reference: Int): Int = size match
    case OverlaySize.Absolute(value) => value
    case OverlaySize.Percent(value)  => math.floor(math.max(1, reference) * (value / 100.0)).toInt

  private def anchorRow(
      anchor: OverlayAnchor,
      height: Int,
      availableHeight: Int,
      marginTop: Int
  ): Int = anchor match
    case OverlayAnchor.TopLeft | OverlayAnchor.TopCenter | OverlayAnchor.TopRight          => marginTop
    case OverlayAnchor.BottomLeft | OverlayAnchor.BottomCenter | OverlayAnchor.BottomRight =>
      marginTop + availableHeight - height
    case OverlayAnchor.LeftCenter | OverlayAnchor.Center | OverlayAnchor.RightCenter       =>
      marginTop + math.floor((availableHeight - height) / 2.0).toInt

  private def anchorCol(
      anchor: OverlayAnchor,
      width: Int,
      availableWidth: Int,
      marginLeft: Int
  ): Int = anchor match
    case OverlayAnchor.TopLeft | OverlayAnchor.LeftCenter | OverlayAnchor.BottomLeft    => marginLeft
    case OverlayAnchor.TopRight | OverlayAnchor.RightCenter | OverlayAnchor.BottomRight =>
      marginLeft + availableWidth - width
    case OverlayAnchor.TopCenter | OverlayAnchor.Center | OverlayAnchor.BottomCenter    =>
      marginLeft + math.floor((availableWidth - width) / 2.0).toInt

  private def clamp(value: Int, min: Int, max: Int): Int = math.max(min, math.min(value, max))
