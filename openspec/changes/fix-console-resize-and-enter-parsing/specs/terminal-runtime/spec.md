## MODIFIED Requirements

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint after dimension changes using pi-tui-style full clear output without entering alternate-screen mode.

#### Scenario: Width resize redraws with full clear
- **WHEN** terminal width changes after a previous render
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame

#### Scenario: Height resize redraws with full clear
- **WHEN** terminal height changes after a previous render
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame

#### Scenario: Resize with overlay recomputes layout
- **WHEN** terminal dimensions change while an autocomplete overlay is visible
- **THEN** the overlay is re-resolved and composited into the full-clear resize redraw without entering alternate-screen mode

## ADDED Requirements

### Requirement: Modified Enter tilde sequence parsing
The terminal input parser SHALL normalize supported tilde-form modified Enter sequences into typed Enter key events with modifier state.

#### Scenario: Shift Enter tilde sequence parses to typed key
- **WHEN** the input parser receives `ESC[13;2~`
- **THEN** it emits `TerminalKey.Enter` with Shift modifier state

#### Scenario: Alt Enter tilde sequence parses to typed key
- **WHEN** the input parser receives `ESC[13;3~`
- **THEN** it emits `TerminalKey.Enter` with Alt modifier state

#### Scenario: Existing modified Enter sequences remain typed
- **WHEN** the input parser receives `ESC[13;2u`, `ESC[27;2;13~`, `ESC[13;3u`, or `ESC[27;3;13~`
- **THEN** it emits `TerminalKey.Enter` with the parsed modifier state instead of raw input
