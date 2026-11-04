## 1. Implementation
- [x] Reproduce self-interruption in shared tests (2 failed / 31) and InterruptedException in real PTY callback cleanup (3 failed / 9).
- [x] Avoid interrupting the cleanup owner in shared, JVM and Native worker shutdown.
- [x] Preserve other-worker interruption and existing interrupt flags in worker-stop signaling.

## 2. Verification
- [x] Focused shared tests pass on JVM and Native (31 each); Native backend tests pass (25); dedicated real PTY tests pass (10), including fullscreen typed Ctrl+C.
- [x] Run the actual fullscreen JVM Ctrl+C process oracle three times: clean exit, restored termios/cursor/paste/alternate screen, no InterruptedException.
- [x] Full quality/compilation pass; full JVM/Native suite passes (2,164 tests), plus 10 dedicated PTY tests. Local 0.10.0 packaging and all ten artifact POM versions pass; no publication occurred.
- [x] Strict validation passes before promotion (19 items); promote and archive this verified fix, then rerun strict validation.
