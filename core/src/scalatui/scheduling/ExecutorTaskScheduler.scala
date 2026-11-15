package scalatui.scheduling

import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.collection.mutable

/**
 * Experimental bounded adapter over an application-owned executor. It never changes executor
 * configuration or shuts it down. Callbacks run outside adapter locks; failures are reported
 * through onFailure and lastFailure. Close cancels unclaimed work but does not wait for running
 * callbacks or registrations still returning from the executor. Inspect isQuiescent and retain
 * executor ownership.
 */
final class ExecutorTaskScheduler(
    executor: ScheduledExecutorService,
    onFailure: Throwable => Unit,
    val maxPending: Int = 1000
) extends TaskScheduler:
  require(maxPending > 0 && maxPending <= 100000, "maxPending must be 1..100000")
  private val origin         = System.nanoTime()
  private var observedMillis = 0L
  private var closed         = false
  private var active         = 0
  private var failure        = Option.empty[Throwable]
  private final class Entry(var callback: Option[() => Unit]):
    var registering = true
    var claimed     = false
    var future      = Option.empty[ScheduledFuture[?]]
  private val entries        = mutable.LinkedHashSet.empty[Entry]

  override def nowMillis: Long = synchronized {
    val elapsed = math.max(0L, (System.nanoTime() - origin) / 1000000L)
    observedMillis = math.max(observedMillis, elapsed)
    observedMillis
  }

  /** Includes registrations in progress and cancellation obligations that failed and need retry. */
  def pendingCount: Int              = synchronized(entries.size)
  def isClosed: Boolean              = synchronized(closed)
  def isQuiescent: Boolean           = synchronized(closed && entries.isEmpty && active <= 0)
  def lastFailure: Option[Throwable] = synchronized(failure)

  override def schedule(delayMillis: Long)(callback: => Unit): ScheduledTask =
    require(
      delayMillis >= 0L && delayMillis <= Long.MaxValue / 1000000L,
      "delay exceeds nanosecond timer range"
    )
    val entry                  = synchronized {
      if closed then throw new SchedulingRejectedException(SchedulingRejection.Closed)
      if entries.size >= maxPending then
        throw new SchedulingRejectedException(SchedulingRejection.Capacity)
      val value = new Entry(Some(() => callback))
      entries.add(value)
      value
    }
    val future                 = try
      executor.schedule(
        new Runnable:
          override def run(): Unit = runEntry(entry)
        ,
        delayMillis,
        TimeUnit.MILLISECONDS
      )
    catch
      case error: Throwable =>
        synchronized {
          entry.callback = None
          entry.registering = false
          entries.remove(entry)
        }
        throw error
    val cancelAfterPublication = synchronized {
      entry.registering = false
      if entry.claimed then
        entries.remove(entry)
        false
      else
        entry.future = Some(future)
        closed || entry.callback.isEmpty
    }
    if cancelAfterPublication then cancelEntry(entry)
    new ScheduledTask:
      override def cancel(): Boolean = cancelEntry(entry)

  private def runEntry(entry: Entry): Unit =
    val callback = synchronized {
      val value = if !closed then entry.callback else None
      entry.callback = None
      entry.claimed = true
      entry.future = None
      if !entry.registering then entries.remove(entry)
      if value.nonEmpty then active += 1
      value
    }
    callback.foreach { action =>
      try action()
      catch
        case error: Throwable =>
          synchronized { failure = Some(error) }
          try onFailure(error)
          catch
            case observer: Throwable =>
              if (observer ne error) && !error.getSuppressed.exists(_ eq observer) then
                error.addSuppressed(observer)
      finally synchronized { active -= 1 }
    }

  private def cancelEntry(entry: Entry): Boolean =
    val cancellation = synchronized {
      if entry.claimed then false -> Option.empty[ScheduledFuture[?]]
      else
        val prevented = entry.callback.nonEmpty
        entry.callback = None
        prevented -> entry.future
    }
    cancellation._2.foreach { future =>
      // A throwing cancel remains registered for an explicit retry. User code is already disarmed.
      future.cancel(false)
      synchronized {
        entry.future = None
        if !entry.registering then entries.remove(entry)
      }
    }
    cancellation._1

  /**
   * Close admission and attempt every pending cancellation. Independent failures are suppressed;
   * failed cancellation obligations remain for a later close/cancel retry. A late future published
   * after close is cancelled by the registering thread, where any failure is reported.
   */
  def close(): Unit =
    val pending = synchronized {
      closed = true
      entries.toVector
    }
    var first   = Option.empty[Throwable]
    pending.foreach { entry =>
      try cancelEntry(entry)
      catch
        case error: Throwable => first match
            case Some(primary) =>
              if (primary ne error) && !primary.getSuppressed.exists(_ eq error) then
                primary.addSuppressed(error)
            case None          => first = Some(error)
    }
    first.foreach(throw _)
