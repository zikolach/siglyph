# text-editing Specification

## Purpose
TBD - created by archiving change scala-tui-port. Update Purpose after archive.
## Requirements
### Requirement: Single-line input component
The library SHALL provide a single-line input component with editable text, cursor positioning, horizontal scrolling, submission callbacks, paste insertion, and common readline-style key bindings.

#### Scenario: Input submits current value
- **WHEN** the input component receives an Enter key event
- **THEN** it invokes its submit callback with the current value

#### Scenario: Input deletes previous word
- **WHEN** the input component receives Ctrl+W or Alt+Backspace
- **THEN** it deletes the word before the cursor using Unicode-aware word boundaries

### Requirement: Multiline editor component
The library SHALL provide a multiline editor component with line-based text storage, visible wrapping, cursor movement, newline insertion, submit behavior, and callbacks for change and submit events.

#### Scenario: Editor inserts printable Unicode
- **WHEN** the editor receives a printable Unicode character
- **THEN** it inserts the character at the cursor without restricting input to ASCII

#### Scenario: Editor submits text
- **WHEN** the editor receives Enter while submit is enabled and no multiline-enter modifier is active
- **THEN** it invokes its submit callback with the editor text

#### Scenario: Editor inserts newline with modifier
- **WHEN** the editor receives Shift+Enter, Ctrl+Enter, or Alt+Enter according to normalized key events
- **THEN** it inserts a newline instead of submitting

### Requirement: Editor buffer operations
The library SHALL keep core text mutation logic separate from terminal rendering so insert, delete, split, merge, movement, undo, and paste marker operations can be tested without a real terminal.

#### Scenario: Backspace over grapheme cluster
- **WHEN** the cursor is after a multi-codepoint grapheme cluster and Backspace is received
- **THEN** the editor removes the whole grapheme cluster and updates cursor position correctly

#### Scenario: Delete to end of line
- **WHEN** the editor receives Ctrl+K
- **THEN** it deletes text from the cursor to the end of the current line

### Requirement: Large paste handling
The editor SHALL preserve large pasted content through compact markers in the visible buffer and substitute original paste content during submission.

#### Scenario: Large paste creates marker
- **WHEN** pasted content exceeds the configured large-paste threshold
- **THEN** the editor inserts a marker describing the paste instead of rendering all pasted lines inline

#### Scenario: Submit expands paste marker
- **WHEN** editor text containing a paste marker is submitted
- **THEN** the submitted text contains the original pasted content at the marker position

### Requirement: Autocomplete integration
The editor SHALL integrate with an autocomplete provider for slash commands, file paths, and attachment-prefixed paths, and SHALL expose a selectable suggestion list controlled by keyboard input.

#### Scenario: Tab requests file suggestions
- **WHEN** the editor receives Tab in a file-completion context
- **THEN** it requests suggestions from the autocomplete provider and displays matching options

#### Scenario: Selecting suggestion applies completion
- **WHEN** a suggestion is selected from the autocomplete list
- **THEN** the editor replaces the matched prefix with the selected completion and moves the cursor to the resulting position

### Requirement: Input handles normalized control events
The `Input` component SHALL respond to normalized typed control-key events produced by real terminal input buffering and parsing.

#### Scenario: Ctrl+A moves to beginning
- **WHEN** an `Input` component receives a normalized Ctrl+A key event
- **THEN** it moves the cursor to the beginning of the value

#### Scenario: Ctrl+E moves to end
- **WHEN** an `Input` component receives a normalized Ctrl+E key event
- **THEN** it moves the cursor to the end of the value

#### Scenario: Ctrl+W deletes word backwards
- **WHEN** an `Input` component receives a normalized Ctrl+W key event
- **THEN** it deletes the word before the cursor

### Requirement: Input handles live bracketed paste
The `Input` component SHALL handle paste events emitted from fragmented bracketed paste input during interactive sessions.

#### Scenario: Pasted newlines are normalized
- **WHEN** a bracketed paste event containing newlines is delivered to a focused `Input`
- **THEN** the input inserts the pasted text with newlines normalized for single-line editing

#### Scenario: Pasted Unicode remains intact
- **WHEN** a bracketed paste event contains non-ASCII Unicode text
- **THEN** the input preserves the Unicode text while inserting it at the cursor

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
