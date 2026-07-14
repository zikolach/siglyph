package scalatui.core

import scalatui.ansi.Ansi

/**
 * A hardware-cursor candidate in zero-based, frame-relative display-cell coordinates.
 *
 * Cursor placements are structural metadata. They encode no terminal bytes and ordinary strings
 * cannot create them. Construction rejects negative coordinates. The containing [[ComponentRender]]
 * validates that each candidate is inside its rows and requested width.
 */
final case class CursorPlacement(
    /** Zero-based frame row. */
    row: Int,
    /** Zero-based display-cell column. */
    column: Int
) derives CanEqual:
  require(row >= 0, "Cursor row must be non-negative")
  require(column >= 0, "Cursor column must be non-negative")

  /** Translate this candidate, rejecting an offset that produces a negative coordinate. */
  def translated(rowOffset: Int = 0, columnOffset: Int = 0): CursorPlacement =
    CursorPlacement(row + rowOffset, column + columnOffset)

private[scalatui] object FakeCursorRender:
  final case class Result(line: String, cursorColumn: Option[Int])

  /**
   * Sanitize application-owned segments before adding inverse-video metadata, truncate the complete
   * line, and retain the cursor column only when the complete fake-cursor token survives.
   */
  def render(before: String, cursor: String, after: String, width: Int): Result =
    val safeBefore  = Ansi.sanitize(before)
    val safeCursor  = Ansi.sanitize(cursor)
    val safeAfter   = Ansi.sanitize(after)
    val column      = Ansi.visibleWidth(safeBefore)
    val cursorWidth = Ansi.visibleWidth(safeCursor)
    val line        = Ansi.truncateToWidth(
      safeBefore + "\u001b[7m" + safeCursor + "\u001b[27m" + safeAfter,
      width,
      ""
    )
    val survives    = width > 0 && column < width && column + cursorWidth <= width
    Result(line, Option.when(survives)(column))
