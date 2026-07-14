package scalatui.unicode

import scalatui.syntax.Containment.*
import scalatui.syntax.Equality.*

private[scalatui] final class GraphemeBoundaryEngine:
  import GraphemeBoundaryEngine.Gcb

  private var started             = false
  private var previous            = Gcb.Other
  private var regionalParity      = false
  private var epExtendContext     = false
  private var zwjAfterEpExtends   = false
  private var incbConsonantActive = false
  private var incbLinkerSeen      = false

  /** Returns whether a grapheme break occurs immediately before `codePoint`. */
  def accept(codePoint: Int): Boolean =
    val current = Gcb.of(codePoint)
    val breaks  =
      if !started then true
      else if previous === Gcb.CR && current === Gcb.LF then false
      else if previous.isControl || current.isControl then true
      else if previous === Gcb.L && (current === Gcb.L || current === Gcb.V || current === Gcb.LV || current === Gcb.LVT)
      then false
      else if (previous === Gcb.LV || previous === Gcb.V) && (current === Gcb.V || current === Gcb.T)
      then false
      else if (previous === Gcb.LVT || previous === Gcb.T) && current === Gcb.T then false
      else if current === Gcb.Extend || current === Gcb.ZWJ then false
      else if current === Gcb.SpacingMark then false
      else if previous === Gcb.Prepend then false
      else if incbConsonantActive && incbLinkerSeen && UnicodeTables.isIncbConsonant(codePoint) then
        false
      else if zwjAfterEpExtends && UnicodeTables.isExtendedPictographic(codePoint) then false
      else if previous === Gcb.RegionalIndicator && current === Gcb.RegionalIndicator then
        !regionalParity
      else true

    val nextZwjAfterEpExtends = current === Gcb.ZWJ && epExtendContext
    epExtendContext =
      UnicodeTables.isExtendedPictographic(codePoint) ||
        (current === Gcb.Extend && epExtendContext)
    zwjAfterEpExtends = nextZwjAfterEpExtends

    if UnicodeTables.isIncbConsonant(codePoint) then
      incbConsonantActive = true
      incbLinkerSeen = false
    else if incbConsonantActive && UnicodeTables.isIncbLinker(codePoint) then incbLinkerSeen = true
    else if !UnicodeTables.isIncbExtend(codePoint) then
      incbConsonantActive = false
      incbLinkerSeen = false

    regionalParity =
      if current === Gcb.RegionalIndicator then
        if started && previous === Gcb.RegionalIndicator then !regionalParity else true
      else false
    previous = current
    started = true
    breaks

  def reset(): Unit =
    started = false
    previous = Gcb.Other
    regionalParity = false
    epExtendContext = false
    zwjAfterEpExtends = false
    incbConsonantActive = false
    incbLinkerSeen = false

  private[unicode] def stateWordCount: Int = 7

private[scalatui] object GraphemeBoundaryEngine:
  private enum Gcb:
    case Other, CR, LF, Control, Extend, ZWJ, RegionalIndicator, Prepend, SpacingMark, L, V, T, LV,
      LVT

    def isControl: Boolean = this === CR || this === LF || this === Control

  private object Gcb:
    def of(codePoint: Int): Gcb =
      if UnicodeTables.isGraphemeCr(codePoint) then Gcb.CR
      else if UnicodeTables.isGraphemeLf(codePoint) then Gcb.LF
      else if UnicodeTables.isGraphemeControl(codePoint) then Gcb.Control
      else if UnicodeTables.isGraphemeExtend(codePoint) then Gcb.Extend
      else if UnicodeTables.isGraphemeZwj(codePoint) then Gcb.ZWJ
      else if UnicodeTables.isRegionalIndicator(codePoint) then Gcb.RegionalIndicator
      else if UnicodeTables.isGraphemePrepend(codePoint) then Gcb.Prepend
      else if UnicodeTables.isGraphemeSpacingMark(codePoint) then Gcb.SpacingMark
      else if UnicodeTables.isGraphemeL(codePoint) then Gcb.L
      else if UnicodeTables.isGraphemeV(codePoint) then Gcb.V
      else if UnicodeTables.isGraphemeT(codePoint) then Gcb.T
      else if UnicodeTables.isGraphemeLv(codePoint) then Gcb.LV
      else if UnicodeTables.isGraphemeLvt(codePoint) then Gcb.LVT
      else Gcb.Other

/**
 * Dependency-free Unicode text operations shared by JVM and Scala Native.
 *
 * Grapheme boundaries follow Unicode 17.0.0 UAX #29 default extended grapheme clusters. The bounded
 * segmenter retains no input text. Display width remains a separate terminal policy and is not
 * defined by UAX #29. Tailored segmentation and runtime Unicode fallbacks are not provided.
 */
object Unicode:
  val version: String = UnicodeTables.version

  final case class Grapheme(text: String, width: Int) derives CanEqual

  private[scalatui] final class IncrementalGraphemeCounter:
    private val engine       = GraphemeBoundaryEngine()
    private var currentCount = 0L

    def count: Long = currentCount

    def process(value: String): Unit =
      foreachCodePoint(value) { codePoint =>
        if engine.accept(codePoint) then currentCount += 1
      }

    def clear(): Unit =
      engine.reset()
      currentCount = 0L

  def codePoints(value: String): Vector[Int] =
    val builder = Vector.newBuilder[Int]
    foreachCodePoint(value)(builder += _)
    builder.result()

  def graphemeClusters(value: String): Vector[String] =
    if value.isEmpty then Vector.empty
    else
      val engine       = GraphemeBoundaryEngine()
      val out          = Vector.newBuilder[String]
      var clusterStart = 0
      var index        = 0
      while index < value.length do
        val codePoint = value.codePointAt(index)
        if engine.accept(codePoint) && index > clusterStart then
          out += value.substring(clusterStart, index)
          clusterStart = index
        index += Character.charCount(codePoint)
      out += value.substring(clusterStart)
      out.result()

  /** Return the first final grapheme boundary at or after a UTF-16 insertion boundary. */
  private[scalatui] def graphemeCursorAfterCodeUnit(
      clusters: Vector[String],
      codeUnitOffset: Int
  ): Int =
    val target = math.max(0, codeUnitOffset)
    var index  = 0
    var end    = 0
    while index < clusters.length && end < target do
      end += clusters(index).length
      index += 1
    index

  def graphemeWidth(cluster: String): Int =
    if cluster === "\t" then 3
    else
      val cps = codePoints(cluster)
      if cps.isEmpty then 0
      else if cps.exists(cp =>
          UnicodeTables.isRegionalIndicator(cp) || UnicodeTables.isEmojiPresentation(cp)
        )
      then 2
      else
        val widths = cps.map(codePointWidth)
        if widths.contains_(2) then 2 else widths.sum

  def stringWidth(value: String): Int =
    graphemeClusters(value).map(graphemeWidth).sum

  def codePointWidth(codePoint: Int): Int =
    if codePoint === '\t'.toInt then 3
    else if UnicodeTables.isZeroWidth(codePoint) then 0
    else if UnicodeTables.isWide(codePoint) then 2
    else if UnicodeTables.isRegionalIndicator(codePoint) then 2
    else if UnicodeTables.isEmojiPresentation(codePoint) then 2
    else 1

  private def foreachCodePoint(value: String)(consume: Int => Unit): Unit =
    var index = 0
    while index < value.length do
      val codePoint = value.codePointAt(index)
      consume(codePoint)
      index += Character.charCount(codePoint)
