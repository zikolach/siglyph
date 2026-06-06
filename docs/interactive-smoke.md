# Interactive Smoke Coverage

Manual smoke checks for interactive runtime demos and the multiline editor demo. Static utility components are shown by `mill demo.run`; tick-driven `Loader` and `CancellableLoader` are shown in the shared interactive demo actions so their state can change live.

## JVM interactive demo

Run in a macOS/Linux terminal:

```bash
mill interactiveJvmDemo.run
# Optional: enable hardware cursor positioning for cursor-tracking checks
mill interactiveJvmDemo.run -- --hardware-cursor
```

Expected behavior:

- Terminal enters raw mode and hides the cursor.
- Bracketed paste mode is enabled while running and disabled on exit.
- `Ctrl+T` switches focus between the action list and multiline editor, leaving `Tab` available to the focused editor for autocomplete.
- `Enter` submits editor text, selects an action, ticks/cancels loader components through the action list, or accepts a visible autocomplete suggestion.
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event.
- Type `/`, `./`, or `@` in editor input and press `Tab` to show slash/path autocomplete suggestions adjacent to the editor area.
- With suggestions visible, `↑` / `↓` navigates, `Enter` or `Tab` accepts, and `Esc` cancels without changing editor text.
- When the action list is focused, select `Tick loader` to advance the loader frame and `Cancel loader` to update the cancellable loader state.
- Arrow keys, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the buffer.
- `Ctrl+-` undoes, `Ctrl+Y` yanks killed text, `Alt+Y` yank-pops, `Alt+D` / `Alt+Delete` deletes a word forward, and modified word-left/word-right shortcuts move by word when reported by the terminal.
- Pasting more than 10 lines or more than 1000 grapheme clusters inserts a compact `[paste #N ...]` marker; submitting the editor expands the marker back to the original pasted text.
- Resize the terminal narrower and wider; the demo redraws without crashing and every line remains within the visible width.
- Resize terminal height; the existing TUI frame is repainted in place and any visible overlay is re-resolved/clamped to the new dimensions without clearing scrollback.
- For an app/demo configured with `TUIOptions(hardwareCursorPositioning = true)`, type in a focused `Input` or `Editor` and verify the terminal hardware cursor/IME candidate window tracks the fake cursor position. Cursor markers must not appear as visible output, and disabling the option must leave fake-cursor behavior unchanged.
- `Esc` and `Ctrl+C` exit and restore the terminal.

## Scala Native interactive demo

Build:

```bash
mill interactiveNativeDemo.nativeLink
```

Run the linked binary from Mill's output directory in an interactive terminal. Optional flags are passed after the binary path.

```bash
out/interactiveNativeDemo/nativeLink.dest/out -- --hardware-cursor
```

Expected behavior matches the JVM multiline editor demo, including narrow-width and height resize redraw checks, using `PosixTerminal` instead of `SttyTerminal`.

## Lifecycle notes

- `SttyTerminal.stop()` and `PosixTerminal.stop()` are intended to be idempotent.
- Both interactive backends disable bracketed paste during stop.
- Both interactive backends poll terminal dimensions while running and request in-place redraws on size changes.
- `TUI.run()` wraps startup and waiting in `try/finally` so terminal state is restored when the run loop exits or fails after startup.
- `TUI` sanitizes final over-wide output before writing to protect live sessions; component tests should still verify direct render-width contracts.
- `TUIOptions(hardwareCursorPositioning = true)` is opt-in. The runtime strips cursor markers from final output in both modes, and when enabled it positions the hardware cursor from the marker that remains after overlay composition.
- Visible overlays are recomputed every render and composited as rectangular cells over base content; spaces in overlay output are literal replacement cells.
