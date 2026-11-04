## 1. Implementation

- [x] Add a shared protocol session recording obligations before enables and clearing after successful disables.
- [x] Delegate both backends while preserving platform cleanup ordering, failure wrappers and raw-mode ownership.
- [x] Add fake-output tests for failed enables/disables, captured mouse modes, idempotence and Kitty cancellation.
- [x] Document architecture and compatibility invariants.

## 2. Verification

- [x] Shared session tests and both backend compilations pass.
- [x] Full JVM/Native suites under a PTY and six dedicated lifecycle tests pass after integration. Earlier no-TTY JVM failures are resolved by running under a PTY.
- [x] Full-tree formatting/lint and all-module compilation pass.
- [x] This OpenSpec change validates strictly; global validation retains five pre-existing overlong-requirement warnings.
