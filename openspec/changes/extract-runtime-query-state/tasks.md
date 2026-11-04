## 1. Extract query bookkeeping

- [x] 1.1 Move flight slots, identities, write reservations, and subscriber transitions into QueryCoordinator.
- [x] 1.2 Preserve TUI lifecycle locking, queue/cutoff routing, outside-lock writes and callback invocation.
- [x] 1.3 Preserve non-destructive classifier snapshots, empty-flight cancellation, and emitted background/scheme-only stop behavior.

## 2. Verify compatibility

- [x] 2.1 Add deterministic helper tests for sharing, cancellation, completion snapshots, stop, failed/racing emission, and callback claims.
- [x] 2.2 Run existing TUISuite, TUIConcurrencySuite, and TUIKeyboardProtocolSuite plus QueryCoordinatorSuite.
- [x] 2.3 Validate OpenSpec artifacts and scoped formatting.

Validation notes: all four focused suites initially passed (174 tests). After renderer integration and formatting, full JVM/Native tests and dedicated PTY lifecycle tests passed. Full-tree formatting/lint and all-module compilation passed. This change validates strictly; global validation retains five pre-existing overlong-requirement warnings.
