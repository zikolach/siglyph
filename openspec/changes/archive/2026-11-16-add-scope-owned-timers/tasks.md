## Implementation
- [x] Add scoped task APIs, bounded registration and fixed-delay recurrence.
- [x] Handle publication/delivery races and expose rejection/failure state.
- [x] Integrate cancellation obligations with scope and TUI cleanup/retry.

## Verification and delivery
- [x] Add deterministic scheduler/cleanup race tests and real executor smoke on JVM/Native.
- [x] Run regressions, quality, docs, examples and strict OpenSpec validation.
- [x] Open a stacked PR and update #99/#94 without claiming file-backed integration.
