## Why

Siglyph needs resize rendering that matches upstream pi-tui behavior and does not leave duplicated frame output after terminal font-size or geometry changes. It also does not parse two modified Enter encodings used by downstream console integrations.

## What Changes

- Strengthen resize rendering requirements so dimension changes use pi-tui-style full clear redraw with `CSI 2 J`, `CSI H`, and `CSI 3 J` while avoiding alternate-screen mode.
- Add parser requirements for modified Enter tilde encodings `ESC[13;2~` and `ESC[13;3~`.
- Preserve existing CSI-u and modifyOtherKeys modified Enter parsing behavior.
- Add tests that prove resize output uses pi-tui-style full clear redraw and avoids alternate-screen mode.
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
