## Why

`Input` and `Editor` now emit a cursor marker near their fake cursor, but the runtime does not yet translate that marker into a real terminal cursor position. Adding opt-in hardware cursor placement improves IME/composition behavior and terminal accessibility while preserving the existing fake-cursor rendering contract.

## What Changes

- Add an opt-in TUI/runtime setting for hardware cursor positioning.
- Scan the final rendered and overlay-composited frame for `CursorMarker.Sequence` (or its marker abstraction), compute the terminal row/column where the marker occurs, and strip the marker before output.
- Move the hardware cursor to the computed terminal position after frame output when a marker is present and the feature is enabled.
- Preserve fake cursor rendering and existing stop positioning behavior when hardware cursor positioning is disabled or no marker is present.
- Keep the implementation ANSI-aware, Unicode-display-width-aware, dependency-free, and shared across JVM and Scala Native.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `terminal-runtime`: adds opt-in marker-driven hardware cursor placement and marker stripping to the TUI write path.
- `component-rendering`: clarifies zero-width marker handling after final composition, including overlays and ANSI-aware row/column calculation.
- `text-editing`: tightens the contract between focused editing components' marker emission and runtime hardware cursor behavior.
- `developer-api`: adds backend-independent public configuration/documentation for enabling or disabling hardware cursor positioning.

## Impact

- Affected code: `TUI` render/write path, `CursorMarker`, ANSI/text measurement utilities, `Input`, `Editor`, virtual terminal assertions, demo configuration/docs.
- Public API: additive runtime option for hardware cursor positioning; no breaking changes.
- Dependencies: no new runtime dependencies.
- Platforms: shared core behavior must work consistently with JVM, Scala Native, and virtual terminal backends.
