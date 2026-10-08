## 1. Framing policy

- [x] 1.1 Add idle-based deadlines in the shared parser: 35 ms for lone Escape and 75 ms for other pending non-paste input, preserving explicit flush and paste behavior.
- [x] 1.2 Replace both periodic flush paths with generation-safe deadline waits through ordered delivery, retaining failure reporting and stop/restart behavior.

## 2. Evidence and documentation

- [x] 2.1 Add deterministic shared JVM and Native coverage for fragmented/repeated Escape, Alt UTF-8, CSI, paste, variable gaps, exact bytes, and cutoff boundaries.
- [x] 2.2 Add bounded stream and lifecycle regressions for read gaps and stop/flush wakeup; review Native worker semantics.
- [x] 2.3 Document the user-observable input timing and limitations in README and terminal conformance docs.
- [x] 2.4 Run focused and aggregate JVM/Native tests, packaging, PTY, format, lint, and strict OpenSpec validation on the final source.
