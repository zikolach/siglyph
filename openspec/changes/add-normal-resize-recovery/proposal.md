## Why

Normal-screen applications can combine durable `appendToScrollback` history with one mutable retained frame, but `PreserveScrollback` resize currently clears the active viewport without giving the TUI owner a safe place to reconstruct the durable tail that occupied it. Putting that tail in the retained tree reverses later append chronology, while direct terminal writes bypass Siglyph's serialization, sanitization, cursor accounting, diagnostics, and cleanup ownership.

## What Changes

- Add an opt-in `NormalResizeRecoveryProvider` and bounded `NormalResizeRecoveryContext` to shared core, configured through `TUIOptions` only for normal-screen `PreserveScrollback` sessions.
- Invoke recovery only for a committed geometry-changing resize redraw. Render and validate the retained live frame first, reserve its physical rows (including one cursor anchor for an empty frame), and ask the provider for current-width durable tail lines only when the remaining strict budget is positive.
- Make the first recovery API text-only: the provider returns ordinary line strings, not a component or `ComponentRender`, so it cannot acquire cursor or typed-control ownership. Existing ordinary-line ANSI allowlisting, sanitization, width handling, and line resets still apply.
- Reject provider output that exceeds `maxRows` before terminal output. Provider exceptions and invalid output use normal fail-fast runtime cleanup and bounded redacted diagnostics.
- After clearing and homing the active viewport, emit recovery lines followed by the retained live frame in one TUI-owned synchronized render write. Keep only the live frame, controls, cursor, layouts, and focus in retained semantic state.
- Track the live frame's physical origin after the recovery prefix so differential redraw, hardware cursor placement, coordinate-aware mouse input, cleanup, and later appends remain live-frame-relative.
- Preserve chronological append behavior: after recovery, a later append is inserted between the recovered durable tail and the retained live frame.
- Recheck resize generation and dimensions before publication. A stale recovery/live candidate is discarded and recomputed through the existing coalesced Render work rather than published at stale geometry.
- Add redaction-safe recovery diagnostics, shared JVM/Scala Native tests, focused PTY ordering/restoration coverage, manual normal-screen smoke coverage, public Scaladoc, runtime documentation, and a changelog entry.
- Keep transcript storage, survivor inference, asynchronous provider completion, alternate-screen recovery, typed recovery controls/images, and new runtime dependencies out of scope.

## Capabilities

### New Capabilities

- `normal-resize-recovery`: Defines opt-in, bounded, text-only reconstruction of durable normal-screen viewport history before the retained live frame.

### Modified Capabilities

- `append-only-output`: Define append placement and ordering when a resize-recovered durable prefix exists above the retained live frame.
- `component-rendering`: Keep recovery output outside the retained differential baseline while preserving cursor, layout, and frame-origin behavior.
- `terminal-runtime`: Extend preserve-scrollback geometry-change redraws with an optional owner-serialized recovery phase and stale-candidate handling.

## Impact

- Public shared-core API: new provider/context types and one additive `TUIOptions` field.
- Shared runtime: resize cause tracking, render planning, row-budget validation, physical live-frame origin accounting, diagnostics, and failure handling in `TUI`.
- Append integration: later append publication clears and relocates only the retained frame and leaves the recovered prefix in chronological history.
- Tests and docs: shared `VirtualTerminal` suites, concurrency/lifecycle tests, JVM PTY conformance, runtime diagnostics, interactive smoke instructions, README/Scaladoc, and changelog.
- Platform and dependencies: identical JVM/Scala Native core behavior with no backend API change and no new runtime dependency.
