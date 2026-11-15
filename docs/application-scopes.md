# Application update scopes

[Documentation index](README.md) · [Roadmap #94](https://github.com/zikolach/siglyph/issues/94)

**Experimental and unreleased; not in 0.11.0.** This is the bounded update-delivery part of #94. It is not yet a scheduling/task-execution framework: no timer, executor, producer or file source is created.

## Try the contract

From a source checkout:

```bash
scala-cli run examples/scala-cli/application-scope.scala --jvm 21
scala-cli run examples/scala-cli/application-scope.scala --native --jvm 21
```

The [complete example](../examples/scala-cli/application-scope.scala) uses the managed test driver and runs without a real TTY. It deterministically demonstrates reentrant updates, automatic rendering and rejection of late results. It does not pretend to be an asynchronous producer.

## Open after startup

`val scope = tui.openApplicationScope(maxPending = 256)` requires an already-running TUI. An application may call `tui.start()`, open the scope, install producer callbacks, then use `tui.run()` to await exit. A managed test fixture can open a scope inside its body.

Submit a finite, nonblocking UI update with `scope.submit { ... }`. Successful execution requests rendering automatically. The result is one of:

| Result | Meaning |
| --- | --- |
| `Accepted` | Admitted; may execute inline, or be queued behind current runtime work. Not a completion/success guarantee. |
| `Full` | Per-scope or shared coordinator capacity is exhausted. Callback was not admitted. |
| `Closed` | Scope/session is closed. Callback was not admitted. |

Delivery is serialized with TUI work, **not pinned to a dedicated UI thread**. On an uncontended runtime, submit can run the callback before returning. Reentrant submissions queue instead of recursively invoking user code. Callbacks run outside lifecycle locks. They must not block or continuously resubmit themselves; admission limits are not execution-time limits.

The scope does not silently replace, coalesce or retry producer updates. On `Full`, the producer must choose its own bounded retry/drop/coalescing policy. Admission is not an atomic transaction around multiple component mutations; failures do not roll back earlier application changes.

## Bounds and lifecycle

- At most **64 open scopes** per TUI.
- Each has a **1..4096 pending-update limit**, default 256. `pendingCount` excludes the executing callback.
- The existing **4096-batch shared effect queue** is a separate ceiling, shared with component effects. Nested component operations remain subject to its existing admission policy.
- Limits count callback references, not bytes captured by application closures.
- Cancellation clears queued callback references immediately. Internal no-op queue entries may occupy shared slots until drained, so closing a scope need not instantly make the shared queue available.

TUI stop invalidates scopes at its lifecycle cutoff. A late result using an old handle returns `Closed`, even after the same TUI restarts. Open a fresh scope for each new session. Accepted-but-unclaimed updates are discarded on close/stop, not run as cancellation notifications.

`isClosed` means admission is closed. `isQuiescent` means admission is closed **and** no claimed callback submitted through this scope remains in flight. This does not cancel independent component effects or external work launched by an update; those keep their existing ownership and runtime semantics.

`close()` cancels pending updates and waits for a claimed callback on another thread. It does not interrupt that callback. If the callback closes its own scope, close establishes the cutoff and returns without waiting for itself; quiescence follows when it returns. Waiting is interruptible and interruption never reopens admission. Avoid closing while holding resources that an in-flight callback needs; cross-resource wait cycles remain application bugs.

TUI.stop keeps its established reentrant/concurrent completion semantics. The scope starts no producer, so it cannot shut down external workers, futures or timers: their owner must still release them. Explicit scope close can release a scope earlier than TUI stop. Scopes are session-owned, not component-owned: removing a component does not close its scope; the application must close it when that ownership ends.

## Failures and testing

Callback errors enter the existing TUI failure/cleanup path. `TUI.run()` reports them, and the [test driver](application-testing.md) observes them on the next action or cleanup. `Accepted` is deliberately not an error/result future.

```bash
mill core.test.testOnly scalatui.core.ApplicationScopeSuite
mill coreNative.test.testOnly scalatui.core.ApplicationScopeSuite
```

Shared tests cover FIFO, per-scope/shared limits, closed-slot reuse, cancellation-before-claim, reference release, self-close, restart isolation, failure propagation, lock isolation and latch-controlled external close. Latch timeouts are deadlock guards, not performance assertions. No sleep-based correctness assertions are used.

## Next slice

Scheduled ticks, injectable clocks, scheduler registration/cancellation, virtual-time driver advancement and automatic log production remain pending under #94/#96/#93. These boundaries are documented before adding an adapter; no timer or effect-runtime dependency is smuggled into this update primitive.
