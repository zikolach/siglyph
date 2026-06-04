package scalatui.components

import scalatui.ansi.Ansi
import scalatui.editing.{EditorBuffer, EditorCursor}
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

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

/** Pure width-aware layout for an [[EditorBuffer]]. */
final case class EditorLayout(
    lines: Vector[EditorVisualLine],
    cursor: EditorVisualCursor
) derives CanEqual

object EditorLayout:
  /** Compute wrapped visual lines and cursor coordinates for `buffer` at `width` columns. */
  def fromBuffer(buffer: EditorBuffer, width: Int): EditorLayout =
    val maxWidth    = math.max(1, width)
    val visualLines = buffer.lines.zipWithIndex.flatMap { (line, index) =>
      wrapLogicalLine(line, index, maxWidth)
    }
    EditorLayout(visualLines, cursorFor(buffer, visualLines))

  private def wrapLogicalLine(
      line: String,
      logicalLine: Int,
      width: Int
  ): Vector[EditorVisualLine] =
    val clusters = Unicode.graphemeClusters(line)
    if clusters.isEmpty then Vector(EditorVisualLine(logicalLine, 0, 0, "", 0))
    else
      val result       = Vector.newBuilder[EditorVisualLine]
      val text         = new StringBuilder
      var startColumn  = 0
      var currentWidth = 0
      var column       = 0

      clusters.foreach { cluster =>
        val clusterWidth = Unicode.graphemeWidth(cluster)
        if currentWidth > 0 && currentWidth + clusterWidth > width then
          result += EditorVisualLine(logicalLine, startColumn, column, text.result(), currentWidth)
          text.clear()
          startColumn = column
          currentWidth = 0

        if clusterWidth > width && currentWidth === 0 then
          val clipped = Ansi.sliceByColumns(cluster, 0, width)
          result += EditorVisualLine(logicalLine, column, column + 1, clipped.text, clipped.width)
          startColumn = column + 1
        else
          text.append(cluster)
          currentWidth += clusterWidth

        column += 1
      }

      if startColumn < clusters.length || text.nonEmpty then
        result += EditorVisualLine(
          logicalLine,
          startColumn,
          clusters.length,
          text.result(),
          currentWidth
        )

      result.result()

  private def cursorFor(
      buffer: EditorBuffer,
      visualLines: Vector[EditorVisualLine]
  ): EditorVisualCursor =
    val cursor      = buffer.cursor
    val lineIndexes = visualLines.zipWithIndex.collect {
      case (line, index) if line.logicalLine === cursor.line => (line, index)
    }
    val selected    = lineIndexes.find { (line, _) =>
      line.startColumn === line.endColumn ||
      (cursor.column >= line.startColumn && cursor.column < line.endColumn) ||
      (cursor.column === line.endColumn && isLastVisualLine(line, lineIndexes.map(_._1)))
    }.getOrElse(lineIndexes.last)

    EditorVisualCursor(selected._2, cursorColumn(buffer.lines(cursor.line), selected._1, cursor))

  private def cursorColumn(
      logicalText: String,
      visualLine: EditorVisualLine,
      cursor: EditorCursor
  ): Int =
    val clusters = Unicode.graphemeClusters(logicalText)
    Unicode.stringWidth(clusters.slice(visualLine.startColumn, cursor.column).mkString)

  private def isLastVisualLine(
      candidate: EditorVisualLine,
      lineVisuals: Vector[EditorVisualLine]
  ): Boolean =
    lineVisuals.lastOption.exists(_ === candidate)
