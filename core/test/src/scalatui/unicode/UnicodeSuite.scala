package scalatui.unicode

import scalatui.terminal.{TerminalInputChunk, TerminalUtf8Decoder}

class UnicodeSuite extends munit.FunSuite:
  import UnicodeGraphemeBreakFixtures.Case

  test("records generated Unicode version and official source"):
    assertEquals(Unicode.version, "17.0.0")
    assertEquals(UnicodeGraphemeBreakFixtures.version, "17.0.0")
    assertEquals(
      UnicodeGraphemeBreakFixtures.sourceUrl,
      "https://www.unicode.org/Public/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt"
    )
    assertEquals(UnicodeGraphemeBreakFixtures.cases.size, 766)

  test("passes every official whole-string grapheme break case losslessly"):
    UnicodeGraphemeBreakFixtures.cases.zipWithIndex.foreach { case (fixture, index) =>
      val value    = stringOf(fixture.codePoints)
      val clusters = Unicode.graphemeClusters(value)
      assertEquals(
        boundaryIndexes(clusters),
        fixture.boundaries,
        s"official GraphemeBreakTest case ${index + 1}"
      )
      assertEquals(clusters.mkString, value, s"lossless case ${index + 1}")
    }

  test("matches official boundaries incrementally at every code-point split"):
    UnicodeGraphemeBreakFixtures.cases.zipWithIndex.foreach { case (fixture, index) =>
      val expected = fixture.boundaries
      (0 to fixture.codePoints.size).foreach { split =>
        val chunks = Vector(fixture.codePoints.take(split), fixture.codePoints.drop(split))
        assertEquals(engineBoundaries(chunks), expected, s"case ${index + 1}, split $split")
      }
      assertEquals(
        engineBoundaries(fixture.codePoints.map(Vector(_))),
        expected,
        s"case ${index + 1}, one-code-point chunks"
      )
    }

  test("decoder-to-engine matches every official case at every UTF-8 byte boundary"):
    UnicodeGraphemeBreakFixtures.cases.zipWithIndex.foreach { case (fixture, index) =>
      val bytes    = stringOf(fixture.codePoints).getBytes("UTF-8")
      val expected = fixture.boundaries
      (1 until bytes.length).foreach { split =>
        assertEquals(
          decodedBoundaries(Vector(bytes.take(split), bytes.drop(split))),
          expected,
          s"case ${index + 1}, byte split $split"
        )
      }
      assertEquals(
        decodedBoundaries(bytes.map(byte => Array(byte)).toVector),
        expected,
        s"case ${index + 1}, one-byte chunks"
      )
    }

  test("reset restores fresh bounded engine state after arbitrarily long input"):
    val engine = GraphemeBoundaryEngine()
    (0 until 100000).foreach(_ => engine.accept(0x0300))
    assertEquals(engine.stateWordCount, 7)
    engine.reset()
    assert(engine.accept(0x1f469))
    assert(!engine.accept(0x200d))
    assert(!engine.accept(0x1f4bb))

    val counter = Unicode.IncrementalGraphemeCounter()
    counter.process("x" + "\u0300" * 100000)
    assertEquals(counter.count, 1L)
    counter.clear()
    counter.process("ab")
    assertEquals(counter.count, 2L)

  test("covers focused Hangul, Indic, marks, prepend, GB11, and regional-indicator rules"):
    val cases = Vector(
      "\u1100\u1161\u11A8" -> Vector("\u1100\u1161\u11A8"),
      "\u0915\u094D\u0915" -> Vector("\u0915\u094D\u0915"),
      "a\u0301\u0903"      -> Vector("a\u0301\u0903"),
      "\u0600a"            -> Vector("\u0600a"),
      "👩\u0308\u200D💻"   -> Vector("👩\u0308\u200D💻"),
      "🇦🇹🇺"             -> Vector("🇦🇹", "🇺")
    )
    cases.foreach { case (value, expected) =>
      assertEquals(Unicode.graphemeClusters(value), expected)
      assertEquals(
        decodedBoundaries(value.getBytes("UTF-8").map(Array(_)).toVector),
        boundaryIndexes(expected)
      )
    }

  test("preserves existing display-width expectations"):
    assertEquals(Unicode.stringWidth("hello"), 5)
    assertEquals(Unicode.stringWidth("表"), 2)
    assertEquals(Unicode.stringWidth("e\u0301"), 1)
    assertEquals(Unicode.stringWidth("🙂"), 2)
    assertEquals(Unicode.stringWidth("🇦🇹"), 2)

  private def engineBoundaries(chunks: Vector[Vector[Int]]): Vector[Int] =
    val engine     = GraphemeBoundaryEngine()
    val boundaries = Vector.newBuilder[Int]
    var index      = 0
    chunks.foreach(_.foreach { codePoint =>
      if engine.accept(codePoint) then boundaries += index
      index += 1
    })
    boundaries += index
    boundaries.result()

  private def decodedBoundaries(chunks: Vector[Array[Byte]]): Vector[Int] =
    val decoder    = TerminalUtf8Decoder()
    val codePoints = Vector.newBuilder[Vector[Int]]
    chunks.filter(_.nonEmpty).foreach { bytes =>
      codePoints += Unicode.codePoints(decoder.process(TerminalInputChunk(bytes)))
    }
    codePoints += Unicode.codePoints(decoder.flush())
    engineBoundaries(codePoints.result())

  private def boundaryIndexes(clusters: Vector[String]): Vector[Int] =
    val starts = Vector.newBuilder[Int]
    var index  = 0
    starts += 0
    clusters.foreach { cluster =>
      index += Unicode.codePoints(cluster).size
      starts += index
    }
    starts.result().distinct

  private def stringOf(codePoints: Vector[Int]): String =
    val builder = StringBuilder()
    codePoints.foreach(codePoint => builder.append(String(Character.toChars(codePoint))))
    builder.result()
