# Executor scheduling

[Documentation index](README.md) · [Virtual time](virtual-time.md) · [Update scopes](application-scopes.md)

**Experimental and unreleased; not in 0.11.0.** `ExecutorTaskScheduler` adapts an application-owned `ScheduledExecutorService`. Unlike the manual clock, it delivers one-shot tasks using a real executor. It creates no executor or thread implicitly.

## Run the consumer smoke

From a source checkout with the supported JVM/Native toolchains:

```bash
scala-cli run examples/scala-cli/executor-scheduling.scala --jvm 21
scala-cli run examples/scala-cli/executor-scheduling.scala --native --jvm 21
```

The [complete example](../examples/scala-cli/executor-scheduling.scala) explicitly creates a one-worker executor, posts a scoped UI update, observes completion and shuts down the worker before managed terminal cleanup. It needs no real TTY.

## Ownership and errors

Construct `ExecutorTaskScheduler(executor, onFailure, maxPending = 1000)`. The failure observer is required: scheduled executor futures otherwise easily hide callback errors. `lastFailure` retains the latest callback failure; if the observer itself throws, its error is suppressed onto the original. Observer/callback code runs outside adapter locks and must remain finite/nonblocking. Registration/cancellation errors propagate synchronously instead of entering this observer. Only errors escaping the scheduled callback are reported here: failures inside a nested `scope.submit` retain the TUI runtime/driver failure channel.

The adapter never shuts down the executor or changes its policies. The application must close the adapter, shut down its executor when appropriate, and await worker termination. Callbacks may run concurrently according to that executor's configuration; use `scope.submit` for serialized UI delivery. A concurrent callback may start or finish before `schedule` returns its handle.

## Limits and cancellation

`maxPending` is 1..100,000. It counts queued registrations, in-progress publication and failed cancellation obligations. Publication remains counted even if the callback finishes early; after publication, running callbacks are excluded. Adapter closed/full admission uses the existing typed rejection; executor rejection propagates unchanged. `nowMillis` is monotonic relative to an adapter-local nanoTime origin. Delays must fit non-negative nanosecond conversion (`Long.MaxValue / 1,000,000` milliseconds); no timing-accuracy guarantee is made.

`cancel()` atomically disarms an unclaimed callback, then requests `future.cancel(false)` outside locks. Running callbacks are not interrupted. If cancellation throws, the callback remains disarmed and its future/reservation is retained for explicit retry. A retry can return false while completing physical cancellation because logical cancellation already happened.

`close()` closes admission and attempts every pending cancellation, suppressing independent failures. It does **not** wait for running callbacks or executor registrations still returning. Late-published futures are cancelled by their registering thread; errors propagate there. `isQuiescent` requires closed admission, no unresolved registrations/cancellation obligations, and no active callback or observer. Retry failed cancellations explicitly; use executor-owner shutdown/wait semantics for worker completion.

Adapter counts do not bound bytes captured by callbacks or the executor's own queue. Cancelled-future retention follows the borrowed executor's policy. For example, its owner may choose remove-on-cancel on a compatible executor. The adapter does not mutate that policy or purge other clients' work.

## Scope boundary

Scheduling a callback that calls `scope.submit` guards UI delivery, but does not make the registration scope-owned. Owners must still cancel those handles. Automatic scope-owned registration, recurring ticks and automatic/file-backed log production remain pending under #94/#93.

## Validation

```bash
mill core.test.testOnly scalatui.scheduling.ExecutorTaskSchedulerSuite
mill coreNative.test.testOnly scalatui.scheduling.ExecutorTaskSchedulerSuite
```

The suite covers bounded admission, executor rejection, callback-before-publication, close-before-publication, cancellation failure/retry, self-close, observer failure and a real executor smoke. Latch waits are deadlock guards, not timer-accuracy assertions. No full emulator or real-time scheduling guarantee is implied.
