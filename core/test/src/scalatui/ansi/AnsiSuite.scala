package scalatui.ansi

import scalatui.unicode.Unicode

class AnsiSuite extends munit.FunSuite:
  test("visible width ignores ANSI escapes"):
    assertEquals(Ansi.visibleWidth("\u001b[31mhello\u001b[0m"), 5)

  test("visible width measures ordinary controls as inert text"):
    assertEquals(Ansi.visibleWidth("a\tb"), 8)

  test("strip removes supported SGR and OSC 8 but makes APC inert"):
    val text =
      "\u001b[31mred\u001b[0m\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\\u001b_marker\u001b\\"
    assertEquals(Ansi.strip(text), "redlink\\u001B_marker\\u001B\\")

  test("APC requires ST and treats BEL as content"):
    val complete = "\u001b_payload\u001b\\"
    val withBel  = "\u001b_before\u0007text\u001b[31mstill APC\u001b\\"
    val open     = "\u001b_before\u0007text\u001b[31mstill APC"

    assertEquals(Ansi.strip(complete + "x"), Ansi.visibleControlText(complete) + "x")
    assertEquals(Ansi.strip(withBel + "x"), Ansi.visibleControlText(withBel) + "x")
    assertEquals(Ansi.strip(open), Ansi.visibleControlText(open))
    assertEquals(Ansi.strip(open), "\\u001B_before\\u0007text\\u001B[31mstill APC")
  test("string controls consume embedded executable-looking metadata atomically"):
    val sgr           = "\u001b[31m"
    val oscOpen       = "\u001b]8;;https://example.com\u0007"
    val oscClose      = "\u001b]8;;\u0007"
    val embedded      = "a" + sgr + oscOpen + "b" + oscClose + "c"
    val dcsCandidates = Vector("\u001bP" + embedded + "\u001b\\", "\u001bP" + embedded)

    dcsCandidates.foreach { candidate =>
      val visible = Ansi.visibleControlText(candidate)
      val width   = Unicode.stringWidth(visible)
      assertEquals(Ansi.strip(candidate), visible, candidate)
      assertEquals(Ansi.sanitize(candidate), visible, candidate)
      assertEquals(Ansi.visibleWidth(candidate), width, candidate)
      assertEquals(Ansi.sliceByColumns(candidate, 0, width), Ansi.Slice(visible, width), candidate)
      assertEquals(Ansi.truncateToWidth(candidate, width, ""), visible, candidate)
      assertEquals(Ansi.padRight(candidate, width), visible, candidate)
      assertEquals(Ansi.wrapTextWithAnsi(candidate, width), Vector(visible), candidate)
    }

    val stOnlyCandidates = Vector(
      "\u001bX" + embedded + "\u001b\\",
      "\u001b^" + embedded + "\u009c",
      "\u001b_" + embedded + "\u009c",
      "\u0090" + embedded + "\u009c",
      "\u0098" + embedded + "\u001b\\",
      "\u009e" + embedded + "\u009c",
      "\u009f" + embedded + "\u009c"
    )
    val inertOsc         = "\u009d52;" + sgr + "\u001b]8;;https://example.com\u009c"

    (stOnlyCandidates :+ inertOsc).foreach(candidate =>
      assertEquals(Ansi.strip(candidate + "z"), Ansi.visibleControlText(candidate) + "z", candidate)
    )

    val supportedOscWithC1St = "\u001b]8;;https://example.com\u009c"
    assertEquals(Ansi.strip(supportedOscWithC1St + "x"), "x")

  test("OSC 8 boundaries count complete UTF-8 sequence bytes"):
    val bounded       = "\u001b]8;;" + "a".repeat(4089) + "\u001b\\"
    val oversized     = "\u001b]8;;" + "a".repeat(4090) + "\u001b\\"
    val supplementary = "\u001b]8;;😀" + "a".repeat(4085) + "\u001b\\"

    assertEquals(bounded.getBytes("UTF-8").length, 4096)
    assertEquals(oversized.getBytes("UTF-8").length, 4097)
    assertEquals(supplementary.getBytes("UTF-8").length, 4096)
    assertEquals(Ansi.strip(bounded), "")
    assertEquals(Ansi.strip(supplementary), "")
    assertEquals(Ansi.strip(oversized), Ansi.visibleControlText(oversized))

  test("truncate preserves ANSI and appends reset/ellipsis"):
    val truncated = Ansi.truncateToWidth("\u001b[31mhello world", 8)
    assertEquals(Ansi.visibleWidth(truncated), 8)
    assert(truncated.contains("..."))
    assert(truncated.contains(Ansi.Reset))

  test("slice by columns respects wide characters"):
    assertEquals(Ansi.sliceByColumns("a表b", 0, 3), Ansi.Slice("a表", 3))
    assertEquals(Ansi.sliceByColumns("a表b", 3, 1), Ansi.Slice("b", 1))

  test("slice by columns avoids partial wide cells at start and end boundaries"):
    assertEquals(Ansi.sliceByColumns("a表b", 2, 3), Ansi.Slice("b", 1))
    assertEquals(Ansi.sliceByColumns("a表b", 0, 2), Ansi.Slice("a", 1))

  test("slice by columns preserves ANSI style and resets emitted styled slices"):
    val sliced = Ansi.sliceByColumns("\u001b[31ma表b", 1, 2)

    assertEquals(Ansi.strip(sliced.text), "表")
    assertEquals(sliced.width, 2)
    assert(sliced.text.startsWith("\u001b[31m"), sliced.text)
    assert(sliced.text.endsWith(Ansi.Reset), sliced.text)

  test("slice by columns omits clipped styled wide grapheme without leaking metadata"):
    val sliced = Ansi.sliceByColumns("\u001b[31m界", 0, 1)

    assertEquals(sliced, Ansi.Slice("", 0))

  test("ANSI metadata inside focused graphemes never creates partial geometry output"):
    val sgr      = "\u001b[31m"
    val osc      = "\u001b]8;;https://example.com\u001b\\"
    val close    = "\u001b]8;;\u001b\\"
    val clusters = Vector(
      ("\u1100", "\u1161\u11a8"),
      ("\u0915", "\u094d\u0915"),
      ("e", "\u0301"),
      ("👩", "\u200d💻"),
      ("🇦", "🇹")
    )

    clusters.foreach { case (first, rest) =>
      val plain  = first + rest
      val raw    = sgr + first + osc + rest + close + Ansi.Reset
      val width  = Ansi.visibleWidth(raw)
      val sliced = Ansi.sliceByColumns("x" + raw + "y", 1, width)
      assertEquals(Ansi.strip(sliced.text), plain, plain)
      assertEquals(sliced.width, width, plain)
      assertEquals(Ansi.strip(Ansi.sliceByColumns(raw, 0, math.max(0, width - 1)).text), "", plain)
      assertEquals(Ansi.strip(Ansi.truncateToWidth(raw + "z", width, "")), plain, plain)
      assertEquals(
        Ansi.wrapTextWithAnsi(raw + "z", width).map(Ansi.strip),
        Vector(plain, "z"),
        plain
      )
      assertEquals(Ansi.visibleWidth(Ansi.padRight(raw, width + 1)), width + 1, plain)
    }

  test("included metadata-interleaved clusters preserve exact order and do not leak state"):
    val sgr           = "\u001b[31m"
    val open          = "\u001b]8;;https://example.com\u001b\\"
    val close         = "\u001b]8;;\u001b\\"
    val raw           = sgr + "e" + open + "\u0301" + close + Ansi.Reset
    val expectedSlice = raw

    assertEquals(
      Ansi.sliceByColumns("x" + raw + "y", 1, 1).text.toVector.map(_.toInt),
      expectedSlice.toVector.map(_.toInt)
    )
    assertEquals(Ansi.truncateToWidth(raw + "z", 1, ""), expectedSlice)
    assertEquals(Ansi.wrapTextWithAnsi(raw + "z", 1), Vector(raw, "z"))
    assertEquals(Ansi.strip(Ansi.wrapTextWithAnsi(raw + "z", 1)(1)), "z")

  test("unterminated repeated OSC and APC prefixes become visible inert data once"):
    val malformed = Vector.fill(10000)("\u001b]x\u001b_y").mkString
    val visible   = Ansi.visibleControlText(malformed)

    assertEquals(Ansi.strip(malformed), visible)
    assertEquals(Ansi.visibleWidth(malformed), Unicode.stringWidth(visible))
    assertEquals(Ansi.truncateToWidth(malformed, Int.MaxValue), visible)

  test("width-one slices omit whole focused wide clusters and retain later fitting text"):
    val sgr      = "\u001b[31m"
    val osc      = "\u001b]8;;https://example.com\u001b\\"
    val close    = "\u001b]8;;\u001b\\"
    val clusters = Vector("界", "\u1100\u1161\u11a8", "\u0915\u094d\u0915", "👩‍💻", "🇦🇹")

    clusters.foreach { cluster =>
      val raw     = sgr + osc + cluster + close + Ansi.Reset + "b"
      val omitted = Ansi.sliceByColumns(raw, 0, 1)
      val later   = Ansi.sliceByColumns(raw, Ansi.visibleWidth(cluster), 1)

      assertEquals(Ansi.strip(omitted.text), "", cluster)
      assert(omitted.width <= 1, cluster)
      assertEquals(omitted.text, "", cluster)
      assertEquals(Ansi.strip(later.text), "b", cluster)
      assertEquals(later.width, 1, cluster)
      assertEquals(later.text, "b", cluster)
    }

  test("wrapped SGR state is replayed and reset on every emitted line"):
    val red   = "\u001b[31m"
    val lines = Ansi.wrapTextWithAnsi(red + "abcde", 2)

    assertEquals(
      lines,
      Vector(red + "ab" + Ansi.Reset, red + "cd" + Ansi.Reset, red + "e" + Ansi.Reset)
    )

  test("selective SGR reset remains effective after wrap replay"):
    val red    = "\u001b[31m"
    val bold   = "\u001b[1m"
    val normal = "\u001b[22m"

    assertEquals(
      Ansi.wrapTextWithAnsi(red + bold + "ab" + normal + "cd", 2),
      Vector(red + bold + "ab" + normal + Ansi.Reset, red + "cd" + Ansi.Reset)
    )

  test("OSC 8 hyperlink is closed and reopened across wrapped lines"):
    val open  = "\u001b]8;;https://example.com\u001b\\"
    val close = "\u001b]8;;\u001b\\"

    assertEquals(
      Ansi.wrapTextWithAnsi(open + "abcd" + close + "e", 2),
      Vector(open + "ab" + close, open + "cd" + close, "e")
    )

  test("slice starting inside active SGR and OSC receives ordered state and closes both"):
    val red   = "\u001b[31m"
    val open  = "\u001b]8;;https://example.com\u001b\\"
    val close = "\u001b]8;;\u001b\\"

    assertEquals(
      Ansi.sliceByColumns(red + open + "abc", 1, 1),
      Ansi.Slice(red + open + "b" + close + Ansi.Reset, 1)
    )

  test("slice ending before source OSC close closes the emitted hyperlink"):
    val open  = "\u001b]8;;https://example.com\u001b\\"
    val close = "\u001b]8;;\u001b\\"

    assertEquals(
      Ansi.sliceByColumns(open + "abc" + close, 0, 2),
      Ansi.Slice(open + "ab" + close, 2)
    )

  test("truncation closes active SGR and OSC state before ellipsis"):
    val red       = "\u001b[31m"
    val open      = "\u001b]8;;https://example.com\u001b\\"
    val close     = "\u001b]8;;\u001b\\"
    val truncated = Ansi.truncateToWidth(red + open + "abcdef", 4, "...")

    assertEquals(truncated, red + open + "a" + close + Ansi.Reset + "..." + Ansi.Reset)

  test("omitted oversized cluster advances active metadata for following fitting text"):
    val red   = "\u001b[31m"
    val open  = "\u001b]8;;https://example.com\u001b\\"
    val close = "\u001b]8;;\u001b\\"

    assertEquals(
      Ansi.wrapTextWithAnsi(red + open + "界b", 1),
      Vector(red + open + "b" + close + Ansi.Reset)
    )

  test("unstyled wrapping output remains unchanged"):
    assertEquals(Ansi.wrapTextWithAnsi("abcdef", 2), Vector("ab", "cd", "ef"))

  test("truncate sanitizes tabs and clips oversized ellipsis"):
    val tabbed = Ansi.truncateToWidth("a\tb", 7, "")
    val narrow = Ansi.truncateToWidth("表abc", 1)

    assertEquals(Ansi.strip(tabbed), "a\\u0009")
    assert(Ansi.visibleWidth(tabbed) <= 7, tabbed)
    assertEquals(Ansi.strip(narrow), ".")
    assert(Ansi.visibleWidth(narrow) <= 1, narrow)

  test("pad right sanitizes tabs"):
    val padded = Ansi.padRight("a\t", 5)

    assertEquals(padded, "a\\u0009")
    assertEquals(Ansi.visibleWidth(padded), 7)

  test("zero requested widths return safe empty or minimal output"):
    assertEquals(Ansi.sliceByColumns("abc", 0, 0), Ansi.Slice("", 0))
    assertEquals(Ansi.truncateToWidth("abc", 0), "")
    assertEquals(Ansi.wrapTextWithAnsi("abc", 0), Vector(""))

  test("wrap text returns lines within width"):
    val lines = Ansi.wrapTextWithAnsi("abc表def", 4)
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)

  test("wrap text carries clipped wide grapheme to next line"):
    val lines = Ansi.wrapTextWithAnsi("abc表def", 4)

    assertEquals(lines.map(Ansi.strip), Vector("abc", "表de", "f"))
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)

  test("wrap text sanitizes tabs"):
    val lines = Ansi.wrapTextWithAnsi("a\tb", 4)

    assertEquals(lines.map(Ansi.strip), Vector("a\\u0", "009b"))
    assert(lines.forall(Ansi.visibleWidth(_) <= 4), lines.toString)

  test("metadata bound accepts 4096 UTF-8 bytes and makes 4097 bytes entirely inert"):
    val bounded   = "\u001b]8;;" + "a".repeat(4090) + "\u0007"
    val oversized = "\u001b]8;;" + "a".repeat(4091) + "\u0007"
    assertEquals(bounded.getBytes("UTF-8").length, Ansi.MaxRecognizedMetadataBytes)
    assertEquals(Ansi.strip(bounded), "")
    assertEquals(Ansi.extractEscape(bounded, 0).map(_.length), Some(bounded.length))
    assertEquals(Ansi.strip(oversized), Ansi.visibleControlText(oversized))
    assertEquals(Ansi.extractEscape(oversized, 0), None)

  test("metadata bound counts non-ASCII OSC 8 content in UTF-8 bytes"):
    val bounded   = "\u001b]8;;" + "猫".repeat(1363) + "a" + "\u0007"
    val oversized = "\u001b]8;;" + "猫".repeat(1364) + "\u0007"
    assertEquals(bounded.getBytes("UTF-8").length, 4096)
    assertEquals(oversized.getBytes("UTF-8").length, 4098)
    assertEquals(Ansi.strip(bounded), "")
    assertEquals(Ansi.strip(oversized), Ansi.visibleControlText(oversized))

  test("all output utilities make unsupported and ordinary controls visible"):
    val oversized = "\u001b]8;;" + "x".repeat(4090) + "\u001b\\"
    val rejected  = Vector(
      "\u001b[?25h",
      "\u001b[2J",
      "\u001b]52;c;payload\u0007",
      "\u001b_Gkitty\u001b\\",
      "\u001bPpayload\u001b\\",
      "\u001bc",
      "\u009b2J",
      "a\u0008b",
      "\u001b",
      "\u001b[999m",
      oversized,
      "\u001b]unterminated\u001b[31m"
    )

    rejected.foreach { value =>
      val visible = Ansi.visibleControlText(value)
      val width   = Unicode.stringWidth(visible)
      assertEquals(Ansi.strip(value), visible, value)
      assertEquals(Ansi.sanitize(value), visible, value)
      assertEquals(Ansi.visibleWidth(value), width, value)
      assertEquals(Ansi.sliceByColumns(value, 0, width), Ansi.Slice(visible, width), value)
      assertEquals(Ansi.truncateToWidth(value, width, ""), visible, value)
      assertEquals(Ansi.padRight(value, width), visible, value)
      assertEquals(Ansi.wrapTextWithAnsi(value, math.max(1, width)), Vector(visible), value)
    }

  test("output utilities preserve only supported SGR and OSC 8 metadata"):
    val open  = "\u001b]8;;https://example.com\u001b\\"
    val close = "\u001b]8;;\u001b\\"
    val value = "\u001b[31m" + open + "x" + close + Ansi.Reset

    assertEquals(Ansi.sanitize(value), value)
    assertEquals(Ansi.strip(value), "x")
    assertEquals(Ansi.truncateToWidth(value, 1, ""), value)

  test("invalid SGR is wholly inert and does not partially change effective state"):
    val invalid = Vector("\u001b[?1m", "\u001b[999m", "\u001b[1;38;5;256m", "\u001b[1;38:2:1:2:3m")
    invalid.foreach { sgr =>
      val wrapped = Ansi.wrapTextWithAnsi("\u001b[31m" + sgr + "ab", 1)
      assertEquals(Ansi.strip(sgr), Ansi.visibleControlText(sgr), sgr)
      assertEquals(wrapped.last, "\u001b[31mb" + Ansi.Reset, sgr)
    }

  test("modeled SGR attributes and colors replay normalized effective state"):
    val attributes = Vector(
      "1",
      "2",
      "3",
      "20",
      "4",
      "4:1",
      "4:2",
      "4:3",
      "4:4",
      "4:5",
      "5",
      "6",
      "7",
      "8",
      "9",
      "11",
      "19",
      "26",
      "51",
      "52",
      "53",
      "60",
      "64",
      "73",
      "74",
      "31",
      "104",
      "38;5;255",
      "48;2;1;2;3",
      "58:5:7",
      "38:2::1:2:3",
      "48:2::4:5:6",
      "58:2::7:8:9"
    )
    attributes.foreach { parameter =>
      val sgr   = s"\u001b[${parameter}m"
      val lines = Ansi.wrapTextWithAnsi(sgr + "ab", 1)
      assertEquals(Ansi.strip(lines.mkString), "ab", parameter)
      assert(lines(1).startsWith(sgr), parameter)
    }

  test("selective resets preserve exact unrelated replay without full reset semantics"):
    val red     = "\u001b[31m"
    val cases   = Vector(
      ("10", "\u001b[11m", red),
      ("22", "\u001b[1m\u001b[2m", "\u001b[3m"),
      ("23", "\u001b[3m", red),
      ("24", "\u001b[4m", red),
      ("25", "\u001b[5m", red),
      ("27", "\u001b[7m", red),
      ("28", "\u001b[8m", red),
      ("29", "\u001b[9m", red),
      ("39", red, "\u001b[44m"),
      ("49", "\u001b[44m", red),
      ("50", "\u001b[26m", red),
      ("54", "\u001b[51m", red),
      ("55", "\u001b[53m", red),
      ("59", "\u001b[58;5;1m", red),
      ("65", "\u001b[60m", red),
      ("75", "\u001b[73m", red)
    )
    cases.foreach { case (reset, target, unrelated) =>
      val lines = Ansi.wrapTextWithAnsi(target + unrelated + "a" + s"\u001b[${reset}m" + "b", 1)
      assertEquals(lines(1), unrelated + "b" + Ansi.Reset, reset)
      assert(!lines(1).contains(target), reset)
    }
    assertEquals(
      Ansi.wrapTextWithAnsi("\u001b[31ma\u001b[0;1mbc", 1).drop(1),
      Vector("\u001b[1mb" + Ansi.Reset, "\u001b[1mc" + Ansi.Reset)
    )
    val invalid = "\u001b[0;999m"
    val source  = red + "a" + invalid + "bc"
    val start   = 1 + Ansi.visibleWidth(invalid)
    assertEquals(Ansi.sliceByColumns(source, start, 1), Ansi.Slice(red + "b" + Ansi.Reset, 1))

  test("long equivalent SGR histories have fixed normalized replay"):
    val history = Vector.fill(1000000)("\u001b[31m\u001b[1m").mkString
    val lines   = Ansi.wrapTextWithAnsi(history + "ab", 1)
    assertEquals(lines(1), "\u001b[31m\u001b[1mb" + Ansi.Reset)
    assert(lines(1).length < 32, lines(1).length.toString)
    assertEquals(Ansi.retainedStateAfter(history).sgrSlots, 2)
    assert(Ansi.retainedStateAfter(history).sgrSlots <= 17)

  test("large OSC is inert and unterminated OSC and APC are visible once"):
    val oversized = "\u001b]8;;" + "x".repeat(5000) + "\u001b\\"
    assertEquals(Ansi.strip(oversized), Ansi.visibleControlText(oversized))
    assertEquals(Ansi.retainedStateAfter(oversized).oscOpenerUtf8Bytes, 0)
    Vector("\u001b]abc", "\u001b_abc", "\u001b]bad\u001b[31mstill bad").foreach(value =>
      assertEquals(Ansi.strip(value), Ansi.visibleControlText(value))
      assertEquals(Ansi.retainedStateAfter(value).oscOpenerUtf8Bytes, 0)
    )

  test("retained OSC opener uses actual bounded runtime state"):
    val opener = "\u001b]8;;" + "x".repeat(4089) + "\u001b\\"
    assertEquals(opener.getBytes("UTF-8").length, 4096)
    assertEquals(Ansi.retainedStateAfter(opener).oscOpenerUtf8Bytes, 4096)
    assert(Ansi.wrapTextWithAnsi(opener + "ab", 1).forall(_.length <= 4106))

  test("OSC BEL and ST replace close and preserve source order with SGR"):
    val belOpen  = "\u001b]8;;a\u0007"
    val belClose = "\u001b]8;;\u0007"
    val stOpen   = "\u001b]8;;b\u001b\\"
    val stClose  = "\u001b]8;;\u001b\\"
    assertEquals(
      Ansi.wrapTextWithAnsi("\u001b[31m" + belOpen + "ab" + belClose, 1)(1),
      "\u001b[31m" + belOpen + "b" + belClose + Ansi.Reset
    )
    assertEquals(
      Ansi.wrapTextWithAnsi(stOpen + "\u001b[31mab" + stClose, 1)(1),
      stOpen + "\u001b[31mb" + stClose + Ansi.Reset
    )
    assertEquals(
      Ansi.wrapTextWithAnsi(belOpen + "a" + stOpen + "bc", 1)(2),
      stOpen + "c" + stClose
    )

  test("ordinary application text beyond metadata bound remains unlimited"):
    val text = "猫abc".repeat(100000)
    assertEquals(Ansi.strip(text), text)
    assertEquals(Ansi.visibleWidth(text), Unicode.stringWidth(text))

  test("editor projection retains control-expansion ownership and atomic supported metadata"):
    val controls   = Vector("\u0000", "\t", "\u007f", "\u0085")
    val projection = Ansi.projectLayout(controls)
    val visible    = controls.map(Ansi.visibleControlText).mkString

    assertEquals(projection.units.map(_.printable).mkString, visible)
    assertEquals(
      projection.units.flatMap(_.parts.collect {
        case part: Ansi.ProjectedPrintable => part.source
      }).toSet,
      controls.indices.map(index => Ansi.SourceRange(index, index + 1)).toSet
    )

    val sgr       = "\u001b[31m"
    val source    = Unicode.graphemeClusters("a" + sgr + "b")
    val supported = Ansi.projectLayout(source)
    val metadata  = supported.units.flatMap(_.parts.collect {
      case part: Ansi.ProjectedMetadata => part
    })

    assertEquals(metadata.map(_.text), Vector(sgr))
    assertEquals(supported.units.map(_.printable).mkString, "ab")
    assertEquals(supported.displayBoundary(1), 1)
    val afterMetadata = Unicode.graphemeClusters("a" + sgr).length
    (1 to afterMetadata).foreach(boundary =>
      assertEquals(supported.displayBoundary(boundary), 1, boundary.toString)
    )

  test("editor projection uses the shared scanner for complete and unterminated controls"):
    val candidates = Vector(
      "\u001bPpayload\u001b\\",
      "\u001bPpayload",
      "\u001b_payload\u001b\\",
      "\u001b_payload",
      "\u001b]52;c;payload\u0007",
      "\u001b]52;c;payload",
      "\u0090payload\u009c",
      "\u0090payload",
      "\u009dpayload\u009c",
      "\u009dpayload"
    )

    candidates.foreach { candidate =>
      val projection = Ansi.projectLayout(Unicode.graphemeClusters(candidate))
      assertEquals(
        projection.units.map(_.printable).mkString,
        Ansi.visibleControlText(candidate),
        candidate
      )
      assertEquals(projection.metadataOnly, Vector.empty, candidate)
    }

  test("editor projection keeps replay state bounded while source content remains unlimited"):
    val open       = "\u001b]8;;https://example.com\u001b\\"
    val source     = open + "x".repeat(100000)
    val projection = Ansi.projectLayout(Unicode.graphemeClusters(source))

    assertEquals(projection.units.map(_.printable).mkString, "x".repeat(100000))
    assert(projection.finalReplay.length <= open.length, projection.finalReplay.length.toString)
    assertEquals(projection.sourceGraphemeCount, Unicode.graphemeClusters(source).length)
