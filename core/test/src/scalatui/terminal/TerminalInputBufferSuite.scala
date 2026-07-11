package scalatui.terminal

class TerminalInputBufferSuite extends munit.FunSuite:
  private def chunk(value: String): TerminalInputChunk =
    TerminalInputChunk(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  test("buffers split CSI arrow sequence"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b[")), Vector.empty)
    assertEquals(buffer.process(chunk("A")), Vector(TerminalInput.Key(TerminalKey.Up)))

  test("flush emits a standalone escape as a typed key exactly once"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b")), Vector.empty)
    assertEquals(buffer.flush(), Vector(TerminalInput.Key(TerminalKey.Escape)))
    assertEquals(buffer.flush(), Vector.empty)

  test("escape continuation before flush remains one combined key"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b")), Vector.empty)
    assertEquals(
      buffer.process(chunk("x")),
      Vector(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(alt = true)))
    )
    assertEquals(buffer.flush(), Vector.empty)

  test("recognizes paste markers split at every byte boundary"):
    val bytes = "\u001b[200~hello\u001b[201~".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    (1 until bytes.length).foreach { split =>
      val buffer = TerminalInputBuffer()
      val events = buffer.process(TerminalInputChunk(bytes.take(split))) ++ buffer.process(
        TerminalInputChunk(bytes.drop(split))
      )
      assertEquals(events.head, TerminalInput.PasteStart)
      assertEquals(events.last, TerminalInput.PasteEnd)
      assertEquals(
        events.collect { case TerminalInput.PasteChunk(value) => value.toArray }.flatten.map(
          _.toChar
        ).mkString,
        "hello"
      )
    }

  test("streams paste larger than 4096 without retaining the paste"):
    val buffer = TerminalInputBuffer()
    val start  = buffer.process(chunk("\u001b[200~"))
    val data   = Array.fill[Byte](TerminalInputChunk.MaxBytes)(0x61)
    val first  = buffer.process(TerminalInputChunk(data))
    val second = buffer.process(TerminalInputChunk(data))
    assertEquals(start, Vector(TerminalInput.PasteStart))
    assertEquals(
      (first ++ second).collect { case TerminalInput.PasteChunk(value) => value.length }.sum,
      8192
    )

  test("flush emits incomplete escape as exact raw stream"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b[1;")), Vector.empty)
    val events = buffer.flush()
    assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.Incomplete))
    assertEquals(
      events.collect { case TerminalInput.RawChunk(value) => value.toArray }.flatten.map(
        _.toChar
      ).mkString,
      "\u001b[1;"
    )

  test("switches overlong CSI to bounded raw streaming"):
    val buffer = TerminalInputBuffer()
    val bytes  =
      ("\u001b[" + "1" * 4094 + "1" + "A").getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val events =
      bytes.grouped(4096).flatMap(part => buffer.process(TerminalInputChunk(part))).toVector
    assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.LimitExceeded(true)))
    assertEquals(
      events.collect { case TerminalInput.RawChunk(value) => value.toArray }.flatten.toVector,
      bytes.toVector
    )

  test("overlong raw stream terminates when byte 4097 is its terminator"):
    val buffer = TerminalInputBuffer()
    val bytes  =
      ("\u001b[" + "1" * 4094 + "x").getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val events =
      bytes.grouped(4096).flatMap(part => buffer.process(TerminalInputChunk(part))).toVector

    assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.LimitExceeded(true)))
    assertEquals(buffer.flush(), Vector.empty)
    assertEquals(
      events.collect { case TerminalInput.RawChunk(value) => value.toArray }.flatten.toVector,
      bytes.toVector
    )

  test("byte-by-byte paste reconstructs exact data beyond one chunk"):
    val data   =
      Array.tabulate[Byte](TerminalInputChunk.MaxBytes + 257)(index => (index % 251).toByte)
    val bytes  = "\u001b[200~".getBytes(java.nio.charset.StandardCharsets.UTF_8) ++
      data ++ "\u001b[201~".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val buffer = TerminalInputBuffer()
    val events = bytes.flatMap(byte => buffer.process(TerminalInputChunk(Array(byte)))).toVector
    assertEquals(events.head, TerminalInput.PasteStart)
    assertEquals(events.last, TerminalInput.PasteEnd)
    assertEquals(
      events.collect { case TerminalInput.PasteChunk(chunk) => chunk.toArray }.flatten.toVector,
      data.toVector
    )

  test("raw terminators split at every boundary preserve introducer data and terminator"):
    val values = Vector(
      "\u001b]11;rgb:11/22/33\u0007" -> TerminalRawKind.Osc,
      "\u001bPpayload\u001b\\"       -> TerminalRawKind.Dcs,
      "\u001b_payload\u001b\\"       -> TerminalRawKind.Apc
    )
    values.foreach { (value, kind) =>
      val bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      (1 until bytes.length).foreach { split =>
        val buffer = TerminalInputBuffer()
        val events = buffer.process(TerminalInputChunk(bytes.take(split))) ++
          buffer.process(TerminalInputChunk(bytes.drop(split)))
        assert(events.nonEmpty, s"no events for $kind at split $split")
        assertEquals(events.head, TerminalInput.RawStart(kind))
        assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.Complete))
        assertEquals(
          events.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten.toVector,
          bytes.toVector
        )
      }
    }

  test("typed candidate emits at byte 4097 and never retains attacker-sized input"):
    val buffer = TerminalInputBuffer()
    val prefix = ("\u001b[" + "1" * 4094).getBytes(java.nio.charset.StandardCharsets.UTF_8)
    assertEquals(prefix.length, TerminalInputBuffer.MaxTypedSequenceBytes)
    assertEquals(buffer.process(TerminalInputChunk(prefix)), Vector.empty)

    val overflow = buffer.process(TerminalInputChunk(Array('1'.toByte)))
    assertEquals(overflow.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(
      overflow.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten.toVector,
      (prefix :+ '1'.toByte).toVector
    )
    assert(
      overflow.collect { case TerminalInput.RawChunk(chunk) => chunk.length }.forall(_ <= 4096)
    )

    val tail   = Array.fill[Byte](TerminalInputChunk.MaxBytes + 17)('2'.toByte) :+ 'A'.toByte
    val events =
      tail.grouped(4096).flatMap(part => buffer.process(TerminalInputChunk(part))).toVector
    assertEquals(events.last, TerminalInput.RawEnd(TerminalRawTermination.LimitExceeded(true)))
    assertEquals(
      (overflow ++ events).collect { case TerminalInput.RawChunk(chunk) =>
        chunk.toArray
      }.flatten.toVector,
      ((prefix :+ '1'.toByte) ++ tail).toVector
    )
    assert(events.collect { case TerminalInput.KeyEvent(_, _, _) => () }.isEmpty)

  test("malformed and incomplete raw streams reconstruct exact arbitrary bytes"):
    val values = Vector(
      Array[Byte](0xc0.toByte),
      Array[Byte](0xe0.toByte, 0x80.toByte, 0x80.toByte),
      Array[Byte](0xf5.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte),
      Array[Byte](0x1b, 0x5d, 0x31, 0x31, 0x3b, 0xff.toByte)
    )
    values.foreach { bytes =>
      val buffer = TerminalInputBuffer()
      val events =
        bytes.flatMap(byte => buffer.process(TerminalInputChunk(Array(byte)))).toVector ++
          buffer.flush()
      assertEquals(
        events.collect { case TerminalInput.RawChunk(chunk) => chunk.toArray }.flatten.toVector,
        bytes.toVector
      )
    }
