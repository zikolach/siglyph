## ADDED Requirements

### Requirement: Tab display width parity
ANSI-aware text utilities and component rendering SHALL treat tab characters as display width 3 unless existing project documentation explicitly defines a different width before implementation.

#### Scenario: Visible width counts tab as three columns
- **WHEN** visible width is measured for text containing one tab character
- **THEN** that tab contributes 3 display columns

#### Scenario: Wrapped text accounts for tabs
- **WHEN** text containing tabs is wrapped to a requested width
- **THEN** each wrapped line fits the requested width using tab width 3

#### Scenario: Truncated text accounts for tabs
- **WHEN** text containing tabs is truncated to a requested width
- **THEN** the output visible width is less than or equal to the requested width using tab width 3

### Requirement: Wide-cell slicing remains terminal-safe
ANSI-aware slicing and truncation SHALL avoid emitting partial wide grapheme clusters when a slice boundary falls inside a wide visible cell.

#### Scenario: Slice starts inside wide cell
- **WHEN** a slice start column falls inside a CJK wide cell
- **THEN** the slice output does not emit a partial grapheme cluster for that cell

#### Scenario: Slice ends inside wide cell
- **WHEN** a slice end column falls inside a CJK wide cell
- **THEN** the slice output does not emit a partial grapheme cluster for that cell

#### Scenario: ANSI style is preserved across slice
- **WHEN** styled text is sliced around wide grapheme clusters
- **THEN** required ANSI state is preserved for emitted text and does not leak styling into later output

### Requirement: Overlay composition handles wide-cell boundaries
Overlay composition SHALL produce valid terminal output when an overlay begins, ends, or overlaps inside the display columns occupied by a wide base cell.

#### Scenario: Overlay begins inside base wide cell
- **WHEN** an overlay starts at the second column of a two-column CJK base cell
- **THEN** the final composited line contains valid cell-aligned output without a split wide glyph

#### Scenario: Overlay ends before base wide cell completes
- **WHEN** an overlay replacement would expose only part of a wide base cell
- **THEN** the final composited line does not emit a partial wide glyph

#### Scenario: Styled overlay over wide base remains width-safe
- **WHEN** a styled overlay covers wide-cell base content
- **THEN** the final line remains ANSI-safe and visible width does not exceed terminal width

### Requirement: Shrink and narrow rendering remain safe
Component rendering and runtime sanitization SHALL remain safe at zero, one-column, and narrow positive widths.

#### Scenario: Zero requested width returns safe output
- **WHEN** an internal text utility receives zero requested width
- **THEN** it returns safe empty or minimal output instead of throwing

#### Scenario: One-column render remains width-safe
- **WHEN** components or overlays are rendered at width 1
- **THEN** final output lines have visible width less than or equal to 1

#### Scenario: Shrinking terminal does not corrupt frame
- **WHEN** a rendered frame is followed by a much narrower terminal width
- **THEN** the next render emits valid width-safe terminal output
