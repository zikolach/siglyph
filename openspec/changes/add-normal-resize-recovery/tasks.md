## 1. Public recovery and diagnostics API

- [x] 1.1 Add shared `NormalResizeRecoveryProvider` and `NormalResizeRecoveryContext` public types with explicit Scaladoc for synchronous invocation, retryability, previous/current strict row ownership, text-only output, platform scope, and non-goals.
- [x] 1.2 Add trailing `TUIOptions.normalResizeRecovery` with an absent default and source-compatibility coverage for existing option construction.
- [x] 1.3 Add fail-fast startup validation for provider combinations other than normal-screen `PreserveScrollback`, with tests proving failure precedes backend startup and terminal output.
- [x] 1.4 Add bounded resize-recovery outcome/failure diagnostic enums and an additive `TUIDiagnosticEvent` case without changing existing event arities.

## 2. Resize eligibility and owner scheduling

- [x] 2.1 Add focused tests showing only a committed width/height delta can invoke recovery, while same-size notifications, startup, append, ordinary input/action/structure/overlay rendering, image cell-size updates, and generic forced redraws cannot.
- [x] 2.2 Track coalesced geometry-change recovery eligibility and generation separately from existing force/clear flags without consuming ingress capacity or adding a scheduler category.
- [x] 2.3 Snapshot positive width, height, and resize generation for one recovery Render attempt and preserve existing six-category owner fairness.
- [x] 2.4 Test and preserve owner serialization, lock separation, non-recursive follow-up work, and absence of concurrent callbacks/component renders while provider code executes.
- [x] 2.5 Add deterministic stale-candidate tests proving resize during live/provider rendering emits no stale bytes, mutates no baseline, yields to ordinary selection, and retries only latest coalesced geometry.

## 3. Budgeted text-only provider rendering

- [x] 3.1 Add tests that render/compose/validate the live frame before provider invocation and calculate `maxRows` from both previous and current terminal height minus each live-frame physical footprint, including viewport growth, the empty-frame anchor, overlay extension, and typed-control reserved rows.
- [x] 3.2 Implement positive-context creation and skip provider invocation when the live frame fills or exceeds the viewport.
- [x] 3.3 Invoke the provider on the owner outside lifecycle/write locks and retain no provider result beyond the current unpublished Render candidate.
- [x] 3.4 Apply existing ordinary-line ANSI allowlisting, Unicode-aware width sanitization, aggregate sanitization accounting, redacted content-sample handling, and line resets to recovery lines.
- [x] 3.5 Reject output above `maxRows` before terminal output without truncation or partial publication; cover empty, exact-boundary, oversized, hostile-control-looking, styled, Unicode, and over-wide lines.

## 4. Combined recovery/live render commit

- [x] 4.1 Add raw-output and virtual-viewport tests for exact synchronized clear/cleanup/recovery/live/cursor ordering, `CSI 3 J` absence, autowrap restoration, and no extra row transition for empty recovery.
- [x] 4.2 Extend the preserve-scrollback full-render planner to assemble recovery and the retained live frame in one TUI-owned terminal write while keeping typed controls attached only to the live frame.
- [x] 4.3 Commit only the prepared live frame to `previousFrame`, previous dimensions, retained control state, base layout, and overlay layouts after successful output.
- [x] 4.4 Update live-frame-relative `cursorRow` and physical `latestFrameStartRow` for recovery prefixes, terminal scrolling, zero-row frames, and over-height live frames.
- [x] 4.5 Add hardware-cursor and coordinate-aware mouse tests proving structured cursor placement and retained layout routing use the relocated live-frame origin.
- [x] 4.6 Preserve retained Kitty replacement/retransmission and iTerm2 live-frame behavior during recovery without assigning image identity or cleanup ownership to recovery text.

## 5. Append, failure, lifecycle, and diagnostics integration

- [x] 5.1 Add shared chronology coverage for append `A`, resize recovery of `A`, append `B`, and retained live output in exact `A -> B -> live frame` order.
- [x] 5.2 Verify later partial/full retained redraws and multiple FIFO appends clear only the live region and never compare, repaint, or erase the committed recovery prefix.
- [x] 5.3 Test provider exceptions and row-budget violations as pre-output fail-fast failures with normal single-owner cleanup and no leaked recovery content.
- [x] 5.4 Test backend failure during the combined write as write-category failure with idempotent terminal restoration and no rollback/success claim.
- [x] 5.5 Cover stop and runtime-failure races before recovery publication, proving queued ordinary recovery work is discarded and no recovery callback extends Stopping or Cleaning.
- [x] 5.6 Emit completed, stale-discarded, provider, row-budget, and write recovery diagnostics with exact bounded counts/generation and verify text, exception messages, escape source, objects, controls, and payloads remain redacted.

## 6. Portable and terminal conformance coverage

- [x] 6.1 Keep all semantic `VirtualTerminal` recovery contracts in shared core tests and run the same suites on JVM and Scala Native.
- [x] 6.2 Extend JVM PTY conformance to verify viewport-clear/recovery/live/append byte ordering, synchronized output, forbidden `CSI 3 J` absence, and terminal lifecycle restoration without emulator-persistence claims.
- [x] 6.3 Add repeated/coalesced width and height resize coverage, including large height growth bounded by the previous viewport, with bounded semantic transcript selection and verify no TUI-owned complete transcript or provider output cache is introduced.
- [x] 6.4 Confirm the implementation changes no terminal backend API and adds no third-party runtime dependency.

## 7. Documentation and release notes

- [x] 7.1 Document configuration and a bounded semantic-tail provider example in README, including current-width reflow, oldest-to-newest output, retryability, speed expectations, and append chronology.
- [x] 7.2 Update `docs/runtime-diagnostics.md` for recovery events, redaction, strict row-budget failure, and terminal-dependent survivor/deduplication limits.
- [x] 7.3 Extend `docs/interactive-smoke.md` with repeated normal-screen recovery checks in Kitty, iTerm2, and a conventional terminal, clearly separating PTY ordering from emulator persistence.
- [x] 7.4 Update porting notes where recovery intentionally extends or differs from current `pi-tui`, and add a user-visible changelog entry referencing issue #56.
- [x] 7.5 Verify all new public types and options have Scaladoc covering JVM/Native parity, lifecycle eligibility, failure behavior, and text-only/typed-control non-goals.

## 8. Validation

- [x] 8.1 Run focused shared recovery, append, TUI concurrency, diagnostics, virtual-terminal, and JVM PTY suites.
- [x] 8.2 Run `mill core.test`, all relevant Scala Native tests with supported Clang 16+, and `mill __.compile`.
- [x] 8.3 Run `mill scalafmtCheck`, `mill scalafixCheck`, `scripts/test-terminal-pty.sh`, and any documented manual emulator smoke checks available in the environment.
- [x] 8.4 Run `openspec validate --all --strict`, `git diff --check`, and a final dependency/public-API/redaction review before reporting implementation complete.
