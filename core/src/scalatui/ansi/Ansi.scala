package scalatui.ansi

import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

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
      val target        = math.max(0, maxWidth - ellipsisWidth)
      val prefix        = sliceByColumns(value, 0, target).text
      if ellipsisWidth === 0 then prefix + Reset else prefix + Reset + ellipsis + Reset

  def padRight(value: String, width: Int): String =
    val padding = math.max(0, width - visibleWidth(value))
    value + " ".repeat(padding)

  def sliceByColumns(value: String, startColumn: Int, maxWidth: Int): Slice =
    if maxWidth <= 0 then Slice("", 0)
    else
      val builder      = new StringBuilder
      var visible      = 0
      var emittedWidth = 0
      var i            = 0
      while i < value.length && emittedWidth < maxWidth do
        extractEscape(value, i) match
          case Some(escape) =>
            if visible >= startColumn then builder.append(escape.code)
            i += escape.length
          case None         =>
            val nextEscape  = nextEscapeIndex(value, i)
            val plainEnd    = if nextEscape < 0 then value.length else nextEscape
            val plain       = value.substring(i, plainEnd)
            val clusters    = Unicode.graphemeClusters(plain)
            var localOffset = 0
            clusters.foreach { cluster =>
              val clusterWidth = Unicode.graphemeWidth(cluster)
              val clusterStart = visible
              val clusterEnd   = visible + clusterWidth
              if clusterEnd > startColumn && emittedWidth + clusterWidth <= maxWidth then
                builder.append(cluster)
                emittedWidth += clusterWidth
              visible = clusterEnd
              localOffset += cluster.length
            }
            i = plainEnd
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
