## Why

The fragment-flush worker currently runs every 75 ms from startup, so a lone Escape can wait almost a full period and an incomplete CSI/Alt sequence can be cut off immediately after a read when the periodic tick races a fragment. Framing should depend on time since the last pending fragment, not on the worker's startup phase.

## What Changes

- Give a lone pending Escape a 35 ms idle cutoff; retain a 75 ms idle cutoff for other incomplete non-paste framing.
- Wake the flush worker when parser state changes, using the existing ordered-delivery lock to serialize deadline checks with reads, stop, and restart.
- Keep explicit EOF/zero-read flush semantics, exact raw bytes, complete Alt/CSI/repeated-Escape events, and indefinitely held active paste unchanged.
- Add deterministic shared JVM/Native deadline tests and bounded stream/lifecycle tests. No output chunking or dependencies change.

## Capabilities

### Modified Capabilities
- `terminal-runtime`: Specify idle-based incomplete-input cutoff and lone-Escape timing.

## Impact

- Public behavior: interactive Escape is dispatched after a shorter idle window; a continuation arriving after that window starts a new input frame. As with all worker deadlines, OS scheduling can delay actual callback delivery.
- Backends: shared StreamTerminal and Native PosixTerminal use the same deadline policy.
- Tests and docs: shared parser and stream regressions, terminal conformance notes, README, and promoted spec.
