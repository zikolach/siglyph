# Terminal resize redraw full-clear behavior

## Summary

A Sigma manual test using Siglyph as the live console showed stale prompt-frame cells after a terminal font-size or geometry change.

The observed artifact was an old horizontal border and cursor block remaining in scrollback while the new prompt frame was rendered at the new width.

## Current behavior

Siglyph now follows upstream `pi-tui` resize behavior for width and height changes:

- Resize redraw emits `ESC[2J ESC[H ESC[3J`.
- The screen is cleared.
- The cursor moves home.
- Scrollback is cleared after the screen clear.
- The current frame is rendered at the new terminal width.
- Alternate screen mode is not used.

This behavior prevents stale full-width frame cells after terminal reflow.

## Evidence

The current implementation owns resize redraw in `core/src/scalatui/core/TUI.scala`:

- Terminal resize callbacks call `requestRender()` and `flushRender()`.
- Width or height changes call `fullRender(frame, width, height, clear = true)`.
- The clear sequence matches upstream `pi-tui`: `ESC[2J ESC[H ESC[3J`.

Current coverage in `core/test/src/scalatui/core/TUISuite.scala` verifies:

- width changes use `pi-tui` full-clear redraw;
- height changes use `pi-tui` full-clear redraw;
- font-size-like resize uses `pi-tui` full-clear redraw;
- alternate screen mode is not emitted.

## Reproduction outline for regressions

1. Start a Siglyph TUI that renders full-width prompt borders.
2. Keep the prompt visible.
3. Change terminal font size or terminal geometry so the column count changes.
4. Verify that one prompt frame remains visible at the current terminal width.

## Non-goals

- Do not fix this in Sigma with prompt-specific redraw hacks.
- Do not use alternate screen mode as the resize strategy.
