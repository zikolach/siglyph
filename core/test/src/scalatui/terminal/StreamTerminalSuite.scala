package scalatui.terminal

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.{CountDownLatch, TimeUnit}

class StreamTerminalSuite extends munit.FunSuite:
  test("stream terminal writes to provided output stream"):
    val out      = ByteArrayOutputStream()
    val terminal = StreamTerminal(output = out, initialColumns = 10, initialRows = 2)
    terminal.write("hello")
    assertEquals(out.toString("UTF-8"), "hello")

  test("stream terminal parses input without raw mode"):
    val in       = ByteArrayInputStream("x".getBytes("UTF-8"))
    val terminal = StreamTerminal(input = in)
    var inputs   = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(50)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("stream terminal buffers fragmented input"):
    val in       = new java.io.InputStream:
      private val chunks                                              = Array("\u001b[", "A")
      private var chunkIndex                                          = 0
      private var offset                                              = 0
      override def read(): Int                                        =
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
    var inputs   = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(100)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Up)))

  test("stream terminal flushes pending escape when read times out"):
    val in       = new java.io.InputStream:
      private var reads                                               = 0
      override def read(): Int                                        = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        reads += 1
        reads match
          case 1 =>
            buffer(off) = 0x1b.toByte
            1
          case 2 => 0
          case _ => -1
    val terminal = StreamTerminal(input = in)
    val observed = CountDownLatch(1)
    var inputs   = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        inputs :+= input
        observed.countDown()
      },
      () => ()
    )
    assert(observed.await(1, TimeUnit.SECONDS))
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Escape)))

  test("stream terminal flushes pending escape even when next read blocks"):
    val releaseRead = CountDownLatch(1)
    val in          = new java.io.InputStream:
      @volatile private var first                                     = true
      override def read(): Int                                        = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        if first then
          first = false
          buffer(off) = 0x1b.toByte
          1
        else
          try releaseRead.await()
          catch case _: InterruptedException => ()
          -1
    val terminal    = StreamTerminal(input = in)
    val observed    = CountDownLatch(1)
    var inputs      = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        inputs :+= input
        observed.countDown()
      },
      () => ()
    )
    try assert(observed.await(1, TimeUnit.SECONDS))
    finally
      releaseRead.countDown()
      terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Escape)))

  test("stream terminal preserves parser order across read and flush threads"):
    val allowSecondRead = CountDownLatch(1)
    val escapeStarted   = CountDownLatch(1)
    val allowEscape     = CountDownLatch(1)
    val xObserved       = CountDownLatch(1)
    val in              = new java.io.InputStream:
      private var reads                                               = 0
      override def read(): Int                                        = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        reads += 1
        reads match
          case 1 =>
            buffer(off) = 0x1b.toByte
            1
          case 2 =>
            allowSecondRead.await()
            buffer(off) = 'x'.toByte
            1
          case _ => -1
    val terminal        = StreamTerminal(input = in)
    val observedLock    = Object()
    var observed        = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        observedLock.synchronized(observed :+= input)
        input match
          case TerminalInput.Key(TerminalKey.Escape, _)         =>
            escapeStarted.countDown()
            allowEscape.await()
          case TerminalInput.Key(TerminalKey.Character("x"), _) => xObserved.countDown()
          case _                                                => ()
      },
      () => ()
    )
    try
      assert(escapeStarted.await(1, TimeUnit.SECONDS))
      allowSecondRead.countDown()
      assert(!xObserved.await(150, TimeUnit.MILLISECONDS))
      allowEscape.countDown()
      assert(xObserved.await(1, TimeUnit.SECONDS))
    finally
      allowEscape.countDown()
      allowSecondRead.countDown()
      terminal.stop()

    assertEquals(
      observedLock.synchronized(observed),
      Vector(
        TerminalInput.Key(TerminalKey.Escape),
        TerminalInput.Key(TerminalKey.Character("x"))
      )
    )

  test("stream terminal drain discards pending escape without dispatching input"):
    val drained                         = CountDownLatch(1)
    var terminal: StreamTerminal | Null = null
    val in                              = new java.io.InputStream:
      private var reads                                               = 0
      override def read(): Int                                        = -1
      override def read(buffer: Array[Byte], off: Int, len: Int): Int =
        reads += 1
        reads match
          case 1 =>
            buffer(off) = 0x1b.toByte
            1
          case 2 =>
            Option(terminal).foreach(_.drainInput())
            drained.countDown()
            try Thread.sleep(150)
            catch case _: InterruptedException => ()
            -1
          case _ => -1
    terminal = StreamTerminal(input = in)
    var inputs                          = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    assertEquals(drained.await(1, TimeUnit.SECONDS), true)
    terminal.stop()
    assertEquals(inputs, Vector.empty)

  test("stream terminal stop is idempotent"):
    val terminal = StreamTerminal()
    terminal.stop()
    terminal.start(_ => (), () => ())
    terminal.stop()
    terminal.stop()

  test("stream terminal reports unsupported title and progress without writing escapes"):
    val out      = ByteArrayOutputStream()
    val terminal = StreamTerminal(output = out)

    assertEquals(Terminal.setTitle(terminal, "title"), false)
    assertEquals(Terminal.setProgress(terminal, active = true), false)

    assertEquals(out.toString("UTF-8"), "")

  test("start and output-side operations do not synchronously deliver registered callbacks"):
    val terminal        = StreamTerminal(input = ByteArrayInputStream(Array.emptyByteArray))
    val caller          = Thread.currentThread()
    var inlineCallbacks = 0
    terminal.start(
      _ => if Thread.currentThread() eq caller then inlineCallbacks += 1,
      () => if Thread.currentThread() eq caller then inlineCallbacks += 1
    )
    try
      terminal.write("output")
      terminal.moveBy(1)
      terminal.hideCursor()
      terminal.showCursor()
      terminal.clearLine()
      terminal.clearFromCursor()
      terminal.clearScreen()
      terminal.drainInput()
      assertEquals(inlineCallbacks, 0)
    finally terminal.stop()
