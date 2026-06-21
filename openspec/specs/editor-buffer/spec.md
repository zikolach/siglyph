# editor-buffer Specification

## Purpose
Defines the pure multiline editing model used by future rendered editor components.
## Requirements
### Requirement: Pure multiline editor buffer
The library SHALL provide a platform-independent editor buffer that stores multiline text, tracks a logical cursor, and exposes text mutation operations without requiring a terminal backend or rendered component.

#### Scenario: Buffer initializes from text
- **WHEN** an editor buffer is created from text containing newline separators
- **THEN** it stores the text as ordered logical lines and places the cursor at the configured logical position

#### Scenario: Buffer exports text
- **WHEN** the current buffer text is requested
- **THEN** it returns the logical lines joined by newline separators without terminal escape sequences

### Requirement: Logical cursor coordinates
The editor buffer SHALL represent the cursor using logical line and grapheme-cluster column coordinates rather than terminal display coordinates.

#### Scenario: Cursor moves within a line
- **WHEN** the cursor moves left or right across text containing multi-codepoint grapheme clusters
- **THEN** it moves by one grapheme cluster rather than by byte, code unit, or code point

#### Scenario: Cursor clamps to valid position
- **WHEN** a cursor move would leave the valid buffer range
- **THEN** the buffer clamps the cursor to the nearest valid logical position

### Requirement: Grapheme-aware text mutation
The editor buffer SHALL insert and delete text using the project's Unicode grapheme-cluster handling so visible characters are not split accidentally.

#### Scenario: Insert printable Unicode
- **WHEN** printable Unicode text is inserted at the cursor
- **THEN** the buffer inserts the text at the logical cursor position and advances the cursor by the inserted grapheme clusters

#### Scenario: Backspace removes one grapheme
- **WHEN** Backspace is applied after a multi-codepoint grapheme cluster
- **THEN** the buffer removes the entire previous grapheme cluster and updates the cursor accordingly

#### Scenario: Delete removes one grapheme
- **WHEN** Delete is applied before a multi-codepoint grapheme cluster
- **THEN** the buffer removes the entire next grapheme cluster without changing the cursor line incorrectly

### Requirement: Line split and merge operations
The editor buffer SHALL support newline insertion and line merging operations for multiline editing.

#### Scenario: Newline splits current line
- **WHEN** newline insertion is applied in the middle of a line
- **THEN** the buffer splits the line at the cursor and moves the cursor to the beginning of the new following line

#### Scenario: Backspace at line start merges with previous line
- **WHEN** Backspace is applied at the beginning of a non-first line
- **THEN** the buffer merges the current line into the previous line and places the cursor at the merge point

#### Scenario: Delete at line end merges with next line
- **WHEN** Delete is applied at the end of a non-last line
- **THEN** the buffer merges the next line into the current line and leaves the cursor at the merge point

### Requirement: Paste insertion
The editor buffer SHALL insert pasted text while preserving line breaks and Unicode content.

#### Scenario: Multiline paste creates lines
- **WHEN** pasted text containing newlines is inserted at the cursor
- **THEN** the buffer splits and inserts logical lines corresponding to the pasted content

#### Scenario: Unicode paste remains intact
- **WHEN** pasted text contains non-ASCII Unicode or multi-codepoint grapheme clusters
- **THEN** the buffer preserves that text and places the cursor after the pasted content

### Requirement: Editor buffer micro-parity audit
The project SHALL audit upstream editor buffer behaviors against local editor buffer tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Buffer behavior is already covered
- **WHEN** an upstream editor buffer behavior has an equivalent local test and matching local behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Buffer behavior lacks only a test
- **WHEN** local editor buffer behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Buffer behavior gap is found
- **WHEN** local editor buffer behavior differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Intentional buffer deviation is found
- **WHEN** local behavior intentionally differs from upstream due to Scala API or typed-input design
- **THEN** the audit records intentional deviation and updates porting notes

