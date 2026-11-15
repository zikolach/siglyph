package scalatui.core

import java.util.concurrent.{CountDownLatch, ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.components.{Loader, Text}
import scalatui.scheduling.*
import scalatui.terminal.{Terminal, TerminalInput, VirtualTerminal}
import scalatui.testing.TUITestDriver

class ScopedTimerSuite extends munit.FunSuite:
  private def fixture(test: (TUITestDriver, ManualTaskScheduler) => Unit): Unit =
    val clock = ManualTaskScheduler()
    TUITestDriver.withSession(40, 3, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, Text("ready", paddingX = 0))
    )(driver => test(driver, clock))

  test("one-shot delivery retires its registration and requests rendering"):
    val clock = ManualTaskScheduler()
    val text  = Text("before", paddingX = 0)
    TUITestDriver.withSession(40, 3, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, text)
    ) { driver =>
      val scope = driver.tui.openApplicationScope()
      val task  = scope.schedule(clock, 10) { text.text = "after" }
      assertEquals(scope.scheduledCount, 1)
      driver.advanceTimeBy(9)
      driver.assertContains("before")
      driver.advanceTimeBy(1)
      driver.assertContains("after")
      assert(task.isDone)
      assert(!task.isCancelled)
      assertEquals(task.lastSubmission, Some(ApplicationSubmission.Accepted))
      assertEquals(scope.scheduledCount, 0)
    }

  test("cancelling a claimed or completed one-shot does not relabel it as cancelled"):
    fixture { (driver, clock) =>
      val scope  = driver.tui.openApplicationScope()
      var handle = Option.empty[ScopedTask]
      handle = Some(scope.schedule(clock, 0) {
        assert(!handle.get.cancel())
        assert(!handle.get.isCancelled)
      })
      driver.advanceTimeBy(0)
      assert(handle.get.isDone)
      assert(!handle.get.cancel())
      assert(!handle.get.isCancelled)
    }

  test("fixed-delay recurrence stops safely through its callback handle"):
    fixture { (driver, clock) =>
      val scope = driver.tui.openApplicationScope()
      var times = Vector.empty[Long]
      val task  = scope.scheduleEvery(clock, 10, 20) { current =>
        times :+= clock.nowMillis
        if times.size >= 3 then assert(current.cancel())
      }
      driver.advanceTimeTo(100)
      assertEquals(times, Vector(10L, 30L, 50L))
      assert(task.isCancelled)
      assert(task.isDone)
      assert(!task.cancel())
      driver.assertNoPendingTasks()
    }

  test("recurrence waits for serialized delivery instead of accumulating catch-up ticks"):
    fixture { (driver, clock) =>
      val scope = driver.tui.openApplicationScope()
      var times = Vector.empty[Long]
      scope.submit {
        scope.scheduleEvery(clock, 0, 10) { task =>
          times :+= clock.nowMillis
          if times.size >= 2 then task.cancel()
        }
        clock.advanceTo(100)
        assert(times.isEmpty)
        assertEquals(clock.pendingCount, 0)
      }
      assertEquals(times, Vector(100L))
      assertEquals(clock.nextDeadlineMillis, Some(110L))
      driver.advanceTimeTo(110)
      assertEquals(times, Vector(100L, 110L))
      driver.assertNoPendingTasks()
    }

  test("TUI teardown cancels recurring work before owned-clock leak checks"):
    val clock = ManualTaskScheduler()
    var task  = Option.empty[ScopedTask]
    TUITestDriver.withSession(40, 3, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, Text("ready"))
    ) { driver =>
      task = Some(driver.tui.openApplicationScope().scheduleEvery(clock, 10, 10)(_ => ()))
      driver.advanceTimeTo(25)
      assertEquals(clock.pendingCount, 1)
    }
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)
    assert(task.get.isDone)
    assert(task.get.isCancelled)

  test("loader ticking can be scope-owned without changing its manual tick contract"):
    val clock  = ManualTaskScheduler()
    val loader = Loader()
    loader.start()
    TUITestDriver.withSession(40, 3, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, loader)
    ) { driver =>
      val scope = driver.tui.openApplicationScope()
      var ticks = 0
      scope.scheduleEvery(clock, 0, loader.intervalMs.toLong) { task =>
        loader.tick()
        ticks += 1
        if ticks >= 3 then task.cancel()
      }
      driver.advanceTimeTo(loader.intervalMs.toLong * 3L - 1L)
      assertEquals(ticks, 3)
      assertEquals(loader.frameIndex, 3)
      driver.assertNoPendingTasks()
    }

  test("closing one scope does not close a shared scheduler or another scope's timers"):
    fixture { (driver, clock) =>
      val first  = driver.tui.openApplicationScope()
      val second = driver.tui.openApplicationScope()
      var values = Vector.empty[Int]
      first.schedule(clock, 10) { values :+= 1 }
      second.schedule(clock, 10) { values :+= 2 }
      first.close()
      assert(first.isQuiescent)
      assert(!clock.isClosed)
      assertEquals(clock.pendingCount, 1)
      driver.advanceTimeTo(10)
      assertEquals(values, Vector(2))
    }

  test("timer admission is bounded and cancellation releases slots"):
    fixture { (driver, clock) =>
      val scope = driver.tui.openApplicationScope(maxScheduled = 1)
      val task  = scope.schedule(clock, 10) { () }
      assertEquals(
        intercept[SchedulingRejectedException](scope.schedule(clock, 20) { () }).reason,
        SchedulingRejection.Capacity
      )
      assert(task.cancel())
      assertEquals(scope.scheduledCount, 0)
      scope.schedule(clock, 20) { () }
      scope.close()
      assertEquals(
        intercept[SchedulingRejectedException](scope.schedule(clock, 0) { () }).reason,
        SchedulingRejection.Closed
      )
      intercept[IllegalArgumentException](driver.tui.openApplicationScope(maxScheduled = 0))
    }

  test("provider-fired work remains cancellable before UI claim"):
    fixture { (driver, clock) =>
      val scope = driver.tui.openApplicationScope()
      var ran   = false
      val task  = scope.schedule(clock, 0) { ran = true }
      scope.submit {
        clock.advanceBy(0)
        assertEquals(scope.pendingCount, 1)
        assert(task.cancel())
      }
      driver.flush()
      assert(!ran)
      assert(task.isDone)
    }

  test("overloaded delivery stops recurrence and exposes Full rather than silently retrying"):
    fixture { (driver, clock) =>
      val scope = driver.tui.openApplicationScope(maxPending = 1)
      var ran   = false
      val task  = scope.scheduleEvery(clock, 0, 10)(_ => ran = true)
      scope.submit {
        scope.submit { () }
        clock.advanceBy(0)
      }
      assert(!ran)
      assert(task.isDone)
      assertEquals(task.lastSubmission, Some(ApplicationSubmission.Full))
      assertEquals(clock.pendingCount, 0)
    }

  test("initial provider failure releases admission without failing the TUI"):
    fixture { (driver, clock) =>
      val scope    = driver.tui.openApplicationScope(maxScheduled = 1)
      val failure  = new IllegalStateException("register failed")
      val rejected = new TaskScheduler:
        override def nowMillis: Long                                         = 0L
        override def schedule(delay: Long)(callback: => Unit): ScheduledTask = throw failure
      assert(intercept[IllegalStateException](scope.schedule(rejected, 0) { () }) eq failure)
      assertEquals(scope.scheduledCount, 0)
      assert(!scope.isClosed)
      scope.schedule(clock, 0) { () }
      driver.advanceTimeBy(0)
    }

  test("callback and rearm failures stop recurrence through the runtime failure channel"):
    fixture { (driver, clock) =>
      val scope         = driver.tui.openApplicationScope()
      val failure       = new IllegalStateException("rearm failed")
      var registrations = 0
      val provider      = new TaskScheduler:
        override def nowMillis: Long                                         = clock.nowMillis
        override def schedule(delay: Long)(callback: => Unit): ScheduledTask =
          registrations += 1
          if registrations > 1 then throw failure
          clock.schedule(delay)(callback)
      val task          = scope.scheduleEvery(provider, 0, 1)(_ => ())
      val caught        = intercept[IllegalStateException](driver.advanceTimeBy(0))
      assert(caught eq failure)
      assert(task.failure.exists(_ eq failure))
      assert(task.isDone)
      assert(scope.isClosed)
    }

  test("user callback failure is preserved and cancels other scope-owned deadlines"):
    fixture { (driver, clock) =>
      val scope   = driver.tui.openApplicationScope()
      val failure = new IllegalStateException("tick failed")
      val later   = scope.schedule(clock, 100) { fail("late callback ran") }
      val task    = scope.scheduleEvery(clock, 0, 1)(_ => throw failure)
      assert(intercept[IllegalStateException](driver.advanceTimeBy(0)) eq failure)
      assert(task.failure.exists(_ eq failure))
      assert(later.isDone)
      assertEquals(clock.pendingCount, 0)
    }

  private final class CountingTerminal(delegate: VirtualTerminal) extends Terminal:
    var stops                                                                  = 0
    override def start(input: TerminalInput => Unit, resize: () => Unit): Unit =
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

  test(
    "timer cancellation failure cannot skip terminal restoration and stop retries only obligations"
  ):
    val terminal    = new CountingTerminal(VirtualTerminal(40, 3))
    val tui         = TUI.fullscreen(terminal, Text("ready"))
    val failure     = new IllegalStateException("cancel failed")
    var failCancel  = true
    var cancelCalls = 0
    val scheduler   = new TaskScheduler:
      override def nowMillis: Long                                         = 0L
      override def schedule(delay: Long)(callback: => Unit): ScheduledTask = new ScheduledTask:
        override def cancel(): Boolean =
          cancelCalls += 1
          if failCancel then throw failure
          true
    tui.start()
    val scope       = tui.openApplicationScope()
    val task        = scope.schedule(scheduler, 100) { () }
    try
      assert(intercept[IllegalStateException](tui.stop()) eq failure)
      assertEquals(terminal.stops, 1)
      assert(!scope.isQuiescent)
      assert(task.failure.exists(_ eq failure))
      intercept[IllegalStateException](tui.start())
      failCancel = false
      tui.stop()
      assertEquals(cancelCalls, 2)
      assertEquals(terminal.stops, 1)
      assert(scope.isQuiescent)
      tui.start()
      assertEquals(scope.submit { fail("stale") }, ApplicationSubmission.Closed)
    finally
      failCancel = false
      tui.stop()

  test("fire-before-publication is deferred and cannot deliver after stop"):
    val terminal    = new CountingTerminal(VirtualTerminal(40, 3))
    val tui         = TUI.fullscreen(terminal, Text("ready"))
    val fired       = new CountDownLatch(1)
    val publish     = new CountDownLatch(1)
    val error       = new AtomicReference[Throwable]()
    var cancelled   = false
    var ran         = false
    val provider    = new TaskScheduler:
      override def nowMillis: Long                                         = 0L
      override def schedule(delay: Long)(callback: => Unit): ScheduledTask =
        val worker = new Thread(() => { callback; fired.countDown() })
        worker.start()
        worker.join(5000L)
        if !publish.await(5L, TimeUnit.SECONDS) then
          throw new IllegalStateException("publication blocked")
        new ScheduledTask:
          override def cancel(): Boolean = { cancelled = true; true }
    tui.start()
    val scope       = tui.openApplicationScope()
    val registering = new Thread(() =>
      try scope.schedule(provider, 0) { ran = true }
      catch case failure: Throwable => error.set(failure)
      ()
    )
    registering.setDaemon(true)
    registering.start()
    try
      val arrived = fired.await(5L, TimeUnit.SECONDS)
      assert(arrived)
      assert(!ran)
      intercept[ScheduledCleanupPendingException](tui.stop())
      assertEquals(terminal.stops, 1)
      intercept[IllegalStateException](tui.start())
    finally
      publish.countDown()
      registering.join(5000L)
      tui.stop()
    assert(!registering.isAlive)
    assertEquals(Option(error.get()), None)
    assert(!cancelled) // Already-fired provider work cannot be cancelled; delivery was disarmed.
    assert(!ran)
    assert(scope.isQuiescent)
    assertEquals(terminal.stops, 1)

  test("stop cancels an unclaimed late-published registration without repeating backend stop"):
    val terminal     = new CountingTerminal(VirtualTerminal(40, 3))
    val tui          = TUI.fullscreen(terminal, Text("ready"))
    val entered      = new CountDownLatch(1)
    val release      = new CountDownLatch(1)
    val error        = new AtomicReference[Throwable]()
    var cancelCalls  = 0
    var lateCallback = Option.empty[() => Unit]
    var ran          = false
    val provider     = new TaskScheduler:
      override def nowMillis: Long                                         = 0L
      override def schedule(delay: Long)(callback: => Unit): ScheduledTask =
        lateCallback = Some(() => callback)
        entered.countDown()
        if !release.await(5L, TimeUnit.SECONDS) then
          throw new IllegalStateException("registration blocked")
        new ScheduledTask:
          override def cancel(): Boolean = { cancelCalls += 1; true }
    tui.start()
    val scope        = tui.openApplicationScope()
    val registration = new Thread(() =>
      try scope.schedule(provider, 10) { ran = true }
      catch case failure: Throwable => error.set(failure)
      ()
    )
    registration.setDaemon(true)
    registration.start()
    try
      val started = entered.await(5L, TimeUnit.SECONDS)
      assert(started)
      intercept[ScheduledCleanupPendingException](tui.stop())
      assertEquals(terminal.stops, 1)
    finally
      release.countDown()
      registration.join(5000L)
      tui.stop()
    assert(!registration.isAlive)
    assertEquals(Option(error.get()), None)
    assertEquals(cancelCalls, 1)
    lateCallback.foreach(_())
    assert(!ran)
    assert(scope.isQuiescent)
    assertEquals(terminal.stops, 1)

  test("real executor recurring task can self-cancel through its supplied handle"):
    val executor  = new ScheduledThreadPoolExecutor(1)
    val failures  = new AtomicReference[Throwable]()
    val scheduler = ExecutorTaskScheduler(executor, failure => failures.set(failure))
    val done      = new CountDownLatch(1)
    try
      TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, Text("ready"))) {
        driver =>
          try
            val scope     = driver.tui.openApplicationScope()
            scope.scheduleEvery(scheduler, 0, 1) { task =>
              task.cancel()
              done.countDown()
            }
            val completed = done.await(5L, TimeUnit.SECONDS)
            assert(completed)
            scope.close()
            assert(scope.isQuiescent)
          finally
            scheduler.close()
            executor.shutdownNow()
            assert(executor.awaitTermination(5L, TimeUnit.SECONDS))
      }
      assertEquals(Option(failures.get()), None)
    finally
      scheduler.close()
      executor.shutdownNow()
      executor.awaitTermination(5L, TimeUnit.SECONDS)
