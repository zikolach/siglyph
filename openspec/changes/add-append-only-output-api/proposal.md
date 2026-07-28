## Why

Normal-screen terminal applications often need to append rich output above a continuously redrawn live frame while preserving shell scrollback. Typed component output correctly keeps terminal controls under TUI ownership, but there is no TUI-owned append-only path for those controls. Consumers must currently duplicate raw protocol encoding, retain an ever-growing live frame, degrade to plain text, or risk later frame cleanup removing output intended for scrollback.

## What Changes

- Add a public, callback-completed normal-screen append operation that accepts a detached one-shot `Component` and renders it inside the TUI runtime owner.
- Preserve the existing `flushRender()` contract: uncontended calls drain synchronously, while reentrant or concurrent calls remain non-waiting. The append completion callback is the authoritative per-operation completion boundary.
- Admit append work only for a running normal-screen TUI configured with `NormalResizeClearPolicy.PreserveScrollback` and an already committed live frame.
- Render with the current terminal width, session-owned image cell dimensions, a restricted one-shot `TUIContext`, and component-provided terminal capabilities. A resize may discard and retry an unpublished candidate; exactly one candidate is published.
- Serialize append operations with input callbacks, structural changes, active-frame rendering, resize handling, terminal writes, queries, controls, and cleanup through the existing TUI runtime owner.
- Validate and sanitize the complete `ComponentRender` before output. Reject cursor placements and Kitty cleanup controls, and keep raw terminal-control encoders private.
- Remap appended Kitty image IDs to fresh runtime-owned IDs and retain only a bounded ownership ledger so later retained-frame cleanup cannot delete append-only output.
- Place append output above the active live frame, reserve its rows, redraw the retained frame, restore its hardware cursor, and update mouse frame-origin accounting without changing focus, overlays, or retained layout ownership.
- Keep successfully appended controls outside retained-frame replacement, resize retransmission, and shutdown cleanup ownership.
- Provide typed admission/completion failures and redaction-safe diagnostics. Pre-publication failures emit no append bytes; backend-write failures use normal runtime failure and terminal restoration without claiming rollback.
- Add shared JVM/Scala Native contract tests, automated PTY byte-order and restoration tests, and documented manual Kitty/iTerm2 emulator smoke coverage.

## Capabilities

### New Capabilities

- `append-only-output`: Define TUI-owned, typed, append-only component rendering for normal-screen scrollback alongside an independently retained live frame.

### Modified Capabilities

None.

## Impact

- Siglyph shared core: `TUI`, runtime work serialization, frame/output planning, typed result models, diagnostics, mouse frame-origin tracking, and typed-control ownership.
- Terminal backends: no component knowledge or new raw protocol authority; existing JVM and Scala Native writes remain byte transports owned by the shared runtime.
- Image rendering: detached `Image` components can be appended using their configured `TerminalCapabilities` and the TUI session's image cell dimensions.
- Validation: shared virtual-terminal suites, concurrency/lifecycle coverage, automated PTY sequence checks, and manual terminal-emulator scrollback checks.
- Downstream consumers: append users must opt into `NormalResizeClearPolicy.PreserveScrollback` and observe the callback when operation outcome matters.
- Dependencies: no new runtime dependency.
