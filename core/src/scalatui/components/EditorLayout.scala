package scalatui.components

import scalatui.ansi.Ansi
import scalatui.ansi.Ansi.LayoutProjection
import scalatui.editing.{EditorBuffer, EditorCursor}
import scalatui.syntax.Equality.*

/** One width-constrained visual row derived from a logical editor line. */
final case class EditorVisualLine(
    logicalLine: Int,
    startColumn: Int,
    endColumn: Int,
    text: String,
    width: Int
) derives CanEqual

/** Visual cursor position in rendered editor rows and display columns. */
final case class EditorVisualCursor(row: Int, column: Int) derives CanEqual

/**
 * Pure JVM/Native width-aware layout for an [[scalatui.editing.EditorBuffer]]. Each complete
 * logical line is scanned once by the shared ANSI scanner. Layout wraps sanitized final Unicode
 * 17.0.0 UAX #29 graphemes, not raw source widths. Supported bounded SGR and OSC 8 remain atomic
 * metadata; rejected controls may expand to several display units that retain one exact half-open
 * source ownership range. Terminal width remains a separate project-specific policy.
 *
 * If a display unit cannot fit on an otherwise empty positive-width row, layout emits no
 * replacement or partial text. The blank row retains source ownership and maps its cursor to column
 * zero. At a non-positive render width, Editor suppresses cursor metadata and printable output.
 */
final case class EditorLayout(
    lines: Vector[EditorVisualLine],
    cursor: EditorVisualCursor
) derives CanEqual

private[components] final case class EditorRenderRow(
    line: EditorVisualLine,
    projection: LayoutProjection,
    unitStart: Int,
    unitEnd: Int,
    omitted: Boolean
):
  def normalText(width: Int): String =
    if width <= 0 || omitted then ""
    else if unitStart === unitEnd then projection.metadataOnlyText
    else
      val units = projection.units.slice(unitStart, unitEnd)
      units.head.replayBefore + units.iterator.map(_.raw).mkString + units.last.closeAfter

  def focusedText(cursorBoundary: Int, width: Int): (String, Option[Int]) =
    if width <= 0 || omitted then ("", None)
    else if unitStart === unitEnd then
      val line = projection.metadataReplayBefore + projection.metadataOnlyRaw +
        "\u001b[7m \u001b[27m" + projection.finalReplay + projection.finalClose
      (line, Some(0))
    else
      val units        = projection.units
      val target       = Option.when(cursorBoundary >= unitStart && cursorBoundary < unitEnd)(
        cursorBoundary
      )
      val cursorColumn =
        target.map(index => units.slice(unitStart, index).iterator.map(_.width).sum)
      val atEnd        = cursorBoundary === unitEnd && line.width < width
      val builder      = new StringBuilder
      builder.append(units(unitStart).replayBefore)
      var index        = unitStart
      while index < unitEnd do
        val unit = units(index)
        if target.contains(index) then appendFocusedUnit(builder, unit)
        else builder.append(unit.raw)
        index += 1
      if atEnd then builder.append("\u001b[7m \u001b[27m")
      builder.append(units(unitEnd - 1).closeAfter)
      (builder.result(), cursorColumn.orElse(Option.when(atEnd)(line.width)))

  private def appendFocusedUnit(builder: StringBuilder, unit: Ansi.ProjectedUnit): Unit =
    var inverseActive = false
    unit.parts.foreach {
      case part: Ansi.ProjectedMetadata  =>
        builder.append(part.text)
        if inverseActive then builder.append("\u001b[7m")
      case part: Ansi.ProjectedPrintable =>
        if !inverseActive then
          builder.append("\u001b[7m")
          inverseActive = true
        builder.append(part.text)
    }
    if inverseActive then builder.append("\u001b[27m")
    builder.append(unit.replayAfter)

  def sourceColumnAt(visualColumn: Int): Int =
    if unitStart === unitEnd then line.startColumn
    else
      val units  = projection.units
      var index  = unitStart
      var width  = 0
      var result = Option.empty[Int]
      while index < unitEnd && result.isEmpty do
        val unit = units(index)
        if visualColumn <= width || visualColumn < width + unit.width then
          result = Some(unit.printableSource.start)
        else
          width += unit.width
          index += 1
      result.getOrElse(units(unitEnd - 1).printableSource.end)

private[components] final case class EditorRenderPlan(
    layout: EditorLayout,
    rows: Vector[EditorRenderRow],
    cursorBoundary: Int
):
  def sourceColumnAt(row: Int, visualColumn: Int): Int = rows(row).sourceColumnAt(visualColumn)

object EditorLayout:
  /**
   * Compute wrapped visual lines and cursor coordinates from sanitized final display graphemes.
   * Impossible-width units own blank rows and use cursor column zero; source text is unchanged.
   */
  def fromBuffer(buffer: EditorBuffer, width: Int): EditorLayout = renderPlan(buffer, width).layout

  private[components] def renderPlan(buffer: EditorBuffer, width: Int): EditorRenderPlan =
    val maxWidth           = math.max(1, width)
    val rows               = buffer.lines.indices.flatMap { logicalLine =>
      val source     = buffer.clustersForLine(logicalLine)
      val projection = Ansi.projectLayout(source)
      wrapProjection(projection, logicalLine, maxWidth)
    }.toVector
    val (cursor, boundary) = cursorFor(buffer.cursor, rows)
    EditorRenderPlan(EditorLayout(rows.map(_.line), cursor), rows, boundary)

  private def wrapProjection(
      projection: LayoutProjection,
      logicalLine: Int,
      width: Int
  ): Vector[EditorRenderRow] =
    if projection.units.isEmpty then
      val ownership = projection.metadataOnly.map(_.source)
      val start     = ownership.headOption.map(_.start).getOrElse(0)
      val end       = ownership.lastOption.map(_.end).getOrElse(0)
      val line      = EditorVisualLine(logicalLine, start, end, projection.metadataOnlyText, 0)
      Vector(EditorRenderRow(line, projection, 0, 0, omitted = false))
    else
      val result       = Vector.newBuilder[EditorRenderRow]
      var rowStart     = 0
      var currentWidth = 0
      var index        = 0

      def appendRow(end: Int, omitted: Boolean): Unit =
        val units = projection.units.slice(rowStart, end)
        val start = units.iterator.map(_.source.start).min
        val stop  = units.iterator.map(_.source.end).max
        val text  =
          if omitted then ""
          else units.head.replayBefore + units.iterator.map(_.raw).mkString + units.last.closeAfter
        val line  = EditorVisualLine(logicalLine, start, stop, text, currentWidth)
        result += EditorRenderRow(line, projection, rowStart, end, omitted)
        rowStart = end
        currentWidth = 0

      while index < projection.units.length do
        val unit = projection.units(index)
        if index > rowStart && currentWidth + unit.width > width then
          appendRow(index, omitted = false)
        if unit.width > width && index === rowStart then
          index += 1
          appendRow(index, omitted = true)
        else
          currentWidth += unit.width
          index += 1
      if rowStart < projection.units.length then appendRow(projection.units.length, omitted = false)
      result.result()

  private def cursorFor(
      cursor: EditorCursor,
      rows: Vector[EditorRenderRow]
  ): (EditorVisualCursor, Int) =
    val indexed    = rows.zipWithIndex.filter(_._1.line.logicalLine === cursor.line)
    val projection = indexed.head._1.projection
    val boundary   = projection.displayBoundary(cursor.column)
    val selected   = indexed.find { case (row, _) =>
      boundary >= row.unitStart && boundary < row.unitEnd
    }.orElse(indexed.find { case (row, _) => boundary === row.unitStart }).getOrElse(indexed.last)
    val row        = selected._1
    val column     = projection.units
      .slice(row.unitStart, math.min(boundary, row.unitEnd))
      .iterator
      .map(_.width)
      .sum
    (EditorVisualCursor(selected._2, math.min(row.line.width, column)), boundary)
