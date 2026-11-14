## Implementation
- [x] Reproduce delayed reply leakage in shared parser/runtime tests before fixing.
- [x] Add bounded reply-header recognition and delimiter-framed raw mode with cancellation/resynchronization.
- [x] Preserve recognized replies on zero-byte stream reads; keep EOF flush explicit.
- [x] Cover every split, genuine keys, cancellation, overflow and timeout/EOF on JVM and Native.

## Verification
- [x] Run parser/runtime/stream suites on JVM and Native.
- [x] Run formatting/lint and strict OpenSpec validation.

Evidence: all eight initial regressions failed before the fix. Review found a pending-ESC cancellation edge case; it failed before correction. Final reply suite15 plus buffer22 and stream31 pass on JVM and Native within full integration. Full stack: 2,254 tests plus ten dedicated PTY tests pass; quality and23 strict OpenSpec items pass.
