package scalatui.terminal

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** Virtual time advances while holding the same monitor as the deadline check and wait. */
private[terminal] final class ManualInputTiming extends InputTiming:
  @volatile private var now                           = 0L
  @volatile private var waitingMonitor: Object | Null = null
  private val ready                                   = CountDownLatch(1)

  override def nanoTime(): Long = now

  override def await(monitor: Object, nanos: Long): Unit =
    waitingMonitor = monitor
    ready.countDown()
    monitor.wait()

  def advanceTo(nanos: Long): Unit =
    require(ready.await(5, TimeUnit.SECONDS), "flush worker did not reach its monitor wait")
    val monitor = waitingMonitor.nn
    monitor.synchronized {
      require(nanos >= now, "virtual monotonic time cannot go backwards")
      now = nanos
      monitor.notifyAll()
    }
