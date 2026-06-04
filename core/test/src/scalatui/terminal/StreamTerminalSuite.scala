package scalatui.terminal

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class StreamTerminalSuite extends munit.FunSuite:
  test("stream terminal writes to provided output stream"):
    val out = ByteArrayOutputStream()
    val terminal = StreamTerminal(output = out, initialColumns = 10, initialRows = 2)
    terminal.write("hello")
    assertEquals(out.toString("UTF-8"), "hello")

  test("stream terminal parses input without raw mode"):
    val in = ByteArrayInputStream("x".getBytes("UTF-8"))
    val terminal = StreamTerminal(input = in)
    var inputs = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(50)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("stream terminal buffers fragmented input"):
    val in = new java.io.InputStream:
      private val chunks = Array("\u001b[", "A")
      private var chunkIndex = 0
      private var offset = 0
      override def read(): Int =
        if chunkIndex >= chunks.length then -1
        else
          val bytes = chunks(chunkIndex).getBytes("UTF-8")
          val value = bytes(offset) & 0xff
          offset += 1
          if offset >= bytes.length then
            chunkIndex += 1
            offset = 0
          value
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        val value = read()
        if value < 0 then -1
        else
          buffer(off) = value.toByte
          1
    val terminal = StreamTerminal(input = in)
    var inputs = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(100)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Up)))

  test("stream terminal flushes pending escape when read times out"):
    val in = new java.io.InputStream:
      private var reads = 0
      override def read(): Int = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        reads += 1
        reads match
          case 1 =>
            buffer(off) = 0x1b.toByte
            1
          case 2 => 0
          case _ => -1
    val terminal = StreamTerminal(input = in)
    var inputs = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(100)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Escape)))

  test("stream terminal flushes pending escape even when next read blocks"):
    val in = new java.io.InputStream:
      @volatile private var first = true
      override def read(): Int = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        if first then
          first = false
          buffer(off) = 0x1b.toByte
          1
        else
          try Thread.sleep(1000)
          catch case _: InterruptedException => ()
          -1
    val terminal = StreamTerminal(input = in)
    var inputs = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(200)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Escape)))

  test("stream terminal stop is idempotent"):
    val terminal = StreamTerminal()
    terminal.stop()
    terminal.start(_ => (), () => ())
    terminal.stop()
    terminal.stop()
