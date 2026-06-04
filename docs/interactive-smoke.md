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
- `Enter` submits editor text or selects an action.
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event.
- Arrow keys, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the buffer.
- `Esc` and `Ctrl+C` exit and restore the terminal.

## Scala Native interactive demo

Build:

```bash
mill interactiveNativeDemo.nativeLink
```

Run the linked binary from Mill's output directory in an interactive terminal. Expected behavior matches the JVM multiline editor demo, using `PosixTerminal` instead of `SttyTerminal`.

## Lifecycle notes

- `SttyTerminal.stop()` and `PosixTerminal.stop()` are intended to be idempotent.
- Both interactive backends disable bracketed paste during stop.
- `TUI.run()` wraps startup and waiting in `try/finally` so terminal state is restored when the run loop exits or fails after startup.
