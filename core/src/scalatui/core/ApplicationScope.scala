package scalatui.core

import scalatui.scheduling.TaskScheduler

/** Admission is distinct from completion: accepted work may be cancelled before execution. */
enum ApplicationSubmission derives CanEqual:
  case Accepted, Full, Closed

/**
 * Experimental session-owned, bounded application update delivery. Created by
 * TUI.openApplicationScope after startup. Updates run serially with TUI work, possibly inline on
 * the submitting thread. Registered timers are owned by this handle; their scheduler/executor
 * remains externally owned.
 */
final class ApplicationScope private[core] (
    owner: TUI,
    private[core] val state: ApplicationScope.State
):
  /** Admit a finite nonblocking update; successful execution requests rendering automatically. */
  def submit(update: => Unit): ApplicationSubmission =
    owner.submitApplicationUpdate(state, () => update)

  /** Register a one-shot update; provider registration failures propagate to the caller. */
  def schedule(scheduler: TaskScheduler, delayMillis: Long)(update: => Unit): ScopedTask =
    owner.scheduleApplicationTimer(state, scheduler, delayMillis, None, _ => update)

  /** Fixed delay from delivery completion, with no overlapping/catch-up ticks. */
  def scheduleEvery(scheduler: TaskScheduler, initialDelayMillis: Long, intervalMillis: Long)(
      update: ScopedTask => Unit
  ): ScopedTask =
    require(intervalMillis > 0L, "interval must be positive")
    owner.scheduleApplicationTimer(
      state,
      scheduler,
      initialDelayMillis,
      Some(intervalMillis),
      update
    )

  /** Includes registrations, queued delivery and unresolved physical cancellation/publication. */
  def scheduledCount: Int = owner.withApplicationLock(state.timers.size)

  /** Admission is permanently closed, including after the owning TUI restarts. */
  def isClosed: Boolean = owner.inspectApplicationScope(state)._1

  /** Closed admission and no callback in flight; queued callback references have been cleared. */
  def isQuiescent: Boolean = owner.inspectApplicationScope(state)._2

  /** Unclaimed updates, excluding the executing callback and shared-coordinator tombstones. */
  def pendingCount: Int = owner.inspectApplicationScope(state)._3

  /**
   * Cancel pending updates and wait for a claimed callback on another thread. Self-close
   * establishes the cutoff without waiting for itself; inspect isQuiescent after the callback
   * returns. Waiting is interruptible; interruption does not reopen admission. This never
   * interrupts user code.
   */
  def close(): Unit = owner.closeApplicationScope(state)

object ApplicationScope:
  private[core] final class Pending(var action: Option[() => Unit])

  /**
   * All mutable state is owned by the TUI lifecycle lock; the latch also exposes the cutoff
   * internally.
   */
  private[core] final class State(val capacity: Int, val timerCapacity: Int):
    val timers        = scala.collection.mutable.LinkedHashSet.empty[ScopeTimer]
    val cancelled     = new java.util.concurrent.CountDownLatch(1)
    val pending       = scala.collection.mutable.HashSet.empty[Pending]
    var executing     = Option.empty[Thread]
    def open: Boolean = cancelled.getCount > 0L
