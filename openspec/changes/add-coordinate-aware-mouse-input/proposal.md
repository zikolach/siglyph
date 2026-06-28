## Why

Terminal mouse wheel input is useful for panels, editors, selection lists, and overlays, but siglyph currently routes input by focus only and does not retain component bounds after rendering. Coordinate-aware mouse input lets applications scroll the component under the pointer while keeping keyboard focus behavior unchanged.

Upstream `pi-tui` buffers mouse escape sequences but does not expose public mouse routing. This change is an intentional siglyph extension, not parity work.

## What Changes

- Add an opt-in terminal mouse mode for interactive backends.
- Add public typed mouse input through `TerminalInput.Mouse(...)`.
- Parse SGR mouse sequences into typed mouse events with terminal cell coordinates, button or wheel action, and modifiers.
- Retain component bounds from the latest render so the TUI can route coordinate-aware mouse events.
- Route mouse events through visible overlays first, then nested base components by bounds.
- Add mouse wheel scrolling for `Editor`, `SelectList`, `SettingsList`, and autocomplete suggestion overlays.
- Preserve existing keyboard focus and keyboard input routing unless a mouse event explicitly changes focus.
- Document that mouse support is opt-in because terminal mouse reporting can affect normal terminal text selection.
- Do not add runtime dependencies.

## Capabilities

### New Capabilities
- `mouse-input`: Opt-in typed terminal mouse input, coordinate-aware routing, and wheel scrolling behavior.

### Modified Capabilities
- `terminal-runtime`: Add opt-in mouse protocol lifecycle, SGR mouse parsing, and typed mouse event delivery.
- `component-rendering`: Add retained component bounds for coordinate-aware routing while preserving current render output semantics.
- `developer-api`: Expose public mouse input models and document opt-in behavior without adding runtime dependencies.
- `text-editing`: Add coordinate-aware wheel scrolling for editor, input-adjacent autocomplete, and selector-style components.

## Impact

- Public API: additive `TerminalInput.Mouse` model and mouse-related data types.
- Runtime: terminal backends gain opt-in mouse protocol enable/disable handling.
- Rendering: TUI keeps a latest-render component bounds tree for hit testing.
- Components: scrollable components handle mouse wheel events when routed by bounds.
- Tests: parser, buffer, virtual terminal, routing, overlay, and component scroll tests.
- Documentation: README, Scaladoc, smoke docs, and porting notes need updates.
- Dependencies: no new runtime dependencies.
