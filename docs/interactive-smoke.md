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
- `Ctrl+T` cycles focus between the multiline editor, action list, and settings list, leaving `Tab` available to the focused editor for autocomplete.
- `Enter` submits editor text, selects an action, ticks/cancels loader components through the action list, or accepts a visible autocomplete suggestion.
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event.
- Type `/he`, `./`, `../`, `@"README`, or `#do` in editor input and press `Tab` to show slash-command, dependency-free filesystem path, attachment, or application-owned `#` trigger suggestions adjacent to the editor area. Completion uses Java/NIO filesystem enumeration only; no external shell tools are required.
- Verify autocomplete fuzzy ranking by typing partial command/path/tag text (for example `/hp` or `#dc`) and checking likely matches are ranked before looser matches when enabled in the demo.
- In `examples/scala-cli/editor-autocomplete.scala`, which injects `EditorAutocompleteDebouncer.Delayed`, type additional characters quickly while autocomplete is visible and confirm stale work is cancelled/ignored: old suggestions remain visible while a refresh is pending, then are replaced or closed by the latest request.
- With suggestions visible, `↑` / `↓` navigates, `Enter` or `Tab` accepts, and `Esc` cancels without changing editor text.
- `PageUp` / `PageDown` pages the cursor in wrapped multiline editor content.
- `Ctrl+]` and `Ctrl+Alt+]` jump forward/backward to the next typed target character.
- When the action list is focused, type to fuzzy-filter actions, use `↑` / `↓` to navigate, select `Tick loader` to advance the loader frame, and select `Cancel loader` to update the cancellable loader state.
- Select terminal integration actions to set the terminal title, turn OSC 9;4 progress on/off, query background color, query color scheme, and toggle color-scheme notifications. Unsupported terminals should show `unsupported` or `no reply` without breaking input. Query actions run in a background thread so the demo remains interactive while waiting for terminal replies.
- When the settings list is focused, type to fuzzy-filter settings and press `Enter` or Space to cycle the selected setting value.
- Arrow keys, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the buffer.
- `Ctrl+-` undoes, `Ctrl+Y` yanks killed text, `Alt+Y` yank-pops, `Alt+D` / `Alt+Delete` deletes a word forward, and modified word-left/word-right shortcuts move by word when reported by the terminal.
- Pasting more than 10 lines or more than 1000 grapheme clusters inserts a compact `[paste #N ...]` marker; submitting the editor expands the marker back to the original pasted text.
- Resize the terminal narrower and wider; the demo redraws without crashing and every line remains within the visible width.
- Resize terminal height; the existing TUI frame is repainted in place and any visible overlay is re-resolved/clamped to the new dimensions without clearing scrollback.
- For an app/demo configured with `TUIOptions(hardwareCursorPositioning = true)`, type in a focused `Input` or `Editor` and verify the terminal hardware cursor/IME candidate window tracks the fake cursor position. Cursor markers must not appear as visible output, and disabling the option must leave fake-cursor behavior unchanged.
- `mill keyTester.run` prints typed key events with modifiers and press/repeat/release metadata when the terminal reports it.
- Kitty keyboard protocol negotiation hooks are exposed by the JVM and Native interactive backends for applications that opt in. Unsupported, stale, or unavailable negotiation falls back to existing basic input parsing.
- Platform-specific modifier fallbacks, including Apple Terminal modified Enter, are implemented only when they can be queried safely. When no safe fallback is available, ordinary key input continues without blocking.
- `Esc` and `Ctrl+C` exit and restore the terminal.

Autocomplete and select command defaults can be overridden via `EditorOptions.keybindings` (via `KeybindingManager`).
See `docs/keybinding-defaults.md` for the complete default map, ambiguity notes, and unsupported terminal encodings.

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
- Both interactive backends expose Kitty keyboard protocol state for tests or application diagnostics.
- Both interactive backends poll terminal dimensions while running and request in-place redraws on size changes.
- `TUI.run()` wraps startup and waiting in `try/finally` so terminal state is restored when the run loop exits or fails after startup.
- `TUI` sanitizes final over-wide output before writing to protect live sessions; component tests should still verify direct render-width contracts.
- `TUIOptions(hardwareCursorPositioning = true)` is opt-in. The runtime strips cursor markers from final output in both modes, and when enabled it positions the hardware cursor from the marker that remains after overlay composition.
- Visible overlays are recomputed every render and composited as rectangular cells over base content; spaces in overlay output are literal replacement cells.
