package scalatui.terminal
import scalatui.syntax.Equality.*

class TerminalInputBufferSuite extends munit.FunSuite:
  private def chunk(value: String): TerminalInputChunk =
    TerminalInputChunk(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  test("buffers split CSI arrow sequence"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b[")), Vector.empty)
    assertEquals(buffer.process(chunk("A")), Vector(TerminalInput.Key(TerminalKey.Up)))

  test("buffers split SGR mouse report"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b[<64;")), Vector.empty)
    assertEquals(buffer.process(chunk("10;5")), Vector.empty)
    assertEquals(
      buffer.process(chunk("M")),
      Vector(TerminalInput.Mouse(MouseAction.Wheel(MouseWheelDirection.Up), row = 4, col = 9))
    )

  test("flush emits incomplete SGR mouse report as exact raw input"):
    val value  = "\u001b[<64;10;"
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk(value)), Vector.empty)
    assertEquals(
      buffer.flush(),
      Vector(
        TerminalInput.RawStart(TerminalRawKind.Csi),
        TerminalInput.RawChunk(chunk(value)),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      )
    )

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
  test("escape plus multibyte UTF-8 scalar is one Alt key across every fragmentation"):
    Vector("¢", "界", "🙂").foreach { scalar =>
      val bytes = ("\u001b" + scalar).getBytes(java.nio.charset.StandardCharsets.UTF_8)
      (0 until (1 << (bytes.length - 1))).foreach { boundaryMask =>
        val buffer = TerminalInputBuffer()
        val events = Vector.newBuilder[TerminalInput]
        var start  = 0
        (0 until bytes.length - 1).foreach { index =>
          if (boundaryMask & (1 << index)) !== 0 then
            events ++= buffer.process(TerminalInputChunk(bytes.slice(start, index + 1)))
            start = index + 1
        }
        events ++= buffer.process(TerminalInputChunk(bytes.drop(start)))
        assertEquals(
          events.result(),
          Vector(TerminalInput.Key(TerminalKey.Character(scalar), KeyModifiers(alt = true))),
          s"$scalar with boundary mask $boundaryMask"
        )
      }
    }

  test("malformed and incomplete Alt UTF-8 framing preserves exact raw bytes"):
    Vector(
      Array[Byte](0x1b, 0xc3.toByte, 0x28),
      Array[Byte](0x1b, 0xf0.toByte, 0x9f.toByte)
    ).foreach { bytes =>
      val buffer = TerminalInputBuffer()
      val events = bytes.flatMap(byte =>
        buffer.process(TerminalInputChunk(Array(byte)))
      ).toVector ++ buffer.flush()
      assertEquals(events.head, TerminalInput.RawStart(TerminalRawKind.Escape))
      assertEquals(
        events.collect { case TerminalInput.RawChunk(value) => value.toArray }.flatten.toVector,
        bytes.toVector
      )
    }

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

  test("large paste emits bounded chunks without event amplification"):
    val buffer  = TerminalInputBuffer()
    val payload = Array.tabulate[Byte](TerminalInputChunk.MaxBytes * 2 + 17)(index =>
      ('a' + index % 26).toByte
    )
    val bytes   = "\u001b[200~".getBytes(java.nio.charset.StandardCharsets.UTF_8) ++
      payload ++ "\u001b[201~".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val events  = bytes.grouped(TerminalInputChunk.MaxBytes).flatMap(part =>
      buffer.process(TerminalInputChunk(part))
    ).toVector
    val chunks  = events.collect { case TerminalInput.PasteChunk(value) => value }

    assertEquals(events.head, TerminalInput.PasteStart)
    assertEquals(events.last, TerminalInput.PasteEnd)
    assertEquals(
      chunks.length,
      (payload.length + TerminalInputChunk.MaxBytes - 1) / TerminalInputChunk.MaxBytes
    )
    assert(chunks.forall(value => value.length >= 1 && value.length <= TerminalInputChunk.MaxBytes))
    assertEquals(chunks.flatMap(_.toArray).toArray.toVector, payload.toVector)
    assert(payload.length > 4096)
    assert(
      events.length < 4096,
      s"${payload.length} payload bytes amplified to ${events.length} events"
    )

  test("flush preserves active paste content until the end marker"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process(chunk("\u001b[200~hello")), Vector(TerminalInput.PasteStart))
    assertEquals(buffer.flush(), Vector.empty)
    assertEquals(
      buffer.process(chunk(" world\u001b[201~")),
      Vector(
        TerminalInput.PasteChunk(
          TerminalInputChunk("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        ),
        TerminalInput.PasteEnd
      )
    )

  test("flush preserves confirmed paste bytes and a partial end marker"):
    val buffer = TerminalInputBuffer()
    assertEquals(
      buffer.process(chunk("\u001b[200~value\u001b[20")),
      Vector(TerminalInput.PasteStart)
    )
    assertEquals(buffer.flush(), Vector.empty)
    assertEquals(
      buffer.process(chunk("1~")),
      Vector(
        TerminalInput.PasteChunk(
          TerminalInputChunk("value".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        ),
        TerminalInput.PasteEnd
      )
    )

  test("clear discards confirmed paste bytes and a partial end marker"):
    val buffer = TerminalInputBuffer()
    buffer.process(chunk("\u001b[200~value\u001b[20"))
    buffer.clear()
    assertEquals(
      buffer.process(chunk("1~")),
      Vector(
        TerminalInput.Key(TerminalKey.Character("1")),
        TerminalInput.Key(TerminalKey.Character("~"))
      )
    )
    assertEquals(buffer.flush(), Vector.empty)

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
    val chunks = events.collect { case TerminalInput.PasteChunk(chunk) => chunk }
    assertEquals(chunks.length, 2)
    assert(chunks.forall(chunk => chunk.length >= 1 && chunk.length <= TerminalInputChunk.MaxBytes))
    assertEquals(chunks.flatMap(_.toArray).toVector, data.toVector)

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
