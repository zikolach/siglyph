## Implementation
- [x] Add borrowed executor adapter with bounded registration and monotonic time.
- [x] Handle early callback/late future publication and cancellation retry obligations.
- [x] Preserve asynchronous callback/observer failures explicitly.

## Verification and delivery
- [x] Add deterministic provider/race tests and real JVM/Native executor smoke.
- [x] Run regression, quality, source example, OpenSpec and documentation checks.
- [x] Open the next stacked PR and update #99/#94 while keeping scope-owned timers pending.
