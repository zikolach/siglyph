package scalatui.demo

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.scheduling.*
import scalatui.terminal.{KeyModifiers, Terminal, TerminalInput, TerminalKey, VirtualTerminal}
import scalatui.testing.TUITestDriver

class LiveLogExplorerSuite extends munit.FunSuite:
  test("live batches preserve bounded retention, selection, pause and immediate cancellation"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(90, 16, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal, capacity = 50)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      try
        val scope   = demo.startStreaming(clock, intervalMillis = 10, batchSize = 5).get
        driver.assertContains("live")
        driver.key(TerminalKey.Up)
        assertEquals(demo.model.snapshot.selected.map(_.id), Some(39L))
        driver.advanceTimeBy(30)
        assertEquals(demo.model.snapshot.generated, 55L)
        assertEquals(demo.model.snapshot.retained, 50)
        assertEquals(demo.model.snapshot.evicted, 5L)
        assertEquals(demo.model.snapshot.selected.map(_.id), Some(39L))
        val indexed = demo.table.data
        driver.key(TerminalKey.Character("p"), KeyModifiers(ctrl = true))
        driver.advanceTimeBy(20)
        assertEquals(demo.model.snapshot.generated, 55L)
        assert(demo.table.data eq indexed)
        driver.key(TerminalKey.Character("p"), KeyModifiers(ctrl = true))
        driver.advanceTimeBy(10)
        assertEquals(demo.model.snapshot.generated, 60L)
        driver.paste("ERROR", chunkBytes = 1)
        driver.key(TerminalKey.Enter)
        assertEquals(demo.model.snapshot.rows.map(_.id), Vector(17L, 34L, 51L))
        driver.key(TerminalKey.Character("x"), KeyModifiers(ctrl = true))
        assert(scope.isQuiescent)
        driver.assertNoPendingTasks()
        driver.advanceTimeBy(100)
        assertEquals(demo.model.snapshot.generated, 60L)
        driver.assertContains("cancelled")
      finally demo.cancelSource()
    }

  test("automatic batches preserve a browsed viewport anchor through eviction"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(90, 16, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal, capacity = 50)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      try
        demo.startStreaming(clock, 10, 5)
        demo.viewport.scrollBy(-5)
        val anchor = demo.table.data.rows(demo.viewport.offset - 1).id
        driver.advanceTimeBy(30)
        assert(!demo.model.snapshot.following)
        assertEquals(demo.table.data.rows(demo.viewport.offset - 1).id, anchor)
        driver.key(TerminalKey.Character("g"), KeyModifiers(ctrl = true))
        assert(demo.model.snapshot.following)
        driver.advanceTimeBy(10)
        assertEquals(demo.model.snapshot.selected.map(_.id), Some(60L))
      finally demo.cancelSource()
    }

  test("streaming setup validates arguments and prevents duplicate or cancelled sources"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(80, 12, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { _ =>
      val demo = application.get
      try
        intercept[IllegalArgumentException](demo.startStreaming(clock, 0, 20))
        intercept[IllegalArgumentException](demo.startStreaming(clock, 10, 1001))
        assert(demo.startStreaming(clock, 10, 20).nonEmpty)
        intercept[IllegalArgumentException](demo.startStreaming(clock, 10, 20))
        demo.cancelSource()
        assertEquals(demo.startStreaming(clock, 10, 20), None)
      finally demo.cancelSource()
    }

  test("cancellation before setup prevents all scheduled work"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(80, 12, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      driver.key(TerminalKey.Character("x"), KeyModifiers(ctrl = true))
      assertEquals(demo.startStreaming(clock), None)
      driver.advanceTimeBy(1000)
      assertEquals(demo.model.snapshot.generated, 40L)
      driver.assertNoPendingTasks()
    }

  test("Escape before setup prevents a live source from being installed"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(80, 12, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { driver =>
      driver.key(TerminalKey.Escape)
      assertEquals(application.get.startStreaming(clock), None)
      driver.assertNoPendingTasks()
    }

  test("overloaded live delivery stops its timer and reports status"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(90, 16, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      try
        val scope = demo.startStreaming(clock, 10, 5).get
        scope.submit {
          (0 until 256).foreach(_ => scope.submit { () })
          clock.advanceBy(10)
        }
        driver.flush()
        assertEquals(demo.model.snapshot.generated, 40L)
        assertEquals(demo.sourceStatus, "overloaded")
        driver.assertContains("overloaded")
        driver.assertNoPendingTasks()
      finally demo.cancelSource()
    }

  test("cancel during provider registration does not turn an early Ctrl+X into a runtime failure"):
    val entered     = new CountDownLatch(1)
    val release     = new CountDownLatch(1)
    val failure     = new AtomicReference[Throwable]()
    var cancelled   = 0
    var late        = Option.empty[() => Unit]
    val provider    = new TaskScheduler:
      override def nowMillis: Long                                         = 0L
      override def schedule(delay: Long)(callback: => Unit): ScheduledTask =
        late = Some(() => callback)
        entered.countDown()
        if !release.await(5L, TimeUnit.SECONDS) then
          throw new IllegalStateException("registration blocked")
        new ScheduledTask:
          override def cancel(): Boolean = { cancelled += 1; true }
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(80, 12)(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo         = application.get
      val registration = new Thread(() =>
        try demo.startStreaming(provider)
        catch case error: Throwable => failure.set(error)
        ()
      )
      registration.setDaemon(true)
      registration.start()
      try
        val started = entered.await(5L, TimeUnit.SECONDS)
        assert(started)
        driver.key(TerminalKey.Character("x"), KeyModifiers(ctrl = true))
        driver.assertContains("cancelled")
      finally
        release.countDown()
        registration.join(5000L)
        demo.cancelSource()
      assert(!registration.isAlive)
      assertEquals(Option(failure.get()), None)
      assertEquals(cancelled, 1)
      late.foreach(_())
      assertEquals(demo.model.snapshot.generated, 40L)
      driver.flush()
    }

  private final class CountingTerminal(
      delegate: VirtualTerminal,
      startFailure: Option[Throwable] = None
  ) extends Terminal:
    var stops                                                                  = 0
    override def start(input: TerminalInput => Unit, resize: () => Unit): Unit =
      startFailure.foreach(throw _)
      delegate.start(input, resize)
    override def stop(): Unit                                                  = { stops += 1; delegate.stop() }
    override def write(value: String): Unit                                    = delegate.write(value)
    override def columns: Int                                                  = delegate.columns
    override def rows: Int                                                     = delegate.rows
    override def moveBy(lines: Int): Unit                                      = delegate.moveBy(lines)
    override def hideCursor(): Unit                                            = delegate.hideCursor()
    override def showCursor(): Unit                                            = delegate.showCursor()
    override def clearLine(): Unit                                             = delegate.clearLine()
    override def clearFromCursor(): Unit                                       = delegate.clearFromCursor()
    override def clearScreen(): Unit                                           = delegate.clearScreen()

  test("registration failure restores terminal and preserves independent producer cleanup failure"):
    val terminal            = new CountingTerminal(VirtualTerminal(80, 12))
    val demo                = LogExplorerDemo(terminal)
    val registrationFailure = new IllegalStateException("registration failed")
    val cleanupFailure      = new IllegalArgumentException("producer cleanup failed")
    var cleaned             = false
    val provider            = new TaskScheduler:
      override def nowMillis: Long                                         = 0L
      override def schedule(delay: Long)(callback: => Unit): ScheduledTask =
        throw registrationFailure
    val caught              = intercept[IllegalStateException] {
      demo.runScheduled(provider, () => { cleaned = true; throw cleanupFailure })
    }
    assert(caught eq registrationFailure)
    assert(caught.getSuppressed.exists(_ eq cleanupFailure))
    assert(cleaned)
    assertEquals(terminal.stops, 1)
    assert(demo.model.snapshot.cancelled)

  test("startup failure still releases the producer and cancels source state"):
    val failure  = new IllegalStateException("terminal startup failed")
    val terminal = new CountingTerminal(VirtualTerminal(80, 12), Some(failure))
    val demo     = LogExplorerDemo(terminal)
    val clock    = ManualTaskScheduler()
    val caught   = intercept[IllegalStateException](demo.runScheduled(clock, () => clock.close()))
    assert(caught eq failure)
    assert(clock.isClosed)
    assert(demo.model.snapshot.cancelled)
    assertEquals(terminal.stops, 1)

  test("runtime stop owns the timer even when the model wrapper is not explicitly stopped"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(80, 12, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      try
        val scope = demo.startStreaming(clock, 10, 20).get
        driver.advanceTimeBy(10)
        driver.tui.stop()
        assert(scope.isQuiescent)
        assertEquals(clock.pendingCount, 0)
        assertEquals(demo.model.snapshot.generated, 60L)
      finally demo.cancelSource()
    }
