//> using scala "3.7.4"

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object GenerateUnicodeTables:
  private val Version = "17.0.0"
  private val Base    = s"https://www.unicode.org/Public/$Version/ucd"
  private val Sources = Vector(
    Source("ReadMe", s"$Base/ReadMe.txt", "ReadMe.txt", Version),
    Source("EastAsianWidth", s"$Base/EastAsianWidth.txt", "EastAsianWidth.txt", Version),
    Source(
      "DerivedGeneralCategory",
      s"$Base/extracted/DerivedGeneralCategory.txt",
      "DerivedGeneralCategory.txt",
      Version
    ),
    Source(
      "GraphemeBreakProperty",
      s"$Base/auxiliary/GraphemeBreakProperty.txt",
      "GraphemeBreakProperty.txt",
      Version
    ),
    Source("EmojiData", s"$Base/emoji/emoji-data.txt", "emoji-data.txt", "17.0"),
    Source(
      "DerivedCoreProperties",
      s"$Base/DerivedCoreProperties.txt",
      "DerivedCoreProperties.txt",
      Version
    ),
    Source(
      "GraphemeBreakTest",
      s"$Base/auxiliary/GraphemeBreakTest.txt",
      "GraphemeBreakTest.txt",
      Version
    )
  )

  private final case class Source(key: String, url: String, filename: String, headerVersion: String)
  private final case class Interval(start: Int, end: Int)
  private final case class GraphemeTest(codePoints: Vector[Int], boundaries: Vector[Int])

  @main def main(outputs: String*): Unit =
    require(outputs.length <= 2, "expected optional runtime and fixture output paths")
    val runtimeOutput =
      outputs.headOption.getOrElse("core/src/scalatui/unicode/UnicodeTables.scala")
    val fixtureOutput = outputs.lift(1).getOrElse(
      "core/test/src/scalatui/unicode/UnicodeGraphemeBreakFixtures.scala"
    )
    validateParserContracts()
    val files         = Sources.map(source => source.key -> fetchAndValidate(source)).toMap

    val zeroWidth     = parseTwoFieldProperties(
      files("DerivedGeneralCategory"),
      "DerivedGeneralCategory.txt",
      Set("Mn", "Me", "Cf", "Cc", "Cs")
    ).valuesIterator.flatten.toVector
    val wide          = parseTwoFieldProperties(
      files("EastAsianWidth"),
      "EastAsianWidth.txt",
      Set("W", "F")
    ).valuesIterator.flatten.toVector
    val graphemeNames = Set(
      "CR",
      "LF",
      "Control",
      "Extend",
      "ZWJ",
      "Regional_Indicator",
      "Prepend",
      "SpacingMark",
      "L",
      "V",
      "T",
      "LV",
      "LVT"
    )
    val grapheme      = parseTwoFieldProperties(
      files("GraphemeBreakProperty"),
      "GraphemeBreakProperty.txt",
      graphemeNames,
      rejectUnselected = true
    )
    val emoji         = parseTwoFieldProperties(
      files("EmojiData"),
      "emoji-data.txt",
      Set("Emoji_Presentation", "Extended_Pictographic")
    )
    val incb          = parseIncb(files("DerivedCoreProperties"), Set("Consonant", "Extend", "Linker"))
    val tests         = parseGraphemeTests(files("GraphemeBreakTest"))

    val byName = Map(
      "zeroWidth"            -> zeroWidth,
      "wide"                 -> wide,
      "graphemeCr"           -> grapheme("CR"),
      "graphemeLf"           -> grapheme("LF"),
      "graphemeControl"      -> grapheme("Control"),
      "graphemeExtend"       -> grapheme("Extend"),
      "graphemeZwj"          -> grapheme("ZWJ"),
      "graphemeSpacingMark"  -> grapheme("SpacingMark"),
      "graphemePrepend"      -> grapheme("Prepend"),
      "graphemeL"            -> grapheme("L"),
      "graphemeV"            -> grapheme("V"),
      "graphemeT"            -> grapheme("T"),
      "graphemeLv"           -> grapheme("LV"),
      "graphemeLvt"          -> grapheme("LVT"),
      "regionalIndicator"    -> grapheme("Regional_Indicator"),
      "emojiPresentation"    -> emoji("Emoji_Presentation"),
      "extendedPictographic" -> emoji("Extended_Pictographic"),
      "incbConsonant"        -> incb("Consonant"),
      "incbExtend"           -> incb("Extend"),
      "incbLinker"           -> incb("Linker")
    )

    write(runtimeOutput, renderRuntime(byName))
    write(fixtureOutput, renderFixtures(tests))
    println(
      s"Generated $runtimeOutput and $fixtureOutput for Unicode $Version (${tests.size} tests)"
    )

  private def fetchAndValidate(source: Source): String =
    val text       = scala.io.Source.fromURL(URI(source.url).toURL, "UTF-8").mkString
    require(text.nonEmpty, s"${source.filename}: empty source")
    val filenameOk =
      if source.filename == "ReadMe.txt" then text.startsWith("# Unicode Character Database")
      else if source.filename == "emoji-data.txt" then
        text.linesIterator.take(3).contains("# emoji-data.txt")
      else
        text.linesIterator.take(
          3
        ).exists(_.startsWith(s"# ${source.filename.stripSuffix(".txt")}-"))
    require(filenameOk, s"${source.filename}: missing or mismatched filename header")
    val versionOk  =
      if source.filename == "ReadMe.txt" then text.contains(s"Version $Version")
      else if source.filename == "emoji-data.txt" then
        text.linesIterator.take(12).contains("# Version: 17.0")
      else text.linesIterator.take(3).exists(_.contains(s"-${source.headerVersion}.txt"))
    require(versionOk, s"${source.filename}: expected Unicode ${source.headerVersion} header")
    text

  private def fetch(url: String): String =
    scala.io.Source.fromURL(URI(url).toURL, "UTF-8").mkString

  private def parseTwoFieldProperties(
      text: String,
      filename: String,
      wanted: Set[String],
      rejectUnselected: Boolean = false
  ): Map[String, Vector[Interval]] =
    val result = scala.collection.mutable.Map.from(wanted.iterator.map(_ -> Vector.empty[Interval]))
    text.linesIterator.zipWithIndex.foreach { case (raw, index) =>
      val line = raw.takeWhile(_ != '#').trim
      if line.nonEmpty then
        val fields   = line.split(";", -1).map(_.trim)
        require(fields.length == 2, s"$filename:${index + 1}: property record must have two fields")
        val interval = parseRange(fields(0), filename, index + 1)
        val property = fields(1)
        require(
          property.matches("[A-Za-z][A-Za-z0-9_]*"),
          s"$filename:${index + 1}: malformed property $property"
        )
        require(
          !rejectUnselected || wanted.contains(property),
          s"$filename:${index + 1}: unknown property $property"
        )
        if wanted.contains(property) then result(property) = result(property) :+ interval
    }
    require(
      result.forall(_._2.nonEmpty),
      s"$filename: missing required properties: ${result.filter(_._2.isEmpty).keys.toVector.sorted.mkString(", ")}"
    )
    result.toMap

  private def parseIncb(text: String, wanted: Set[String]): Map[String, Vector[Interval]] =
    val filename = "DerivedCoreProperties.txt"
    val result   = scala.collection.mutable.Map.from(wanted.iterator.map(_ -> Vector.empty[Interval]))
    text.linesIterator.zipWithIndex.foreach { case (raw, index) =>
      val line = raw.takeWhile(_ != '#').trim
      if line.nonEmpty then
        val fields = line.split(";", -1).map(_.trim)
        require(
          fields.length == 2 || (fields.length == 3 && fields(1) == "InCB"),
          s"$filename:${index + 1}: malformed property record"
        )
        parseRange(fields(0), filename, index + 1)
        if fields(1) == "InCB" then
          require(fields.length == 3, s"$filename:${index + 1}: InCB record must have three fields")
          require(
            wanted.contains(fields(2)),
            s"$filename:${index + 1}: unknown InCB value ${fields(2)}"
          )
          result(fields(2)) = result(fields(2)) :+ parseRange(fields(0), filename, index + 1)
        else
          require(
            fields(1).matches("[A-Za-z][A-Za-z0-9_]*"),
            s"$filename:${index + 1}: malformed property ${fields(1)}"
          )
    }
    require(result.forall(_._2.nonEmpty), s"$filename: missing required InCB sections")
    result.toMap

  private def validateParserContracts(): Unit =
    def rejects(run: => Any, label: String): Unit =
      val rejected =
        try
          run
          false
        catch case _: IllegalArgumentException => true
      require(rejected, s"parser contract did not reject $label")

    rejects(
      parseTwoFieldProperties("0041 ; W ; extra", "contract.txt", Set("W")),
      "an extra semicolon field"
    )
    rejects(
      parseTwoFieldProperties("0041 ; W suffix", "contract.txt", Set("W")),
      "a whitespace-suffixed wanted property"
    )
    rejects(
      parseTwoFieldProperties(
        "0041 ; Unknown",
        "contract.txt",
        Set("Control"),
        rejectUnselected = true
      ),
      "an unknown Grapheme_Cluster_Break property"
    )
    rejects(
      parseIncb("0041 ; InCB\n0042 ; InCB ; Consonant", Set("Consonant")),
      "a malformed InCB record"
    )

  private def parseGraphemeTests(text: String): Vector[GraphemeTest] =
    val filename = "GraphemeBreakTest.txt"
    val tests    = text.linesIterator.zipWithIndex.flatMap { case (raw, index) =>
      val line = raw.takeWhile(_ != '#').trim
      if line.isEmpty then None
      else
        val tokens     = line.split("\\s+").toVector
        require(
          tokens.nonEmpty && tokens.head == "÷" && tokens.last == "÷",
          s"$filename:${index + 1}: malformed marker sequence"
        )
        require(
          tokens.length % 2 == 1,
          s"$filename:${index + 1}: malformed marker/code-point sequence"
        )
        val codePoints = Vector.newBuilder[Int]
        val boundaries = Vector.newBuilder[Int]
        var cpCount    = 0
        var tokenIndex = 0
        while tokenIndex < tokens.length do
          val marker = tokens(tokenIndex)
          require(marker == "÷" || marker == "×", s"$filename:${index + 1}: invalid marker $marker")
          if marker == "÷" then boundaries += cpCount
          if tokenIndex + 1 < tokens.length then
            codePoints += parseCodePoint(tokens(tokenIndex + 1), filename, index + 1)
            cpCount += 1
          tokenIndex += 2
        Some(GraphemeTest(codePoints.result(), boundaries.result()))
    }.toVector
    require(tests.nonEmpty, s"$filename: no test cases")
    tests

  private def parseRange(text: String, filename: String, line: Int): Interval =
    text.split("\\.\\.", -1) match
      case Array(single)     =>
        val cp = parseCodePoint(single, filename, line)
        Interval(cp, cp)
      case Array(start, end) =>
        val first = parseCodePoint(start, filename, line)
        val last  = parseCodePoint(end, filename, line)
        require(first <= last, s"$filename:$line: descending range")
        Interval(first, last)
      case _                 => throw IllegalArgumentException(s"$filename:$line: malformed range $text")

  private def parseCodePoint(text: String, filename: String, line: Int): Int =
    require(text.matches("[0-9A-Fa-f]{4,6}"), s"$filename:$line: malformed code point $text")
    val value = Integer.parseInt(text, 16)
    require(value <= 0x10ffff, s"$filename:$line: code point outside Unicode range")
    value

  private def renderRuntime(byName: Map[String, Vector[Interval]]): String =
    val sourceList = Sources.filterNot(_.key == "GraphemeBreakTest").map(source =>
      s"    \"${source.url}\""
    ).sorted.mkString(",\n")
    val tableDefs  = byName.toVector.sortBy(_._1).map { case (name, intervals) =>
      s"  private val $name: Array[Int] = ${formatIntervals(merge(intervals))}"
    }.mkString("\n\n")
    s"""package scalatui.unicode

// format: off
/** Generated by scripts/GenerateUnicodeTables.scala from Unicode $Version data. Do not edit by hand. */
private[scalatui] object UnicodeTables:
  val version: String = \"$Version\"
  val sourceUrls: Vector[String] = Vector(
$sourceList
  )

  // format: off
$tableDefs

  def isZeroWidth(codePoint: Int): Boolean = contains(zeroWidth, codePoint)
  def isWide(codePoint: Int): Boolean = contains(wide, codePoint)
  def isGraphemeCr(codePoint: Int): Boolean = contains(graphemeCr, codePoint)
  def isGraphemeLf(codePoint: Int): Boolean = contains(graphemeLf, codePoint)
  def isGraphemeControl(codePoint: Int): Boolean = contains(graphemeControl, codePoint)
  def isGraphemeExtend(codePoint: Int): Boolean = contains(graphemeExtend, codePoint)
  def isGraphemeZwj(codePoint: Int): Boolean = contains(graphemeZwj, codePoint)
  def isGraphemeSpacingMark(codePoint: Int): Boolean = contains(graphemeSpacingMark, codePoint)
  def isGraphemePrepend(codePoint: Int): Boolean = contains(graphemePrepend, codePoint)
  def isGraphemeL(codePoint: Int): Boolean = contains(graphemeL, codePoint)
  def isGraphemeV(codePoint: Int): Boolean = contains(graphemeV, codePoint)
  def isGraphemeT(codePoint: Int): Boolean = contains(graphemeT, codePoint)
  def isGraphemeLv(codePoint: Int): Boolean = contains(graphemeLv, codePoint)
  def isGraphemeLvt(codePoint: Int): Boolean = contains(graphemeLvt, codePoint)
  def isRegionalIndicator(codePoint: Int): Boolean = contains(regionalIndicator, codePoint)
  def isEmojiPresentation(codePoint: Int): Boolean = contains(emojiPresentation, codePoint)
  def isExtendedPictographic(codePoint: Int): Boolean = contains(extendedPictographic, codePoint)
  def isIncbConsonant(codePoint: Int): Boolean = contains(incbConsonant, codePoint)
  def isIncbExtend(codePoint: Int): Boolean = contains(incbExtend, codePoint)
  def isIncbLinker(codePoint: Int): Boolean = contains(incbLinker, codePoint)

  private def contains(table: Array[Int], codePoint: Int): Boolean =
    var lo = 0
    var hi = table.length / 2 - 1
    var found = false
    while lo <= hi && !found do
      val mid = (lo + hi) >>> 1
      val start = table(mid * 2)
      val end = table(mid * 2 + 1)
      if codePoint < start then hi = mid - 1
      else if codePoint > end then lo = mid + 1
      else found = true
    found
  // format: on
"""

  private def renderFixtures(tests: Vector[GraphemeTest]): String =
    val source = Sources.find(_.key == "GraphemeBreakTest").get
    val rows   = tests.map { test =>
      val cps        = test.codePoints.map(hex).mkString("Vector(", ", ", ")")
      val boundaries = test.boundaries.mkString("Vector(", ", ", ")")
      s"    Case($cps, $boundaries)"
    }.mkString(",\n")
    s"""package scalatui.unicode

// format: off
/** Generated by scripts/GenerateUnicodeTables.scala from Unicode $Version data. Do not edit by hand. */
private[unicode] object UnicodeGraphemeBreakFixtures:
  val version: String = \"$Version\"
  val sourceUrl: String = \"${source.url}\"

  final case class Case(codePoints: Vector[Int], boundaries: Vector[Int]) derives CanEqual

  // format: off
  val cases: Vector[Case] = Vector(
$rows
  )
  // format: on
"""

  private def write(output: String, content: String): Unit =
    val path = Path.of(output)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content.replace("\r\n", "\n"), StandardCharsets.UTF_8)

  private def merge(intervals: Vector[Interval]): Vector[Interval] =
    intervals.sortBy(interval => (interval.start, interval.end)).foldLeft(Vector.empty[Interval]) {
      (acc, next) =>
        acc.lastOption match
          case Some(last) if next.start <= last.end + 1 =>
            acc.updated(acc.size - 1, last.copy(end = math.max(last.end, next.end)))
          case _                                        => acc :+ next
    }

  private def formatIntervals(intervals: Vector[Interval]): String =
    if intervals.isEmpty then "Array.emptyIntArray"
    else
      intervals.flatMap(interval => Vector(hex(interval.start), hex(interval.end))).grouped(10)
        .map(_.mkString("    ", ", ", ""))
        .mkString("Array(\n", ",\n", "\n  )")

  private def hex(value: Int): String = "0x" + value.toHexString
