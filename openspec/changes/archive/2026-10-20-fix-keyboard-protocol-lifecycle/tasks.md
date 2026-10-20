# Tasks

## 1. Keyboard state and backend cleanup

- [x] 1.1 Require exact complete keyboard response matching in the shared negotiator; verify valid zero flags, malformed frames, duplicate replies, and deadline boundaries with deterministic core tests.
- [x] 1.2 Update JVM and Native hooks to cancel pending negotiation without a pop and preserve active or failed-enable cleanup; verify pending stop, repeated stop, failed disable retry, and restart rejection in backend suites.
- [x] 1.3 Document pending cancellation and active cleanup in keyboard capability Scaladoc; verify that the documented behavior matches backend tests.

## 2. Runtime orchestration

- [x] 2.1 Request keyboard negotiation during TUI startup only on capable backends through the existing write boundary; verify automatic request, nonblocking startup, and unsupported-backend behavior with virtual capability fakes.
- [x] 2.2 Correlate complete keyboard replies and queue ordered response acceptance outside the lifecycle lock; verify fragmented and duplicate replies, stale rejection, unrelated raw replay, paste preservation, and Shift+Enter followed by ordinary Enter in core tests.
- [x] 2.3 Cancel negotiation through lifecycle cleanup and prevent response work after stop; verify stop races, startup and runtime failures, protocol/frame write serialization, and cleanup idempotence with controlled tests.
- [x] 2.4 Update README and docs/interactive-smoke.md with automatic negotiation, legacy limitations, and a Ghostty-through-Herdr Shift+Enter smoke procedure; inspect the docs against tested behavior.

- [x] 2.5 Retain alternate-screen ownership after repeated keyboard cleanup failure and support restoration retry through TUI.stop(); verify repeated failure, restart rejection, retry serialization, finite callback cutoffs, no repeated successful cleanup, and failure reporting without an indefinite run wait; document the retry contract.

Focused verification for task 2.5 passed on JVM with `mill core.test.testOnly scalatui.core.TUIKeyboardProtocolSuite scalatui.core.TUIConcurrencySuite scalatui.core.TUITerminalFailureSuite scalatui.core.TUISuite scalatui.core.FullscreenViewportSuite scalatui.terminal.KittyKeyboardProtocolSuite`. Changed Scala files were formatted with `scalafmt`. `openspec validate fix-keyboard-protocol-lifecycle --strict` passed. Group 3 integration verification and physical terminal smoke checks remain with the parent.

## 3. Integration verification

- [x] 3.1 Run core.test, terminalJvm.test, coreNative.test, terminalNative.test, and all-module compilation; record results and any actual execution blockers.
- [x] 3.2 Run mill scalafmtCheck, mill scalafixCheck, and openspec validate --all --strict; resolve scoped failures and record results.
- [x] 3.3 Review the implementation against every delta scenario and inspect the final diff for scope; record any remaining physical terminal validation gap explicitly.

## Verification results

- Passed all core and terminal JVM and Native tests under a macOS PTY with `script -q /tmp/siglyph-keyboard-tests.log mill --no-daemon --ticker false '{core,terminalJvm,coreNative,terminalNative}.test'`. The brace selector runs every named module; separate bare task names would be arguments to the first test task.
- Passed all-module compilation, formatting, and Scalafix with `mill --no-daemon --ticker false __.compile + scalafmtCheck + scalafixCheck`.
- Passed `./scripts/test-terminal-pty.sh` and `git diff --check`.
- Passed `openspec validate fix-keyboard-protocol-lifecycle --strict` before sync and archive.
- Ran `openspec validate --all --strict`. The change and terminal-runtime spec pass. Five unchanged specs fail strict validation because existing requirement descriptions exceed 500 characters: autocomplete, component-rendering, developer-api, image-rendering, and normal-resize-recovery. These failures were present before implementation and remain outside this fix.
- Independent review found no blocking defects after O2-1. The reviewer mapped negotiation, deadline handling, bounded input, cleanup retries, screen ownership, callback cutoffs, and failure propagation scenarios to the focused suites.
- Physical Ghostty-through-Herdr forwarding remains unverified. The procedure is in docs/interactive-smoke.md. Encoded-input and PTY tests do not establish physical modifier forwarding.

## Workflow follow-up

- Sync the terminal-runtime delta and archive after implementation, validation, and review approval.
- Add user-facing fixes under CHANGELOG.md Unreleased and create a PR containing implementation, tests, documentation, synced specs, and archived artifacts.
- Publish a Siglyph release only with explicit approval.
- Update Sigma's dependency and run its input regressions only with approval for that repository and dependency change.
- Verify physical Shift+Enter in Ghostty through Herdr when that interactive environment is available.
