## Ownership and registration
ApplicationScope.schedule(scheduler, delay)(update) and scheduleEvery(scheduler, initialDelay, interval)(task => update) own the returned registration, not the scheduler. A distinct maxScheduled limit (default 64, 1..4096) includes unresolved registration/cancellation obligations. Open-scope count continues to include scopes with unresolved cleanup. A recurring callback receives its own handle, avoiding self-referential initialization races.

Reserve the timer before invoking provider code outside lifecycle locks. Each occurrence has a registration identity, publication flag, fired flag and delivery flag. A provider callback arriving before schedule returns only records its arrival; user delivery waits for handle publication. Closing in that window disarms user code and cancels the late handle. A provider throwing before returning a handle remains responsible for its unreturned resources; scope references/admission are released and user delivery is disarmed.

## Delivery and recurrence
Only one occurrence can be registered or awaiting UI delivery per timer. A due occurrence enters the existing scoped update queue. Full/Closed admission stops the timer and is visible in lastSubmission; there is no silent retry or drop-and-continue policy. User code is claimed under the lifecycle lock and invoked outside it. Cancelling queued delivery prevents user code even when the provider already fired.

Recurring work is fixed-delay from completion of serialized UI delivery, not fixed-rate: no catch-up ticks or overlapping callbacks. Rearm runs from the runtime drain owner, so early provider callbacks queue rather than recurse into user code. Initial registration errors throw to the caller; later rearm failures enter the TUI failure channel. Callback failures stop recurrence and retain the original error. Handles expose cancellation/done/submission/failure state.

## Closure and cleanup
Scope/TUI cutoff clears callbacks and disarms registrations under the lifecycle lock. Physical cancel calls happen outside it. Successful cancellation or an already-fired registration releases its slot. Failed cancellation keeps the handle for retry. In-progress publication remains observable and is not guessed complete.

Explicit scope close attempts cancellation then waits for an executing callback on another thread. Self-close establishes the cutoff without waiting for itself; a still-publishing registration can leave it non-quiescent. Successful external close requires no scheduled cleanup obligation; otherwise an explicit pending-cleanup error is reported. Scope quiescence includes scheduled registrations, not only immediate callbacks.

TUI cleanup attempts every timer cancellation independently of terminal restoration. Cancellation failure/in-progress publication keeps the runtime in Cleaning and rejects restart, even if the terminal is already restored. Explicit stop retries timer obligations without repeating completed backend/screen restoration. Late publication attempts cancellation itself; after obligations resolve, explicit stop completes the runtime lifecycle.

Independent component effects and external producers are not implicitly owned. Schedulers are never closed by a scope. Provider schedule/cancel and user callbacks must be finite/nonblocking; the library does not sandbox them or interrupt running user code.

## Validation
Shared JVM/Native tests cover one-shot/recurring delivery, cancellation after provider fire but before UI claim, self-cancel, scope/TUI stop, shared providers, overload, registration and rearm failures, early fire/late publication, cleanup retry and terminal shutdown despite cancel failure. Real executor smoke uses latches, not sleep assertions. Manual-clock fixture cleanup must see no leaked scope-owned callbacks.
