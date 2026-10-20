package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.*
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

class TUIKeyboardProtocolSuite extends munit.FunSuite:
  private class RecordingTerminal extends Terminal:
    val virtual                                                                    = VirtualTerminal()
    var input: TerminalInput => Unit                                               = _ => ()
    var beforeWrite: String => Unit                                                = _ => ()
    var stops                                                                      = 0
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      input = onInput
      virtual.start(onInput, onResize)
    override def stop(): Unit                                                      =
      stops += 1
      virtual.stop()
    override def write(data: String): Unit                                         =
      beforeWrite(data)
      virtual.write(data)
    override def columns: Int                                                      = virtual.columns
    override def rows: Int                                                         = virtual.rows
    override def moveBy(lines: Int): Unit                                          = write(s"\u001b[${lines}B")
    override def hideCursor(): Unit                                                = write("\u001b[?25l")
    override def showCursor(): Unit                                                = write("\u001b[?25h")
    override def clearLine(): Unit                                                 = write("\u001b[K")
    override def clearFromCursor(): Unit                                           = write("\u001b[J")
    override def clearScreen(): Unit                                               = write("\u001b[2J")

  private class KeyboardTerminal extends RecordingTerminal, KittyKeyboardProtocolTerminal:
    val negotiator                                                                               = KittyKeyboardProtocolNegotiator()
    var cleanupPending                                                                           = false
    var arrival                                                                                  = Option.empty[Long]
    var deadline                                                                                 = Long.MaxValue
    var clockMillis                                                                              = 0L
    override def keyboardProtocolState: KittyKeyboardProtocolState                               =
      negotiator.expire(clockMillis)
      negotiator.state
    override def requestKittyKeyboardProtocol(timeoutMillis: Long): Unit                         =
      negotiator.begin(0L, deadline)
      write(KittyKeyboardProtocol.QuerySequence)
    override def acceptKittyKeyboardProtocolResponse(response: String, nowMillis: Long): Boolean =
      arrival = Some(nowMillis)
      val accepted = negotiator.receiveResponse(response, nowMillis)
      if accepted then
        cleanupPending = true
        write(KittyKeyboardProtocol.EnableSequence)
      accepted
    override def disableKittyKeyboardProtocol(): Unit                                            =
      if cleanupPending then write(KittyKeyboardProtocol.DisableSequence)
      cleanupPending = false
      negotiator.disable()
    override def stop(): Unit                                                                    =
      disableKittyKeyboardProtocol()
      super.stop()

  private def send(terminal: RecordingTerminal, chunks: String*): Unit =
    val buffer = TerminalInputBuffer()
    chunks.foreach { chunk =>
      buffer.process(TerminalInputChunk(chunk.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .foreach(terminal.input)
    }
    buffer.flush().foreach(terminal.input)

  private def collect(tui: TUI): scala.collection.mutable.ArrayBuffer[TerminalInput] =
    val seen = scala.collection.mutable.ArrayBuffer.empty[TerminalInput]
    tui.addInputListener { input =>
      seen += input
      InputResult.Handled()
    }
    seen

  private def count(terminal: RecordingTerminal, value: String): Int =
    terminal.virtual.writes.count(_ === value)

  test("startup requests negotiation without waiting and pending stop cancels without a pop"):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    tui.start()
    assertEquals(count(terminal, KittyKeyboardProtocol.QuerySequence), 1)
    assert(terminal.keyboardProtocolState.isInstanceOf[KittyKeyboardProtocolState.Pending])
    tui.stop()
    tui.stop()
    send(terminal, "\u001b[?0u")
    assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Inactive)
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 0)
    assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 0)
    assertEquals(terminal.stops, 1)

  test(
    "fragmented zero reply and duplicate stay private and modified Enter precedes ordinary Enter"
  ):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    val seen     = collect(tui)
    tui.start()
    send(terminal, "\u001b[?", "0", "u\u001b[?3u\u001b[13;2u\r")
    assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 1)
    val keys     = seen.collect { case TerminalInput.KeyEvent(key, modifiers, _) =>
      key -> modifiers.shift
    }
    assertEquals(keys.toVector, Vector(TerminalKey.Enter -> true, TerminalKey.Enter -> false))
    assertEquals(seen.length, 2)
    tui.stop()
    tui.stop()
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)

  test("stale valid replies are consumed without enabling"):
    val terminal = KeyboardTerminal()
    terminal.deadline = 0L
    val tui      = TUI(terminal)
    val seen     = collect(tui)
    tui.start()
    send(terminal, "\u001b[?0u")
    assertEquals(seen.toVector, Vector.empty)
    assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 0)
    tui.stop()

  test("malformed unrelated incomplete oversized and paste input retain the parser event stream"):
    Vector(
      "\u001b[?;u",
      "\u001b[?2147483648u",
      "\u001b[99z",
      "\u001b[?0",
      "\u001b[?" + "0" * (TerminalInputChunk.MaxBytes + 1) + "u",
      "\u001b[200~\u001b[?0u\u001b[201~"
    ).foreach { raw =>
      val terminal = KeyboardTerminal()
      val tui      = TUI(terminal)
      val seen     = collect(tui)
      tui.start()
      val buffer   = TerminalInputBuffer()
      val expected = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        .grouped(TerminalInputChunk.MaxBytes).flatMap(bytes =>
          buffer.process(TerminalInputChunk(bytes))
        )
        .toVector ++ buffer.flush()
      expected.foreach(terminal.input)
      assertEquals(seen.toVector, expected, raw.take(30))
      assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 0)
      tui.stop()
    }

  test("unsupported backend retains input and emits no keyboard query"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    val seen     = collect(tui)
    tui.start()
    send(terminal, "a\r\u001b[A")
    assertEquals(count(terminal, KittyKeyboardProtocol.QuerySequence), 0)
    assertEquals(seen.length, 3)
    tui.stop()

  private def await(latch: CountDownLatch): Unit =
    if !latch.await(5L, TimeUnit.SECONDS) then throw AssertionError("gate timed out after 5000 ms")
  private def join(thread: Thread): Unit         =
    thread.join(5000L)
    assert(!thread.isAlive)

  test("queued response uses ingress arrival time and cannot interleave a blocked frame"):
    val terminal = KeyboardTerminal()
    terminal.deadline = System.currentTimeMillis() + 60000L
    val tui      = TUI(terminal)
    tui.start()
    val entered  = CountDownLatch(1)
    val release  = CountDownLatch(1)
    val error    = AtomicReference[Throwable]()
    terminal.beforeWrite = _ =>
      entered.countDown()
      await(release)
    val render   = Thread(() =>
      try
        tui.addChild(new Component:
          override def render(width: Int): ComponentRender = ComponentRender.text("frame"))
        tui.requestRender()
        tui.flushRender()
      catch case e: Throwable => error.set(e)
    )
    render.start()
    await(entered)
    try
      send(terminal, "\u001b[?0u\r")
      val beforeRelease = System.currentTimeMillis()
      assert(
        beforeRelease <= terminal.deadline,
        "the complete reply must be admitted before the deadline"
      )
      terminal.clockMillis = terminal.deadline + 1L
      assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Inactive)
      assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 0)
      release.countDown()
      join(render)
      Option(error.get()).foreach(throw _)
      assert(terminal.arrival.exists(_ <= beforeRelease))
      assert(terminal.arrival.exists(_ <= terminal.deadline))
      assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Active(0))
      assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 1)
    finally
      release.countDown()
      tui.stop()

    tui.stop()
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)

  test("stop invalidates a response queued behind a frame"):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    tui.start()
    val entered  = CountDownLatch(1)
    val release  = CountDownLatch(1)
    terminal.beforeWrite = _ =>
      entered.countDown()
      await(release)
    val render   = Thread(() =>
      tui.addChild(new Component:
        override def render(width: Int): ComponentRender = ComponentRender.text("frame"))
      tui.requestRender()
      tui.flushRender()
    )
    render.start()
    await(entered)
    try
      send(terminal, "\u001b[?0u")
      tui.stop()
    finally release.countDown()
    join(render)
    assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 0)
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 0)
    assertEquals(terminal.stops, 1)

  test("failed enable restores the attempted mode and stops the backend"):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    tui.start()
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.EnableSequence then throw RuntimeException("enable failed")
    send(terminal, "\u001b[?0u")
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(terminal.stops, 1)
    tui.stop()
    assertEquals(terminal.stops, 1)

  test("startup failure cancels a pending query and attempts backend cleanup"):
    val terminal = KeyboardTerminal()
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.QuerySequence then throw RuntimeException("query failed")
    val tui      = TUI(terminal)
    intercept[RuntimeException](tui.start())
    assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Inactive)
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 0)
    assertEquals(terminal.stops, 1)

  test("stop during enable waits for output ownership and then pops once"):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    tui.start()
    val entered  = CountDownLatch(1)
    val release  = CountDownLatch(1)
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.EnableSequence then
        entered.countDown()
        await(release)
    val response = Thread(() => send(terminal, "\u001b[?0u"))
    response.start()
    await(entered)
    val stopped  = CountDownLatch(1)
    val stop     = Thread(() =>
      tui.stop()
      stopped.countDown()
    )
    try
      stop.start()
      await(stopped)
      assertEquals(terminal.stops, 0)
    finally release.countDown()
    join(stop)
    join(response)
    assertEquals(count(terminal, KittyKeyboardProtocol.EnableSequence), 1)
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(terminal.stops, 1)

  test("runtime frame failure restores an active mode and other cleanup"):
    val terminal = KeyboardTerminal()
    val tui      = TUI(terminal)
    tui.start()
    send(terminal, "\u001b[?0u")
    var fail     = true
    terminal.beforeWrite = _ =>
      if fail then
        fail = false
        throw RuntimeException("frame failed")
    tui.requestRender(force = true)
    tui.flushRender()
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(terminal.stops, 1)
    assertEquals(terminal.virtual.writes.last, KittyKeyboardProtocol.DisableSequence)

  test("startup failure after an explicit backend push restores the active mode"):
    val terminal = new KeyboardTerminal:
      override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
        super.start(onInput, onResize)
        requestKittyKeyboardProtocol(100L)
        acceptKittyKeyboardProtocolResponse("\u001b[?0u", 0L)
        throw RuntimeException("startup failed after push")
    val tui      = TUI(terminal)
    intercept[RuntimeException](tui.start())
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(terminal.stops, 1)

  Vector(false, true).foreach { failFirstPop =>
    test(s"alternate-screen keyboard cleanup and backend retry precede screen exit: $failFirstPop"):
      val terminal        = KeyboardTerminal()
      val tui             = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
      tui.start()
      send(terminal, "\u001b[?0u")
      val popsOnAlternate = scala.collection.mutable.ArrayBuffer.empty[Boolean]
      var onAlternate     = true
      terminal.beforeWrite = data =>
        if data === TUI.AlternateScreenExit then onAlternate = false
        if data === KittyKeyboardProtocol.DisableSequence then
          popsOnAlternate += onAlternate
          if failFirstPop && popsOnAlternate.length === 1 then
            throw RuntimeException("first pop failed")
      if failFirstPop then intercept[RuntimeException](tui.stop())
      else tui.stop()
      tui.stop()
      assertEquals(popsOnAlternate.toVector, Vector.fill(if failFirstPop then 2 else 1)(true))
      assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
      assertEquals(count(terminal, TUI.AlternateScreenExit), 1)
      assertEquals(terminal.stops, 1)
  }

  private def alternateTui(terminal: Terminal, fullscreen: Boolean): TUI =
    if fullscreen then TUI.fullscreen(terminal, scalatui.components.Text("frame"))
    else TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))

  Vector(false, true).foreach { fullscreen =>
    test(s"repeated pop failure retains screen and explicit retry restores once: $fullscreen"):
      var drains      = 0
      val terminal    = new KeyboardTerminal with TerminalInputDrainSupport:
        override def drainInput(maxMillis: Long, idleMillis: Long): Unit = drains += 1
      val tui         = alternateTui(terminal, fullscreen)
      var detachments = 0
      tui.addChild(new Component with ContextualComponent:
        override def render(width: Int): ComponentRender           = ComponentRender.empty
        override def tuiContext_=(value: Option[TUIContext]): Unit =
          if value.isEmpty then detachments += 1)
      tui.start()
      send(terminal, "\u001b[?0u")
      var pops        = 0
      var fail        = true
      terminal.beforeWrite = data =>
        if data === KittyKeyboardProtocol.DisableSequence then
          pops += 1
          assertEquals(count(terminal, TUI.AlternateScreenExit), 0)
          if fail then throw RuntimeException("pop failed")
      intercept[RuntimeException](tui.stop())
      assertEquals(pops, 2)
      intercept[IllegalStateException](tui.start())
      tui.flushRender()
      send(terminal, "a\u001b[?0u")
      tui.queryTerminalBackgroundColor(_ => ())
      assertEquals(pops, 2)
      intercept[RuntimeException](tui.stop())
      assertEquals(pops, 3)
      assertEquals(count(terminal, TUI.AlternateScreenExit), 0)
      fail = false
      tui.stop()
      tui.stop()
      assertEquals(pops, 4)
      assertEquals(drains, 1)
      assertEquals(detachments, 1)
      assertEquals(count(terminal, "\u001b[?25h"), 1)
      assertEquals(count(terminal, TUI.AlternateScreenExit), 1)
      tui.start()
      tui.stop()

    test(s"screen exit failure retries exit only: $fullscreen"):
      val terminal = KeyboardTerminal()
      val tui      = alternateTui(terminal, fullscreen)
      tui.start()
      send(terminal, "\u001b[?0u")
      var exits    = 0
      var fail     = true
      terminal.beforeWrite = data =>
        if data === TUI.AlternateScreenExit then
          exits += 1
          if fail then throw RuntimeException("exit failed")
      intercept[RuntimeException](tui.stop())
      intercept[IllegalStateException](tui.start())
      intercept[RuntimeException](tui.stop())
      fail = false
      tui.stop()
      assertEquals(exits, 3)
      assertEquals(terminal.stops, 1)
      assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
  }

  test("backend stop failure after successful pop retains screen without repeating pop"):
    var attempts = 0
    var fail     = true
    val terminal = new KeyboardTerminal:
      override def stop(): Unit =
        attempts += 1
        super.stop()
        if fail then throw RuntimeException("backend stop failed")
    val tui      = alternateTui(terminal, false)
    tui.start()
    send(terminal, "\u001b[?0u")
    intercept[RuntimeException](tui.stop())
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(count(terminal, TUI.AlternateScreenExit), 0)
    fail = false
    tui.stop()
    assertEquals(attempts, 2)
    assertEquals(count(terminal, KittyKeyboardProtocol.DisableSequence), 1)
    assertEquals(count(terminal, TUI.AlternateScreenExit), 1)

  test("active stops coalesce and admitted retry precedes later rejection callbacks"):
    val terminal        = KeyboardTerminal()
    val tui             = alternateTui(terminal, false)
    tui.start()
    send(terminal, "\u001b[?0u")
    val entered         = CountDownLatch(1)
    val release         = CountDownLatch(1)
    var pops            = 0
    var fail            = true
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.DisableSequence then
        pops += 1
        if fail then
          entered.countDown()
          await(release)
          throw RuntimeException("pop failed")
    val stopFailure     = AtomicReference[Throwable]()
    val stopper         = Thread(() =>
      try tui.stop()
      catch case error: Throwable => stopFailure.set(error)
    )
    stopper.start()
    await(entered)
    val concurrent      = Vector.fill(8)(Thread(() => tui.stop()))
    concurrent.foreach(_.start())
    concurrent.foreach(join)
    var callbacks       = Vector.empty[String]
    tui.queryTerminalBackgroundColor { _ =>
      callbacks :+= "finite"
      tui.queryTerminalColorScheme(_ => callbacks :+= "later")
    }
    release.countDown()
    join(stopper)
    assertEquals(pops, 2)
    assertEquals(callbacks, Vector("finite", "later"))
    assertEquals(Option(stopFailure.get()).map(_.getMessage), Some("pop failed"))
    val callbackEntered = CountDownLatch(1)
    val allowCallback   = CountDownLatch(1)
    val owner           = Thread(() =>
      tui.queryTerminalBackgroundColor { _ =>
        callbackEntered.countDown()
        await(allowCallback)
      }
      ()
    )
    owner.start()
    await(callbackEntered)
    fail = false
    tui.stop()
    tui.queryTerminalColorScheme { _ =>
      assertEquals(count(terminal, TUI.AlternateScreenExit), 1)
      callbacks :+= "after retry"
    }
    allowCallback.countDown()
    join(owner)
    assertEquals(pops, 3)
    assertEquals(callbacks, Vector("finite", "later", "after retry"))

  Vector(false, true).foreach { deferred =>
    test(s"run reports original failure without automatic restoration retry: $deferred"):
      val original = RuntimeException("original failure")
      val terminal = new KeyboardTerminal:
        override def requestKittyKeyboardProtocol(timeoutMillis: Long): Unit =
          super.requestKittyKeyboardProtocol(timeoutMillis)
          if !deferred then
            acceptKittyKeyboardProtocolResponse("\u001b[?0u", 0L)
            throw original
      val tui      = alternateTui(terminal, false)
      var pops     = 0
      var fail     = true
      terminal.beforeWrite = data =>
        if data === KittyKeyboardProtocol.DisableSequence then
          pops += 1
          if fail then throw RuntimeException("pop failed")
      val failure  = AtomicReference[Throwable]()
      val entered  = CountDownLatch(1)
      val release  = CountDownLatch(1)
      if deferred then
        tui.addInputListener { _ =>
          entered.countDown()
          await(release)
          throw original
        }
        tui.start()
        send(terminal, "\u001b[?0u")
      val runner   = Thread(() =>
        try tui.run()
        catch case error: Throwable => failure.set(error)
      )
      runner.start()
      if deferred then
        val owner = Thread(() => send(terminal, "x"))
        owner.start()
        await(entered)
        release.countDown()
        join(owner)
      join(runner)
      assert(failure.get() eq original)
      assert(original.getSuppressed.exists(_.getMessage === "pop failed"))
      assertEquals(pops, 2)
      assertEquals(count(terminal, TUI.AlternateScreenExit), 0)
      intercept[IllegalStateException](tui.start())
      fail = false
      tui.stop()
      assertEquals(pops, 3)
      assertEquals(count(terminal, TUI.AlternateScreenExit), 1)
  }

  test("run reports synchronous cleanup failure and leaves retry explicit"):
    val cleanupFailure = RuntimeException("backend stop failed")
    var attempts       = 0
    var fail           = true
    val backend        = new KeyboardTerminal:
      override def stop(): Unit =
        attempts += 1
        super.stop()
        if fail then throw cleanupFailure
    val runtime        = alternateTui(backend, false)
    runtime.addChild(new Component:
      override def render(width: Int): ComponentRender =
        runtime.requestExit()
        ComponentRender.empty)
    val reported       = intercept[RuntimeException](runtime.run())
    assert(reported eq cleanupFailure)
    assertEquals(attempts, 1)
    assertEquals(count(backend, TUI.AlternateScreenExit), 0)
    fail = false
    runtime.stop()
    assertEquals(attempts, 2)
    assertEquals(count(backend, TUI.AlternateScreenExit), 1)

  test("failed restoration seals finite callbacks before later blocked rejection work"):
    val terminal        = KeyboardTerminal()
    val tui             = alternateTui(terminal, false)
    tui.start()
    send(terminal, "\u001b[?0u")
    val popEntered      = CountDownLatch(1)
    val allowPop        = CountDownLatch(1)
    val finiteEntered   = CountDownLatch(1)
    val allowFinite     = CountDownLatch(1)
    val lateEntered     = CountDownLatch(1)
    val allowLate       = CountDownLatch(1)
    var pops            = 0
    var fail            = true
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.DisableSequence then
        pops += 1
        if fail then
          popEntered.countDown()
          await(allowPop)
          throw RuntimeException("pop failed")
    val runFailure      = AtomicReference[Throwable]()
    val runner          = Thread(() =>
      try tui.run()
      catch case error: Throwable => runFailure.set(error)
    )
    runner.start()
    val waitingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
    while (runner.getState !== Thread.State.WAITING) && System.nanoTime() < waitingDeadline do
      Thread.`yield`()
    assertEquals(runner.getState, Thread.State.WAITING)
    val stopFailure     = AtomicReference[Throwable]()
    val stopper         = Thread(() =>
      try tui.stop()
      catch case error: Throwable => stopFailure.set(error)
    )
    stopper.start()
    await(popEntered)
    var callbacks       = Vector.empty[String]
    tui.queryTerminalBackgroundColor { _ =>
      callbacks :+= "finite query"
      finiteEntered.countDown()
      await(allowFinite)
    }
    tui.appendToScrollback(scalatui.components.Text("rejected"), _ => callbacks :+= "finite append")
    allowPop.countDown()
    await(finiteEntered)
    assertEquals(runFailure.get(), null)
    tui.queryTerminalColorScheme { _ =>
      lateEntered.countDown()
      await(allowLate)
      callbacks :+= "late"
    }
    allowFinite.countDown()
    await(lateEntered)
    try
      join(runner)
      assertEquals(Option(runFailure.get()).map(_.getMessage), Some("pop failed"))
      assertEquals(callbacks, Vector("finite query", "finite append"))
      fail = false
      tui.stop()
      tui.queryTerminalBackgroundColor { _ =>
        assertEquals(count(terminal, TUI.AlternateScreenExit), 1)
        callbacks :+= "after retry"
      }
    finally allowLate.countDown()
    join(stopper)
    assertEquals(pops, 3)
    assertEquals(callbacks, Vector("finite query", "finite append", "late", "after retry"))

  test("concurrent stops during an explicit failed retry coalesce even with a shared failure"):
    val terminal      = KeyboardTerminal()
    val tui           = alternateTui(terminal, false)
    tui.start()
    send(terminal, "\u001b[?0u")
    val sharedFailure = RuntimeException("pop failed")
    var pops          = 0
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.DisableSequence then
        pops += 1
        throw sharedFailure
    assert(intercept[RuntimeException](tui.stop()) eq sharedFailure)
    assertEquals(pops, 2)
    val retryEntered  = CountDownLatch(1)
    val allowRetry    = CountDownLatch(1)
    terminal.beforeWrite = data =>
      if data === KittyKeyboardProtocol.DisableSequence then
        pops += 1
        retryEntered.countDown()
        await(allowRetry)
        throw sharedFailure
    val retryFailure  = AtomicReference[Throwable]()
    val retry         = Thread(() =>
      try tui.stop()
      catch case error: Throwable => retryFailure.set(error)
    )
    retry.start()
    await(retryEntered)
    val concurrent    = Vector.fill(8)(Thread(() => tui.stop()))
    concurrent.foreach(_.start())
    concurrent.foreach(join)
    allowRetry.countDown()
    join(retry)
    assert(retryFailure.get() eq sharedFailure)
    assertEquals(pops, 3)
    tui.flushRender()
    assertEquals(pops, 3)
    terminal.beforeWrite = _ => ()
    tui.stop()
    assertEquals(count(terminal, TUI.AlternateScreenExit), 1)

  Vector("normal", "alternate", "fullscreen").foreach { screen =>
    Vector(false, true).foreach { failCleanup =>
      test(
        s"interrupted idle run owns initial cleanup and leaves retry explicit: $screen/$failCleanup"
      ):
        val terminal    = KeyboardTerminal()
        val tui         = screen match
          case "normal"    => TUI(terminal)
          case "alternate" => alternateTui(terminal, false)
          case _           => alternateTui(terminal, true)
        tui.start()
        send(terminal, "\u001b[?0u")
        var popAttempts = 0
        var fail        = failCleanup
        terminal.beforeWrite = data =>
          if data === KittyKeyboardProtocol.DisableSequence then
            popAttempts += 1
            if fail then throw RuntimeException("interrupted cleanup failed")
        val reported    = AtomicReference[Throwable]()
        val runner      = Thread(() =>
          try tui.run()
          catch case error: Throwable => reported.set(error)
        )
        runner.setDaemon(true)
        runner.start()
        try
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (runner.getState !== Thread.State.WAITING) && System.nanoTime() < deadline do
            Thread.`yield`()
          assertEquals(runner.getState, Thread.State.WAITING)
          runner.interrupt()
          join(runner)
          assert(reported.get().isInstanceOf[InterruptedException])
          if failCleanup then
            assertEquals(popAttempts, 2)
            assert(
              reported.get().getSuppressed.exists(_.getMessage === "interrupted cleanup failed")
            )
            assertEquals(count(terminal, TUI.AlternateScreenExit), 0)
            intercept[IllegalStateException](tui.start())
          else
            assertEquals(popAttempts, 1)
            assertEquals(terminal.stops, 1)
            assertEquals(
              count(terminal, TUI.AlternateScreenExit),
              if screen === "normal" then 0 else 1
            )
          fail = false
          tui.stop()
          tui.stop()
          assertEquals(popAttempts, if failCleanup then 3 else 1)
          assertEquals(terminal.stops, 1)
          assertEquals(
            count(terminal, TUI.AlternateScreenExit),
            if screen === "normal" then 0 else 1
          )
        finally
          fail = false
          // Release an orphaned Stopping session even when this regression fails.
          tui.flushRender()
          tui.stop()
          runner.join(5000L)
    }
  }

  test("first stop reserves restoration before notifying a concurrent stop caller"):
    val cleanupFailure = RuntimeException("owned restoration failure")
    val terminal       = new KeyboardTerminal:
      override def stop(): Unit =
        super.stop()
        throw cleanupFailure
    val runtime        = AtomicReference[TUI]()
    val concurrent     = AtomicReference[Thread]()
    val firstFailure   = AtomicReference[Throwable]()
    val secondFailure  = AtomicReference[Throwable]()
    val secondReturned = CountDownLatch(1)
    var coalesced      = false
    val observer       = TUIDiagnosticObserver {
      case TUIDiagnosticEvent.Lifecycle(TUIDiagnosticLifecycleState.Stopping, _) =>
        val caller = Thread(() =>
          try runtime.get().stop()
          catch case error: Throwable => secondFailure.set(error)
          finally secondReturned.countDown()
        )
        concurrent.set(caller)
        caller.start()
        // The first caller is paused after selecting cleanup but before entering its drain.
        // A concurrent stop must coalesce, not steal ownership and block on this observer.
        coalesced = secondReturned.await(5L, TimeUnit.SECONDS)
      case _                                                                     => ()
    }
    val tui            = TUI(terminal, TUIOptions(diagnosticObserver = Some(observer)))
    runtime.set(tui)
    tui.start()
    try tui.stop()
    catch case error: Throwable => firstFailure.set(error)
    join(concurrent.get())
    assert(coalesced, "concurrent stop stole the initial caller's cleanup reservation")
    assert(firstFailure.get() eq cleanupFailure)
    assertEquals(secondFailure.get(), null)
    assertEquals(terminal.stops, 1)
