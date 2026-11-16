## Ownership and admission
The adapter borrows ScheduledExecutorService. Its owner creates/configures/shuts down the executor and waits for worker termination. The adapter never changes its policies. A registration occupies one bounded slot before calling schedule outside the lock; slots include in-progress publication and failed cancellation obligations. Publication remains counted even if a callback is already claimed/completed; after publication, running callbacks are excluded. Per-adapter maxPending defaults to 1000 (1..100000). Closed/capacity failures use the existing typed rejection; executor registration failures propagate unchanged and release the reservation.

## Callback and handle races
The executor must comply with the scheduling contract: no synchronous callback on the caller's stack, although another thread may run before schedule returns. Claim/disarm is atomic under the adapter monitor; callbacks and failure observers run outside it. A claimed callback cannot be cancelled or interrupted. Successful or failed completion releases active state. A callback claimed before handle publication must not resurrect an entry when its future is installed.

Close marks admission closed, disarms queued callbacks and attempts all published future cancellations outside locks. In-progress registrations remain accounted for and cancel their future after publication. Close is a cutoff, not a wait for registration or callback completion; isQuiescent additionally requires zero pending obligations and zero running callbacks. An executor schedule call that never returns prevents quiescence; arbitrary provider code is not sandboxed.

Cancellation exceptions propagate and retain the future/reservation for explicit close/cancel retry. User callbacks stay disarmed even if physical cancellation fails. Callback-after-failed-cancel runs a no-op and releases the obligation. Repeated cancel may return false while successfully retrying physical cleanup because logical cancellation already occurred.

## Failure and retention
A required onFailure observer receives asynchronous callback errors. lastFailure retains the latest error; observer failures are suppressed onto it rather than lost in an inaccessible executor future. Observer code must remain finite/nonblocking. Registration and cancellation failures are synchronous and are not routed through this callback-error observer.

Adapter counts do not bound application closure bytes or the executor's own queues. In particular, cancelled future retention follows the borrowed executor's policy; owners may configure remove-on-cancel on compatible executors. No global purge or configuration mutation affects other users of the executor.

## Time and scope
nowMillis is monotonic, relative to an adapter-local nanoTime origin. Delays are non-negative and limited to Long.MaxValue / 1000000 milliseconds to avoid conversion overflow. Execution accuracy/latency is executor- and workload-dependent; no real-time guarantees or fixed thread affinity are claimed.

The adapter does not make timers ApplicationScope-owned. Production integrations must cancel their handles and close/shut down resources explicitly. Scope-owned registration, recurring tasks and reference-app producer ownership are the next slice.

## Verification
Deterministic fake-executor tests cover capacity, rejection, late publication/close, cancellation failure/retry, callback claim, observer failure and lock isolation. A real ScheduledThreadPoolExecutor smoke runs on JVM/Native with latch-based completion and owner shutdown, never sleep-based correctness assertions. The application-test driver remains virtual-clock-only and does not wait for wall-clock jobs.
