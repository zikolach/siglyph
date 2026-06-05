## ADDED Requirements

### Requirement: Component width contract with runtime safety net
Components SHALL continue to render lines within the requested width, and the TUI runtime SHALL protect terminal sessions from final over-wide output.

#### Scenario: Component contract remains testable
- **WHEN** a project component is rendered directly in tests
- **THEN** every returned line is expected to have visible width less than or equal to the requested width

#### Scenario: Runtime clamps violating final output
- **WHEN** a component violates the width contract during a TUI render
- **THEN** the runtime sanitizes the final output before writing rather than crashing the terminal session

### Requirement: Narrow-width demo rendering
Project interactive demos SHALL render safely at narrow terminal widths.

#### Scenario: Interactive demo renders at narrow widths
- **WHEN** the shared interactive demo is rendered at widths including 1, 10, 22, 40, and 80
- **THEN** every line written by the TUI has visible width less than or equal to the active terminal width

#### Scenario: Static labels fit narrow widths
- **WHEN** demo headings, control hints, and section labels exceed the requested width
- **THEN** they are wrapped, truncated, or replaced with width-safe fallback text

### Requirement: ANSI-safe truncation for sanitized output
Runtime sanitization SHALL preserve terminal escape correctness while limiting visible width.

#### Scenario: Styled over-wide line is sanitized
- **WHEN** an over-wide rendered line contains ANSI styling or non-printing escape sequences
- **THEN** truncation accounts for visible width rather than byte or code-unit length and does not leak styling into following lines

#### Scenario: Unicode over-wide line is sanitized
- **WHEN** an over-wide rendered line contains CJK, combining marks, or emoji grapheme clusters
- **THEN** truncation avoids splitting visible grapheme clusters where the Unicode utilities can identify them
