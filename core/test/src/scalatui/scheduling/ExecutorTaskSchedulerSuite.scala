package scalatui.scheduling

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ArrayBuffer

class ExecutorTaskSchedulerSuite extends munit.FunSuite:
  private final class FakeFuture extends ScheduledFuture[AnyRef]:
    var cancelCalls                                         = 0
    var cancelFailure                                       = Option.empty[Throwable]
    var cancelled                                           = false
    override def cancel(interrupt: Boolean): Boolean        =
      assert(!interrupt)
      cancelCalls += 1
      cancelFailure.foreach(throw _)
      val changed = !cancelled
      cancelled = true
      changed
    override def isCancelled: Boolean                       = cancelled
    override def isDone: Boolean                            = cancelled
    override def get(): AnyRef                              = throw new UnsupportedOperationException()
    override def get(timeout: Long, unit: TimeUnit): AnyRef = get()
    override def getDelay(unit: TimeUnit): Long             = 0L
    override def compareTo(other: Delayed): Int             = 0

  private final class FakeExecutor extends AbstractExecutorService, ScheduledExecutorService:
    val tasks                                                                                 = ArrayBuffer.empty[(Runnable, FakeFuture)]
    var beforeReturn: () => Unit                                                              = () => ()
    var reject                                                                                = Option.empty[Throwable]
    var stopped                                                                               = false
    override def schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[?] =
      reject.foreach(throw _)
      val future = new FakeFuture
      tasks += command -> future
      beforeReturn()
      future
    override def schedule[V](
        callable: Callable[V],
        delay: Long,
        unit: TimeUnit
    ): ScheduledFuture[V]                                                                     =
      throw new UnsupportedOperationException()
    override def scheduleAtFixedRate(
        command: Runnable,
        initial: Long,
        period: Long,
        unit: TimeUnit
    ): ScheduledFuture[?]                                                                     =
      throw new UnsupportedOperationException()
    override def scheduleWithFixedDelay(
        command: Runnable,
        initial: Long,
        delay: Long,
        unit: TimeUnit
    ): ScheduledFuture[?]                                                                     =
      throw new UnsupportedOperationException()
    override def execute(command: Runnable): Unit                                             = throw new UnsupportedOperationException()
    override def shutdown(): Unit                                                             = stopped = true
    override def shutdownNow(): java.util.List[Runnable]                                      = {
      stopped = true; java.util.Collections.emptyList[Runnable]()
    }
    override def isShutdown: Boolean                                                          = stopped
    override def isTerminated: Boolean                                                        = stopped
    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean                     = stopped

  test("bounded admission, cancellation and closed rejection do not own executor shutdown"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => (), maxPending = 1)
    var ran       = false
    val task      = scheduler.schedule(100) { ran = true }
    assertEquals(scheduler.pendingCount, 1)
    assertEquals(
      intercept[SchedulingRejectedException](scheduler.schedule(0) { () }).reason,
      SchedulingRejection.Capacity
    )
    assertEquals(executor.tasks.size, 1)
    assert(task.cancel())
    assert(!task.cancel())
    executor.tasks.head._1.run()
    assert(!ran)
    assertEquals(scheduler.pendingCount, 0)
    scheduler.schedule(1) { () }
    scheduler.close()
    assert(scheduler.isQuiescent)
    assert(!executor.stopped)
    assertEquals(
      intercept[SchedulingRejectedException](scheduler.schedule(0) { () }).reason,
      SchedulingRejection.Closed
    )

  test("executor registration failure releases the reserved slot"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => (), maxPending = 1)
    val failure   = new RejectedExecutionException("executor unavailable")
    executor.reject = Some(failure)
    assert(intercept[RejectedExecutionException](scheduler.schedule(0) { () }) eq failure)
    assertEquals(scheduler.pendingCount, 0)
    executor.reject = None
    scheduler.schedule(0) { () }
    scheduler.close()

  test("a callback may finish before future publication without resurrecting pending state"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => (), maxPending = 1)
    val ran       = new CountDownLatch(1)
    executor.beforeReturn = () =>
      val worker   = new Thread(() => executor.tasks.head._1.run())
      worker.start()
      val finished = ran.await(5L, TimeUnit.SECONDS)
      worker.join(5000L)
      assert(finished)
      assert(!worker.isAlive)
      assertEquals(scheduler.pendingCount, 1)
      assertEquals(
        intercept[SchedulingRejectedException](scheduler.schedule(0) { () }).reason,
        SchedulingRejection.Capacity
      )
      scheduler.close()
      assert(!scheduler.isQuiescent)
    val task      = scheduler.schedule(0) { ran.countDown() }
    assertEquals(scheduler.pendingCount, 0)
    assert(!task.cancel())
    assertEquals(executor.tasks.head._2.cancelCalls, 0)
    scheduler.close()
    assert(scheduler.isQuiescent)

  test("close racing with future publication disarms the callback and cancels the late future"):
    val executor     = new FakeExecutor
    val scheduler    = ExecutorTaskScheduler(executor, _ => ())
    val entered      = new CountDownLatch(1)
    val release      = new CountDownLatch(1)
    val error        = new AtomicReference[Throwable]()
    var ran          = false
    executor.beforeReturn = () =>
      entered.countDown()
      if !release.await(5L, TimeUnit.SECONDS) then
        throw new IllegalStateException("publication timed out")
    val registration = new Thread(() =>
      try scheduler.schedule(0) { ran = true }
      catch case failure: Throwable => error.set(failure)
      ()
    )
    registration.setDaemon(true)
    registration.start()
    try
      val started = entered.await(5L, TimeUnit.SECONDS)
      assert(started)
      scheduler.close()
      assert(scheduler.isClosed)
      assert(!scheduler.isQuiescent)
      assertEquals(scheduler.pendingCount, 1)
    finally
      release.countDown()
      registration.join(5000L)
    assert(!registration.isAlive)
    assertEquals(Option(error.get()), None)
    assertEquals(executor.tasks.head._2.cancelCalls, 1)
    executor.tasks.head._1.run()
    assert(!ran)
    assert(scheduler.isQuiescent)

  test("close attempts every cancellation and retains failed obligations for retry"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    var ran       = false
    scheduler.schedule(1) { ran = true }
    scheduler.schedule(2) { ran = true }
    val failure   = new IllegalStateException("cancel failed")
    executor.tasks.head._2.cancelFailure = Some(failure)
    assert(intercept[IllegalStateException](scheduler.close()) eq failure)
    assert(executor.tasks(1)._2.cancelled)
    assertEquals(scheduler.pendingCount, 1)
    assert(!scheduler.isQuiescent)
    executor.tasks.head._2.cancelFailure = None
    scheduler.close()
    assert(scheduler.isQuiescent)
    executor.tasks.foreach(_._1.run())
    assert(!ran)

  test("multiple cancellation failures preserve registration-order precedence"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    scheduler.schedule(1) { () }
    scheduler.schedule(2) { () }
    val first     = new IllegalStateException("first cancel failed")
    val second    = new IllegalStateException("second cancel failed")
    executor.tasks(0)._2.cancelFailure = Some(first)
    executor.tasks(1)._2.cancelFailure = Some(second)
    val caught    = intercept[IllegalStateException](scheduler.close())
    assert(caught eq first)
    assert(caught.getSuppressed.exists(_ eq second))
    assertEquals(scheduler.pendingCount, 2)
    executor.tasks.foreach(_._2.cancelFailure = None)
    scheduler.close()
    assert(scheduler.isQuiescent)

  test("late-publication cancellation failure propagates and remains retryable"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    val failure   = new IllegalStateException("late cancel failed")
    executor.beforeReturn = () =>
      executor.tasks.head._2.cancelFailure = Some(failure)
      scheduler.close()
    val caught    = intercept[IllegalStateException](scheduler.schedule(0) { () })
    assert(caught eq failure)
    assertEquals(scheduler.pendingCount, 1)
    assert(!scheduler.isQuiescent)
    executor.tasks.head._2.cancelFailure = None
    scheduler.close()
    assert(scheduler.isQuiescent)

  test("a failed-cancel wrapper can run harmlessly and release its obligation"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    var ran       = false
    val task      = scheduler.schedule(0) { ran = true }
    executor.tasks.head._2.cancelFailure = Some(new IllegalStateException("cancel failed"))
    intercept[IllegalStateException](task.cancel())
    assertEquals(scheduler.pendingCount, 1)
    executor.tasks.head._1.run()
    assert(!ran)
    assertEquals(scheduler.pendingCount, 0)
    assert(!task.cancel())
    scheduler.close()

  test("claimed callbacks cannot cancel themselves and self-close does not wait"):
    val executor  = new FakeExecutor
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    var handle    = Option.empty[ScheduledTask]
    handle = Some(scheduler.schedule(0) {
      assert(!handle.get.cancel())
      scheduler.close()
      assert(!scheduler.isQuiescent)
    })
    executor.tasks.head._1.run()
    assert(scheduler.isQuiescent)

  test("callback and observer errors remain observable without executor-future inspection"):
    val executor  = new FakeExecutor
    val original  = new IllegalStateException("callback failed")
    val observer  = new IllegalArgumentException("observer failed")
    var reported  = Option.empty[Throwable]
    val scheduler =
      ExecutorTaskScheduler(executor, error => { reported = Some(error); throw observer })
    scheduler.schedule(0) { throw original }
    executor.tasks.head._1.run()
    assert(reported.exists(_ eq original))
    assert(scheduler.lastFailure.exists(_ eq original))
    assert(original.getSuppressed.exists(_ eq observer))
    scheduler.close()
    assert(scheduler.isQuiescent)

  test("invalid configuration and huge delays fail before executor admission"):
    val executor  = new FakeExecutor
    intercept[IllegalArgumentException](ExecutorTaskScheduler(executor, _ => (), 0))
    val scheduler = ExecutorTaskScheduler(executor, _ => ())
    intercept[IllegalArgumentException](scheduler.schedule(-1) { () })
    intercept[IllegalArgumentException](scheduler.schedule(Long.MaxValue) { () })
    assertEquals(executor.tasks.size, 0)
    val before    = scheduler.nowMillis
    assert(scheduler.nowMillis >= before)
    scheduler.close()

  test("real executor runs off the calling stack and remains owned by the application"):
    val executor       = new ScheduledThreadPoolExecutor(1)
    val error          = new AtomicReference[Throwable]()
    val scheduler      = ExecutorTaskScheduler(executor, failure => error.set(failure))
    val done           = new CountDownLatch(1)
    val callbackThread = new AtomicReference[Thread]()
    val caller         = Thread.currentThread()
    try
      scheduler.schedule(0) {
        callbackThread.set(Thread.currentThread())
        done.countDown()
      }
      val completed = done.await(5L, TimeUnit.SECONDS)
      assert(completed)
      assert(callbackThread.get() ne caller)
      scheduler.close()
      assert(!executor.isShutdown)
      assertEquals(Option(error.get()), None)
    finally
      scheduler.close()
      executor.shutdownNow()
      assert(executor.awaitTermination(5L, TimeUnit.SECONDS))
    assert(scheduler.isQuiescent)
