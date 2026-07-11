package scalatui.core

import scalatui.terminal.{
  RgbColor,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalImageProtocol,
  TerminalInput,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalKey,
  TerminalProgressSupport,
  TerminalRawKind,
  TerminalRawTermination,
  TerminalTitleSupport
}

class TUIConcurrencySuite extends munit.FunSuite:
  private def parseInput(value: String): Vector[TerminalInput] =
    val buffer = TerminalInputBuffer()
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).grouped(TerminalInputChunk.MaxBytes)
      .flatMap(bytes => buffer.process(TerminalInputChunk(bytes))).toVector ++ buffer.flush()

  private final class Gate:
    private var open = false

    def await(timeoutMillis: Long = 5000L): Unit = synchronized {
      val deadline  = System.currentTimeMillis() + timeoutMillis
      var remaining = timeoutMillis
      while !open && remaining > 0L do
        wait(remaining)
        remaining = deadline - System.currentTimeMillis()
      if !open then throw AssertionError(s"gate did not open within $timeoutMillis ms")
    }

    def release(): Unit = synchronized {
      open = true
      notifyAll()
    }

  private def awaitWaiting(thread: Thread, timeoutMillis: Long = 5000L): Unit =
    val deadline = System.currentTimeMillis() + timeoutMillis
    while thread.getState != Thread.State.WAITING && System.currentTimeMillis() < deadline do
      Thread.`yield`()
    assertEquals(thread.getState, Thread.State.WAITING)

  private class RecordingTerminal
      extends Terminal,
        TerminalTitleSupport,
        TerminalProgressSupport:
    private var inputHandler: TerminalInput => Unit = _ => ()
    private var resizeHandler: () => Unit           = () => ()
    private var currentColumns                      = 20
    private var currentRows                         = 5
    private var writesBuffer                        = Vector.empty[String]
    private var activeWrites                        = 0
    private var concurrentWriteObserved             = false
    private var startCount                          = 0
    private var stopCount                           = 0
    var beforeStart: () => Unit                     = () => ()
    var beforeWrite: String => Unit                 = _ => ()
    var writeFailure: String => Option[Throwable]   = _ => None
    var beforeStop: () => Unit                      = () => ()

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = synchronized {
      startCount += 1
      inputHandler = onInput
      resizeHandler = onResize
      beforeStart()
    }

    override def stop(): Unit = synchronized {
      beforeStop()
      stopCount += 1
    }

    override def write(data: String): Unit =
      synchronized {
        activeWrites += 1
        if activeWrites > 1 then concurrentWriteObserved = true
      }
      try
        beforeWrite(data)
        writeFailure(data).foreach(throw _)
        synchronized { writesBuffer :+= data }
      finally synchronized { activeWrites -= 1 }

    override def columns: Int = synchronized(currentColumns)
    override def rows: Int    = synchronized(currentRows)

    override def moveBy(lines: Int): Unit           = write(s"move:$lines")
    override def hideCursor(): Unit                 = write("hide")
    override def showCursor(): Unit                 = write("show")
    override def clearLine(): Unit                  = write("clear-line")
    override def clearFromCursor(): Unit            = write("clear-tail")
    override def clearScreen(): Unit                = write("clear-screen")
    override def setTitle(title: String): Unit      = write(s"title:$title")
    override def setProgress(active: Boolean): Unit = write(s"progress:$active")

    def send(input: TerminalInput): Unit = inputHandler(input)

    def resize(columns: Int, rows: Int): Unit =
      synchronized {
        currentColumns = columns
        currentRows = rows
      }
      resizeHandler()

    def output: String              = synchronized(writesBuffer.mkString)
    def writes: Vector[String]      = synchronized(writesBuffer)
    def hadConcurrentWrite: Boolean = synchronized(concurrentWriteObserved)
    def starts: Int                 = synchronized(startCount)
    def stops: Int                  = synchronized(stopCount)

  test("render and concurrent flush do not invert an application lock"):
    val terminal      = RecordingTerminal()
    val application   = Object()
    val renderEntered = Gate()
    val allowRender   = Gate()
    var renders       = 0
    val component     = new Component:
      override def render(width: Int): Vector[String] =
        renderEntered.release()
        allowRender.await()
        application.synchronized { renders += 1 }
        Vector("frame")
    val tui           = TUI(terminal)
    tui.addChild(component)

    val starter       = Thread(() => tui.start())
    starter.start()
    renderEntered.await()
    val flushReturned = Gate()
    val publisher     = Thread(() =>
      application.synchronized {
        tui.requestRender()
        tui.flushRender()
        flushReturned.release()
      }
    )
    publisher.start()
    flushReturned.await()
    allowRender.release()
    starter.join(2000)
    publisher.join(2000)

    assert(!starter.isAlive)
    assert(!publisher.isAlive)
    assert(renders >= 1)
    tui.stop()

  test("global and focused input callbacks avoid lock inversion and preserve later input order"):
    def verify(targetGlobal: Boolean): Unit =
      val terminal                                  = RecordingTerminal()
      val application                               = Object()
      val applicationHeld                           = Gate()
      val callbackEntered                           = Gate()
      val flushReturned                             = Gate()
      val received                                  = scala.collection.mutable.ArrayBuffer.empty[String]
      def record(kind: String, value: String): Unit =
        if (kind.equals("global") && targetGlobal) ||
          (kind.equals("focused") && !targetGlobal)
        then
          callbackEntered.release()
          application.synchronized(received += s"$kind-$value")
        else received += s"$kind-$value"
      val component                                 = new Component:
        override def render(width: Int): Vector[String]                   = Vector(received.mkString)
        override def handleInputResult(input: TerminalInput): InputResult = input match
          case TerminalInput.Key(TerminalKey.Character(value), _) =>
            record("focused", value)
            InputResult.NoRender
          case _                                                  => InputResult.Ignored
      val tui                                       = TUI(terminal)
      tui.addChild(component)
      tui.setFocus(component)
      tui.addInputListener { input =>
        input match
          case TerminalInput.Key(TerminalKey.Character(value), _) => record("global", value)
          case _                                                  => ()
        InputResult.Ignored
      }
      tui.start()

      val holder     = Thread(() =>
        application.synchronized {
          applicationHeld.release()
          callbackEntered.await()
          tui.requestRender()
          tui.flushRender()
          flushReturned.release()
        }
      )
      holder.start()
      applicationHeld.await()
      val firstInput = Thread(() =>
        terminal.send(TerminalInput.Key(TerminalKey.Character("a")))
      )
      firstInput.start()
      flushReturned.await()
      holder.join(2000)
      firstInput.join(2000)
      terminal.send(TerminalInput.Key(TerminalKey.Character("b")))

      assert(!holder.isAlive)
      assert(!firstInput.isAlive)
      assertEquals(
        received.toVector,
        Vector("global-a", "focused-a", "global-b", "focused-b")
      )
      tui.stop()

    verify(targetGlobal = true)
    verify(targetGlobal = false)

  test("reentrant flush is non-recursive and concurrent flush is non-waiting"):
    val terminal  = RecordingTerminal()
    val entered   = Gate()
    val release   = Gate()
    var depth     = 0
    var maxDepth  = 0
    var renders   = 0
    var runtime   = Option.empty[TUI]
    val component = new Component:
      override def render(width: Int): Vector[String] =
        depth += 1
        maxDepth = math.max(maxDepth, depth)
        renders += 1
        if renders == 1 then
          runtime.foreach(_.requestRender())
          runtime.foreach(_.flushRender())
          entered.release()
          release.await()
        depth -= 1
        Vector(renders.toString)
    val tui       = TUI(terminal)
    runtime = Some(tui)
    tui.addChild(component)

    val starter    = Thread(() => tui.start())
    starter.start()
    entered.await()
    val returned   = Gate()
    val concurrent = Thread(() => {
      tui.requestRender(force = true)
      tui.flushRender()
      returned.release()
    })
    concurrent.start()
    returned.await()
    release.release()
    starter.join(2000)
    concurrent.join(2000)

    assertEquals(maxDepth, 1)
    assert(renders >= 2)
    tui.stop()

  test("resize during rendering rejects the stale candidate and redraws latest dimensions"):
    val terminal  = RecordingTerminal()
    val entered   = Gate()
    val release   = Gate()
    var widths    = Vector.empty[Int]
    var first     = true
    val component = new Component:
      override def render(width: Int): Vector[String] =
        widths :+= width
        if first then
          first = false
          entered.release()
          release.await()
        Vector("x" * width)
    val tui       = TUI(terminal)
    tui.addChild(component)

    val starter = Thread(() => tui.start())
    starter.start()
    entered.await()
    terminal.resize(12, 4)
    terminal.resize(8, 3)
    release.release()
    starter.join(2000)

    assert(!starter.isAlive)
    assertEquals(widths.last, 8)
    assert(terminal.output.contains("x" * 8))
    terminal.send(TerminalInput.Key(TerminalKey.Character("z")))
    tui.stop()

  test("cell-dimension reply drains rendering after releasing the lifecycle lock"):
    val terminal        = RecordingTerminal()
    val application     = Object()
    val applicationHeld = Gate()
    val renderEntered   = Gate()
    val flushReturned   = Gate()
    var gateRender      = false
    val tui             = TUI(terminal)
    tui.addChild(new Component:
      override def render(width: Int): Vector[String] =
        if gateRender then
          renderEntered.release()
          application.synchronized(())
        Vector("frame"))
    tui.start()
    gateRender = true

    val holder = Thread(() =>
      application.synchronized {
        applicationHeld.release()
        renderEntered.await()
        tui.requestRender()
        tui.flushRender()
        flushReturned.release()
      }
    )
    holder.start()
    applicationHeld.await()
    val reply  = Thread(() => parseInput("\u001b[6;12;24t").foreach(terminal.send))
    reply.start()
    flushReturned.await()
    holder.join(2000)
    reply.join(2000)

    assert(!holder.isAlive)
    assert(!reply.isAlive)
    tui.stop()

  test("4097th ingress publisher blocks and wakes when dequeue frees capacity"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var received        = 0
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
          callbackEntered.release()
          allowCallback.await()
        case _                                                   => received += 1
      InputResult.NoRender
    }
    tui.start()
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4096).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )
    val blocked         = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("last"))))
    blocked.start()
    awaitWaiting(blocked)
    allowCallback.release()
    blocked.join(5000)
    owner.join(5000)

    assert(!blocked.isAlive)
    assertEquals(received, 4097)
    tui.stop()

  test("stop wakes a blocked ingress publisher and rejects its event"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var received        = 0
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
          callbackEntered.release()
          allowCallback.await()
        case _                                                   => received += 1
      InputResult.NoRender
    }
    tui.start()
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4096).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )
    val blocked         = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("rejected"))))
    blocked.start()
    awaitWaiting(blocked)
    tui.stop()
    blocked.join(2000)
    assert(!blocked.isAlive)
    allowCallback.release()
    owner.join(2000)

    assertEquals(received, 0)
    assertEquals(terminal.stops, 1)

  test("protocol correlation fragments consume zero slots and completion consumes one batch slot"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var completions     = 0
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
          callbackEntered.release()
          allowCallback.await()
        case _                                                   => ()
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(_ => completions += 1)
    tui.queryTerminalBackgroundColor(_ => completions += 1)
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4095).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )

    terminal.send(TerminalInput.RawStart(TerminalRawKind.Osc))
    terminal.send(TerminalInput.RawChunk(TerminalInputChunk(
      "\u001b]11;#112233\u0007".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )))
    terminal.send(TerminalInput.RawEnd(TerminalRawTermination.Complete))

    val blocked = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("after"))))
    blocked.start()
    awaitWaiting(blocked)
    allowCallback.release()
    blocked.join(5000)
    owner.join(5000)

    assert(!blocked.isAlive)
    assertEquals(completions, 2)
    tui.stop()

  test("full ingress does not block zero-slot fragments and blocks non-mutating completion"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var completions     = 0
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
          callbackEntered.release()
          allowCallback.await()
        case _                                                   => ()
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(_ => completions += 1)
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4096).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )

    terminal.send(TerminalInput.RawStart(TerminalRawKind.Osc))
    terminal.send(TerminalInput.RawChunk(TerminalInputChunk(
      "\u001b]11;#112233\u0007".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )))
    val completion = Thread(() =>
      terminal.send(TerminalInput.RawEnd(TerminalRawTermination.Complete))
    )
    completion.start()
    awaitWaiting(completion)
    assertEquals(completions, 0)

    allowCallback.release()
    completion.join(5000)
    owner.join(5000)
    assert(!completion.isAlive)
    assertEquals(completions, 1)
    tui.stop()

  test("unrelated correlated replay uses one slot per raw event in exact order"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var rawEvents       = Vector.empty[TerminalInput]
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _)                             =>
          callbackEntered.release()
          allowCallback.await()
        case TerminalInput.RawStart(_) | TerminalInput.RawChunk(_) | TerminalInput.RawEnd(_) =>
          rawEvents :+= input
        case _                                                                               => ()
      InputResult.NoRender
    }
    tui.start()
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4094).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )
    val bytes           = "\u001b[?999x".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    terminal.send(TerminalInput.RawStart(TerminalRawKind.Csi))
    terminal.send(TerminalInput.RawChunk(TerminalInputChunk(bytes)))
    terminal.send(TerminalInput.RawEnd(TerminalRawTermination.Complete))
    allowCallback.release()
    owner.join(5000)
    assertEquals(
      rawEvents,
      Vector(
        TerminalInput.RawStart(TerminalRawKind.Csi),
        TerminalInput.RawChunk(TerminalInputChunk(bytes)),
        TerminalInput.RawEnd(TerminalRawTermination.Complete)
      )
    )
    tui.stop()

  test("overlong correlated replay uses one slot per raw event and preserves bytes and flight"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var rawEvents       = Vector.empty[TerminalInput]
    var completion      = Option.empty[TerminalQueryResult[RgbColor]]
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _)                             =>
          callbackEntered.release()
          allowCallback.await()
        case TerminalInput.RawStart(_) | TerminalInput.RawChunk(_) | TerminalInput.RawEnd(_) =>
          rawEvents :+= input
        case _                                                                               => ()
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(result => completion = Some(result))
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    (1 to 4094).foreach(index =>
      terminal.send(TerminalInput.Key(TerminalKey.Character(index.toString)))
    )
    val first           = Array.fill[Byte](TerminalInputChunk.MaxBytes)('x'.toByte)
    val last            = Array('y'.toByte)
    terminal.send(TerminalInput.RawStart(TerminalRawKind.Osc))
    terminal.send(TerminalInput.RawChunk(TerminalInputChunk(first)))
    terminal.send(TerminalInput.RawChunk(TerminalInputChunk(last)))
    allowCallback.release()
    owner.join(5000)
    assertEquals(
      rawEvents,
      Vector(
        TerminalInput.RawStart(TerminalRawKind.Osc),
        TerminalInput.RawChunk(TerminalInputChunk(first)),
        TerminalInput.RawChunk(TerminalInputChunk(last))
      )
    )
    parseInput("\u001b]11;#112233\u0007").foreach(terminal.send)
    assertEquals(completion, Some(TerminalQueryResult.Success(RgbColor(17, 34, 51))))
    tui.stop()

  test("maximal correlated replay drains before a later input on an idle runtime"):
    val terminal      = RecordingTerminal()
    val tui           = TUI(terminal)
    val replayEntered = Gate()
    val allowReplay   = Gate()
    val received      = scala.collection.mutable.ArrayBuffer.empty[TerminalInput]
    val chunks        = Vector.tabulate(TerminalInputChunk.MaxBytes)(index =>
      TerminalInputChunk(Array((index & 0xff).toByte))
    )
    tui.addInputListener { input =>
      received += input
      input match
        case TerminalInput.RawStart(_) =>
          replayEntered.release()
          allowReplay.await()
        case _                         => ()
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(_ => ())
    terminal.send(TerminalInput.RawStart(TerminalRawKind.Osc))
    chunks.foreach(chunk => terminal.send(TerminalInput.RawChunk(chunk)))

    val termination = Thread(() =>
      terminal.send(TerminalInput.RawEnd(TerminalRawTermination.Complete))
    )
    termination.start()
    replayEntered.await()
    val later       = TerminalInput.Key(TerminalKey.Character("later"))
    val publisher   = Thread(() => terminal.send(later))
    publisher.start()
    awaitWaiting(publisher)

    allowReplay.release()
    termination.join(5000)
    publisher.join(5000)

    assert(!termination.isAlive, "maximal replay publication did not return")
    assert(!publisher.isAlive, "later publication did not return")
    assertEquals(received.headOption, Some(TerminalInput.RawStart(TerminalRawKind.Osc)))
    assertEquals(received.length, TerminalInputChunk.MaxBytes + 3)
    received.slice(1, TerminalInputChunk.MaxBytes + 1).zip(chunks).foreach {
      case (TerminalInput.RawChunk(actual), expected) =>
        assertEquals(actual.toArray.toVector, expected.toArray.toVector)
      case (actual, _)                                => fail(s"expected raw chunk, received $actual")
    }
    assertEquals(
      received(TerminalInputChunk.MaxBytes + 1),
      TerminalInput.RawEnd(TerminalRawTermination.Complete)
    )
    assertEquals(received.last, later)
    tui.stop()

  test("stop discards a maximal replay continuation and wakes a later publisher"):
    val terminal      = RecordingTerminal()
    val tui           = TUI(terminal)
    val replayEntered = Gate()
    val allowReplay   = Gate()
    val received      = scala.collection.mutable.ArrayBuffer.empty[TerminalInput]
    val chunks        = Vector.fill(TerminalInputChunk.MaxBytes)(
      TerminalInputChunk(Array('x'.toByte))
    )
    tui.addInputListener { input =>
      received += input
      input match
        case TerminalInput.RawStart(_) =>
          replayEntered.release()
          allowReplay.await()
        case _                         => ()
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(_ => ())
    terminal.send(TerminalInput.RawStart(TerminalRawKind.Osc))
    chunks.foreach(chunk => terminal.send(TerminalInput.RawChunk(chunk)))

    val termination = Thread(() =>
      terminal.send(TerminalInput.RawEnd(TerminalRawTermination.Complete))
    )
    termination.start()
    replayEntered.await()
    val publisher   = Thread(() =>
      terminal.send(TerminalInput.Key(TerminalKey.Character("discarded")))
    )
    publisher.start()
    awaitWaiting(publisher)

    tui.stop()
    publisher.join(2000)
    assert(!publisher.isAlive, "blocked publisher did not wake on stop")
    allowReplay.release()
    termination.join(5000)

    assert(!termination.isAlive, "replay owner did not finish cleanup")
    assertEquals(received.toVector, Vector(TerminalInput.RawStart(TerminalRawKind.Osc)))
    assertEquals(terminal.stops, 1)

  test("query subscribers share one request and complete in subscription order"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    tui.start()
    var results  = Vector.empty[String]

    tui.queryTerminalBackgroundColor(result => results :+= s"first:$result")
    tui.queryTerminalBackgroundColor(result => results :+= s"second:$result")

    assertEquals(
      terminal.writes.count(_.contains(TerminalColorProtocol.BackgroundColorQuery)),
      1
    )
    parseInput("\u001b]11;#112233\u0007").foreach(terminal.send)
    assertEquals(
      results,
      Vector(
        s"first:${TerminalQueryResult.Success(RgbColor(17, 34, 51))}",
        s"second:${TerminalQueryResult.Success(RgbColor(17, 34, 51))}"
      )
    )
    assert(!terminal.hadConcurrentWrite)

  test("all-cancelled empty flight accepts a subscriber and consumes a later empty reply"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    tui.start()
    var results  = Vector.empty[TerminalQueryResult[TerminalColorScheme]]
    val cancel   = tui.queryTerminalColorScheme(_ => fail("cancelled subscriber completed"))
    cancel()
    cancel()

    tui.queryTerminalColorScheme(result => results :+= result)
    assertEquals(terminal.writes.count(_.contains(TerminalColorProtocol.ColorSchemeQuery)), 1)
    parseInput("\u001b[?997;2n").foreach(terminal.send)
    assertEquals(results, Vector(TerminalQueryResult.Success(TerminalColorScheme.Light)))

    val cancelEmpty = tui.queryTerminalColorScheme(_ => fail("empty flight completed"))
    cancelEmpty()
    parseInput("\u001b[?997;1n").foreach(terminal.send)
    assertEquals(results, Vector(TerminalQueryResult.Success(TerminalColorScheme.Light)))
    tui.stop()

  test("cancellation before claim wins and completion callbacks are exactly once"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var calls           = Vector.empty[String]
    tui.start()
    tui.queryTerminalBackgroundColor { _ =>
      calls :+= "first"
      callbackEntered.release()
      allowCallback.await()
    }
    val cancelSecond    = tui.queryTerminalBackgroundColor(_ => calls :+= "second")
    val reply           = Thread(() => parseInput("\u001b]11;#112233\u0007").foreach(terminal.send))
    reply.start()
    callbackEntered.await()
    cancelSecond()
    cancelSecond()
    allowCallback.release()
    reply.join(2000)

    assertEquals(calls, Vector("first"))
    parseInput("\u001b]11;#445566\u0007").foreach(terminal.send)
    assertEquals(calls, Vector("first"))

  test("callback claim wins a concurrent cancellation race"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackClaimed = Gate()
    val allowCallback   = Gate()
    var calls           = 0
    tui.start()
    val cancel          = tui.queryTerminalColorScheme { _ =>
      callbackClaimed.release()
      allowCallback.await()
      calls += 1
    }
    val reply           = Thread(() => parseInput("\u001b[?997;2n").foreach(terminal.send))
    reply.start()
    callbackClaimed.await()

    cancel()
    cancel()
    allowCallback.release()
    reply.join(2000)

    assert(!reply.isAlive)
    assertEquals(calls, 1)

  test("input query completion and notification callbacks preserve ingress FIFO order"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var events          = Vector.empty[String]
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character(value), _) =>
          events :+= value
          if value.equals("hold") then
            callbackEntered.release()
            allowCallback.await()
        case _                                                  => ()
      InputResult.NoRender
    }
    tui.onTerminalColorSchemeChange(_ => events :+= "notification")
    tui.setTerminalColorSchemeNotifications(enabled = true)
    tui.start()
    tui.queryTerminalBackgroundColor(_ => events :+= "query")
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    terminal.send(TerminalInput.Key(TerminalKey.Character("later")))
    parseInput("\u001b[?997;1n").foreach(terminal.send)
    parseInput("\u001b]11;#abcdef\u0007").foreach(terminal.send)
    assertEquals(events, Vector("hold"))
    allowCallback.release()
    owner.join(2000)
    assertEquals(events, Vector("hold", "later", "notification", "query"))
    tui.stop()

  test("unrelated and overlong raw streams stay ordinary and preserve the active flight"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    var results  = Vector.empty[TerminalQueryResult[RgbColor]]
    var ordinary = Vector.empty[TerminalInput]
    tui.addInputListener { input =>
      ordinary :+= input
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(result => results :+= result)

    val unrelatedProtocol = parseInput("\u001b[?998;2n")
    val unrelatedRaw      = parseInput("\u001bPpayload\u001b\\")
    val overlong          = parseInput(
      "\u001b]11;" + ("1" * TerminalInputChunk.MaxBytes) + "\u0007"
    )
    (unrelatedProtocol ++ unrelatedRaw ++ overlong).foreach(terminal.send)
    assertEquals(results, Vector.empty)
    assertEquals(ordinary, unrelatedProtocol ++ unrelatedRaw ++ overlong)

    tui.queryTerminalBackgroundColor(result => results :+= result)
    assertEquals(
      terminal.writes.count(_.contains(TerminalColorProtocol.BackgroundColorQuery)),
      1
    )
    parseInput("\u001b]11;#112233\u0007").foreach(terminal.send)
    assertEquals(
      results,
      Vector(
        TerminalQueryResult.Success(RgbColor(17, 34, 51)),
        TerminalQueryResult.Success(RgbColor(17, 34, 51))
      )
    )
    tui.stop()

  test("stop waits for reserved emission and completes it as stopped"):
    val terminal       = RecordingTerminal()
    val requestEntered = Gate()
    val allowRequest   = Gate()
    var result         = Option.empty[TerminalQueryResult[RgbColor]]
    terminal.beforeWrite = data =>
      if data.contains(TerminalColorProtocol.BackgroundColorQuery) then
        requestEntered.release()
        allowRequest.await()
    val tui            = TUI(terminal)
    tui.start()
    val query          = Thread(() => {
      tui.queryTerminalBackgroundColor(value => result = Some(value))
      ()
    })
    query.start()
    requestEntered.await()
    tui.stop()
    assertEquals(terminal.stops, 0)
    allowRequest.release()
    query.join(2000)

    assertEquals(result, Some(TerminalQueryResult.Stopped))
    assertEquals(terminal.stops, 1)

  test("queries during starting and stopping emit no request and serialize stopped completion"):
    val terminal       = RecordingTerminal()
    val startupEntered = Gate()
    val allowStartup   = Gate()
    terminal.beforeStart = () => {
      startupEntered.release()
      allowStartup.await()
    }
    val tui            = TUI(terminal)
    val starter        = Thread(() => tui.start())
    starter.start()
    startupEntered.await()
    var startingResult = Option.empty[TerminalQueryResult[RgbColor]]
    tui.queryTerminalBackgroundColor(value => startingResult = Some(value))
    assertEquals(startingResult, None)
    allowStartup.release()
    starter.join(2000)
    assertEquals(startingResult, Some(TerminalQueryResult.Stopped))
    assert(!terminal.output.contains(TerminalColorProtocol.BackgroundColorQuery))

    val callbackEntered = Gate()
    val allowCallback   = Gate()
    tui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    tui.stop()
    var stoppingResult  = Option.empty[TerminalQueryResult[TerminalColorScheme]]
    tui.queryTerminalColorScheme(value => stoppingResult = Some(value))
    assertEquals(stoppingResult, None)
    allowCallback.release()
    owner.join(2000)
    assertEquals(stoppingResult, Some(TerminalQueryResult.Stopped))
    assertEquals(terminal.writes.count(_.contains(TerminalColorProtocol.ColorSchemeQuery)), 0)

  test("Stopping registration before cleanup commit completes once before restoration"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var ordering        = Vector.empty[String]
    var completions     = 0
    terminal.beforeStop = () => ordering :+= "restore"
    val tui             = TUI(terminal)
    tui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    tui.start()
    val owner           = Thread(() =>
      terminal.send(TerminalInput.Key(TerminalKey.Character("hold")))
    )
    owner.start()
    callbackEntered.await()

    tui.stop()
    val cancel = tui.queryTerminalBackgroundColor { result =>
      completions += 1
      ordering :+= s"query:$result"
    }
    cancel()
    assertEquals(ordering, Vector.empty)
    allowCallback.release()
    owner.join(2000)

    assert(!owner.isAlive)
    assertEquals(completions, 0)
    assertEquals(ordering, Vector("restore"))

    var uncancelledCompletions = 0
    val secondTerminal         = RecordingTerminal()
    val secondEntered          = Gate()
    val allowSecond            = Gate()
    var secondOrdering         = Vector.empty[String]
    secondTerminal.beforeStop = () => secondOrdering :+= "restore"
    val secondTui              = TUI(secondTerminal)
    secondTui.addInputListener { _ =>
      secondEntered.release()
      allowSecond.await()
      InputResult.NoRender
    }
    secondTui.start()
    val secondOwner            = Thread(() =>
      secondTerminal.send(TerminalInput.Key(TerminalKey.Character("hold")))
    )
    secondOwner.start()
    secondEntered.await()
    secondTui.stop()
    secondTui.queryTerminalBackgroundColor { result =>
      uncancelledCompletions += 1
      secondOrdering :+= s"query:$result"
    }
    allowSecond.release()
    secondOwner.join(2000)

    assertEquals(uncancelledCompletions, 1)
    assertEquals(
      secondOrdering,
      Vector(s"query:${TerminalQueryResult.Stopped}", "restore")
    )

  test("stop retains accepted query completion and discards queued ordinary ingress"):
    val terminal        = RecordingTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var ordinaryCalls   = 0
    var queryResult     = Option.empty[TerminalQueryResult[RgbColor]]
    var ordering        = Vector.empty[String]
    terminal.beforeStop = () => ordering :+= "cleanup"
    tui.addInputListener { input =>
      input match
        case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
          callbackEntered.release()
          allowCallback.await()
        case _                                                   => ordinaryCalls += 1
      InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor { value =>
      queryResult = Some(value)
      ordering :+= "query"
    }
    val owner           = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    terminal.send(TerminalInput.Key(TerminalKey.Character("discarded")))
    parseInput("\u001b]11;#112233\u0007").foreach(terminal.send)
    tui.stop()
    allowCallback.release()
    owner.join(2000)

    assertEquals(queryResult, Some(TerminalQueryResult.Success(RgbColor(17, 34, 51))))
    assertEquals(ordinaryCalls, 0)
    assertEquals(terminal.stops, 1)
    assertEquals(ordering, Vector("query", "cleanup"))

  test("stop completes an emitted flight as stopped before cleanup"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    var result   = Option.empty[TerminalQueryResult[TerminalColorScheme]]
    tui.start()
    tui.queryTerminalColorScheme(value => result = Some(value))

    tui.stop()

    assertEquals(result, Some(TerminalQueryResult.Stopped))
    assertEquals(terminal.stops, 1)

  test("query emission and callback failures continue completions and cleanup"):
    val terminal = RecordingTerminal()
    terminal.writeFailure = data =>
      Option.when(data.contains(TerminalColorProtocol.ColorSchemeQuery))(
        RuntimeException("request emission failed")
      )
    val tui      = TUI(terminal)
    tui.start()
    var failed   = Option.empty[TerminalQueryResult[TerminalColorScheme]]
    tui.queryTerminalColorScheme(value => failed = Some(value))
    assert(failed.exists {
      case TerminalQueryResult.Failed(cause) => cause.getMessage.equals("request emission failed")
      case _                                 => false
    })
    assertEquals(terminal.stops, 1)

    val callbackTerminal = RecordingTerminal()
    val callbackTui      = TUI(callbackTerminal)
    var secondCalled     = false
    callbackTui.start()
    callbackTui.queryTerminalBackgroundColor(_ => throw RuntimeException("callback failed"))
    callbackTui.queryTerminalBackgroundColor(_ => secondCalled = true)
    parseInput("\u001b]11;#112233\u0007").foreach(callbackTerminal.send)
    assert(secondCalled)
    assertEquals(callbackTerminal.stops, 1)

  test("overlay, context, focus, notification, and failure hooks remain drain isolated"):
    val terminal             = RecordingTerminal()
    val hookEntered          = Gate()
    val allowHook            = Gate()
    val concurrentDone       = Gate()
    var activeHooks          = 0
    var concurrentHooks      = false
    var contexts             = Vector.empty[Boolean]
    var focusValues          = Vector.empty[Boolean]
    val component            = new Component with ContextualComponent with Focusable:
      private var currentFocus                                   = false
      override def render(width: Int): Vector[String]            = Vector("base")
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        activeHooks += 1
        if activeHooks > 1 then concurrentHooks = true
        contexts :+= value.nonEmpty
        activeHooks -= 1
      override def focused: Boolean                              = currentFocus
      override def focused_=(value: Boolean): Unit               =
        activeHooks += 1
        if activeHooks > 1 then concurrentHooks = true
        focusValues :+= value
        currentFocus = value
        activeHooks -= 1
    val tui                  = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    val overlay              = new Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector("overlay")
      override def tuiContext_=(value: Option[TUIContext]): Unit = contexts :+= value.nonEmpty
    var handle               = Option.empty[OverlayHandle]
    val shower               = Thread(() =>
      handle = Some(tui.showOverlay(
        overlay,
        OverlayOptions(visible = (_, _) => {
          activeHooks += 1
          if activeHooks > 1 then concurrentHooks = true
          hookEntered.release()
          allowHook.await()
          activeHooks -= 1
          true
        })
      ))
    )
    shower.start()
    hookEntered.await()
    val concurrent           = Thread(() => {
      tui.setFocus(component)
      tui.requestRender()
      tui.flushRender()
      concurrentDone.release()
    })
    concurrent.start()
    concurrentDone.await()
    assert(!tui.hasOverlay)
    allowHook.release()
    shower.join(2000)
    concurrent.join(2000)
    assert(tui.hasOverlay)
    var notificationObserved = false
    tui.onTerminalColorSchemeChange { _ =>
      notificationObserved = true
      tui.requestRender()
      tui.flushRender()
    }
    tui.setTerminalColorSchemeNotifications(enabled = true)
    parseInput("\u001b[?997;1n").foreach(terminal.send)
    assert(notificationObserved)
    handle.foreach(_.hide())
    tui.removeChild(component)
    assertEquals(contexts, Vector(true, true, false, false))
    assert(focusValues.contains(true))
    assert(!concurrentHooks)
    tui.stop()

    val failingTerminal = RecordingTerminal()
    val failingTui      = TUI(failingTerminal)
    failingTui.addChild(new Component:
      override def render(width: Int): Vector[String] = throw RuntimeException("hook failure"))
    intercept[RuntimeException](failingTui.start())
    failingTui.stop()
    assertEquals(failingTerminal.stops, 1)

  test("desired children publish immediately and committed hooks preserve operation order"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var contexts        = Vector.empty[String]
    final class Child(val name: String) extends Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector(name)
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        contexts :+= s"$name:${value.nonEmpty}"
    val first = Child("first")
    val later = Child("later")
    val tui   = TUI(terminal)
    tui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    tui.start()
    val owner = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()

    tui.addChild(first)
    tui.addChild(first)
    tui.removeChild(first)
    assertEquals(tui.children, Vector(first))
    tui.clear()
    tui.addChild(later)
    assertEquals(tui.children, Vector(later))

    allowCallback.release()
    owner.join(2000)
    assertEquals(contexts, Vector("first:true", "first:false", "later:true"))
    tui.stop()

  test("Stopping and Cleaning reject structure and discard uncommitted desired entries"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    val restorationHit  = Gate()
    val allowRestore    = Gate()
    var attached        = Vector.empty[String]
    final class Child(val name: String) extends Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector(name)
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        attached :+= s"$name:${value.nonEmpty}"
    val committed = Child("committed")
    val pending          = Child("pending")
    val stoppingRejected = Child("stopping")
    val cleaningRejected = Child("cleaning")
    terminal.beforeStop = () => {
      restorationHit.release()
      allowRestore.await()
    }
    val tui              = TUI(terminal)
    tui.addChild(committed)
    tui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    tui.start()
    val owner            = Thread(() =>
      terminal.send(TerminalInput.Key(TerminalKey.Character("hold")))
    )
    owner.start()
    callbackEntered.await()

    tui.addChild(pending)
    assertEquals(tui.children, Vector(committed, pending))
    tui.stop()
    assertEquals(tui.children, Vector(committed))
    tui.addChild(stoppingRejected)
    tui.removeChild(committed)
    tui.clear()
    assertEquals(tui.children, Vector(committed))

    allowCallback.release()
    restorationHit.await()
    tui.addChild(cleaningRejected)
    tui.removeChild(committed)
    tui.clear()
    assertEquals(tui.children, Vector(committed))
    allowRestore.release()
    owner.join(2000)

    assert(!owner.isAlive)
    assertEquals(attached, Vector("committed:true"))
    assertEquals(terminal.stops, 1)

  test("structural hook failure keeps its commit and discards later ordinary structure"):
    val terminal      = RecordingTerminal()
    val attachEntered = Gate()
    val allowFailure  = Gate()
    var laterHooks    = 0
    val child         = new Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector("child")
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        if value.nonEmpty then
          attachEntered.release()
          allowFailure.await()
          throw RuntimeException("attach failed")
    val later         = new Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector("later")
      override def tuiContext_=(value: Option[TUIContext]): Unit = laterHooks += 1
    val tui           = TUI(terminal)
    tui.start()
    val publisher     = Thread(() => tui.addChild(child))
    publisher.start()
    attachEntered.await()

    tui.addChild(later)
    assertEquals(tui.children, Vector(child, later))
    allowFailure.release()
    publisher.join(2000)

    assert(!publisher.isAlive)
    assertEquals(tui.children, Vector(child))
    assertEquals(laterHooks, 0)
    assertEquals(terminal.stops, 1)

  test("Cleaning registrations cannot postpone restoration and use one finite cutoff"):
    val terminal       = RecordingTerminal()
    val restorationHit = Gate()
    val allowRestore   = Gate()
    var ordering       = Vector.empty[String]
    var completions    = Vector.empty[Int]
    terminal.beforeStop = () => {
      ordering :+= "restore-entered"
      restorationHit.release()
      allowRestore.await()
      ordering :+= "restored"
    }
    val tui            = TUI(terminal)
    tui.start()
    val stopper        = Thread(() => tui.stop())
    stopper.start()
    restorationHit.await()
    tui.stop()

    (1 to 64).foreach { index =>
      tui.queryTerminalBackgroundColor { result =>
        assertEquals(result, TerminalQueryResult.Stopped)
        completions :+= index
      }
    }
    assertEquals(completions, Vector.empty)
    allowRestore.release()
    stopper.join(2000)

    assert(!stopper.isAlive)
    assertEquals(ordering, Vector("restore-entered", "restored"))
    assertEquals(completions, (1 to 64).toVector)
    tui.queryTerminalColorScheme { result =>
      assertEquals(result, TerminalQueryResult.Stopped)
      completions :+= 65
    }
    assertEquals(completions, (1 to 65).toVector)

  test("ordinary work selection cycles through all five continuously ready categories"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    val events          = scala.collection.mutable.ArrayBuffer.empty[String]
    var tracking        = false
    var structuralCount = 0
    var actionCount     = 0
    var ingressCount    = 0
    var controlCount    = 0
    var renderCount     = 0
    val tui             = TUI(terminal)

    def nextStructural(): Component = new Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector.empty
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        if tracking && value.nonEmpty then
          events += "structural"
          structuralCount += 1
          if structuralCount < 6 then tui.addChild(nextStructural())

    val focusable = new Component with Focusable:
      private var current                             = false
      override def render(width: Int): Vector[String] = Vector.empty
      override def focused: Boolean                   = current
      override def focused_=(value: Boolean): Unit    =
        current = value
        if tracking && value then
          events += "action"
          actionCount += 1
          if actionCount < 6 then
            tui.setFocus(null)
            tui.setFocus(this)

    tui.addChild(new Component:
      override def render(width: Int): Vector[String] =
        if tracking then
          events += "render"
          renderCount += 1
          if renderCount < 6 then
            tui.requestRender()
            tui.requestRender(force = true)
            tui.requestRender()
        Vector("frame"))
    tui.addInputListener {
      case TerminalInput.Key(TerminalKey.Character("hold"), _)  =>
        callbackEntered.release()
        allowCallback.await()
        InputResult.NoRender
      case TerminalInput.Key(TerminalKey.Character("cycle"), _) =>
        events += "ingress"
        ingressCount += 1
        if ingressCount < 6 then
          terminal.send(TerminalInput.Key(TerminalKey.Character("cycle")))
        InputResult.NoRender
      case _                                                    => InputResult.Ignored
    }
    terminal.beforeWrite = data =>
      if tracking && data.contains("title:cycle") then
        events += "control"
        controlCount += 1
        if controlCount < 6 then tui.setTerminalTitle("cycle")

    tui.start()
    tracking = true
    val owner = Thread(() => terminal.send(TerminalInput.Key(TerminalKey.Character("hold"))))
    owner.start()
    callbackEntered.await()
    tui.addChild(nextStructural())
    tui.setFocus(focusable)
    terminal.send(TerminalInput.Key(TerminalKey.Character("cycle")))
    tui.setTerminalTitle("cycle")
    tui.requestRender()
    allowCallback.release()
    owner.join(5000)

    assert(!owner.isAlive)
    val cycle = Vector("structural", "action", "ingress", "control", "render")
    events.take(25).sliding(2).foreach {
      case Seq(previous, next) =>
        val distance = (cycle.indexOf(next) - cycle.indexOf(previous) + cycle.length) % cycle.length
        assert(distance > 0, s"ordinary selection did not advance cyclically: $previous, $next")
      case _                   => ()
    }
    assertEquals(structuralCount, 6)
    assertEquals(actionCount, 6)
    assertEquals(ingressCount, 6)
    assertEquals(controlCount, 6)
    assertEquals(renderCount, 6)
    tui.stop()

  test("full Starting ingress does not retain backend start or invoke application callbacks"):
    val startCanReturn   = Gate()
    val allowStartReturn = Gate()
    val acceptedFull     = Gate()
    val publisherDone    = Gate()
    val terminal         = new RecordingTerminal:
      override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
        super.start(onInput, onResize)
        val publisher = Thread(() => {
          (1 to 4096).foreach(index =>
            onInput(TerminalInput.Key(TerminalKey.Character(index.toString)))
          )
          acceptedFull.release()
          onInput(TerminalInput.Key(TerminalKey.Character("extra")))
          publisherDone.release()
        })
        publisher.start()
        acceptedFull.await()
        startCanReturn.release()
        allowStartReturn.await()
    var received         = Vector.empty[String]
    val tui              = TUI(terminal)
    tui.addInputListener {
      case TerminalInput.Key(TerminalKey.Character(value), _) =>
        received :+= value
        InputResult.NoRender
      case _                                                  => InputResult.Ignored
    }
    val starter          = Thread(() => tui.start())
    starter.start()
    startCanReturn.await()
    assertEquals(received, Vector.empty)
    allowStartReturn.release()
    publisherDone.await()
    starter.join(5000)

    assert(!starter.isAlive)
    assertEquals(received.length, 4097)
    assertEquals(received.head, "1")
    assertEquals(received.last, "extra")
    tui.stop()

  test("Cleaning cutoff detaches a finite set and serializes continuous late work"):
    val terminal         = RecordingTerminal()
    val restorationHit   = Gate()
    val allowRestore     = Gate()
    val detachedEntered  = Gate()
    val allowDetached    = Gate()
    val reentrantEntered = Gate()
    val allowReentrant   = Gate()
    var callbacks        = Vector.empty[String]
    var active           = 0
    var overlap          = false
    val action           = new Component with Focusable:
      private var current                             = false
      override def render(width: Int): Vector[String] = Vector.empty
      override def focused: Boolean                   = current
      override def focused_=(value: Boolean): Unit    =
        active += 1
        if active > 1 then overlap = true
        callbacks :+= "action"
        current = value
        active -= 1
    terminal.beforeStop = () => {
      restorationHit.release()
      allowRestore.await()
    }
    val tui              = TUI(terminal)
    tui.start()
    val stopper          = Thread(() => tui.stop())
    stopper.start()
    restorationHit.await()
    tui.queryTerminalBackgroundColor { _ =>
      active += 1
      if active > 1 then overlap = true
      callbacks :+= "detached"
      detachedEntered.release()
      tui.queryTerminalColorScheme { _ =>
        active += 1
        if active > 1 then overlap = true
        callbacks :+= "reentrant"
        reentrantEntered.release()
        allowReentrant.await()
        active -= 1
      }
      allowDetached.await()
      active -= 1
    }
    allowRestore.release()
    detachedEntered.await()

    val registrar = Thread(() =>
      (1 to 256).foreach { index =>
        tui.queryTerminalBackgroundColor { _ =>
          active += 1
          if active > 1 then overlap = true
          callbacks :+= s"late-$index"
          active -= 1
        }
      }
    )
    registrar.start()
    registrar.join(5000)
    assert(!registrar.isAlive)
    assertEquals(callbacks, Vector("detached"))

    allowDetached.release()
    reentrantEntered.await()
    val restart       = Thread(() => tui.start())
    val stoppedAction = Thread(() => tui.setFocus(action))
    restart.start()
    stoppedAction.start()
    tui.queryTerminalColorScheme(_ => callbacks :+= "post-commit")
    restart.join(2000)
    stoppedAction.join(2000)
    assert(!restart.isAlive)
    assert(!stoppedAction.isAlive)
    assertEquals(terminal.starts, 1)
    assertEquals(callbacks, Vector("detached", "reentrant"))

    allowReentrant.release()
    stopper.join(5000)

    assert(!stopper.isAlive)
    assert(!overlap)
    assertEquals(
      callbacks,
      Vector("detached", "reentrant") ++
        (1 to 256).map(index => s"late-$index") ++ Vector("post-commit", "action")
    )

  test("stopped focus action owns the drain against a racing start and stays non-recursive"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var depth           = 0
    var maxDepth        = 0
    var callbacks       = 0
    var tui             = Option.empty[TUI]
    val focusable       = new Component with Focusable:
      private var current                             = false
      override def render(width: Int): Vector[String] = Vector.empty
      override def focused: Boolean                   = current
      override def focused_=(value: Boolean): Unit    =
        depth += 1
        maxDepth = math.max(maxDepth, depth)
        callbacks += 1
        current = value
        if value then
          callbackEntered.release()
          tui.foreach(_.setFocus(null))
          allowCallback.await()
        depth -= 1
    val runtime         = TUI(terminal)
    tui = Some(runtime)

    val owner   = Thread(() => runtime.setFocus(focusable))
    owner.start()
    callbackEntered.await()
    val starter = Thread(() => runtime.start())
    starter.start()
    starter.join(2000)

    assert(!starter.isAlive)
    assertEquals(terminal.starts, 0)
    allowCallback.release()
    owner.join(2000)

    assert(!owner.isAlive)
    assertEquals(callbacks, 2)
    assertEquals(maxDepth, 1)

  test("queued stopped query cancellation wins before drain claim"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var callbacks       = Vector.empty[String]
    val focusable       = new Component with Focusable:
      private var current                             = false
      override def render(width: Int): Vector[String] = Vector.empty
      override def focused: Boolean                   = current
      override def focused_=(value: Boolean): Unit    =
        current = value
        callbackEntered.release()
        allowCallback.await()
    val tui             = TUI(terminal)

    val owner  = Thread(() => tui.setFocus(focusable))
    owner.start()
    callbackEntered.await()
    val cancel = tui.queryTerminalBackgroundColor(_ => callbacks :+= "cancelled")
    tui.queryTerminalColorScheme(_ => callbacks :+= "uncancelled")
    cancel()
    cancel()
    allowCallback.release()
    owner.join(2000)

    assert(!owner.isAlive)
    assertEquals(callbacks, Vector("uncancelled"))

  test("synchronous stopped query owns the drain against a racing start"):
    val terminal        = RecordingTerminal()
    val callbackEntered = Gate()
    val allowCallback   = Gate()
    var callbacks       = 0
    val tui             = TUI(terminal)

    val owner   = Thread(() =>
      tui.queryTerminalBackgroundColor { result =>
        assertEquals(result, TerminalQueryResult.Stopped)
        callbacks += 1
        callbackEntered.release()
        allowCallback.await()
      }
      ()
    )
    owner.start()
    callbackEntered.await()
    val starter = Thread(() => tui.start())
    starter.start()
    starter.join(2000)

    assert(!starter.isAlive)
    assertEquals(terminal.starts, 0)
    allowCallback.release()
    owner.join(2000)

    assert(!owner.isAlive)
    assertEquals(callbacks, 1)

  test("cleanup failure waits for detached and post-cutoff completions"):
    val terminal        = RecordingTerminal()
    val restorationHit  = Gate()
    val allowRestore    = Gate()
    val detachedEntered = Gate()
    val allowDetached   = Gate()
    var callbacks       = Vector.empty[String]
    var stopFailure     = Option.empty[Throwable]
    var failRestoration = true
    terminal.beforeStop = () => {
      restorationHit.release()
      allowRestore.await()
      if failRestoration then throw RuntimeException("restoration failed")
    }
    val tui             = TUI(terminal)
    tui.start()
    val stopper         = Thread(() =>
      try tui.stop()
      catch case error: Throwable => stopFailure = Some(error)
    )
    stopper.start()
    restorationHit.await()
    tui.queryTerminalBackgroundColor { result =>
      assertEquals(result, TerminalQueryResult.Stopped)
      callbacks :+= "detached"
      detachedEntered.release()
      allowDetached.await()
    }
    allowRestore.release()
    detachedEntered.await()

    tui.queryTerminalColorScheme { result =>
      assertEquals(result, TerminalQueryResult.Stopped)
      callbacks :+= "post-cutoff"
    }
    assertEquals(callbacks, Vector("detached"))
    assertEquals(stopFailure, None)
    allowDetached.release()
    stopper.join(2000)

    assert(!stopper.isAlive)
    assertEquals(callbacks, Vector("detached", "post-cutoff"))
    assertEquals(stopFailure.map(_.getMessage), Some("restoration failed"))
    failRestoration = false
    tui.start()
    assertEquals(terminal.starts, 2)
    tui.stop()

  test("claimed structure survives stop and queued overlay captures prior focus action"):
    val terminal      = RecordingTerminal()
    val attachEntered = Gate()
    val allowAttach   = Gate()
    var attached      = false
    val child         = new Component with ContextualComponent:
      override def render(width: Int): Vector[String]            = Vector("child")
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        if value.nonEmpty then
          attached = true
          attachEntered.release()
          allowAttach.await()
    val first         = new Component with Focusable:
      var focused                                     = false
      override def render(width: Int): Vector[String] = Vector("first")
    val second        = new Component with Focusable:
      var focused                                     = false
      override def render(width: Int): Vector[String] = Vector("second")
    val overlay       = new Component with Focusable:
      var focused                                     = false
      override def render(width: Int): Vector[String] = Vector("overlay")
    val tui           = TUI(terminal)
    tui.addChild(first)
    tui.addChild(second)
    tui.setFocus(first)
    tui.start()

    val publisher = Thread(() => tui.addChild(child))
    publisher.start()
    attachEntered.await()
    tui.stop()
    assertEquals(tui.children, Vector(first, second, child))
    allowAttach.release()
    publisher.join(5000)
    assert(attached)

    val secondTerminal = RecordingTerminal()
    val secondTui      = TUI(secondTerminal)
    val holdEntered    = Gate()
    val allowHold      = Gate()
    secondTui.addChild(first)
    secondTui.addChild(second)
    secondTui.setFocus(first)
    secondTui.addInputListener { _ =>
      holdEntered.release()
      allowHold.await()
      InputResult.NoRender
    }
    secondTui.start()
    val owner          = Thread(() =>
      secondTerminal.send(TerminalInput.Key(TerminalKey.Character("hold")))
    )
    owner.start()
    holdEntered.await()
    secondTui.setFocus(second)
    val handle         = secondTui.showOverlay(overlay)
    allowHold.release()
    owner.join(5000)
    handle.hide()

    assert(second.focused)
    assert(!first.focused)
    secondTui.stop()

  test("startup, uncontended, reentrant, and concurrent stop have one cleanup owner"):
    val startupTerminal = RecordingTerminal()
    val startupEntered  = Gate()
    val allowStartup    = Gate()
    startupTerminal.beforeStart = () => {
      startupEntered.release()
      allowStartup.await()
    }
    val startupTui      = TUI(startupTerminal)
    startupTui.addChild(new Component:
      override def render(width: Int): Vector[String] = Vector("must-not-render"))
    val starter         = Thread(() => startupTui.start())
    starter.start()
    startupEntered.await()
    val startupStopped  = Gate()
    val startupStopper  = Thread(() => {
      startupTui.stop()
      startupStopped.release()
    })
    startupStopper.start()
    startupStopped.await()
    allowStartup.release()
    starter.join(2000)
    startupStopper.join(2000)
    assert(!starter.isAlive)
    assert(!startupTerminal.output.contains("must-not-render"))
    assertEquals(startupTerminal.stops, 1)
    startupTui.stop()
    assertEquals(startupTerminal.stops, 1)

    val uncontendedTerminal = RecordingTerminal()
    val uncontendedTui      = TUI(uncontendedTerminal)
    uncontendedTui.addChild(new Component:
      override def render(width: Int): Vector[String] = Vector("line"))
    uncontendedTui.start()
    uncontendedTui.stop()
    assertEquals(uncontendedTerminal.stops, 1)
    assert(uncontendedTerminal.output.endsWith("show"))

    val reentrantTerminal = RecordingTerminal()
    val reentrantTui      = TUI(reentrantTerminal)
    var returnedInHook    = false
    reentrantTui.addInputListener { _ =>
      reentrantTui.stop()
      returnedInHook = true
      InputResult.NoRender
    }
    reentrantTui.start()
    reentrantTerminal.send(TerminalInput.Key(TerminalKey.Character("x")))
    assert(returnedInHook)
    assertEquals(reentrantTerminal.stops, 1)
    reentrantTui.stop()
    assertEquals(reentrantTerminal.stops, 1)

    val concurrentTerminal = RecordingTerminal()
    val callbackEntered    = Gate()
    val allowCallback      = Gate()
    val concurrentTui      = TUI(concurrentTerminal)
    concurrentTui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    concurrentTui.start()
    val inputOwner         = Thread(() =>
      concurrentTerminal.send(TerminalInput.Key(TerminalKey.Character("y")))
    )
    inputOwner.start()
    callbackEntered.await()
    concurrentTui.stop()
    assertEquals(concurrentTerminal.stops, 0)
    allowCallback.release()
    inputOwner.join(2000)
    assert(!inputOwner.isAlive)
    assertEquals(concurrentTerminal.stops, 1)

  test("run waits for deferred final cleanup before returning"):
    val terminal                 = RecordingTerminal()
    val terminalStarted          = Gate()
    terminal.beforeStart = () => terminalStarted.release()
    val callbackEntered          = Gate()
    val allowCallback            = Gate()
    val tui                      = TUI(terminal)
    tui.addInputListener { _ =>
      callbackEntered.release()
      allowCallback.await()
      InputResult.NoRender
    }
    val runner                   = Thread(() => tui.run())
    runner.start()
    terminalStarted.await()
    val inputOwner               = Thread(() =>
      terminal.send(TerminalInput.Key(TerminalKey.Character("q")))
    )
    inputOwner.start()
    callbackEntered.await()
    tui.requestExit()
    val runReturnedBeforeCleanup = Gate()
    val observer                 = Thread(() => {
      runner.join()
      runReturnedBeforeCleanup.release()
    })
    observer.start()
    assertEquals(terminal.stops, 0)
    allowCallback.release()
    inputOwner.join(2000)
    runner.join(2000)
    observer.join(2000)
    assert(!runner.isAlive)
    assertEquals(terminal.stops, 1)

  test("startup, frame, protocol, control, cursor, and cleanup output stays ordered"):
    val terminal    = RecordingTerminal()
    terminal.beforeStart = () => terminal.write("startup")
    val tui         = TUI(
      terminal,
      TUIOptions(hardwareCursorPositioning = true)
    )
    tui.addChild(new Component:
      override def render(width: Int): Vector[String] = Vector(s"frame${CursorMarker.Sequence}"))
    tui.start()
    val cancelQuery = tui.queryTerminalBackgroundColor(_ => ())
    assert(tui.setTerminalTitle("ordered-title"))
    assert(tui.setTerminalProgress(active = true))
    cancelQuery()
    tui.stop()

    val writes                              = terminal.writes
    def indexContaining(value: String): Int = writes.indexWhere(_.contains(value))
    val startupIndex                        = indexContaining("startup")
    val hideIndex                           = indexContaining("hide")
    val startupProtocolIndex                = indexContaining(TerminalImageProtocol.QueryCellDimensions)
    val frameIndex                          = indexContaining("frame")
    val queryIndex                          = indexContaining(TerminalColorProtocol.BackgroundColorQuery)
    val titleIndex                          = indexContaining("title:ordered-title")
    val progressIndex                       = indexContaining("progress:true")
    val showIndex                           = writes.lastIndexWhere(_.contains("show"))
    assert(startupIndex >= 0)
    assert(hideIndex > startupIndex)
    assert(startupProtocolIndex > hideIndex)
    assert(frameIndex > startupProtocolIndex)
    assert(queryIndex > frameIndex)
    assert(titleIndex > queryIndex)
    assert(progressIndex > titleIndex)
    assert(showIndex > progressIndex)
    assertEquals(terminal.stops, 1)
    assert(!terminal.hadConcurrentWrite)

  test("accepted title and progress execute before a later concurrent stop cleanup"):
    val terminal = RecordingTerminal()
    val tui      = TUI(terminal)
    val entered  = Gate()
    val release  = Gate()
    var block    = false
    tui.addChild(new Component:
      override def render(width: Int): Vector[String] =
        if block then
          entered.release()
          release.await()
        Vector("frame"))
    tui.start()
    block = true
    val owner    = Thread(() => {
      tui.requestRender(force = true)
      tui.flushRender()
    })
    owner.start()
    entered.await()
    assert(tui.setTerminalTitle("retained"))
    assert(tui.setTerminalProgress(active = true))
    tui.stop()
    assertEquals(terminal.stops, 0)
    release.release()
    owner.join(2000)
    assert(!owner.isAlive)
    val writes   = terminal.writes
    assert(
      writes.indexWhere(_.contains("title:retained")) < writes.lastIndexWhere(_.contains("show"))
    )
    assert(
      writes.indexWhere(_.contains("progress:true")) < writes.lastIndexWhere(_.contains("show"))
    )
    assertEquals(terminal.stops, 1)
    assert(!terminal.hadConcurrentWrite)
