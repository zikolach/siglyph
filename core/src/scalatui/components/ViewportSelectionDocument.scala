package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{ViewportDecoration, ViewportSelection}
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

import java.nio.charset.StandardCharsets

private[components] final case class SelectionCellRange(start: Int, end: Int)

private[components] final class ViewportSelectionDocument private (
    val rows: Vector[ViewportSelectionDocument.Row],
    val graphemes: Vector[String]
):
  val length: Int = graphemes.length

  def sameText(other: ViewportSelectionDocument): Boolean = graphemes === other.graphemes

  def cellRange(row: Int, column: Int): Option[SelectionCellRange] =
    rows.lift(row).flatMap(_.cellRange(column))

  def rowRange(row: Int): Option[SelectionCellRange] = rows.lift(row).flatMap { value =>
    Option.when(value.endOffset > value.startOffset)(
      SelectionCellRange(value.startOffset, value.endOffset)
    )
  }

  def wordRange(row: Int, column: Int): Option[SelectionCellRange] =
    rows.lift(row).flatMap(_.wordRange(column))

  def selection(anchor: Int, focus: Int): Option[ViewportSelection] =
    val start = math.max(0, math.min(length, math.min(anchor, focus)))
    val end   = math.max(start, math.min(length, math.max(anchor, focus)))
    Option.when(end > start) {
      val selectedRows = rows.flatMap { row =>
        val rowStart = math.max(start, row.startOffset)
        val rowEnd   = math.min(end, row.endOffset)
        Option.when(rowEnd > rowStart) {
          row.graphemes.slice(rowStart - row.startOffset, rowEnd - row.startOffset).mkString
            .reverse.dropWhile(_.isWhitespace).reverse
        }
      }
      ViewportSelection(start, end, selectedRows.mkString("\n"))
    }.filter(_.text.nonEmpty)

  def decorations(anchor: Int, focus: Int, viewportOffset: Int): Vector[ViewportDecoration] =
    val start = math.max(0, math.min(length, math.min(anchor, focus)))
    val end   = math.max(start, math.min(length, math.max(anchor, focus)))
    rows.zipWithIndex.flatMap { case (row, documentRow) =>
      val rowStart = math.max(start, row.startOffset)
      val rowEnd   = math.min(end, row.endOffset)
      val localRow = documentRow - viewportOffset
      Option.when(rowEnd > rowStart && localRow >= 0) {
        val from     = rowStart - row.startOffset
        val until    = rowEnd - row.startOffset
        val text     = row.graphemes.slice(from, until).mkString
        val startCol = row.columns(from)
        val width    = row.columns(until) - startCol
        ViewportDecoration(localRow, startCol, "\u001b[7m" + text + Ansi.Reset, width)
      }
    }

private[components] object ViewportSelectionDocument:
  final val MaxRetainedGraphemes: Int = 262144
  final val MaxRetainedUtf8Bytes: Int = 1048576
  final val MaxRetainedRows: Int      = 65536

  final case class Row(
      graphemes: Vector[String],
      columns: Vector[Int],
      startOffset: Int,
      endOffset: Int
  ):
    def cellRange(column: Int): Option[SelectionCellRange] =
      if graphemes.isEmpty then None
      else
        val safeColumn = math.max(0, column)
        graphemes.indices.find(i => safeColumn >= columns(i) && safeColumn < columns(i + 1))
          .map(i => SelectionCellRange(startOffset + i, startOffset + i + 1))
          .orElse(Option.when(safeColumn >= columns.last)(SelectionCellRange(endOffset, endOffset)))

    def wordRange(column: Int): Option[SelectionCellRange] = cellRange(column)
      .filter(cell => cell.end > cell.start).map { cell =>
        val clicked = cell.start - startOffset
        val kinds   = graphemes.map(wordKind)
        var from    = clicked
        var until   = clicked + 1
        while from > 0 && canJoin(kinds(from - 1), kinds(from)) do from -= 1
        while until < graphemes.length && canJoin(kinds(until - 1), kinds(until)) do until += 1
        SelectionCellRange(startOffset + from, startOffset + until)
      }

  private enum WordKind:
    case Word, Joiner, Other

  private[components] final class Builder(maxGraphemes: Int, maxUtf8Bytes: Int):
    require(maxGraphemes > 0, "Selection grapheme bound must be positive")
    require(maxUtf8Bytes > 0, "Selection UTF-8 byte bound must be positive")

    private val rows         = Vector.newBuilder[Row]
    private val all          = Vector.newBuilder[String]
    private var offset       = 0
    private var retainedByte = 0
    private var retainedRows = 0
    private var scanned      = 0
    private var full         = false

    def isFull: Boolean       =
      full || offset >= maxGraphemes || retainedByte >= maxUtf8Bytes ||
        retainedRows >= MaxRetainedRows
    def scannedGraphemes: Int = scanned

    def appendRow(rendered: String): Unit =
      if !isFull then
        val kept        = Vector.newBuilder[String]
        val columns     = Vector.newBuilder[Int]
        val startOffset = offset
        var column      = 0
        val completed   = Ansi.foreachSelectionGraphemeWhile(
          rendered,
          maxGraphemes - offset,
          maxUtf8Bytes - retainedByte
        ) { grapheme =>
          scanned += 1
          val bytes = grapheme.getBytes(StandardCharsets.UTF_8).length
          if offset >= maxGraphemes || retainedByte + bytes > maxUtf8Bytes then
            full = true
            false
          else
            columns += column
            kept += grapheme
            all += grapheme
            offset += 1
            retainedByte += bytes
            column += Unicode.graphemeWidth(grapheme)
            true
        }
        if !completed then full = true
        val values      = kept.result()
        rows += Row(values, columns.result() :+ column, startOffset, offset)
        retainedRows += 1

    def result(): ViewportSelectionDocument =
      new ViewportSelectionDocument(rows.result(), all.result())

  def apply(
      renderedRows: Vector[String],
      maxGraphemes: Int,
      maxUtf8Bytes: Int
  ): ViewportSelectionDocument =
    val builder  = Builder(maxGraphemes, maxUtf8Bytes)
    val iterator = renderedRows.iterator
    while iterator.hasNext && !builder.isFull do builder.appendRow(iterator.next())
    builder.result()

  private def wordKind(grapheme: String): WordKind =
    if grapheme === "/" || grapheme === "-" then WordKind.Joiner
    else if Unicode.codePoints(grapheme).exists(codePoint =>
        Character.isLetterOrDigit(codePoint) || codePoint === '_'.toInt
      )
    then WordKind.Word
    else WordKind.Other

  private def canJoin(left: WordKind, right: WordKind): Boolean =
    val selectable = (left !== WordKind.Other) && (right !== WordKind.Other)
    selectable && (left === WordKind.Joiner || right === WordKind.Joiner ||
      (left === WordKind.Word && right === WordKind.Word))
