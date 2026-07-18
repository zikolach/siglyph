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
    val observed = CountDownLatch(1)
    var inputs   = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        inputs :+= input
        observed.countDown()
      },
      () => ()
    )
    try
      assert(
        observed.await(5, TimeUnit.SECONDS),
        "fragmented input was not delivered as one parsed key"
      )
    finally terminal.stop()
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

  test("ordered delivery suppresses the remainder when a callback invalidates its generation"):
    val delivery   = OrderedInputDelivery()
    val generation = delivery.start(())
    var observed   = Vector.empty[String]

    delivery.parseAndDeliver(
      generation,
      Vector(
        TerminalInput.Key(TerminalKey.Character("first")),
        TerminalInput.Key(TerminalKey.Character("second"))
      )
    ) {
      case TerminalInput.Key(TerminalKey.Character(value), _) =>
        observed :+= value
        delivery.stop(generation, ())
      case _                                                  => ()
    }

    assertEquals(observed, Vector("first"))

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

  test("stream terminal delivers complete ordered EOF vectors and terminates"):
    val incompleteUtf8 = Array[Byte](0xf0.toByte, 0x9f.toByte)
    val incompleteCsi  = Array[Byte](0x1b, 0x5b, 0x31, 0x3b)
    val values         = Vector(
      Array[Byte](0x1b)                          -> Vector(TerminalInput.Key(TerminalKey.Escape)),
      incompleteUtf8                             -> Vector(
        TerminalInput.RawStart(TerminalRawKind.Utf8),
        TerminalInput.RawChunk(TerminalInputChunk(incompleteUtf8)),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      ),
      incompleteCsi                              -> Vector(
        TerminalInput.RawStart(TerminalRawKind.Csi),
        TerminalInput.RawChunk(TerminalInputChunk(incompleteCsi)),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      ),
      (Array[Byte]('x'.toByte) ++ incompleteCsi) -> Vector(
        TerminalInput.Key(TerminalKey.Character("x")),
        TerminalInput.RawStart(TerminalRawKind.Csi),
        TerminalInput.RawChunk(TerminalInputChunk(incompleteCsi)),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      )
    )
    values.foreach { (bytes, expected) =>
      val terminal = StreamTerminal(input = ByteArrayInputStream(bytes))
      val observed = CountDownLatch(expected.size)
      var inputs   = Vector.empty[TerminalInput]
      terminal.start(
        input => {
          inputs :+= input
          observed.countDown()
        },
        () => ()
      )
      assert(observed.await(1, TimeUnit.SECONDS), s"missing EOF framing for ${bytes.toVector}")
      assertEquals(inputs, expected)

      val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
      var restarted = false
      while !restarted && System.nanoTime() < deadline do
        try
          terminal.start(_ => (), () => ())
          restarted = true
        catch case _: IllegalStateException => Thread.onSpinWait()
      assert(restarted, s"finite EOF did not terminate for ${bytes.toVector}")
      terminal.stop()
    }

  test("stream terminal completes the final EOF vector before invalidating its generation"):
    val bytes        = Array[Byte]('x'.toByte, 0x1b, 0x5b, 0x31, 0x3b)
    val finalStarted = CountDownLatch(1)
    val releaseFinal = CountDownLatch(1)
    val terminal     = StreamTerminal(input = ByteArrayInputStream(bytes))
    var inputs       = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        inputs :+= input
        if input === TerminalInput.RawEnd(TerminalRawTermination.Incomplete) then
          finalStarted.countDown()
          releaseFinal.await()
      },
      () => ()
    )

    assert(finalStarted.await(1, TimeUnit.SECONDS))
    assertEquals(
      intercept[IllegalStateException](terminal.start(_ => (), () => ())).getMessage,
      "StreamTerminal is already running"
    )
    releaseFinal.countDown()

    val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    var restarted = false
    while !restarted && System.nanoTime() < deadline do
      try
        terminal.start(_ => (), () => ())
        restarted = true
      catch case _: IllegalStateException => Thread.onSpinWait()
    assert(restarted, "finite EOF did not invalidate after delivering its complete final vector")
    terminal.stop()
    assertEquals(
      inputs,
      Vector(
        TerminalInput.Key(TerminalKey.Character("x")),
        TerminalInput.RawStart(TerminalRawKind.Csi),
        TerminalInput.RawChunk(TerminalInputChunk(bytes.drop(1))),
        TerminalInput.RawEnd(TerminalRawTermination.Incomplete)
      )
    )

  test("stream terminal discards incomplete paste at finite EOF without synthetic end"):
    val bytes     = "\u001b[200~unfinished".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val terminal  = StreamTerminal(input = ByteArrayInputStream(bytes))
    val pasteSeen = CountDownLatch(1)
    val stopped   = CountDownLatch(1)
    var inputs    = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        inputs :+= input
        if input === TerminalInput.PasteStart then pasteSeen.countDown()
      },
      () => ()
    )
    assert(pasteSeen.await(1, TimeUnit.SECONDS))
    val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    while !stopped.await(0, TimeUnit.NANOSECONDS) && System.nanoTime() < deadline do
      try
        terminal.start(_ => (), () => ())
        stopped.countDown()
      catch case _: IllegalStateException => Thread.onSpinWait()
    terminal.stop()
    assert(stopped.getCount === 0L, "finite EOF did not terminate the generation")
    assertEquals(inputs, Vector(TerminalInput.PasteStart))

  test("stream terminal rejects restart while the old flush worker remains alive"):
    val releaseRead   = CountDownLatch(1)
    val callbackStart = CountDownLatch(1)
    val releaseFlush  = CountDownLatch(1)
    val input         = new java.io.InputStream:
      private var first                                                     = true
      override def read(): Int                                              = -1
      override def read(buffer: Array[Byte], offset: Int, length: Int): Int =
        if first then
          first = false
          buffer(offset) = 0x1b.toByte
          1
        else
          try releaseRead.await()
          catch case _: InterruptedException => ()
          -1
    val terminal      = StreamTerminal(input = input)
    terminal.start(
      _ => {
        callbackStart.countDown()
        var released = false
        while !released do
          try
            releaseFlush.await()
            released = true
          catch case _: InterruptedException => ()
      },
      () => ()
    )
    assert(callbackStart.await(1, TimeUnit.SECONDS))
    terminal.stop()
    releaseRead.countDown()

    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    var message  = ""
    while (message !== "cannot restart StreamTerminal while the previous flush worker is still alive") &&
      System.nanoTime() < deadline
    do
      message = intercept[IllegalStateException](terminal.start(_ => (), () => ())).getMessage
      if message !== "cannot restart StreamTerminal while the previous flush worker is still alive"
      then Thread.onSpinWait()
    assertEquals(
      message,
      "cannot restart StreamTerminal while the previous flush worker is still alive"
    )
    releaseFlush.countDown()
    terminal.stop()

  test("stream terminal drain discards pending escape without dispatching input"):
    val drained                         = CountDownLatch(1)
    val observationWindowComplete       = CountDownLatch(1)
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
            try
              // Keep input blocked for 150 ms, spanning more than one 75 ms flush period.
              Thread.sleep(150)
            catch case _: InterruptedException => ()
            finally observationWindowComplete.countDown()
            -1
          case _ => -1
    terminal = StreamTerminal(input = in)
    var inputs                          = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    assertEquals(drained.await(1, TimeUnit.SECONDS), true)
    assert(
      observationWindowComplete.await(5, TimeUnit.SECONDS),
      "pending escape observation window did not complete"
    )
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
