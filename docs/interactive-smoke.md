# Interactive Smoke Coverage

Manual smoke checks for interactive runtime demos and the multiline editor demo. Static utility components are shown by `mill demo.run`; tick-driven `Loader` and `CancellableLoader` are shown in the shared interactive demo actions so their state can change live.

Automated PTY lifecycle and terminal-restoration coverage is documented in
[terminal-conformance.md](terminal-conformance.md).

## Fullscreen transcript example

Run the shared JVM example in a macOS or Linux terminal:

```bash
mill fullscreenJvmDemo.run
```

Build and run the same shared source through Scala Native:

```bash
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out
```

Manual checks for both launchers:

- Confirm the alternate screen has exactly the current terminal height. Resize width and height while background rows are appended. The editor and footer remain visible while the transcript takes the remaining rows.
- Scroll upward with the wheel. Follow-end stops. New background rows do not move the viewport. Scroll to the end or activate `Jump to latest` to resume follow-end.
- Place the pointer over nested scroll content in an application that embeds another `ScrollView`. The deepest view consumes wheel delta first. Remaining delta reaches an ancestor only with `OverscrollPolicy.Chain`. Automated nested geometry and routing evidence is in `FullscreenViewportSuite`.
- Press `Ctrl+Shift+F`, type a query from the transcript, use Enter and Shift+Enter to move between matches, then press Escape. Search captures editor input while open and leaves editor text unchanged.
- Drag the primary button across transcript text. Double-click a path or kebab-case token and triple-click a row. Selection uses complete grapheme clusters across wrapped rows.
- Press `Ctrl+Alt+C` or click the footer after selecting text. The example's explicit `HostClipboard` callback accepts the bounded text and updates footer status. It stores text in application memory and does not integrate with an operating-system clipboard. Confirm no OSC 52 prompt or terminal clipboard permission appears.
- Press `Ctrl+C` to exit. Confirm normal shell content returns, the cursor is visible, bracketed paste and mouse tracking are disabled, and terminal input is canonical with echo restored.

These are emulator-visible manual checks. `FullscreenTranscriptDemoSuite`, `FullscreenViewportSuite`,
`TranscriptSearchSuite`, `ViewportSelectionSuite`, and `MouseFoundationSuite` provide automated JVM
and Scala Native behavior checks with `VirtualTerminal`. They do not prove emulator rendering or an
operating-system clipboard integration.

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
- Type `/he`, `./`, `@"README`, or `#do` in editor input and press `Tab` to show slash-command, dependency-free filesystem path, attachment, or application-owned `#` trigger suggestions adjacent to the editor area. The demo explicitly confines filesystem completion to its workspace root; parent, home, and absolute syntax stay disabled. Completion uses Java/NIO filesystem enumeration only; no external shell tools are required.
- Verify autocomplete fuzzy ranking by typing partial command/path/tag text (for example `/hp` or `#dc`) and checking likely matches are ranked before looser matches when enabled in the demo.
- In `examples/scala-cli/editor-autocomplete.scala`, which injects `EditorAutocompleteDebouncer.Delayed`, type additional characters quickly while autocomplete is visible and confirm stale work is cancelled/ignored: old suggestions remain visible while a refresh is pending, then are replaced or closed by the latest request.
- With suggestions visible, `↑` / `↓` navigates, `Enter` or `Tab` accepts, and `Esc` cancels without changing editor text.
- In an app/demo configured with `TUIOptions(mouseInput = true)`, scroll the mouse wheel over the action list, settings list, editor, and visible autocomplete suggestions. The item under the pointer moves while keyboard focus stays where it was unless a component explicitly changes focus.
- `PageUp` / `PageDown` pages the cursor in wrapped multiline editor content.
- `Ctrl+]` and `Ctrl+Alt+]` jump forward/backward to the next typed target character.
- When the action list is focused, type to fuzzy-filter actions, use `↑` / `↓` to navigate, select `Tick loader` to advance the loader frame, and select `Cancel loader` to update the cancellable loader state.
- Select terminal integration actions to set the terminal title, turn OSC 9;4 progress on/off, query background color, query color scheme, and toggle color-scheme notifications. Unsupported or stopped terminals show the corresponding callback result without breaking input. Query actions use direct callbacks; no query thread or core timeout is created.
- When the settings list is focused, type to fuzzy-filter settings and press `Enter` or Space to cycle the selected setting value.
- Arrow keys, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the buffer.
- `Ctrl+-` undoes, `Ctrl+Y` yanks killed text, `Alt+Y` yank-pops, `Alt+D` / `Alt+Delete` deletes a word forward, and modified word-left/word-right shortcuts move by word when reported by the terminal.
- Pasting more than 10 lines or more than 1000 grapheme clusters across any terminal read boundaries inserts one compact `[paste #N ...]` marker. CRLF, CR, and LF normalize across chunk boundaries; submitting or expanding the marker recovers the complete normalized paste.
- Resize the terminal narrower and wider; the demo redraws without crashing and every line remains within the visible width.
- Resize terminal height; the frame is repainted and visible overlays are re-resolved and clamped.
  Normal-screen width or height redraw clears normal scrollback; alternate-screen redraw does not
  affect normal scrollback.
- During animated or asynchronous updates, repeatedly change both terminal width and height. Verify
  stale-width frames do not appear, input remains responsive, and the final frame uses the final
  dimensions on both JVM and Scala Native.
- For an app/demo configured with `TUIOptions(hardwareCursorPositioning = true)`, type in a focused `Input` or `Editor` and verify the terminal hardware cursor/IME candidate window tracks the fake cursor position. Cursor markers must not appear as visible output, and disabling the option must leave fake-cursor behavior unchanged.
- `mill keyTester.run` prints typed key events with modifiers and press/repeat/release metadata when the terminal reports it.
- Kitty keyboard protocol negotiation hooks are exposed by the JVM and Native interactive backends for applications that opt in. Unsupported, stale, or unavailable negotiation falls back to existing basic input parsing.
- Platform-specific modifier fallbacks, including Apple Terminal modified Enter, are implemented only when they can be queried safely. When no safe fallback is available, ordinary key input continues without blocking.
- `Esc` and `Ctrl+C` exit and restore the terminal.

Autocomplete and select command defaults can be overridden via `EditorOptions.keybindings` (via `KeybindingManager`).
See `docs/keybinding-defaults.md` for the complete default map, ambiguity notes, and unsupported terminal encodings.

## Append-only image smoke checks

Use a small normal-screen application configured with
`NormalResizeClearPolicy.PreserveScrollback`, one retained text/input frame, and detached text,
Kitty-image, and iTerm2-image components passed to `appendToScrollback`.

- In Kitty, append text and a Kitty image, resize repeatedly, continue editing the retained frame,
  and exit. Confirm appended rows remain in scrollback, the live frame redraws below them, and exit
  restores cursor and terminal modes.
- In iTerm2, append text and a one-shot iTerm2 image and perform the same resize/live-render/exit
  checks. This is emulator smoke coverage; a raw PTY cannot prove image persistence.
- Render an iTerm2 image in the retained live frame, then request append. Confirm the callback
  returns `RetainedITerm2Control`, no append bytes are emitted, and the visible retained image/frame
  is not relocated or disturbed.
- Confirm cursor placements and Kitty cleanup controls fail before output and terminal state is
  restored through normal fail-fast cleanup.

## Normal-screen resize recovery smoke checks

Use a small normal-screen application with `PreserveScrollback`, a bounded application-owned
semantic transcript, `NormalResizeRecoveryProvider`, one retained editor/status frame, and a button
or key that publishes detached output through `appendToScrollback`.

- In Kitty, iTerm2, and one conventional terminal (for example Terminal.app, GNOME Terminal, or
  xterm), append durable `A`, repeatedly change width and height, and have the provider reflow and
  select at most the newest `context.maxRows` transcript rows. Confirm the selected tail appears
  immediately above the retained live frame without entering alternate screen or clearing shell
  scrollback.
- After recovery of `A`, append durable `B`. Confirm visible chronology is `A`, `B`, then the
  retained editor/status frame. Change only retained status and confirm neither `A` nor `B` is
  repainted as part of the live frame.
- Repeat shrink/grow cycles with a live frame that leaves recovery capacity, fills the viewport,
  exceeds the viewport, and is empty. Confirm zero capacity skips provider invocation and an empty
  live frame keeps a blank anchor below recovered output for a later append.
- Trigger another resize while provider rendering is intentionally delayed. Confirm no stale-width
  recovery appears and only latest-width output commits. Provider logic must remain retryable and
  free of one-shot side effects.
- Return too many provider rows and throw a provider exception in a disposable test application.
  Confirm no recovery lines publish, bounded diagnostics contain no transcript or exception text,
  and terminal cursor/modes are restored.
- Treat these as emulator persistence checks. Automated PTY coverage verifies clear/recovery/live/
  append byte ordering, `CSI 3 J` absence, synchronized output, and restoration, but a PTY cannot
  prove which rows a real emulator preserves or reflows.

## Fullscreen image smoke checks

Use a fullscreen layout with one scrolling `Image.withSessionCapabilities` and sticky text above or
below it. Run in Kitty and iTerm2:

- Move the image completely outside the scroll clip. Confirm no image appears in sticky regions.
- Leave only part of the image footprint visible. Siglyph omits the complete typed control. It does not crop or partially execute Kitty or iTerm2 output.
- In Kitty, return an unchanged image before the configured retention age and count bounds expire. Confirm placement returns without visible payload retransmission flicker. Move enough distinct images or generations through the viewport to force eviction, then confirm later return follows normal retransmission.
- In iTerm2, confirm only a completely visible footprint renders. Partial and relocated clipped images are omitted because iTerm2 lacks the required placement and cleanup contract.
- Exit after visible, offscreen, and evicted Kitty states. Confirm the normal screen and cursor return.

These image checks are manual because a PTY cannot display Kitty or iTerm2 graphics. `FullscreenViewportSuite` and `ImageSuite` assert control omission, retention, eviction, isolation, and cleanup bytes automatically.

## Scala Native interactive demo

Build:

```bash
mill interactiveNativeDemo.nativeLink
```

Scala Native requires Clang 16 or newer for reliable multithreaded module initialization. When the
system `clang` is older, point every Native module and test at a newer installation:

```bash
export SIGLYPH_NATIVE_CLANG=/path/to/llvm/bin/clang
export SIGLYPH_NATIVE_CLANGPP=/path/to/llvm/bin/clang++
```

Run the linked binary from Mill's output directory in an interactive terminal. Optional flags are passed after the binary path.

```bash
./out/interactiveNativeDemo/nativeLink.dest/out
./out/interactiveNativeDemo/nativeLink.dest/out --hardware-cursor
```

Expected behavior matches the JVM multiline editor demo, including opt-in mouse scroll checks, narrow-width and height resize redraw checks, using `PosixTerminal` instead of `SttyTerminal`.

## Lifecycle notes

- `SttyTerminal.stop()` and `PosixTerminal.stop()` are intended to be idempotent.
- Both interactive backends disable bracketed paste during stop.
- Both interactive backends expose Kitty keyboard protocol state for tests or application diagnostics.
- Both interactive backends poll terminal dimensions while running and request in-place redraws on size changes.
- `TUI.run()` wraps startup and waiting in `try/finally` so terminal state is restored when the run loop exits or fails after startup.
- One synchronous work drain serializes input, callbacks, rendering, retained control output, and
  cleanup progression. Application callbacks do not run under the lifecycle lock. Backend writes
  use a separate non-nested edge lock.
- Background-color and color-scheme queries use one wire flight per protocol. Subscribers complete
  in order on the drain, can cancel independently through an idempotent silent function, and own
  any timeout scheduling. Query callbacks may run before the query method returns.
- Ordinary terminal ingress is a lossless FIFO bounded at 4096 events. Correlation-only protocol
  fragments consume no slot, one recognized completion/notification batch consumes one slot, and
  replayed ordinary raw events consume one slot each. Backend publishers apply backpressure when
  required capacity is full and wake when capacity is freed or stop rejects later input. Resize
  remains coalesced and consumes no FIFO slot.
- `Terminal.start` returns without invoking input, resize, or failure callbacks on its calling
  stack. A backend may publish independently from another thread before `start` returns. Output
  methods also do not deliver callbacks synchronously.
- Unexpected active reader, fragment-flush, or resize-worker failure wakes `TUI.run()`. The first
  failure is rethrown after idempotent cleanup. EOF, stop interruption, and stale worker generations
  are normal outcomes. Deterministic worker-failure tests are automated. The demos do not inject
  backend worker failures manually.
- An uncontended `flushRender()` or `stop()` completes synchronously. A reentrant or concurrent call
  publishes work and returns without waiting for active application code; `run()` still waits for
  terminal restoration before returning.
- `TUI` sanitizes final over-wide output before writing to protect live sessions; component tests should still verify direct render-width contracts.
- `TUIOptions(hardwareCursorPositioning = true)` is opt-in. The runtime strips cursor markers from final output in both modes, and when enabled it positions the hardware cursor from the marker that remains after overlay composition.
- `TUIOptions(mouseInput = true)` is opt-in. It enables terminal mouse reporting during interactive runs and may affect normal terminal text selection while active.
- Visible overlays are recomputed every render and composited as rectangular cells over base content; spaces in overlay output are literal replacement cells.
