## Context

Siglyph uses upstream pi-tui as the primary behavior reference. Manual terminal font-size resize testing showed that scrollback-preserving in-place resize redraws can leave duplicated frame output in normal terminal screen mode. Resize redraw should match pi-tui by clearing the viewport and scrollback before writing the recomputed frame.

The terminal input parser already handles CSI-u modified Enter sequences and modifyOtherKeys forms such as `ESC[27;2;13~` and `ESC[27;3;13~`. It does not parse xterm-style tilde modified Enter forms `ESC[13;2~` and `ESC[13;3~`, so those sequences reach applications as raw input.

## Goals / Non-Goals

**Goals:**
- Redraw TUI frames after terminal width or height changes using pi-tui-style full clear output.
- Preserve existing differential-renderer behavior for normal non-resize updates.
- Parse `ESC[13;2~` as `TerminalKey.Enter` with Shift.
- Parse `ESC[13;3~` as `TerminalKey.Enter` with Alt.
- Keep existing CSI-u and modifyOtherKeys modified Enter parsing behavior unchanged.
- Cover the behavior with unit tests using `VirtualTerminal` and parser fixtures.

**Non-Goals:**
- Do not add a new terminal backend.
- Do not change public `TerminalInput`, `TerminalKey`, or `KeyModifiers` data shapes.
- Do not add runtime dependencies.
- Do not implement downstream application-specific submit-key routing.

## Decisions

### 1. Use pi-tui-style full clear redraw on resize

On dimension changes, the renderer should use the same clear sequence as upstream pi-tui: `CSI 2 J`, `CSI H`, and `CSI 3 J`, then write the recomputed frame. This avoids stale frame copies after terminal font-size changes and keeps resize behavior aligned with pi-tui.

Alternatives considered:
- Redraw from a known frame origin with clear-below output. Rejected because terminal font-size reflow can move visible frame content and leave duplicate output.
- Restore a saved cursor position before clearing. Rejected because it can move the viewport into older scrollback and hide duplicates rather than removing them.
- Treat resize as a normal partial render. Rejected because wrapping and overlay placement can change across the whole frame.

### 2. Avoid alternate-screen mode

The resize path should not enter alternate-screen mode. It should remain in the normal terminal screen and use pi-tui-style full clear output for resize redraw.

Alternatives considered:
- Switch interactive sessions to alternate-screen mode. Rejected because this changes the default runtime model and is larger than the resize fix.

### 3. Extend modified-function parsing for Enter only

The parser should keep the current modified function-key path and add code-point handling for key code `13` in tilde-form modified sequences. This is a small parser normalization change and does not require new public types.

Alternatives considered:
- Parse every unknown modified function-key code as a code point. Rejected because function-key tilde sequences use terminal key numbers, not Unicode code points, for many keys.
- Leave `ESC[13;2~` and `ESC[13;3~` as raw input. Rejected because downstream applications need typed modified Enter events.

## Risks / Trade-offs

- Full clear resize redraw can remove terminal scrollback entries according to terminal emulator behavior → Accept this for pi-tui parity and reliable resize repainting.
- Full clear resize redraw can move the viewport to the current frame → Test that resize redraw avoids alternate-screen mode and recomputes frame content.
- Terminal encodings differ across emulators → Keep all currently supported modified Enter fixtures and add the two missing tilde fixtures rather than replacing existing parsing.

## Migration Plan

1. Add parser tests for `ESC[13;2~` and `ESC[13;3~`.
2. Add renderer tests proving width and height resize emit pi-tui-style full clear output and avoid alternate-screen mode.
3. Update `TerminalInputParser` to normalize the two tilde modified Enter encodings.
4. Update resize rendering to use full clear output on dimension changes.
5. Run focused core tests and OpenSpec validation.

## Open Questions

None.
