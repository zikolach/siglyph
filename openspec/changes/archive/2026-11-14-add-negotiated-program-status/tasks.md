## 1. Implementation

- [x] Add conservative Herdr/Rex/Orca and Alacritty fallback profiles; preserve overrides and multiplexers.
- [x] Add optional typed OSC 7501 capability, positive negotiation, bounded UTF-8 formatter and retryable session state.
- [x] Integrate both interactive backends and serialized TUI output/cleanup; consume complete late replies without application ingress callbacks.
- [x] Document public API, ownership, bounds, upstream pin and limitations.

## 2. Verification

- [x] Run shared JVM and Native status/profile suites (11 tests each) and TUISuite (92 tests each).
- [x] Run backend suites under `script -q /dev/null mill --no-server`: SttyTerminalSuite 24/24 and PosixTerminalSuite 25/25.
- [x] Format assigned Scala sources.
- [x] Validate OpenSpec change strictly.

Initial backend runs without an attached TTY failed (`/dev/tty: Device not configured`); rerunning with a PTY and no Mill server resolved the environmental issue and exposed unconditional cleanup instrumentation, fixed by attempting only outstanding status obligations. Native initially rejected java.util.Locale; replaced with portable ASCII enum encoding. Focused tests now pass.

Final integrated verification: quality and all-module compilation pass; 2,254 JVM/Native tests plus ten dedicated real-PTY tests pass; strict OpenSpec validation23/23 passes.

## Review F1: independent backend shutdown

- [x] Reproduce zero backend stop calls after status-clear failure (three of four new shared cases fail before the fix).
- [x] Attempt status reset and backend stop independently, preserve primary/suppressed failures, and retain status-only retries without repeating successful backend shutdown.
- [x] Verify shared cleanup/lifecycle suites on JVM and Native (89 tests each), JVM real-PTY restoration (11 tests), Native backend cleanup (26 tests), and the full JVM/Native suite (2,264 tests).
- [x] Quality and strict OpenSpec validation pass (23/23). Independent code review found no additional defect; its test attempt was blocked by the concurrent Mill build, so test evidence is from the parent runs.
