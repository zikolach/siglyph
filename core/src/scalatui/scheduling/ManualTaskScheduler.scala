package scalatui.scheduling

import scala.collection.mutable
import scalatui.syntax.Equality.*

/**
 * Result of one bounded advance. Resume with advanceTo(targetMillis) when reachedTarget is false.
 */
final case class TimeAdvance(
    nowMillis: Long,
    targetMillis: Long,
    callbacksRun: Int,
    pendingCount: Int,
    reachedTarget: Boolean
) derives CanEqual

/**
 * Bounded deterministic clock with no worker or timer. Registration/cancellation are thread-safe;
 * only its constructing thread may advance time, and advancement cannot be reentered. Callbacks run
 * outside the queue lock. Closing discards pending work, not an already-claimed callback.
 */
final class ManualTaskScheduler(val maxPending: Int = 1000, initialMillis: Long = 0L)
    extends TaskScheduler:
  require(maxPending > 0 && maxPending <= 100000, "maxPending must be 1..100000")
  require(initialMillis >= 0L, "initial time must be non-negative")
  private val owner     = Thread.currentThread()
  private final class Entry(val key: (Long, Long), var callback: Option[() => Unit])
  private val queue     = mutable.TreeMap.empty[(Long, Long), Entry]
  private var current   = initialMillis
  private var sequence  = 0L
  private var closed    = false
  private var advancing = false

  override def nowMillis: Long         = synchronized(current)
  def pendingCount: Int                = synchronized(queue.size)
  def isClosed: Boolean                = synchronized(closed)
  def nextDeadlineMillis: Option[Long] = synchronized(queue.headOption.map(_._1._1))

  override def schedule(delayMillis: Long)(callback: => Unit): ScheduledTask =
    require(delayMillis >= 0L, "delay must be non-negative")
    val entry = synchronized {
      if closed then throw new SchedulingRejectedException(SchedulingRejection.Closed)
      if queue.size >= maxPending then
        throw new SchedulingRejectedException(SchedulingRejection.Capacity)
      require(delayMillis <= Long.MaxValue - current, "deadline overflow")
      require(sequence < Long.MaxValue, "registration sequence exhausted")
      val next = new Entry((current + delayMillis, sequence), Some(() => callback))
      sequence += 1L
      queue.put(next.key, next)
      next
    }
    new ScheduledTask:
      override def cancel(): Boolean = ManualTaskScheduler.this.synchronized {
        val pending = entry.callback.nonEmpty
        if pending then
          queue.remove(entry.key)
          entry.callback = None
        pending
      }

  private def checkOwner(): Unit =
    if Thread.currentThread() ne owner then
      throw new IllegalStateException("virtual time must advance on its owning thread")

  private[scalatui] def validateTarget(targetMillis: Long, maxCallbacks: Int): Unit =
    checkOwner()
    synchronized {
      if closed then throw new SchedulingRejectedException(SchedulingRejection.Closed)
      require(!advancing, "virtual time advancement is not reentrant")
      require(targetMillis >= current, "virtual time cannot move backwards")
      require(maxCallbacks > 0 && maxCallbacks <= 100000, "maxCallbacks must be 1..100000")
    }

  private[scalatui] def targetAfter(deltaMillis: Long): Long = synchronized {
    require(
      deltaMillis >= 0L && deltaMillis <= Long.MaxValue - current,
      "invalid time delta or overflow"
    )
    current + deltaMillis
  }

  /** Process at most maxCallbacks due callbacks, without hiding unfinished due work. */
  def advanceBy(deltaMillis: Long, maxCallbacks: Int = 1000): TimeAdvance =
    advanceTo(targetAfter(deltaMillis), maxCallbacks)

  /**
   * Exceptions stop at the failing deadline. Remaining callbacks are retained until
   * cancelled/advanced.
   */
  def advanceTo(targetMillis: Long, maxCallbacks: Int = 1000): TimeAdvance =
    advanceObserved(targetMillis, maxCallbacks)(())

  private[scalatui] def advanceObserved(
      targetMillis: Long,
      maxCallbacks: Int
  )(observe: => Unit): TimeAdvance =
    validateTarget(targetMillis, maxCallbacks)
    synchronized { advancing = true }
    var ran      = 0
    var continue = true
    try
      while continue && ran < maxCallbacks do
        val action = synchronized {
          if closed then None
          else
            queue.headOption match
              case Some((key, entry)) if key._1 <= targetMillis =>
                queue.remove(key)
                current = key._1
                val callback = entry.callback
                entry.callback = None
                callback
              case _                                            => None
        }
        action match
          case Some(callback) =>
            ran += 1
            callback()
            observe
          case None           => continue = false
      synchronized {
        val due = queue.headOption.exists(_._1._1 <= targetMillis)
        if !closed && !due then current = targetMillis
        TimeAdvance(current, targetMillis, ran, queue.size, !due && (current === targetMillis))
      }
    finally synchronized { advancing = false }

  /**
   * Cancel all pending registrations. This is not a wait for a callback already claimed elsewhere.
   */
  def close(): Unit =
    closeAndCountPending()
    ()

  /**
   * Atomic fixture teardown: close admission and report registrations discarded by this operation.
   */
  private[scalatui] def closeAndCountPending(): Int = synchronized {
    val pending = queue.size
    closed = true
    queue.valuesIterator.foreach(_.callback = None)
    queue.clear()
    pending
  }
