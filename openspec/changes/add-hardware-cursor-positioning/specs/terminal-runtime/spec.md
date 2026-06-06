## ADDED Requirements

### Requirement: Marker-driven hardware cursor positioning
The TUI runtime SHALL provide an opt-in mode that positions the terminal hardware cursor at the focused editing cursor by scanning final rendered output for a zero-width cursor marker.

#### Scenario: Hardware cursor moves to marker position
- **WHEN** hardware cursor positioning is enabled and the final frame contains a cursor marker
- **THEN** the runtime strips the marker from terminal output and moves the hardware cursor to the marker's row and display column after writing the frame

#### Scenario: No marker preserves existing render behavior
- **WHEN** hardware cursor positioning is enabled but the final frame contains no cursor marker
- **THEN** the runtime writes the frame without marker-derived cursor movement and preserves existing stop-positioning behavior

#### Scenario: Disabled mode strips marker without cursor movement
- **WHEN** hardware cursor positioning is disabled and a focused component emits a cursor marker
- **THEN** the runtime strips the marker before writing output and does not move the hardware cursor to the marker position

#### Scenario: Stop still leaves terminal readable
- **WHEN** an interactive TUI using hardware cursor positioning stops
- **THEN** existing shutdown behavior shows the cursor, disables terminal protocols, restores terminal state, and positions the shell below the rendered TUI content

### Requirement: Cursor marker scanning is ANSI and Unicode aware
The TUI runtime SHALL locate cursor markers using display-cell coordinates while treating ANSI/control sequences and the marker itself as zero-width non-printing output.

#### Scenario: ANSI styling before marker is ignored for column
- **WHEN** a line contains ANSI styling or OSC hyperlink sequences before the cursor marker
- **THEN** the computed hardware cursor column is based on visible display width, not raw string length

#### Scenario: Wide Unicode before marker uses display width
- **WHEN** a line contains CJK, emoji, or combining-mark grapheme clusters before the cursor marker
- **THEN** the computed hardware cursor column matches the rendered terminal display-cell position

#### Scenario: Multiple markers are stripped deterministically
- **WHEN** multiple cursor markers are present in the final frame due to invalid component output
- **THEN** the runtime selects the first marker in row-major order for cursor placement and strips all marker sequences from output
