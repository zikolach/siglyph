## ADDED Requirements

### Requirement: Multiline editor uses pure buffer foundation
The rendered multiline editor component SHALL delegate text storage and mutation to the pure editor buffer rather than duplicating buffer operations in rendering code.

#### Scenario: Editor delegates insertion
- **WHEN** the rendered editor receives printable input
- **THEN** it applies insertion through the editor buffer and renders the resulting buffer state

#### Scenario: Editor delegates deletion
- **WHEN** the rendered editor receives Backspace, Delete, Ctrl+K, or word-deletion input
- **THEN** it applies the corresponding mutation through the editor buffer and renders the resulting buffer state

### Requirement: Editor buffer supports submit extraction
The editor buffer SHALL expose a stable way to retrieve the current logical text for submit callbacks and tests.

#### Scenario: Submit text preserves logical newlines
- **WHEN** submit text is requested from a buffer with multiple lines
- **THEN** the returned text preserves logical newline separators between lines

### Requirement: Editor buffer tests cover Unicode editing
The editor-buffer foundation SHALL include regression tests for ASCII, CJK, combining marks, emoji, multiline edits, and paste handling before a rendered multiline editor is built on top of it.

#### Scenario: Unicode deletion test coverage
- **WHEN** editor buffer deletion behavior changes
- **THEN** tests verify that multi-codepoint grapheme clusters are removed as whole visible characters

#### Scenario: Multiline paste test coverage
- **WHEN** paste insertion behavior changes
- **THEN** tests verify both line splitting and Unicode preservation
