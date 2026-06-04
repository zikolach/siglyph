package scalatui.unicode

object Unicode:
  val version: String = UnicodeTables.version

  final case class Grapheme(text: String, width: Int) derives CanEqual

  def codePoints(value: String): Vector[Int] =
    val builder = Vector.newBuilder[Int]
    var i = 0
    while i < value.length do
      val cp = value.codePointAt(i)
      builder += cp
      i += Character.charCount(cp)
    builder.result()

  def graphemeClusters(value: String): Vector[String] =
    if value.isEmpty then Vector.empty
    else
      val cps = codePointSlices(value)
      val out = Vector.newBuilder[String]
      var clusterStart = cps.head.start
      var prev = cps.head.codePoint
      var regionalInCluster = if isRegionalIndicator(prev) then 1 else 0
      var i = 1
      while i < cps.length do
        val curr = cps(i).codePoint
        val break = shouldBreak(prev, curr, regionalInCluster)
        if break then
          out += value.substring(clusterStart, cps(i).start)
          clusterStart = cps(i).start
          regionalInCluster = if isRegionalIndicator(curr) then 1 else 0
        else if isRegionalIndicator(curr) then regionalInCluster += 1
        else if !isExtendLike(curr) then regionalInCluster = 0
        prev = curr
        i += 1
      out += value.substring(clusterStart)
      out.result()

  def graphemeWidth(cluster: String): Int =
    if cluster == "\t" then 3
    else
      val cps = codePoints(cluster)
      if cps.isEmpty then 0
      else if cps.exists(cp => UnicodeTables.isRegionalIndicator(cp) || UnicodeTables.isEmojiPresentation(cp)) then 2
      else
        val widths = cps.map(codePointWidth)
        if widths.contains(2) then 2 else widths.sum

  def stringWidth(value: String): Int =
    graphemeClusters(value).map(graphemeWidth).sum

  def codePointWidth(codePoint: Int): Int =
    if codePoint == '\t'.toInt then 3
    else if UnicodeTables.isZeroWidth(codePoint) then 0
    else if UnicodeTables.isWide(codePoint) then 2
    else if UnicodeTables.isRegionalIndicator(codePoint) then 2
    else if UnicodeTables.isEmojiPresentation(codePoint) then 2
    else 1

  private final case class CodePointSlice(codePoint: Int, start: Int, end: Int)

  private def codePointSlices(value: String): Vector[CodePointSlice] =
    val builder = Vector.newBuilder[CodePointSlice]
    var i = 0
    while i < value.length do
      val cp = value.codePointAt(i)
      val end = i + Character.charCount(cp)
      builder += CodePointSlice(cp, i, end)
      i = end
    builder.result()

  private def shouldBreak(prev: Int, curr: Int, regionalInCluster: Int): Boolean =
    if UnicodeTables.isGraphemeCr(prev) && UnicodeTables.isGraphemeLf(curr) then false
    else if isControlBreak(prev) || isControlBreak(curr) then true
    else if isExtendLike(curr) then false
    else if UnicodeTables.isGraphemePrepend(prev) then false
    else if UnicodeTables.isGraphemeZwj(prev) && UnicodeTables.isExtendedPictographic(curr) then false
    else if isRegionalIndicator(prev) && isRegionalIndicator(curr) then regionalInCluster % 2 == 0
    else true

  private def isControlBreak(codePoint: Int): Boolean =
    UnicodeTables.isGraphemeCr(codePoint) || UnicodeTables.isGraphemeLf(codePoint) || UnicodeTables.isGraphemeControl(codePoint)

  private def isExtendLike(codePoint: Int): Boolean =
    UnicodeTables.isGraphemeExtend(codePoint) || UnicodeTables.isGraphemeZwj(codePoint) || UnicodeTables.isGraphemeSpacingMark(codePoint)

  private def isRegionalIndicator(codePoint: Int): Boolean = UnicodeTables.isRegionalIndicator(codePoint)
