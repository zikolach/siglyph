## 1. Runtime API

- [x] 1.1 Add a public typed screen-mode option to `TUIOptions` with normal-screen mode as the default.
- [x] 1.2 Add Scaladoc for the screen-mode option, including default behavior, cleanup scope, and non-goals.
- [x] 1.3 Keep existing `TUI(terminal)` and `TUI(terminal, TUIOptions())` construction source-compatible.

## 2. Alternate-Screen Lifecycle

- [x] 2.1 Emit `ESC[?1049h` during `TUI.start()` only when alternate-screen mode is configured and before the first rendered frame.
- [x] 2.2 Track whether alternate-screen enter was emitted for the current lifecycle so cleanup can emit exit exactly once.
- [x] 2.3 Emit `ESC[?1049l` during `TUI.stop()` when alternate-screen mode was entered.
- [x] 2.4 Skip normal-screen cursor-below-content parking when stopping a TUI that entered alternate-screen mode.
- [x] 2.5 Preserve existing normal-screen start, render, resize, and stop behavior when alternate-screen mode is not configured.

## 3. Resize and Cleanup Semantics

- [x] 3.1 Keep normal-screen resize redraws using synchronized output, autowrap disabled, `CSI 2 J`, `CSI H`, and `CSI 3 J`.
- [x] 3.2 Make alternate-screen resize redraws full-frame redraws that clear the active alternate-screen viewport, home the cursor, and do not emit `CSI 3 J`.
- [x] 3.3 Ensure runtime failure cleanup restores autowrap if needed, shows the cursor, exits alternate screen when entered, and stops the terminal backend.
- [x] 3.4 Ensure repeated `stop()` calls do not duplicate alternate-screen exit output.

## 4. Tests

- [x] 4.1 Add default-mode tests proving startup, resize, and shutdown do not emit `ESC[?1049h` or `ESC[?1049l`.
- [x] 4.2 Add alternate-screen tests proving enter is emitted before the first frame and exit is emitted on stop.
- [x] 4.3 Add alternate-screen shutdown tests proving cursor-below-content parking is skipped.
- [x] 4.4 Add alternate-screen resize tests proving the redraw remains full-frame, does not emit another enter sequence, and does not emit `CSI 3 J`.
- [x] 4.5 Add failure-path tests proving alternate-screen exit and cursor/autowrap restoration happen after runtime failure.
- [x] 4.6 Add idempotent stop tests proving alternate-screen exit is emitted at most once per lifecycle.

## 5. Documentation and Validation

- [x] 5.1 Update README or runtime docs to describe alternate-screen mode, normal-screen default behavior, scrollback effects, and non-goals.
- [x] 5.2 Update relevant OpenSpec promoted specs when implementation is complete.
- [x] 5.3 Run `mill core.test`.
- [x] 5.4 Run `mill __.compile`.
- [x] 5.5 Run `mill scalafmtCheck`.
- [x] 5.6 Run `mill scalafixCheck`.
- [x] 5.7 Run `openspec validate --all --strict`.
- [x] 5.8 Run `git diff --check`.
