## First boundary
ApplicationScope is a session-owned handle, not an executor or timer. Open it only after TUI.start succeeds. submit takes a deferred callback and returns Accepted, Full or Closed. Accepted acknowledges admission, not eventual execution or success. An uncontended submission may execute synchronously on the submitting thread; delivery is serialized with TUI input/rendering, not affined to a dedicated UI thread. Callbacks must be finite and nonblocking.

## Admission and bounds
TUI lifecycleLock owns all scope state. Each scope holds at most maxPending unclaimed updates (default 256, allowed 1..4096), excluding the currently executing callback. At most 64 scopes may remain open. The existing component-effect queue supplies a separate shared 4096-batch ceiling; either ceiling returns Full before admission. No FIFO replacement, silent coalescing or producer-side retry queue is added. These counts bound retained callbacks, not bytes captured by application closures.

Use the existing coordinator admission under the lifecycle lock; dispatch and render flushing happen after releasing that lock. User callbacks never run under it. A successful update requests rendering; input/protocol queues and priorities are unchanged. Reentrant submissions queue rather than recursively running user callbacks. A callback that continually resubmits can still monopolize execution: bounded queue capacity is not an execution-time sandbox.

## Close and restart
Queued task objects retain an optional callback. Cancellation clears these references immediately and removes scope ownership; coordinator tombstones occupy their existing queue slots until the ordinary drain reaches them. Pending capacity therefore need not be immediately reusable in the shared coordinator after cancellation.

Stop invalidates all open scopes at the existing lifecycle cutoff, before cleanup. Old handles never reopen; restarting requires a fresh scope, providing identity-based generation isolation without changing terminal ingress. Unclaimed callbacks are discarded, not invoked as cancellation notifications.

close establishes a cutoff and waits for an already-claimed callback when invoked by another thread, using monitor wait/notify without holding the lock while waiting. close from the scope's own callback cannot wait for itself: it establishes the cutoff and returns; isQuiescent becomes true after that callback returns. isClosed means admission is closed; isQuiescent additionally means no callback remains in flight. TUI.stop retains its existing reentrant/concurrent completion semantics. User code can cooperate by observing isClosed; no interruption or forcible cancellation is performed. Cross-resource waits and blocking user callbacks remain the application's responsibility.

## Failure and ownership
Callback failures go through the existing TUI runtime failure/cleanup path, not an invented error handler. TUI.run or TUITestDriver exposes them; Accepted does not imply callback success. The scope starts no external work and owns no external producer. Timers, futures and file sources must still be stopped by their owners. Scheduler registration/cancellation and test-clock integration are separate follow-up work.

## Validation
Use synchronous/reentrant tests and latch-controlled concurrent tests, never Thread.sleep assertions. Cover FIFO, limits, cancellation-before-claim, close during an in-flight update, self-close, TUI stop/restart, failure, render publication and maximum scope count. Run shared JVM/Native suites and full JVM core regression tests. Do not claim virtual-time behavior or real-emulator appearance from these tests.
