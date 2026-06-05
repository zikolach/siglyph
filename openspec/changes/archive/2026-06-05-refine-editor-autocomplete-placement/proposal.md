## Why

Editor autocomplete now works through the overlay stack, but placement relies on demo code manually computing terminal rows. This is a workaround: `pi-tui` attaches editor suggestions to the editor area, so `scala-tui` should provide reusable editor-owned placement that preserves the overlay architecture without forcing applications to duplicate layout math.

## What Changes

- Add editor-owned autocomplete placement logic so suggestion overlays appear adjacent to the rendered editor area by default.
- Replace the shared demo's manual autocomplete row calculation with the editor's built-in placement behavior.
- Add an overlay placement mode or anchor abstraction suitable for component-bottom/editor-adjacent placement without requiring a full runtime layout tree.
- Ensure placement updates on render and resize so suggestions continue tracking wrapped/multiline editor height.
- Preserve terminal scrollback and frame position when resize occurs while autocomplete overlays are visible.
- Preserve existing generic overlay APIs and allow applications to override placement options when desired.
- Keep current async autocomplete provider behavior unchanged.

## Capabilities

### New Capabilities

<!-- No new standalone capability; this refines existing overlay/editor behavior. -->

### Modified Capabilities
- `component-rendering`: Overlay placement gains component-adjacent/editor-owned behavior while preserving existing hybrid terminal positioning.
- `developer-api`: Editor autocomplete placement APIs become easier to use without application-side terminal row calculation.
- `text-editing`: Editor autocomplete suggestions are positioned by the editor near its rendered area by default, matching `pi-tui` placement intent more closely.
- `terminal-runtime`: Resize redraw behavior is refined to repaint the existing TUI frame region without clearing scrollback or jumping the frame to the top of the terminal.

## Impact

- Affected APIs: `Editor`, `EditorOptions`, autocomplete overlay placement options, and possibly small overlay anchor/placement model types.
- Affected demo: shared interactive demo removes manual row computation and relies on editor placement.
- Affected tests: editor autocomplete placement tests, interactive demo placement tests, overlay placement tests, resize/narrow-width regression tests.
- Dependencies: no new third-party runtime dependencies.
