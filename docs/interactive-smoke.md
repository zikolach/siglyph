# Interactive Smoke Coverage

Manual smoke checks for interactive runtime demos and the multiline editor demo:

## JVM interactive demo

Run in a macOS/Linux terminal:

```bash
mill interactiveJvmDemo.run
```

Expected behavior:

- Terminal enters raw mode and hides the cursor.
- Bracketed paste mode is enabled while running and disabled on exit.
- `Tab` switches focus between the action list and multiline editor.
- `Enter` submits editor text, selects an action, or accepts a visible autocomplete suggestion.
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event.
- Type `/` at the start of editor input to show slash-command autocomplete suggestions.
- With suggestions visible, `↑` / `↓` navigates, `Enter` or `Tab` accepts, and `Esc` cancels without changing editor text.
- Arrow keys, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the buffer.
- Resize the terminal narrower and wider; the demo redraws without crashing and every line remains within the visible width.
- Resize terminal height; stale content is cleared by a full redraw and any visible overlay is re-resolved/clamped to the new dimensions.
- `Esc` and `Ctrl+C` exit and restore the terminal.

## Scala Native interactive demo

Build:

```bash
mill interactiveNativeDemo.nativeLink
```

Run the linked binary from Mill's output directory in an interactive terminal. Expected behavior matches the JVM multiline editor demo, including narrow-width and height resize redraw checks, using `PosixTerminal` instead of `SttyTerminal`.

## Lifecycle notes

- `SttyTerminal.stop()` and `PosixTerminal.stop()` are intended to be idempotent.
- Both interactive backends disable bracketed paste during stop.
- Both interactive backends poll terminal dimensions while running and request redraws on size changes.
- `TUI.run()` wraps startup and waiting in `try/finally` so terminal state is restored when the run loop exits or fails after startup.
- `TUI` sanitizes final over-wide output before writing to protect live sessions; component tests should still verify direct render-width contracts.
- Visible overlays are recomputed every render and composited as rectangular cells over base content; spaces in overlay output are literal replacement cells.
