package scalatui.scheduling

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

class ManualTaskSchedulerSuite extends munit.FunSuite:
  test("callbacks are deferred and ordered by deadline then registration"):
    val clock  = ManualTaskScheduler()
    var seen   = Vector.empty[(String, Long)]
    clock.schedule(20) { seen :+= "late" -> clock.nowMillis }
    clock.schedule(10) { seen :+= "first" -> clock.nowMillis }
    clock.schedule(10) { seen :+= "second" -> clock.nowMillis }
    assertEquals(seen, Vector.empty)
    assertEquals(clock.nextDeadlineMillis, Some(10L))
    val result = clock.advanceTo(30)
    assertEquals(seen, Vector("first" -> 10L, "second" -> 10L, "late" -> 20L))
    assertEquals(result, TimeAdvance(30L, 30L, 3, 0, true))

  test("zero-delay reentrant scheduling preserves FIFO"):
    val clock = ManualTaskScheduler()
    var seen  = Vector.empty[Int]
    clock.schedule(0) {
      seen :+= 1
      clock.schedule(0) { seen :+= 3 }
    }
    clock.schedule(0) { seen :+= 2 }
    clock.advanceBy(0)
    assertEquals(seen, Vector(1, 2, 3))

  test("callback budget bounds an endlessly rescheduling zero-delay source"):
    val clock         = ManualTaskScheduler(maxPending = 1)
    var calls         = 0
    def again(): Unit =
      calls += 1
      clock.schedule(0) { again() }
      ()
    clock.schedule(0) { again() }
    val first         = clock.advanceTo(100, maxCallbacks = 5)
    assertEquals(first, TimeAdvance(0L, 100L, 5, 1, false))
    assertEquals(calls, 5)
    val second        = clock.advanceTo(first.targetMillis, maxCallbacks = 3)
    assertEquals(second.callbacksRun, 3)
    assertEquals(calls, 8)
    assert(!second.reachedTarget)
    clock.close()
    assertEquals(clock.pendingCount, 0)

  test("time does not jump over unprocessed due work and target can be resumed"):
    val clock  = ManualTaskScheduler()
    clock.schedule(10) { () }
    clock.schedule(20) { () }
    val first  = clock.advanceTo(100, maxCallbacks = 1)
    assertEquals(first.nowMillis, 10L)
    assert(!first.reachedTarget)
    assertEquals(clock.nextDeadlineMillis, Some(20L))
    val second = clock.advanceTo(first.targetMillis, maxCallbacks = 1)
    assertEquals(second.nowMillis, 100L)
    assert(second.reachedTarget)

  test("cancellation frees capacity immediately and is idempotent"):
    val clock       = ManualTaskScheduler(maxPending = 1)
    var ran         = false
    val handle      = clock.schedule(100) { ran = true }
    val full        = intercept[SchedulingRejectedException](clock.schedule(0) { () })
    assertEquals(full.reason, SchedulingRejection.Capacity)
    assert(handle.cancel())
    assert(!handle.cancel())
    assertEquals(clock.pendingCount, 0)
    val replacement = clock.schedule(2) { () }
    clock.advanceBy(2)
    assert(!replacement.cancel())
    assert(!ran)

  test("cancel during its own callback cannot claim to interrupt already-claimed work"):
    val clock    = ManualTaskScheduler()
    var handle   = Option.empty[ScheduledTask]
    var finished = false
    handle = Some(clock.schedule(0) {
      assert(!handle.get.cancel())
      finished = true
    })
    clock.advanceBy(0)
    assert(finished)

  test("close clears pending registrations and rejects later operations"):
    val clock  = ManualTaskScheduler()
    var ran    = false
    val handle = clock.schedule(20) { ran = true }
    clock.close()
    clock.close()
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)
    assert(!handle.cancel())
    assertEquals(
      intercept[SchedulingRejectedException](clock.schedule(0) { ran = true }).reason,
      SchedulingRejection.Closed
    )
    intercept[SchedulingRejectedException](clock.advanceBy(20))
    assert(!ran)

  test("self-close stops advancement at that deadline and discards later work"):
    val clock  = ManualTaskScheduler()
    var later  = false
    clock.schedule(5) { clock.close() }
    clock.schedule(6) { later = true }
    val result = clock.advanceTo(100)
    assertEquals(result.nowMillis, 5L)
    assert(!result.reachedTarget)
    assert(!later)
    assertEquals(clock.pendingCount, 0)

  test("callback errors stop at the failing deadline without consuming unrelated work"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalStateException("timer failed")
    var later   = false
    clock.schedule(5) { throw failure }
    clock.schedule(6) { later = true }
    val caught  = intercept[IllegalStateException](clock.advanceTo(100))
    assert(caught eq failure)
    assertEquals(clock.nowMillis, 5L)
    assertEquals(clock.pendingCount, 1)
    assert(!later)
    clock.advanceTo(100)
    assert(later)

  test("invalid time, budgets and overflow do not mutate clock state"):
    intercept[IllegalArgumentException](ManualTaskScheduler(0))
    intercept[IllegalArgumentException](ManualTaskScheduler(initialMillis = -1))
    val clock = ManualTaskScheduler(initialMillis = 10)
    intercept[IllegalArgumentException](clock.schedule(-1) { () })
    intercept[IllegalArgumentException](clock.advanceTo(9))
    intercept[IllegalArgumentException](clock.advanceBy(-1))
    intercept[IllegalArgumentException](clock.advanceTo(10, maxCallbacks = 0))
    intercept[IllegalArgumentException](clock.schedule(Long.MaxValue) { () })
    intercept[IllegalArgumentException](clock.advanceBy(Long.MaxValue))
    assertEquals(clock.nowMillis, 10L)
    assertEquals(clock.pendingCount, 0)
    clock.advanceTo(Long.MaxValue)
    clock.schedule(0) { () }
    assert(clock.advanceBy(0).reachedTarget)

  test("advancement is non-reentrant and restricted to the creating thread"):
    val clock   = ManualTaskScheduler()
    clock.schedule(0) {
      intercept[IllegalArgumentException](clock.advanceBy(0))
    }
    clock.advanceBy(0)
    val failure = new AtomicReference[Throwable]()
    val worker  = new Thread(() =>
      try clock.advanceBy(1)
      catch case error: Throwable => failure.set(error)
      ()
    )
    worker.start()
    worker.join(5000L)
    assert(!worker.isAlive)
    assert(failure.get().isInstanceOf[IllegalStateException])
    assertEquals(clock.nowMillis, 0L)

  test("callbacks run outside queue locks and cancellation is safe on another thread"):
    val clock  = ManualTaskScheduler()
    var later  = false
    val handle = clock.schedule(20) { later = true }
    clock.schedule(0) {
      val done      = new CountDownLatch(1)
      val worker    = new Thread(() =>
        handle.cancel()
        done.countDown()
      )
      worker.setDaemon(true)
      worker.start()
      val completed = done.await(5L, TimeUnit.SECONDS)
      assert(completed)
      worker.join(5000L)
      assert(!worker.isAlive)
    }
    clock.advanceTo(100)
    assert(!later)
    assertEquals(clock.pendingCount, 0)
