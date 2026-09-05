package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  RgbColor,
  StreamTerminal,
  Terminal,
  TerminalInput,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalKey
}

import java.io.{ByteArrayOutputStream, InputStream}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class TUITerminalFailureSuite extends munit.FunSuite:
  private final class LegacyTerminal extends Terminal:
    var starts = 0

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = starts += 1
    override def stop(): Unit                                                      = ()
    override def write(data: String): Unit                                         = ()
    override def columns: Int                                                      = 20
    override def rows: Int                                                         = 5
    override def moveBy(lines: Int): Unit                                          = ()
    override def hideCursor(): Unit                                                = ()
    override def showCursor(): Unit                                                = ()
    override def clearLine(): Unit                                                 = ()
    override def clearFromCursor(): Unit                                           = ()
    override def clearScreen(): Unit                                               = ()

  private final class FailureTerminal extends Terminal:
    @volatile private var inputHandler: TerminalInput => Unit = _ => ()
    @volatile private var failureHandler: Throwable => Unit   = _ => ()
    val stops                                                 = AtomicInteger(0)
    val shows                                                 = AtomicInteger(0)
    val callbackThreads                                       = new java.util.concurrent.ConcurrentLinkedQueue[Thread]()
    val stopThread                                            = AtomicReference[Thread]()
    var beforeStop                                            = () => ()
    var stopFailure                                           = Option.empty[Throwable]

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      start(onInput, onResize, _ => ())

    override def start(
        onInput: TerminalInput => Unit,
        onResize: () => Unit,
        onFailure: Throwable => Unit
    ): Unit =
      inputHandler = onInput
      failureHandler = error => {
        callbackThreads.add(Thread.currentThread())
        onFailure(error)
      }

    override def stop(): Unit              =
      stopThread.set(Thread.currentThread())
      stops.incrementAndGet()
      beforeStop()
      stopFailure.foreach(throw _)
    override def write(data: String): Unit = ()
    override def columns: Int              = 20
    override def rows: Int                 = 5
    override def moveBy(lines: Int): Unit  = ()
    override def hideCursor(): Unit        = ()
    override def showCursor(): Unit        = shows.incrementAndGet()
    override def clearLine(): Unit         = ()
    override def clearFromCursor(): Unit   = ()
    override def clearScreen(): Unit       = ()
    def send(input: TerminalInput): Unit   = inputHandler(input)
    def fail(error: Throwable): Unit       = failureHandler(error)

  private def parseInput(value: String): Vector[TerminalInput] =
    val buffer = TerminalInputBuffer()
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).grouped(TerminalInputChunk.MaxBytes)
      .flatMap(bytes => buffer.process(TerminalInputChunk(bytes))).toVector ++ buffer.flush()

  test("three-callback start keeps old-only terminal implementations source compatible"):
    val terminal = LegacyTerminal()
    var failures = 0

    terminal.start(_ => (), () => (), _ => failures += 1)

    assertEquals(terminal.starts, 1)
    assertEquals(failures, 0)

  test("backend failure bypasses ingress, retains completions, restores, and rethrows first"):
    val terminal        = FailureTerminal()
    val tui             = TUI(terminal)
    val callbackEntered = CountDownLatch(1)
    val releaseCallback = CountDownLatch(1)
    val ordinaryCalls   = AtomicInteger(0)
    val queryResult     = AtomicReference[Option[TerminalQueryResult[RgbColor]]](None)
    val runFailure      = AtomicReference[Throwable]()
    val primary         = RuntimeException("input worker failed")
    val independent     = RuntimeException("resize worker failed")
    val cleanup         = RuntimeException("terminal restoration failed")
    terminal.stopFailure = Some(cleanup)
    tui.addInputListener {
      case TerminalInput.Key(TerminalKey.Character("hold"), _) =>
        callbackEntered.countDown()
        assert(releaseCallback.await(5, TimeUnit.SECONDS))
        InputResult.NoRender
      case _                                                   =>
        ordinaryCalls.incrementAndGet()
        InputResult.NoRender
    }
    tui.start()
    tui.queryTerminalBackgroundColor(result => queryResult.set(Some(result)))
    val runner          = Thread(() =>
      try tui.run()
      catch case error: Throwable => runFailure.set(error)
    )
    val owner           = Thread(() =>
      terminal.send(TerminalInput.Key(TerminalKey.Character("hold")))
    )
    runner.start()
    owner.start()
    assert(callbackEntered.await(5, TimeUnit.SECONDS))
    terminal.send(TerminalInput.Key(TerminalKey.Character("discarded")))
    parseInput("\u001b]11;#112233\u0007").foreach(terminal.send)

    val primaryReporter = Thread.currentThread()
    terminal.fail(primary)
    releaseCallback.countDown()
    owner.join(5000)
    runner.join(5000)
    val secondReporter  = Thread(() => terminal.fail(independent), "second-terminal-failure")
    secondReporter.start()
    secondReporter.join(5000)

    assert(!owner.isAlive)
    assert(!runner.isAlive, "run did not wake after backend failure")
    assert(!secondReporter.isAlive)
    assert(runFailure.get() eq primary)
    assertEquals(
      primary.getSuppressed.toVector,
      Vector(cleanup, independent)
    )
    assertEquals(queryResult.get(), Some(TerminalQueryResult.Success(RgbColor(17, 34, 51))))
    assertEquals(ordinaryCalls.get(), 0)
    assertEquals(terminal.stops.get(), 1)
    assertEquals(terminal.shows.get(), 1)
    assertEquals(
      terminal.callbackThreads.toArray.toVector,
      Vector(primaryReporter, secondReporter)
    )

  test("concurrent backend failure is suppressed while the first reporter owns cleanup"):
    val terminal       = FailureTerminal()
    val tui            = TUI(terminal)
    val stopEntered    = CountDownLatch(1)
    val allowStop      = CountDownLatch(1)
    val primary        = RuntimeException("reader failed")
    val independent    = RuntimeException("resize failed")
    terminal.beforeStop = () => {
      stopEntered.countDown()
      allowStop.await()
    }
    tui.start()
    val firstReporter  = Thread(() => terminal.fail(primary), "reader-failure")
    val secondReporter = Thread(() => terminal.fail(independent), "resize-failure")

    firstReporter.start()
    assert(stopEntered.await(5, TimeUnit.SECONDS))
    secondReporter.start()
    secondReporter.join(5000)
    allowStop.countDown()
    firstReporter.join(5000)

    assert(!firstReporter.isAlive)
    assert(!secondReporter.isAlive)
    assertEquals(primary.getSuppressed.toVector, Vector(independent))
    assertEquals(terminal.stops.get(), 1)
    assert(terminal.stopThread.get() eq firstReporter)

  test("StreamTerminal worker failure can clean up from its reporting thread"):
    val failure  = RuntimeException("stream reader failed")
    val input    = new InputStream:
      override def read(): Int = throw failure
    val output   = ByteArrayOutputStream()
    val terminal = StreamTerminal(input = input, output = output)
    val tui      = TUI(terminal)

    val reported = intercept[RuntimeException](tui.run())

    assert(reported eq failure)
    assert(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("\u001b[?25h"))
