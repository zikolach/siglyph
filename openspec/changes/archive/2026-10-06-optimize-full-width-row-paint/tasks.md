## 1. Contract and regression

- [x] 1.1 Add a shared JVM/Native regression proving exact bytes and metadata for full-width untouched rows and later overpaint.
- [x] 1.2 Capture fixed-workload JVM allocation/time and Native time baselines before production change.

## 2. Implementation

- [x] 2.1 Implement the guarded full-width untouched-row paint path without changing text trust or metadata behavior.
- [x] 2.2 Prove regression green on JVM and Native and capture comparable after measurements.

## 3. Validation

- [x] 3.1 Run focused and aggregate JVM/Native, formatting, lint, change-specific strict OpenSpec, and repository gates; record all-item strict validation and host limits.

## Evidence

- JVM `LayoutSuite` red before the paint change: both new tests failed only on `fullWidthFastPathRows` (0 instead of 7 and 1); exact byte assertions passed. Green afterward with the expanded row set.
- Shared `LayoutSuite` green on `mill core.test.testOnly scalatui.core.LayoutSuite` and `mill coreNative.test.testOnly scalatui.core.LayoutSuite`.
- Fixed 80x24/100-frame benchmark, 3 warmups/5 samples: JVM 315631275 to 209566375 ns median, 629819064 to 460567840 thread-allocated bytes; Native 5868456041 to 4513203909 ns median. Both checksums 11200. Native allocation unsupported. See `docs/performance-benchmarks.md`.
- `mill scalafmtCheck`, `mill scalafixCheck`, `mill __.compile`, PTY-backed `mill --no-daemon __.test`, `./scripts/test-terminal-pty.sh`, and `mill __.jar` passed on Linux. Strict change validation passed with OpenSpec 1.14.1. `validate --all --strict` still fails on five pre-existing canonical specs with overlong requirement warnings (autocomplete, component-rendering, developer-api, image-rendering, normal-resize-recovery); the new change itself passes.
- No macOS host was available; shared Scala Native and JVM tests ran on Linux. No platform-general speedup claim is made.
