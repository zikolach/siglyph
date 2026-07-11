package scalatui.terminal
import scalatui.syntax.Equality.*

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

  test("ordered delivery stop releases a waiting batch and isolates the next generation"):
    val delivery      = OrderedInputDelivery()
    val firstStarted  = CountDownLatch(1)
    val releaseFirst  = CountDownLatch(1)
    val secondParsed  = CountDownLatch(1)
    val oldFinished   = CountDownLatch(2)
    val nextDelivered = CountDownLatch(2)
    val observedLock  = Object()
    var observed      = Vector.empty[String]
    val oldGeneration = delivery.start(())
    val first         = Thread(() =>
      try
        delivery.parseAndDeliver(
          oldGeneration,
          Vector(TerminalInput.Key(TerminalKey.Character("first")))
        ) { _ =>
          firstStarted.countDown()
          releaseFirst.await()
          observedLock.synchronized(observed :+= "first")
        }
      finally oldFinished.countDown()
    )
    val second        = Thread(() =>
      try
        delivery.parseAndDeliver(
          oldGeneration, {
            secondParsed.countDown()
            Vector(TerminalInput.Key(TerminalKey.Character("second")))
          }
        )(_ => observedLock.synchronized(observed :+= "second"))
      finally oldFinished.countDown()
    )
    first.start()
    assert(firstStarted.await(1, TimeUnit.SECONDS))
    second.start()
    assert(secondParsed.await(1, TimeUnit.SECONDS))

    delivery.stop(oldGeneration, ())
    releaseFirst.countDown()
    assert(oldFinished.await(1, TimeUnit.SECONDS))
    assertEquals(observedLock.synchronized(observed), Vector("first"))

    val nextGeneration = delivery.start(())
    Vector("third", "fourth").foreach { value =>
      delivery.parseAndDeliver(
        nextGeneration,
        Vector(TerminalInput.Key(TerminalKey.Character(value)))
      ) { _ =>
        observedLock.synchronized(observed :+= value)
        nextDelivered.countDown()
      }
    }
    assert(nextDelivered.await(1, TimeUnit.SECONDS))
    assertEquals(observedLock.synchronized(observed), Vector("first", "third", "fourth"))

    delivery.parseAndDeliver(
      oldGeneration,
      Vector(TerminalInput.Key(TerminalKey.Character("stale")))
    )(_ => observedLock.synchronized(observed :+= "stale"))
    assertEquals(observedLock.synchronized(observed), Vector("first", "third", "fourth"))

  test("ordered delivery restores waiter interruption when callback throws and keeps ordering"):
    val delivery       = OrderedInputDelivery()
    val firstStarted   = CountDownLatch(1)
    val releaseFirst   = CountDownLatch(1)
    val waiterStarted  = CountDownLatch(1)
    val waiterFinished = CountDownLatch(1)
    val laterDelivered = CountDownLatch(1)
    val restored       = java.util.concurrent.atomic.AtomicBoolean(false)
    val generation     = delivery.start(())
    val first          = Thread(() =>
      delivery.parseAndDeliver(generation, Vector(TerminalInput.PasteStart)) { _ =>
        firstStarted.countDown()
        releaseFirst.await()
      }
    )
    val waiter         = Thread(() =>
      try
        delivery.parseAndDeliver(
          generation, {
            waiterStarted.countDown()
            Vector(TerminalInput.PasteEnd)
          }
        )(_ => throw RuntimeException("delivery failed"))
      catch case _: RuntimeException => ()
      finally
        restored.set(Thread.currentThread().isInterrupted)
        waiterFinished.countDown()
    )
    first.start()
    assert(firstStarted.await(1, TimeUnit.SECONDS))
    waiter.start()
    assert(waiterStarted.await(1, TimeUnit.SECONDS))
    waiter.interrupt()
    releaseFirst.countDown()
    assert(waiterFinished.await(1, TimeUnit.SECONDS))
    assert(restored.get())

    delivery.parseAndDeliver(generation, Vector(TerminalInput.PasteStart))(_ =>
      laterDelivered.countDown()
    )
    assert(laterDelivered.await(1, TimeUnit.SECONDS))

  test("stopped generation cannot continue a stale flush loop after restart"):
    val delivery          = OrderedInputDelivery()
    val staleMayCheck     = CountDownLatch(1)
    val staleFinished     = CountDownLatch(1)
    val staleParsed       = CountDownLatch(1)
    val oldGeneration     = delivery.start(())
    val staleFlushThread  = Thread(() =>
      staleMayCheck.await()
      if delivery.isActive(oldGeneration) then
        delivery.parseAndDeliver(
          oldGeneration, {
            staleParsed.countDown()
            Vector(TerminalInput.PasteEnd)
          }
        )(_ => ())
      staleFinished.countDown()
    )
    staleFlushThread.start()
    delivery.stop(oldGeneration, ())
    val currentGeneration = delivery.start(())
    staleMayCheck.countDown()
    assert(staleFinished.await(1, TimeUnit.SECONDS))
    assertEquals(staleParsed.getCount, 1L)
    assert(delivery.isActive(currentGeneration))

  test("stale generation does not parse or consume current pending input after restart"):
    val delivery      = OrderedInputDelivery()
    val inputBuffer   = TerminalInputBuffer()
    val staleMayRun   = CountDownLatch(1)
    val staleFinished = CountDownLatch(1)
    val staleParsed   = CountDownLatch(1)
    val observed      = scala.collection.mutable.ArrayBuffer.empty[TerminalInput]
    val oldGeneration = delivery.start(inputBuffer.clear())
    val stale         = Thread(() =>
      try
        staleMayRun.await()
        delivery.parseAndDeliver(
          oldGeneration, {
            staleParsed.countDown()
            inputBuffer.flush()
          }
        )(observed += _)
      finally staleFinished.countDown()
    )
    stale.start()

    delivery.stop(oldGeneration, inputBuffer.clear())
    val currentGeneration = delivery.start(inputBuffer.clear())
    delivery.parseAndDeliver(
      currentGeneration,
      inputBuffer.process(TerminalInputChunk(Array(0x1b.toByte)))
    )(observed += _)

    staleMayRun.countDown()
    assert(staleFinished.await(1, TimeUnit.SECONDS))
    assertEquals(staleParsed.getCount, 1L)

    delivery.parseAndDeliver(
      currentGeneration,
      inputBuffer.process(TerminalInputChunk(Array('x'.toByte)))
    )(observed += _)
    assertEquals(
      observed.toVector,
      Vector(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(alt = true)))
    )

  test("stream terminal rejects restart until an interrupt-ignoring reader terminates"):
    val oldReadStarted     = CountDownLatch(1)
    val releaseOldRead     = CountDownLatch(1)
    val releaseCurrentRead = CountDownLatch(1)
    val input              = new java.io.InputStream:
      private var reads = 0

      override def read(): Int = -1

      override def read(buffer: Array[Byte], offset: Int, length: Int): Int = synchronized {
        reads += 1
        if reads === 1 then
          oldReadStarted.countDown()
          var released = false
          while !released do
            try
              releaseOldRead.await()
              released = true
            catch case _: InterruptedException => ()
          -1
        else if reads === 2 then
          buffer(offset) = 'x'.toByte
          1
        else
          releaseCurrentRead.await()
          -1
      }

    val terminal = StreamTerminal(input = input)
    terminal.start(_ => (), () => ())
    assert(oldReadStarted.await(1, TimeUnit.SECONDS))
    terminal.stop()
    interceptMessage[IllegalStateException](
      "cannot restart StreamTerminal while the previous input reader is still alive"
    )(terminal.start(_ => (), () => ()))

    releaseOldRead.countDown()
    val delivered = CountDownLatch(1)
    val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    var started   = false
    while !started && System.nanoTime() < deadline do
      try
        terminal.start(
          {
            case TerminalInput.Key(TerminalKey.Character("x"), _) => delivered.countDown()
            case _                                                => ()
          },
          () => ()
        )
        started = true
      catch case _: IllegalStateException => Thread.onSpinWait()
    assert(started, "old input reader did not terminate before the restart deadline")
    assert(delivered.await(1, TimeUnit.SECONDS))
    assertEquals(
      intercept[IllegalStateException](terminal.start(_ => (), () => ())).getMessage,
      "StreamTerminal is already running"
    )
    releaseCurrentRead.countDown()
    terminal.stop()

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
