package scalatui.editing

import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/**
 * Logical cursor position inside an [[EditorBuffer]].
 *
 * @param line
 *   zero-based logical line index
 * @param column
 *   zero-based grapheme-cluster column within the line
 */
final case class EditorCursor(line: Int, column: Int) derives CanEqual

/**
 * Pure multiline text model for future editor components.
 *
 * Cursor columns are logical grapheme-cluster offsets, not terminal display columns or UTF-16
 * offsets.
 */
final class EditorBuffer private (
    private var currentLines: Vector[String],
    initialCursor: EditorCursor
):
  private var currentCursor = clamp(initialCursor)

  /** Current logical lines. The returned vector is a snapshot and can be used safely in tests. */
  def lines: Vector[String] = currentLines

  /** Current logical cursor position. */
  def cursor: EditorCursor = currentCursor

  /** Current buffer contents joined with `\n` separators. */
  def text: String = currentLines.mkString("\n")

  /** Text to pass to submit callbacks; currently the same as [[text]]. */
  def submitText: String = text

  /** Move the cursor to `cursor`, clamped to the valid buffer range. */
  def setCursor(cursor: EditorCursor): Unit =
    currentCursor = clamp(cursor)

  /** Move one grapheme cluster left, or to the end of the previous line. */
  def moveLeft(): Unit =
    if currentCursor.column > 0 then
      currentCursor = currentCursor.copy(column = currentCursor.column - 1)
    else if currentCursor.line > 0 then
      val previousLine = currentCursor.line - 1
      currentCursor = EditorCursor(previousLine, lineLength(previousLine))

  /** Move one grapheme cluster right, or to the start of the next line. */
  def moveRight(): Unit =
    val length = lineLength(currentCursor.line)
    if currentCursor.column < length then
      currentCursor = currentCursor.copy(column = currentCursor.column + 1)
    else if currentCursor.line < currentLines.length - 1 then
      currentCursor = EditorCursor(currentCursor.line + 1, 0)

  /** Move to the previous logical line, preserving the column when possible. */
  def moveUp(): Unit =
    if currentCursor.line > 0 then
      val targetLine = currentCursor.line - 1
      currentCursor =
        EditorCursor(targetLine, math.min(currentCursor.column, lineLength(targetLine)))

  /** Move to the next logical line, preserving the column when possible. */
  def moveDown(): Unit =
    if currentCursor.line < currentLines.length - 1 then
      val targetLine = currentCursor.line + 1
      currentCursor =
        EditorCursor(targetLine, math.min(currentCursor.column, lineLength(targetLine)))

  /** Move to the start of the current logical line. */
  def moveToLineStart(): Unit =
    currentCursor = currentCursor.copy(column = 0)

  /** Move to the end of the current logical line. */
  def moveToLineEnd(): Unit =
    currentCursor = currentCursor.copy(column = lineLength(currentCursor.line))

  /** Insert text at the cursor, treating embedded newlines as multiline paste. */
  def insert(text: String): Unit =
    val normalized = normalizeNewlines(text)
    if normalized.contains('\n') then insertPaste(normalized)
    else insertClusters(Unicode.graphemeClusters(normalized))

  /** Split the current logical line at the cursor. */
  def insertNewline(): Unit =
    val cs     = currentLineClusters
    val before = cs.take(currentCursor.column).mkString
    val after  = cs.drop(currentCursor.column).mkString
    currentLines = currentLines.patch(currentCursor.line, Vector(before, after), 1)
    currentCursor = EditorCursor(currentCursor.line + 1, 0)

  /** Insert pasted text, preserving normalized logical newlines and Unicode content. */
  def insertPaste(text: String): Unit =
    val parts = normalizeNewlines(text).split("\n", -1).toVector
    if parts.length === 1 then insertClusters(Unicode.graphemeClusters(parts.head))
    else
      val cs          = currentLineClusters
      val prefix      = cs.take(currentCursor.column).mkString
      val suffix      = cs.drop(currentCursor.column).mkString
      val replacement = Vector(prefix + parts.head) ++ parts.slice(
        1,
        parts.length - 1
      ) ++ Vector(parts.last + suffix)
      currentLines = currentLines.patch(currentCursor.line, replacement, 1)
      currentCursor = EditorCursor(
        currentCursor.line + parts.length - 1,
        Unicode.graphemeClusters(parts.last).length
      )

  /** Delete the previous grapheme cluster or merge with the previous line. */
  def backspace(): Unit =
    if currentCursor.column > 0 then
      val cs = currentLineClusters
      replaceCurrentLine(
        (cs.take(currentCursor.column - 1) ++ cs.drop(currentCursor.column)).mkString
      )
      currentCursor = currentCursor.copy(column = currentCursor.column - 1)
    else if currentCursor.line > 0 then
      val previousLine   = currentLines(currentCursor.line - 1)
      val previousLength = Unicode.graphemeClusters(previousLine).length
      val merged         = previousLine + currentLines(currentCursor.line)
      currentLines = currentLines.patch(currentCursor.line - 1, Vector(merged), 2)
      currentCursor = EditorCursor(currentCursor.line - 1, previousLength)

  /** Delete the next grapheme cluster or merge with the next line. */
  def delete(): Unit =
    val cs = currentLineClusters
    if currentCursor.column < cs.length then
      replaceCurrentLine(
        (cs.take(currentCursor.column) ++ cs.drop(currentCursor.column + 1)).mkString
      )
    else if currentCursor.line < currentLines.length - 1 then
      val merged = currentLines(currentCursor.line) + currentLines(currentCursor.line + 1)
      currentLines = currentLines.patch(currentCursor.line, Vector(merged), 2)

  /** Delete from the cursor to the end of the current logical line. */
  def deleteToEndOfLine(): Unit =
    val cs = currentLineClusters
    replaceCurrentLine(cs.take(currentCursor.column).mkString)

  /** Delete the word before the cursor, or merge with the previous line at line start. */
  def deleteWordBackwards(): Unit =
    if currentCursor.column > 0 then
      val cs = currentLineClusters
      var i  = currentCursor.column
      while i > 0 && cs(i - 1).forall(_.isWhitespace) do i -= 1
      while i > 0 && !cs(i - 1).forall(_.isWhitespace) do i -= 1
      replaceCurrentLine((cs.take(i) ++ cs.drop(currentCursor.column)).mkString)
      currentCursor = currentCursor.copy(column = i)
    else backspace()

  private def insertClusters(inserted: Vector[String]): Unit =
    if inserted.nonEmpty then
      val cs = currentLineClusters
      replaceCurrentLine(
        (cs.take(currentCursor.column) ++ inserted ++ cs.drop(currentCursor.column)).mkString
      )
      currentCursor = currentCursor.copy(column = currentCursor.column + inserted.length)

  private def replaceCurrentLine(value: String): Unit =
    currentLines = currentLines.updated(currentCursor.line, value)

  private def currentLineClusters: Vector[String] =
    Unicode.graphemeClusters(currentLines(currentCursor.line))

  private def lineLength(line: Int): Int = Unicode.graphemeClusters(currentLines(line)).length

  private def clamp(cursor: EditorCursor): EditorCursor =
    val line = math.max(0, math.min(currentLines.length - 1, cursor.line))
    EditorCursor(line, math.max(0, math.min(lineLength(line), cursor.column)))

  private def normalizeNewlines(value: String): String =
    value.replace("\r\n", "\n").replace('\r', '\n')

object EditorBuffer:
  /** Empty buffer with the cursor at the beginning. */
  def empty: EditorBuffer = fromText("", EditorCursor(0, 0))

  /** Create a buffer from text with the cursor at the end of the final logical line. */
  def apply(text: String): EditorBuffer =
    val lines    = splitLines(text)
    val lastLine = lines.length - 1
    fromLines(lines, EditorCursor(lastLine, Unicode.graphemeClusters(lines(lastLine)).length))

  /** Create a buffer from text and clamp the requested cursor to the resulting logical lines. */
  def fromText(text: String, cursor: EditorCursor = EditorCursor(0, 0)): EditorBuffer =
    fromLines(splitLines(text), cursor)

  /** Create a buffer from logical lines and clamp the requested cursor. */
  def fromLines(lines: Vector[String], cursor: EditorCursor = EditorCursor(0, 0)): EditorBuffer =
    new EditorBuffer(if lines.isEmpty then Vector("") else lines, cursor)

  private def splitLines(text: String): Vector[String] =
    text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector match
      case Vector() => Vector("")
      case lines    => lines
