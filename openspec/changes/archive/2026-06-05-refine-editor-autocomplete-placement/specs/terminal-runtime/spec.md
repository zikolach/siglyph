## MODIFIED Requirements

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint the existing TUI frame region without clearing terminal scrollback.

#### Scenario: Width resize redraws frame in place
- **WHEN** terminal width changes after a previous render
- **THEN** the TUI redraws from the previous frame start, clears below that point, and writes the new frame without emitting clear-scrollback sequences

#### Scenario: Height resize redraws frame in place
- **WHEN** terminal height changes after a previous render
- **THEN** the TUI redraws from the previous frame start, clears below that point, and writes the new frame without moving the TUI to the top of the terminal viewport

#### Scenario: Resize with overlay preserves scrollback
- **WHEN** terminal dimensions change while an autocomplete overlay is visible
- **THEN** the overlay is re-resolved and composited without clearing previous terminal output above the TUI frame
