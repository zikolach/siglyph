## Contract and ownership
TaskScheduler exposes a monotonic millisecond time domain and schedule(delayMillis)(callback). ScheduledTask.cancel returns true only when it prevents an unclaimed callback; it never interrupts a claimed callback. Scheduling must not invoke the callback synchronously on its calling stack. Concurrent providers may run a callback on another thread before schedule returns; clients must account for that eventual adapter race. Provider/callback failures are not swallowed. Closed/capacity admission uses SchedulingRejectedException with an explicit reason.

The first provider is ManualTaskScheduler, not a production timer. It owns no threads. Scheduling and cancellation are synchronized; advancement belongs to its constructing thread and is non-reentrant. Concurrent registrations are ordered by admission, not promised deterministic across unordered racing threads.

## Queue and time
Use an ordered map keyed by deadline and monotonically increasing registration ID. Pending capacity is bounded (default 1,000); cancellation physically removes registrations and clears callbacks immediately, with no accumulation of tombstones. Claiming removes an entry before invoking user code outside locks. FIFO applies at equal deadlines, including zero-delay callbacks scheduled during advancement.

advanceTo/advanceBy process at most maxCallbacks (default 1,000). Before each callback, virtual time moves to its deadline. If the budget ends while due work remains, time stays at the last processed deadline and AdvanceResult retains the original target so callers can resume with advanceTo. Otherwise time reaches the requested target. Negative/backward/overflowing time and invalid budgets fail before mutation. Exceptions stop that advancement at the failing deadline; remaining tasks stay pending. Closing cancels pending work, not an already-claimed callback; it prevents subsequent scheduling/advancement. A callback may close the scheduler itself.

## Managed driver integration
An optional manual clock passed to withSession transfers cleanup ownership to the fixture. advanceTimeBy/advanceTimeTo consume one driver step plus the clock's separate bounded callback budget, then flush TUI work and observe runtime failures. No clock means advancement is rejected. Geometry/input budgets remain separate.

Fixture close attempts TUI restoration and clock closure independently. Clock closure atomically counts and cancels pending callbacks. Leaked pending tasks fail the fixture with PendingScheduledTasksException; completed resource cleanup is not undone. Preserve primary body/runtime errors and suppress independent cleanup failures. A failed factory also closes its transferred clock. The driver does not wait for real workers or promise global asynchronous idleness.

## Validation and deferred work
Test ordering, equal deadlines, reentrant registration, cancellation, closure, queue/time limits, bounded repeated zero-delay tasks, callback errors and lock isolation on JVM/Native. Test driver advancement, pending-task diagnostics, factory/body failures and late callbacks against stopped/restarted ApplicationScopes. Wall-clock adapters, periodic tasks, automatic scope cancellation of scheduled handles, and automatic/file-backed reference-app production remain deferred.
