## Why

Siglyph currently emits full-screen clear output on terminal resize and does not parse two modified Enter encodings used by downstream console integrations. This blocks scrollback-style console users that need resize-safe rendering and typed Shift+Enter / Alt+Enter input from common terminal encodings.

## What Changes

- Strengthen resize rendering requirements so dimension changes redraw the existing frame region without `CSI 2 J`, `CSI 3 J`, or alternate-screen behavior.
- Add parser requirements for modified Enter tilde encodings `ESC[13;2~` and `ESC[13;3~`.
- Preserve existing CSI-u and modifyOtherKeys modified Enter parsing behavior.
- Add tests that prove resize output avoids full-screen and scrollback clear sequences.
- Add parser tests that prove the new modified Enter encodings normalize to typed `TerminalInput.KeyEvent` values.
- No runtime dependencies are added.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `terminal-runtime`: Clarify resize redraw safety and expand modified Enter parsing coverage.

## Impact

- Affected code: `core/src/scalatui/core/TUI.scala`, `core/src/scalatui/terminal/TerminalInputParser.scala`.
- Affected tests: `core/test/src/scalatui/core/TUISuite.scala`, `core/test/src/scalatui/terminal/TerminalInputParserSuite.scala`, and nearby terminal-runtime tests.
- API impact: no breaking public API change is intended.
- Dependency impact: none.
