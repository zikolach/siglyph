package scalatui.scheduling

/** A cancellation handle; cancelling never interrupts a callback already claimed for execution. */
trait ScheduledTask:
  /**
   * True only when an unclaimed callback was prevented from running. Repeated cancellation is
   * false.
   */
  def cancel(): Boolean

/** Explicit scheduler admission failure; other provider failures propagate unchanged. */
enum SchedulingRejection derives CanEqual:
  case Closed, Capacity

final class SchedulingRejectedException(val reason: SchedulingRejection)
    extends IllegalStateException(s"scheduler rejected registration: $reason")

/**
 * Experimental scheduling boundary. Time is monotonic and meaningful only within this scheduler.
 * schedule never invokes user code synchronously on its calling stack. A concurrent provider may
 * invoke it on another thread before returning the handle. Providers and producers have separate
 * ownership; this trait does not imply ownership of an executor or a TUI session.
 */
trait TaskScheduler:
  def nowMillis: Long
  def schedule(delayMillis: Long)(callback: => Unit): ScheduledTask
