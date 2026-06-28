package scalatui.terminal

class TerminalInputParserSuite extends munit.FunSuite:
  private def parse(value: String): Vector[TerminalInput] =
    TerminalInputBuffer().process(
      TerminalInputChunk(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    )

  test("parses basic keys and printable unicode"):
    assertEquals(parse("\r"), Vector(TerminalInput.Key(TerminalKey.Enter)))
    assertEquals(parse("\u001b[A"), Vector(TerminalInput.Key(TerminalKey.Up)))
    assertEquals(parse("ä"), Vector(TerminalInput.Key(TerminalKey.Character("ä"))))

  test("parses SGR mouse press release wheel and modifiers"):
    assertEquals(
      parse("\u001b[<0;3;2M"),
      Vector(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 1, col = 2))
    )
    assertEquals(
      parse("\u001b[<0;3;2m"),
      Vector(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), row = 1, col = 2))
    )
    val directions = Vector(
      64 -> MouseWheelDirection.Up,
      65 -> MouseWheelDirection.Down,
      66 -> MouseWheelDirection.Left,
      67 -> MouseWheelDirection.Right
    )
    directions.foreach { (code, direction) =>
      assertEquals(
        parse(s"\u001b[<${code};3;2M"),
        Vector(TerminalInput.Mouse(MouseAction.Wheel(direction), row = 1, col = 2))
      )
    }
    assertEquals(
      parse("\u001b[<28;1;1M"),
      Vector(TerminalInput.Mouse(
        MouseAction.Press(MouseButton.Left),
        row = 0,
        col = 0,
        modifiers = KeyModifiers(ctrl = true, shift = true, alt = true)
      ))
    )
    assertEquals(
      parse("\u001b[<3;1;1M"),
      Vector(TerminalInput.Mouse(MouseAction.Press(MouseButton.Other(3)), row = 0, col = 0))
    )

  test("preserves invalid SGR mouse coordinates as raw input"):
    Vector(
      "\u001b[<0;0;2M",
      "\u001b[<0;3;0M",
      "\u001b[<999999999999999999999;1;1M"
    ).foreach { value =>
      val bytes  = value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val events = parse(value)
      assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Csi))
      assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.Complete))
      assertEquals(
        events.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten.toVector,
        bytes.toVector
      )
    }

  test("preserves modified, Kitty CSI-u, modifyOtherKeys, SS3, Alt, and controls"):
    assertEquals(
      parse("\u001b[1;5D"),
      Vector(TerminalInput.Key(TerminalKey.Left, KeyModifiers(ctrl = true)))
    )
    assertEquals(
      parse("\u001b[97;9:2u"),
      Vector(TerminalInput.KeyEvent(
        TerminalKey.Character("a"),
        KeyModifiers(superKey = true),
        KeyEventType.Repeat
      ))
    )
    assertEquals(
      parse("\u001b[27;3;120~"),
      Vector(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(alt = true)))
    )
    assertEquals(parse("\u001bOH"), Vector(TerminalInput.Key(TerminalKey.Home)))
    assertEquals(
      parse("\u001bb"),
      Vector(TerminalInput.Key(TerminalKey.Left, KeyModifiers(alt = true)))
    )
    assertEquals(
      parse("\u0003"),
      Vector(TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    )

  test("parses split UTF-8 scalars at every boundary"):
    val bytes = "🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    (1 until bytes.length).foreach { split =>
      val buffer = TerminalInputBuffer()
      assertEquals(buffer.process(TerminalInputChunk(bytes.take(split))), Vector.empty)
      assertEquals(
        buffer.process(TerminalInputChunk(bytes.drop(split))),
        Vector(TerminalInput.Key(TerminalKey.Character("🙂")))
      )
    }

  test("oversized numeric fields remain exact malformed raw input"):
    val value  = "\u001b[1;" + "9" * 128 + "A"
    val bytes  = value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val events = TerminalInputBuffer().process(TerminalInputChunk(bytes))

    assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.Complete))
    assertEquals(
      events.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten.toVector,
      bytes.toVector
    )

  test("streams malformed UTF-8 with exact bytes"):
    val bytes         = Array(0xc3.toByte, 0x28.toByte)
    val events        = TerminalInputBuffer().process(TerminalInputChunk(bytes))
    val reconstructed = events.collect { case TerminalInput.RawChunk(chunk) =>
      chunk.toArray
    }.flatten.toArray
    assertEquals(reconstructed.toVector, bytes.toVector)

  test("incremental UTF-8 decoder handles split malformed and incomplete input"):
    val decoder = TerminalUtf8Decoder()
    val bytes   = "界🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val decoded = bytes.map(byte => decoder.process(TerminalInputChunk(Array(byte)))).mkString
    assertEquals(decoded + decoder.flush(), "界🙂")

    val malformed = TerminalUtf8Decoder()
    assertEquals(
      malformed.process(TerminalInputChunk(Array(0xc3.toByte, 0x28.toByte))),
      "�("
    )
    assertEquals(malformed.flush(), "")

    val incomplete = TerminalUtf8Decoder()
    assertEquals(incomplete.process(TerminalInputChunk(Array(0xf0.toByte, 0x9f.toByte))), "")
    assertEquals(incomplete.flush(), "�")

  test("terminal input chunks copy input and output arrays and enforce bounds"):
    val source = Array[Byte](1, 2, 3)
    val chunk  = TerminalInputChunk(source)
    source(0) = 9
    val output = chunk.toArray
    output(1) = 9
    assertEquals(chunk.toArray.toVector, Vector[Byte](1, 2, 3))
    intercept[IllegalArgumentException](TerminalInputChunk(Array.emptyByteArray))
    intercept[IllegalArgumentException](
      TerminalInputChunk(Array.ofDim[Byte](TerminalInputChunk.MaxBytes + 1))
    )

  test("incremental UTF-8 decoder replaces overlong surrogate and out-of-range encodings"):
    val malformed = Vector(
      Array[Byte](0xc0.toByte, 0xaf.toByte),
      Array[Byte](0xed.toByte, 0xa0.toByte, 0x80.toByte),
      Array[Byte](0xf4.toByte, 0x90.toByte, 0x80.toByte, 0x80.toByte)
    )
    malformed.foreach { bytes =>
      val decoder  = TerminalUtf8Decoder()
      val actual   = bytes.map(byte => decoder.process(TerminalInputChunk(Array(byte)))).mkString +
        decoder.flush()
      val expected = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
      assertEquals(actual, expected)
    }

  test("flush is the explicit Escape and Alt framing boundary"):
    val scalar = "🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8)

    val beforeFlush = TerminalInputBuffer()
    assertEquals(
      beforeFlush.process(TerminalInputChunk(Array(0x1b.toByte) ++ scalar)),
      Vector(TerminalInput.Key(TerminalKey.Character("🙂"), KeyModifiers(alt = true)))
    )
    assertEquals(beforeFlush.flush(), Vector.empty)

    val acrossFlush = TerminalInputBuffer()
    assertEquals(
      acrossFlush.process(TerminalInputChunk(Array(0x1b.toByte))),
      Vector.empty
    )
    assertEquals(acrossFlush.flush(), Vector(TerminalInput.Key(TerminalKey.Escape)))
    assertEquals(
      acrossFlush.process(TerminalInputChunk(scalar)),
      Vector(TerminalInput.Key(TerminalKey.Character("🙂")))
    )

  test("flush ends incomplete Alt UTF-8 framing and later bytes are separate"):
    val scalar   = "🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val buffer   = TerminalInputBuffer()
    assertEquals(
      buffer.process(TerminalInputChunk(Array(0x1b.toByte) ++ scalar.take(2))),
      Vector.empty
    )
    assertEquals(
      buffer.flush(),
      Vector(
        TerminalInput.RawStart(TerminalRawKind.Escape),
        TerminalInput.RawChunk(TerminalInputChunk(Array(0x1b.toByte) ++ scalar.take(2))),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      )
    )
    val later    = buffer.process(TerminalInputChunk(scalar.drop(2)))
    val expected = scalar.drop(2).toVector.flatMap { byte =>
      Vector(
        TerminalInput.RawStart(TerminalRawKind.Utf8),
        TerminalInput.RawChunk(TerminalInputChunk(Array(byte))),
        TerminalInput.RawEnd(TerminalRawTermination.Malformed)
      )
    }
    assertEquals(later, expected)
