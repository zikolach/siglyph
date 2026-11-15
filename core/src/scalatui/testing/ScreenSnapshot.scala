package scalatui.testing

import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/** Immutable modeled screen, not a screenshot or a promise of emulator pixel fidelity. */
final case class ScreenSnapshot(columns: Int, rows: Int, lines: Vector[String]):
  require(columns >= 0 && rows >= 0, "screen geometry must be non-negative")
  require(lines.length === rows, "screen line count must equal its height")
  require(
    lines.forall(line => !line.exists(ScreenSnapshot.isControl)),
    "screen rows must be plain text"
  )
  require(
    lines.forall(line => Unicode.stringWidth(line) <= columns),
    "screen row exceeds its width"
  )

  /** Compare complete rows, ignoring trailing ASCII blanks as VirtualTerminal does. */
  def assertMatches(expected: ScreenSnapshot): Unit =
    val sameGeometry = (columns === expected.columns) && (rows === expected.rows)
    val sameLines    = lines.map(ScreenSnapshot.trimRight) ===
      expected.lines.map(ScreenSnapshot.trimRight)
    if !sameGeometry || !sameLines then
      val differences = (0 until math.max(rows, expected.rows)).iterator.filter { index =>
        lines.lift(index).map(ScreenSnapshot.trimRight) !==
          expected.lines.lift(index).map(ScreenSnapshot.trimRight)
      }.take(6).map { index =>
        val wantedLine = expected.lines.lift(index).map(ScreenSnapshot.trimRight)
        val actualLine = lines.lift(index).map(ScreenSnapshot.trimRight)
        val offset     =
          ScreenSnapshot.firstDifference(wantedLine.getOrElse(""), actualLine.getOrElse(""))
        val from       = math.max(0, offset - 40)
        val wanted     =
          wantedLine.map(value => ScreenSnapshot.previewFrom(value, from)).getOrElse("<missing>")
        val actual     =
          actualLine.map(value => ScreenSnapshot.previewFrom(value, from)).getOrElse("<missing>")
        s"row $index (first difference at UTF-16 offset $offset)\n  expected |$wanted|\n  actual   |$actual|"
      }.mkString("\n")
      throw new AssertionError(
        s"Screen mismatch: expected ${expected.columns}x${expected.rows}, actual ${columns}x$rows\n$differences"
      )

  /** Assert a literal single-row substring without dumping the complete screen on failure. */
  def assertContains(value: String): Unit =
    require(value.nonEmpty, "screen substring must not be empty")
    if !lines.exists(_.indexOf(value) >= 0) then
      val sample = lines.take(6).zipWithIndex.map { entry =>
        s"row ${entry._2} |${ScreenSnapshot.preview(entry._1)}|"
      }.mkString("\n")
      throw new AssertionError(
        s"Screen ${columns}x$rows does not contain |${ScreenSnapshot.preview(value)}|\n$sample"
      )

object ScreenSnapshot:
  private[testing] def isControl(value: Char): Boolean =
    value < ' ' || (value >= '\u007f' && value <= '\u009f')

  private def trimRight(value: String): String =
    var end = value.length
    while end > 0 && (value.charAt(end - 1) === ' ') do end -= 1
    value.substring(0, end)

  private def firstDifference(expected: String, actual: String): Int =
    var offset = 0
    val size   = math.min(expected.length, actual.length)
    while offset < size && (expected.charAt(offset) === actual.charAt(offset)) do offset += 1
    offset

  private[testing] def preview(value: String): String = previewFrom(value, 0)

  private def previewFrom(value: String, from: Int): String =
    var start  = math.min(value.length, math.max(0, from))
    if start > 0 && start < value.length && value.charAt(start) >= '\uDC00' &&
      value.charAt(start) <= '\uDFFF' && value.charAt(start - 1) >= '\uD800' &&
      value.charAt(start - 1) <= '\uDBFF'
    then start -= 1
    val capped = value.substring(start, math.min(value.length.toLong, start.toLong + 160L).toInt)
    val prefix = if capped.lastOption.exists(value => value >= '\uD800' && value <= '\uDBFF') then
      capped.dropRight(1)
    else capped
    val safe   = prefix.flatMap { character =>
      if isControl(character) then f"\\u${character.toInt}%04X" else character.toString
    }
    (if start > 0 then "…" else "") + safe +
      (if value.length > start + prefix.length then "…" else "")
