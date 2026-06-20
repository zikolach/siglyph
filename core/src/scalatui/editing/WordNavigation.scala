package scalatui.editing

import scalatui.unicode.Unicode
import scalatui.syntax.Equality.*

/** Unicode-aware word navigation helpers for cluster-indexed text components. */
object WordNavigation:
  /** Find the cluster cursor after moving one word backward from `cursor`. */
  def findWordBackward(
      text: String,
      cursor: Int,
      isAtomicSegment: String => Boolean = _ => false
  ): Int =
    findWordBackward(Unicode.graphemeClusters(text), cursor, isAtomicSegment)

  /** Find the cluster cursor after moving one word backward from `cursor`. */
  def findWordBackward(
      clusters: Vector[String],
      cursor: Int,
      isAtomicSegment: String => Boolean
  ): Int =
    var i = math.max(0, math.min(cursor, clusters.length))
    while i > 0 && !isAtomicSegment(clusters(i - 1)) && kindOf(
        clusters(i - 1)
      ) === SegmentKind.Whitespace
    do
      i -= 1
    if i <= 0 then 0
    else if isAtomicSegment(clusters(i - 1)) then i - 1
    else
      val targetKind = kindOf(clusters(i - 1)) match
        case SegmentKind.Word        => SegmentKind.Word
        case SegmentKind.Whitespace  => SegmentKind.Whitespace
        case SegmentKind.Punctuation => SegmentKind.Punctuation
      while i > 0 && !isAtomicSegment(clusters(i - 1)) && kindOf(clusters(i - 1)) === targetKind do
        i -= 1
      i

  /** Find the cluster cursor after moving one word forward from `cursor`. */
  def findWordForward(
      text: String,
      cursor: Int,
      isAtomicSegment: String => Boolean = _ => false
  ): Int =
    findWordForward(Unicode.graphemeClusters(text), cursor, isAtomicSegment)

  /** Find the cluster cursor after moving one word forward from `cursor`. */
  def findWordForward(
      clusters: Vector[String],
      cursor: Int,
      isAtomicSegment: String => Boolean
  ): Int =
    var i = math.max(0, math.min(cursor, clusters.length))
    while i < clusters.length && !isAtomicSegment(clusters(i)) && kindOf(
        clusters(i)
      ) === SegmentKind.Whitespace
    do
      i += 1
    if i >= clusters.length then clusters.length
    else if isAtomicSegment(clusters(i)) then i + 1
    else
      val targetKind = kindOf(clusters(i)) match
        case SegmentKind.Word        => SegmentKind.Word
        case SegmentKind.Whitespace  => SegmentKind.Whitespace
        case SegmentKind.Punctuation => SegmentKind.Punctuation
      while i < clusters.length && !isAtomicSegment(clusters(i)) && kindOf(
          clusters(i)
        ) === targetKind
      do
        i += 1
      i

  private enum SegmentKind derives CanEqual:
    case Whitespace, Word, Punctuation

  private def kindOf(cluster: String): SegmentKind =
    val codePoints = Unicode.codePoints(cluster)
    if codePoints.forall(Character.isWhitespace) then SegmentKind.Whitespace
    else if codePoints.exists(isWordCodePoint) then SegmentKind.Word
    else SegmentKind.Punctuation

  private def isWordCodePoint(codePoint: Int): Boolean =
    Character.isLetterOrDigit(codePoint) || codePoint === '_'
