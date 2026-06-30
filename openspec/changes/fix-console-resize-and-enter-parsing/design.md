## Context

Siglyph's terminal runtime already has scrollback-preserving resize requirements, but the current renderer path still emits a full-screen clear sequence during width or height resize. Downstream console integrations need terminal resize to redraw the owned frame region without clearing previous shell scrollback.

The terminal input parser already handles CSI-u modified Enter sequences and modifyOtherKeys forms such as `ESC[27;2;13~` and `ESC[27;3;13~`. It does not parse xterm-style tilde modified Enter forms `ESC[13;2~` and `ESC[13;3~`, so those sequences reach applications as raw input.

## Goals / Non-Goals

**Goals:**
- Redraw TUI frames after terminal width or height changes without emitting full-screen clear or scrollback-clear sequences.
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

### 1. Redraw from the known frame origin on resize

On dimension changes, the renderer should reuse the known frame start row when available, move to that frame origin, clear only from that point downward, and write the newly composed frame. This keeps resize redraws full-frame relative to the TUI region without clearing shell scrollback above the frame.

Alternatives considered:
- Keep using full-screen clear on resize. Rejected because it violates the existing scrollback-preserving runtime contract.
- Treat resize as a normal partial render. Rejected because wrapping and overlay placement can change across the whole frame.

### 2. Keep clear-screen operations explicit and opt-in

The resize path must not call the full-screen clear branch. Any existing explicit clear operation should remain separate from automatic resize rendering so tests can distinguish intentional full-screen operations from resize redraws.

Alternatives considered:
- Remove clear-screen support from terminal backends. Rejected because the terminal abstraction still exposes clear operations for applications that explicitly choose them.

### 3. Extend modified-function parsing for Enter only

The parser should keep the current modified function-key path and add code-point handling for key code `13` in tilde-form modified sequences. This is a small parser normalization change and does not require new public types.

Alternatives considered:
- Parse every unknown modified function-key code as a code point. Rejected because function-key tilde sequences use terminal key numbers, not Unicode code points, for many keys.
- Leave `ESC[13;2~` and `ESC[13;3~` as raw input. Rejected because downstream applications need typed modified Enter events.

## Risks / Trade-offs

- Resize redraw may still overwrite lines below the previous frame start if an application writes outside the TUI owner while the TUI is running → Document and test only the runtime-owned frame contract.
- Frame origin may be unknown if cursor-position queries are unavailable → Fall back to current scrollback-preserving first-render behavior and avoid full-screen clear in automatic resize paths.
- Terminal encodings differ across emulators → Keep all currently supported modified Enter fixtures and add the two missing tilde fixtures rather than replacing existing parsing.

## Migration Plan

1. Add parser tests for `ESC[13;2~` and `ESC[13;3~`.
2. Add renderer tests proving width and height resize do not emit `CSI 2 J`, `CSI 3 J`, or alternate-screen sequences.
3. Update `TerminalInputParser` to normalize the two tilde modified Enter encodings.
4. Update resize rendering to redraw the TUI frame region without full-screen clear output.
5. Run focused core tests and OpenSpec validation.

## Open Questions

None.
