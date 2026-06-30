## MODIFIED Requirements

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint the existing TUI frame region without clearing terminal scrollback, clearing the full terminal screen, or entering alternate-screen mode.

#### Scenario: Width resize redraws frame in place
- **WHEN** terminal width changes after a previous render
- **THEN** the TUI redraws from the previous frame start, clears below that point, and writes the new frame without emitting full-screen clear, scrollback-clear, or alternate-screen sequences

#### Scenario: Height resize redraws frame in place
- **WHEN** terminal height changes after a previous render
- **THEN** the TUI redraws from the previous frame start, clears below that point, and writes the new frame without moving the TUI to the top of the terminal viewport or emitting full-screen clear, scrollback-clear, or alternate-screen sequences

#### Scenario: Resize with overlay preserves scrollback
- **WHEN** terminal dimensions change while an autocomplete overlay is visible
- **THEN** the overlay is re-resolved and composited without clearing previous terminal output above the TUI frame

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
