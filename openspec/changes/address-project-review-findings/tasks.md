## 1. Logical Text Lines

- [x] 1.1 Add a shared logical-line wrapping helper that normalizes CRLF, CR, and LF before ANSI-aware width wrapping without changing ordinary C0 sanitization.
- [x] 1.2 Migrate `Text` and affected built-in/extras text paths to emit logical rows through the new helper while preserving ANSI state and width contracts.
- [x] 1.3 Add focused tests for empty lines, all line-ending forms, narrow wrapping, wide graphemes, and SGR/OSC 8 spans crossing logical boundaries.

## 2. Typed Keyboard Parity

- [x] 2.1 Refactor shared fixed-sequence decoding into an auditable table-driven representation with bounded raw fallback.
- [x] 2.2 Add common CSI/SS3 F1-F12, SS3 cursor/navigation, and legacy modifier sequence mappings to the typed key model.
- [x] 2.3 Normalize supported control-byte and functional keypad protocol codes and implement safe backend-specific modifier fallbacks where the existing JVM/Native compatibility layers permit.
- [x] 2.4 Add golden, fragmentation, prefix, ambiguity, modifier, and JVM/Native backend-contract tests for the expanded key matrix.
- [x] 2.5 Document terminal-specific key limitations and the raw fallback contract without advertising unsupported platform behavior.

## 3. Session-Owned Image Geometry

- [x] 3.1 Add a source-compatible cell-dimensions accessor to `TUIContext` and store deterministic fallback/current dimensions in each `TUI` instance.
- [x] 3.2 Update cell-size reply correlation to mutate only the receiving runtime and request a render only when that session's value changes.
- [x] 3.3 Add idempotent context propagation for contextual descendants of built-in composite components, including child mutation after attachment and detach cleanup.
- [x] 3.4 Make high-level `Image` runtime sizing read its attached context, preserve fixed sizing, and remove TUI/high-level reads and writes of process-global geometry.
- [x] 3.5 Deprecate or narrow legacy global geometry helpers with migration Scaladoc while preserving low-level source compatibility where feasible.
- [x] 3.6 Add direct, nested, overlay, detached, invalid-response, and concurrent-two-TUI image sizing tests on shared JVM/Native paths.

## 4. Thread-Safe Loader Cancellation

- [x] 4.1 Replace the loader token's check/set Boolean with a shared atomic transition that provides cross-thread visibility and one cancellation winner.
- [x] 4.2 Ensure only the winning cancellation invokes `onCancel` and requests its cancellation render while preserving synchronous callback/error semantics.
- [x] 4.3 Add JVM concurrency and shared idempotence tests plus Scaladoc for visibility, callback, and dependency-free behavior.

## 5. Filesystem Autocomplete

- [x] 5.1 Extend path-completion options with source-compatible current-directory, containment-root, home, absolute, and traversal policies.
- [x] 5.2 Implement canonical containment and symlink checks that allow safe parent traversal without disclosing or walking escaped paths.
- [x] 5.3 Make scan/result bounds, evaluated-set ordering, quoting, cancellation checkpoints, and determinism claims match the specified behavior.
- [x] 5.4 Add an opt-in iterative recursive attachment provider with depth, visited-entry, result, containment, and cancellation bounds.
- [x] 5.5 Add temporary-filesystem tests for relative, parent, home, absolute, quoted, symlink, scan-limit, recursive, and cancellation cases.
- [x] 5.6 Update the interactive demo and smoke examples to exercise only policies they explicitly configure.

## 6. Markdown Render Caching

- [x] 6.1 Add a source-compatible renderer cache-generation/invalidation contract for immutable and stateful custom renderers.
- [x] 6.2 Implement a one-entry `Markdown` component cache keyed by text, effective geometry, renderer identity, and renderer generation; clear it from setters and `invalidate()`.
- [x] 6.3 Add parser-call-count, text, width, padding, generation, fallback, fatal-error, bounded-retention, JVM, and Native cache tests.

## 7. Runtime Diagnostics and Resize Policy

- [x] 7.1 Add shared structured diagnostic event/observer types and a two-value normal-screen resize clear policy to `TUIOptions` with legacy defaults.
- [x] 7.2 Emit ordered redacted write, redraw, resize, and lifecycle metadata only when enabled, outside runtime locks, and disable a throwing observer without blocking cleanup.
- [x] 7.3 Route normal-screen resize output through the configured policy while leaving alternate-screen behavior unchanged.
- [x] 7.4 Add tests for disabled overhead paths, event ordering/isolation/redaction, observer failure, default full clearing, preserved scrollback, overlays, and alternate screen.
- [x] 7.5 Add Scaladoc and runtime documentation for diagnostic privacy/failure semantics and resize-policy visual trade-offs.

## 8. Terminal Conformance and CI

- [x] 8.1 Improve `VirtualTerminal` only for conformance-critical grapheme width, wide-cell advance, autowrap, cursor, and erase semantics while retaining deterministic fast unit use.
- [x] 8.2 Add bounded platform-aware PTY lifecycle tests and scripts for raw mode, write ordering, resize notification, failure cleanup, and `stty` restoration.
- [x] 8.3 Add a cached macOS CI job for relevant formatting, lint, JVM, Scala Native, and terminal tests while retaining Linux packaging.
- [x] 8.4 Document any explicit platform exclusions and verify PTY failures cannot leave local or CI terminal state unrestored.

## 9. Compatibility Documentation and Completion

- [x] 9.1 Add a version-pinned `pi-tui` compatibility matrix separating API surface, behavioral parity, intentional deviations, and Siglyph extensions with local evidence links.
- [x] 9.2 Align README, porting notes, interactive smoke instructions, and Markdown/autocomplete limitations with implemented defaults and remaining non-goals.
- [x] 9.3 Replace stale post-MVP active-change references and archive the completed `fix-mouse-input-review-findings` change after confirming its deltas are promoted.
- [x] 9.4 Run `mill __.compile`, the complete JVM/Native test suite under a PTY, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`, resolving every failure.
- [x] 9.5 Confirm no production runtime dependency was added and record exact validation commands and any remaining platform gap in the change/PR handoff.

## Validation Handoff

- `mill --no-server __.compile`
- `script -q -e /dev/null mill --no-server __.test`
- `./scripts/test-terminal-pty.sh`
- `mill --no-server scalafmtCheck`
- `mill --no-server scalafixCheck`
- `openspec validate --all --strict`
- `git diff --check`

No production runtime dependency was added; dependency declarations in `build.mill` are unchanged.

The deterministic `VirtualTerminal` conformance model and real PTY lifecycle coverage run on the JVM. Native terminal code is compiled and tested through shared contract suites, but native real-PTY start/stop, deterministic cleanup fault injection, and Windows/ConPTY remain explicit platform gaps.
