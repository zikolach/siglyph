package scalatui.ansi

import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/**
 * ANSI-aware terminal text helpers. Visible width counts tabs as 3 columns, ignores escape
 * sequences, and treats wide grapheme clusters as atomic cells when slicing or truncating.
 */
object Ansi:
  final case class Escape(code: String, length: Int) derives CanEqual
  final case class Slice(text: String, width: Int) derives CanEqual

  val Reset: String = "\u001b[0m"

  def extractEscape(value: String, offset: Int): Option[Escape] =
    if offset >= value.length || (value.charAt(offset) !== '\u001b') then None
    else if offset + 1 >= value.length then None
    else
      value.charAt(offset + 1) match
        case '[' => extractCsi(value, offset)
        case ']' => extractTerminated(value, offset, start = offset + 2)
        case '_' => extractTerminated(value, offset, start = offset + 2)
        case _   => None

  def strip(value: String): String =
    val builder = new StringBuilder
    var i       = 0
    while i < value.length do
      extractEscape(value, i) match
        case Some(escape) => i += escape.length
        case None         =>
          builder.append(value.charAt(i))
          i += 1
    builder.result()

  def visibleWidth(value: String): Int = Unicode.stringWidth(strip(value))

  def truncateToWidth(value: String, maxWidth: Int, ellipsis: String = "..."): String =
    if maxWidth <= 0 then ""
    else if visibleWidth(value) <= maxWidth then value
    else
      val ellipsisWidth = visibleWidth(ellipsis)
      if ellipsisWidth === 0 then withReset(sliceByColumns(value, 0, maxWidth).text)
      else if ellipsisWidth >= maxWidth then withReset(sliceByColumns(ellipsis, 0, maxWidth).text)
      else
        val target = maxWidth - ellipsisWidth
        val prefix = sliceByColumns(value, 0, target).text
        withReset(prefix) + ellipsis + Reset

  def padRight(value: String, width: Int): String =
    val padding = math.max(0, width - visibleWidth(value))
    value + " ".repeat(padding)

  def sliceByColumns(value: String, startColumn: Int, maxWidth: Int): Slice =
    if maxWidth <= 0 then Slice("", 0)
    else
      val safeStart      = math.max(0, startColumn)
      val builder        = new StringBuilder
      val pendingEscapes = new StringBuilder
      var visible        = 0
      var emittedWidth   = 0
      var usedEscapes    = false
      var i              = 0

      def appendPendingEscapes(): Unit =
        if pendingEscapes.nonEmpty then
          builder.append(pendingEscapes.result())
          pendingEscapes.clear()
          usedEscapes = true

      while i < value.length && emittedWidth < maxWidth do
        extractEscape(value, i) match
          case Some(escape) =>
            if visible >= safeStart then
              appendPendingEscapes()
              builder.append(escape.code)
              usedEscapes = true
            else pendingEscapes.append(escape.code)
            i += escape.length
          case None         =>
            val nextEscape = nextEscapeIndex(value, i)
            val plainEnd   = if nextEscape < 0 then value.length else nextEscape
            val plain      = value.substring(i, plainEnd)
            val clusters   = Unicode.graphemeClusters(plain)
            clusters.foreach { cluster =>
              val clusterWidth = Unicode.graphemeWidth(cluster)
              val clusterStart = visible
              val clusterEnd   = visible + clusterWidth
              if clusterStart >= safeStart && clusterEnd <= safeStart + maxWidth && emittedWidth + clusterWidth <= maxWidth
              then
                appendPendingEscapes()
                builder.append(cluster)
                emittedWidth += clusterWidth
              visible = clusterEnd
            }
            i = plainEnd
      if usedEscapes then builder.append(Reset)
      Slice(builder.result(), emittedWidth)

  def wrapTextWithAnsi(value: String, width: Int): Vector[String] =
    if width <= 0 then Vector("")
    else
      value.split("\n", -1).toVector.flatMap { line =>
        if line.isEmpty then Vector("")
        else
          val result    = Vector.newBuilder[String]
          var remaining = line
          while visibleWidth(remaining) > width do
            val sliced = sliceByColumns(remaining, 0, width)
            result += sliced.text
            remaining = sliceByColumns(remaining, width, Int.MaxValue / 4).text
          result += remaining
          result.result()
      }

  private def extractCsi(value: String, offset: Int): Option[Escape] =
    var i      = offset + 2
    var result = Option.empty[Escape]
    while i < value.length && result.isEmpty do
      val ch = value.charAt(i)
      if ch >= 0x40 && ch <= 0x7e then
        result = Some(Escape(value.substring(offset, i + 1), i + 1 - offset))
      i += 1
    result

  private def extractTerminated(value: String, offset: Int, start: Int): Option[Escape] =
    var i      = start
    var result = Option.empty[Escape]
    while i < value.length && result.isEmpty do
      if value.charAt(i) === '\u0007' then
        result = Some(Escape(value.substring(offset, i + 1), i + 1 - offset))
      else if (value.charAt(i) === '\u001b') && i + 1 < value.length && (value
          .charAt(i + 1) === '\\')
      then result = Some(Escape(value.substring(offset, i + 2), i + 2 - offset))
      i += 1
    result

  private def nextEscapeIndex(value: String, offset: Int): Int = value.indexOf('\u001b', offset)

  private def withReset(value: String): String =
    if value.endsWith(Reset) then value else value + Reset
