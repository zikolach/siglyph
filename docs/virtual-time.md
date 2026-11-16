# Virtual-time scheduling

[Documentation index](README.md) · [Application testing](application-testing.md) · [Update scopes](application-scopes.md)

**Available since 0.12.0; APIs remain experimental.** This adds the scheduling boundary and a deterministic provider for #94/#96. It does not introduce a production timer, executor or background thread.

## Run the source-checkout example

```bash
scala-cli run examples/scala-cli/virtual-time.scala --jvm 21
scala-cli run examples/scala-cli/virtual-time.scala --native --jvm 21
```

The [complete example](../examples/scala-cli/virtual-time.scala) drives an ApplicationScope through virtual deadlines, cancels a task and verifies terminal cleanup without a real TTY or test-framework dependency.

## Scheduling contract

`TaskScheduler` provides `nowMillis` and `schedule(delayMillis)(callback)`. Its time domain is monotonic and local to that scheduler; do not compare it with wall-clock timestamps or another scheduler.

A `ScheduledTask` handle supports `cancel()`: true means an unclaimed callback was prevented from running. Repeated cancellation and cancellation after claim return false; running callbacks are never interrupted. A negative delay or overflowing deadline is rejected. Closed/full providers throw `SchedulingRejectedException` with `SchedulingRejection.Closed` or `.Capacity`; other failures propagate.

Scheduling cannot invoke user code synchronously on the calling stack. A future concurrent provider may run a callback on another thread **before schedule returns**. Clients must not assume the cancellation handle has already been assigned when a callback begins. ManualTaskScheduler never runs callbacks until explicitly advanced.

## ManualTaskScheduler

- Starts at zero by default; `initialMillis` may choose another non-negative origin.
- Has a bounded pending queue (`maxPending`, default 1,000, maximum 100,000).
- Orders by deadline, then registration order. Zero-delay work registered inside a callback follows previously registered work at the same deadline.
- Registration and cancellation are thread-safe; ordering between unordered racing threads is determined by actual admission, not promised repeatability.
- Only the constructing thread may advance time. Advancement is non-reentrant; callbacks run outside queue locks.
- Cancellation physically removes registrations and clears callbacks. There are no cancelled tombstones accumulating until distant deadlines.
- `close()` rejects new work and discards pending callbacks. It is not a barrier for a callback already claimed before concurrent close.

## Bounded advancement

`advanceBy(deltaMillis, maxCallbacks = 1000)` and `advanceTo(targetMillis, maxCallbacks = 1000)` process at most the specified callback budget (1..100,000). Each callback observes its own deadline as `nowMillis`.

The returned `TimeAdvance` includes current time, the original target, callbacks run, remaining pending count and `reachedTarget`. If due work remains at the budget boundary, **time does not jump past it**. Resume with `advanceTo(result.targetMillis, ...)`, rather than adding the original delta again. This bounds even a zero-delay callback that reschedules itself forever.

If no due work remains, time reaches the target, even if later deadlines remain pending. Callback failure propagates immediately at that callback's deadline; unrelated queued work remains available for explicit cancellation or subsequent advancement. Closing from inside a callback stops further advancement at that point.

Limits bound callback counts and registrations, not execution time or bytes captured by arbitrary user closures. A callback can still block forever; no timeout thread or forced interruption is added.

## Managed application tests

Construct a clock and pass `clock = Some(clock)` to `TUITestDriver.withSession`. This transfers its cleanup ownership to the fixture. Use `driver.advanceTimeBy`/`advanceTimeTo`, not raw advancement, to enforce driver step budgets and observe TUI failures after each virtual callback.

`driver.pendingScheduledTasks` and `assertNoPendingTasks()` expose unfinished work. A fixture that exits with queued callbacks **fails**, but first cancels them and restores its terminal. Pending-task diagnostics do not replace a primary body/runtime error; independent cleanup errors are suppressed. Explicitly cancel work that a test intentionally leaves outstanding. Factory failure also closes the transferred clock.

A manual scheduler and an ApplicationScope have separate ownership. `clock.schedule(50) { scope.submit { ... } }` guards UI delivery but does **not** bind that timer registration to scope close. An old scope rejects delivery after TUI restart; the caller still owns the scheduled handle. A [borrowed-executor adapter](executor-scheduling.md) now supplies production one-shot scheduling. [Scope-owned timers](scoped-timers.md) now provide automatic registration cancellation and fixed-delay recurrence; bare scheduler calls remain independently owned.

## Verification

```bash
mill core.test.testOnly scalatui.scheduling.ManualTaskSchedulerSuite scalatui.testing.VirtualTimeDriverSuite
mill coreNative.test.testOnly scalatui.scheduling.ManualTaskSchedulerSuite scalatui.testing.VirtualTimeDriverSuite
```

Tests cover deadline/FIFO ordering, bounded self-rescheduling, cancellation and capacity reuse, invalid/overflowing time, failure and self-close, owner/reentrancy guards, callback lock isolation, driver advancement, task leaks, terminal/clock cleanup failure precedence and stale scoped updates after restart. These are deterministic queue/virtual-screen checks, not production timer accuracy or emulator pixel tests.
