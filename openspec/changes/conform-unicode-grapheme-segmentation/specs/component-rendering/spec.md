## MODIFIED Requirements

### Requirement: ANSI-aware text utilities
The library SHALL provide pure Scala utilities for measuring, slicing, truncating, wrapping, and padding terminal text while preserving ANSI SGR and OSC hyperlink state where required. The utilities SHALL treat Unicode 17.0.0 UAX #29 default extended grapheme clusters as atomic text units and SHALL delegate text-boundary decisions to the shared segmenter.

#### Scenario: ANSI styles do not affect width
- **WHEN** text contains ANSI color escape sequences around five printable characters
- **THEN** visible width returns 5

#### Scenario: Wrapped styled text preserves style
- **WHEN** styled text wraps across multiple terminal lines
- **THEN** each wrapped line contains the necessary escape sequences to render the intended style without leaking styling into later lines

#### Scenario: ANSI geometry preserves grapheme clusters
- **WHEN** slicing, truncation, wrapping, or padding reaches a boundary inside a Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequence
- **THEN** the utility does not emit a partial Unicode 17.0.0 default extended grapheme cluster

#### Scenario: ANSI metadata surrounds a grapheme boundary
- **WHEN** SGR or OSC hyperlink metadata occurs within or adjacent to text that forms one default extended grapheme cluster
- **THEN** metadata remains non-printing and correctly preserved while text boundaries match the shared segmenter

#### Scenario: Display-width policy is preserved
- **WHEN** a complete default extended grapheme cluster is measured or placed by an ANSI utility
- **THEN** the utility applies the existing display-width policy without redefining width from UAX #29

### Requirement: Editor visual layout
The editor SHALL compute a width-aware visual layout that maps logical buffer lines and cursor positions to wrapped terminal lines, using Unicode 17.0.0 UAX #29 default extended grapheme clusters as atomic layout units.

#### Scenario: Long logical line wraps
- **WHEN** an editor logical line exceeds the available render width
- **THEN** the editor layout wraps it into multiple visual lines that each fit within the requested width

#### Scenario: Cursor maps to wrapped visual position
- **WHEN** the logical cursor is on a wrapped logical line
- **THEN** the layout maps it to the correct visual row and display column

#### Scenario: Wide Unicode layout
- **WHEN** editor text contains CJK, combining marks, or emoji grapheme clusters
- **THEN** wrapping and cursor placement use display width rather than byte, code unit, or code point counts

#### Scenario: Complex clusters remain atomic
- **WHEN** editor text contains Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequences at a wrap or cursor boundary
- **THEN** layout, cursor placement, and fake-cursor rendering do not divide a Unicode 17.0.0 default extended grapheme cluster

#### Scenario: Editor layout preserves width policy
- **WHEN** the editor lays out a complete default extended grapheme cluster
- **THEN** it uses the existing display-width policy for that cluster
