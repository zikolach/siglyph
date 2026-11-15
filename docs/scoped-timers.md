# Scope-owned timers

[Documentation index](README.md) · [Update scopes](application-scopes.md) · [Virtual time](virtual-time.md) · [Executor adapter](executor-scheduling.md)

**Experimental and unreleased; not in 0.11.0.** ApplicationScope now owns delayed and recurring registrations. It still does not own or shut down the scheduler/executor itself.

## Run a complete consumer

```bash
scala-cli run examples/scala-cli/scoped-timers.scala --jvm 21
scala-cli run examples/scala-cli/scoped-timers.scala --native --jvm 21
```

The [complete source example](../examples/scala-cli/scoped-timers.scala) runs three real-executor ticks, self-cancels recurrence, cancels an unused delayed callback through scope close, and explicitly joins the application-owned worker before final screen assertions. It needs no real TTY and does not assert timer accuracy.

## Registration

After starting a TUI, open `tui.openApplicationScope(maxPending = 256, maxScheduled = 64)`.

- `scope.schedule(scheduler, delayMillis) { update }` owns a one-shot update.
- `scope.scheduleEvery(scheduler, initialDelayMillis, intervalMillis) { task => update }` owns fixed-delay recurrence. The callback receives its own handle so it can call `task.cancel()` without a self-referential initialization race.
- `maxScheduled` is 1..4096 and includes waiting timers, queued/running delivery and unresolved registration/cancellation obligations. It is separate from immediate-update and shared coordinator limits.
- Negative initial delays and non-positive intervals fail before admission. Closed/capacity admission uses SchedulingRejectedException. Provider-specific limits can fail initial registration or later rearm; initial errors propagate to the caller and release the reservation.

Callbacks may run before the scope registration method returns, but never before the provider's cancellation handle has been published. Avoid referring to an incompletely initialized one-shot handle from its callback.

## Delivery and recurrence

Updates use the existing serialized TUI work boundary. Successful updates request rendering normally. A periodic timer has at most one registered occurrence or queued delivery: rearm happens after the user callback completes, with the interval measured from that point—not from the original deadline or from frame painting. There are no overlapping user callbacks or catch-up ticks.

`ScopedTask` exposes `cancel`, `isCancelled`, `isDone`, `lastSubmission` and `failure`. Delivery rejection (`Full`/`Closed`) stops the timer and records its outcome instead of retrying forever. A later scheduler/rearm error or user callback failure stops recurrence and enters the existing TUI failure channel. Acceptance is not successful completion.

Cancelling unclaimed delivery prevents user code even if the provider already fired. Cancelling recurrence during its callback prevents future ticks, not the current callback. Cancelling a claimed/completed one-shot returns false without retroactively labelling it cancelled. Scope closure can still mark remaining work cancelled while already-claimed callbacks finish cooperatively.

## Closure and failure

Scope close and TUI stop disarm timer callbacks at the lifecycle cutoff, then cancel provider handles **outside runtime locks**. They do not call scheduler.close or executor.shutdown. Closing one scope must not affect another scope using the same provider.

Provider cancellation errors retain their handles for retry. Registration still in progress remains visible through `scheduledCount`/non-quiescence; external close/stop reports ScheduledCleanupPendingException rather than guessing cleanup complete. Late publication attempts cancellation itself. Self-close does not wait for its own callback and may temporarily remain non-quiescent.

TUI cleanup always attempts terminal restoration even when timer cancellation fails. Restart stays blocked until obligations resolve and an explicit `tui.stop()` retry completes lifecycle cleanup. Completed backend/screen restoration is not repeated on that retry. Provider failures before returning a handle leave unreturned provider resources under that provider's ownership; user delivery remains disarmed.

Provider schedule/cancel calls and application callbacks must be finite/nonblocking. The library does not interrupt user code or sandbox arbitrary providers. Scope quiescence concerns its own callbacks and registration cleanup, not independent component effects or external producers.

## Loader recipe

Use `scope.scheduleEvery(scheduler, 0, loader.intervalMs.toLong) { _ => loader.tick(); () }` after `loader.start()`. This opts into managed ticking without changing Loader's deterministic manual `tick()` API. The shared tests exercise this recipe with virtual time. Application-owned file/network producers still require their own lifecycle integration.

## Validation

```bash
mill core.test.testOnly scalatui.core.ScopedTimerSuite
mill coreNative.test.testOnly scalatui.core.ScopedTimerSuite
```

Coverage includes one-shot and fixed-delay ticks, contention without catch-up, queued-delivery cancellation, shared-provider isolation, overload, registration/rearm failures, early fire/late publication, cleanup retry, terminal restoration despite cancellation failure, loader ticking and real executor self-cancellation. Latch deadlines are deadlock guards, not timing guarantees. [Live log generation](live-log-explorer.md) now uses these APIs; file-backed production remains a separate integration slice.
