## ADDED Requirements

### Requirement: Editor visual layout
The editor SHALL compute a width-aware visual layout that maps logical buffer lines and cursor positions to wrapped terminal lines.

#### Scenario: Long logical line wraps
- **WHEN** an editor logical line exceeds the available render width
- **THEN** the editor layout wraps it into multiple visual lines that each fit within the requested width

#### Scenario: Cursor maps to wrapped visual position
- **WHEN** the logical cursor is on a wrapped logical line
- **THEN** the layout maps it to the correct visual row and display column

#### Scenario: Wide Unicode layout
- **WHEN** editor text contains CJK, combining marks, or emoji grapheme clusters
- **THEN** wrapping and cursor placement use display width rather than byte, code unit, or code point counts

### Requirement: Editor fake cursor rendering
The editor SHALL render a visible fake cursor using inverse-video styling within the component output.

#### Scenario: Cursor on character
- **WHEN** the cursor is positioned before an existing grapheme cluster
- **THEN** the editor renders that cluster with inverse-video styling

#### Scenario: Cursor at line end
- **WHEN** the cursor is positioned at the end of a visual line
- **THEN** the editor renders an inverse-video space at the cursor position

#### Scenario: Cursor hidden when unfocused
- **WHEN** the editor is not focused
- **THEN** it renders text without the inverse-video cursor marker

### Requirement: Editor render width contract
The rendered editor SHALL satisfy the component render contract for all output lines.

#### Scenario: Rendered editor lines fit width
- **WHEN** the editor is rendered at a requested width
- **THEN** every returned line has visible width less than or equal to that width after ANSI and non-printing escape sequences are ignored
