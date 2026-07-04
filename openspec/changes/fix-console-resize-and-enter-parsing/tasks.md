## 1. Parser Coverage

- [x] 1.1 Add parser tests for `ESC[13;2~` producing `TerminalKey.Enter` with Shift modifier state.
- [x] 1.2 Add parser tests for `ESC[13;3~` producing `TerminalKey.Enter` with Alt modifier state.
- [x] 1.3 Keep parser regression coverage for `ESC[13;2u`, `ESC[27;2;13~`, `ESC[13;3u`, and `ESC[27;3;13~`.
- [x] 1.4 Update `TerminalInputParser` so tilde-form modified Enter sequences with key code `13` emit typed Enter events instead of raw input.

## 2. Resize Rendering Coverage

- [x] 2.1 Add TUI resize tests proving width changes emit pi-tui-style full clear output and avoid alternate-screen mode.
- [x] 2.2 Add TUI resize tests proving height changes emit pi-tui-style full clear output and avoid alternate-screen mode.
- [x] 2.3 Add or update overlay resize coverage to verify resize redraw recomputes overlay composition with pi-tui-style full clear output.

## 3. Resize Rendering Implementation

- [x] 3.1 Update the TUI resize render path to use the full-screen clear branch on dimension changes.
- [x] 3.2 Preserve normal first render and non-resize differential rendering behavior.
- [x] 3.3 Preserve explicit terminal clear operations as separate opt-in backend behavior.

## 4. Validation

- [x] 4.1 Run `mill core.test.testOnly scalatui.terminal.TerminalInputParserSuite`.
- [x] 4.2 Run `mill core.test.testOnly scalatui.core.TUISuite`.
- [x] 4.3 Run `openspec validate fix-console-resize-and-enter-parsing --type change --strict`.
