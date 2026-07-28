## Why

Normal-screen terminal applications often need to append rich output above a continuously redrawn live frame while preserving shell scrollback. Typed component output correctly keeps terminal controls under TUI ownership, but there is no TUI-owned append-only path for those controls, forcing consumers to choose between duplicating raw protocol encoding, retaining an ever-growing live frame, degrading to plain text, or allowing later frame cleanup to remove output that was intended for scrollback.

## What Changes

- Add a public, normal-screen append operation that accepts a `Component` and renders it once using the owning TUI's current width, `TUIContext`, terminal capabilities, and session-owned image cell dimensions.
- Serialize append operations with input callbacks, structural changes, active-frame rendering, resize handling, terminal writes, and cleanup through the existing TUI runtime owner.
- Validate the resulting `ComponentRender` before output and preserve ordinary lines, `TerminalControlPlacement` values, and structured cursor-independent geometry without exposing a public raw-control encoder.
- Place appended output above the active live frame, reserve its rendered rows, and redraw the live frame without corrupting prompt text, overlays, hardware cursor placement, or previously appended scrollback.
- Transfer successfully appended controls out of retained-frame replacement ownership so later active-frame changes and TUI shutdown do not emit cleanup that removes append-only output.
- Include queued append work in the existing `flushRender` completion boundary; validation and planning failures publish nothing, while terminal-write failures follow normal runtime failure and cleanup semantics without reporting success.
- Reject append-only rendering outside a running normal-screen lifecycle with an explicit bounded failure; alternate-screen sessions keep retained-frame semantics.
- Add shared JVM and Scala Native contract tests plus JVM PTY coverage for text, Kitty and iTerm2 controls, image row reservation, resize, concurrent append/render activity, failure atomicity, and terminal cleanup.

## Capabilities

### New Capabilities

- `append-only-output`: Define TUI-owned, typed, append-only component rendering for normal-screen scrollback alongside an independently retained live frame.

### Modified Capabilities

None.

## Impact

- Siglyph shared core: `TUI`, runtime work serialization, frame/output planning, typed-control ownership, and public result/failure models.
- Terminal backends: no new raw protocol authority; existing JVM and Scala Native writes continue through the shared runtime owner.
- Image rendering: `Image` and other typed-control components become usable for append-only normal-screen output without exposing payload encoders.
- Validation: shared virtual-terminal suites, concurrency/lifecycle coverage, and interactive JVM PTY smoke tests.
- Downstream consumers can migrate direct terminal-control writes to the typed append operation; adopting the API remains an explicit consumer change.
