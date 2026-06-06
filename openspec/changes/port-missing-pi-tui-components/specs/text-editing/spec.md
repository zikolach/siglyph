## ADDED Requirements

### Requirement: Single-line Input supports advanced editing history
The `Input` component SHALL provide user-visible undo/redo and kill-ring style editing commands in addition to existing control-key editing.

#### Scenario: Undo restores prior input state
- **WHEN** the user types text and then triggers Undo with the configured undo key binding (`Ctrl+Z` by default)
- **THEN** `Input` reverts to the previous text and cursor cluster position and continues accepting input from that restored state

#### Scenario: Kill-ring tracks word deletions
- **WHEN** the user invokes a word deletion command (`Ctrl+W`, `Alt+Backspace`, or `Ctrl+Backspace`) repeatedly
- **THEN** removed segments are stored in a kill-ring list so later yank operations can recover the most recent removed content

#### Scenario: Yank restores killed content
- **WHEN** the user performs yank after one or more kill operations
- **THEN** `Input` inserts the most recent killed text at the cursor position and updates the cursor accordingly

#### Scenario: Yank-pop cycles kill-ring entries
- **WHEN** the user performs yank repeatedly without an intervening insertion command
- **THEN** successive yank-pop operations rotate kill-ring candidates and replace the previously inserted yanked segment with the next candidate

### Requirement: Editor supports undo and kill-ring commands
The editor SHALL expose undo/redo and kill-ring behavior through the rendered `Editor` API, preserving buffer-cursor consistency and callback ordering.

#### Scenario: Undo restores previous editor buffer
- **WHEN** editable multiline text is changed and Undo is invoked
- **THEN** the editor restores the prior `EditorBuffer` snapshot, including cursor line/column, and continues rendering from that state

#### Scenario: Kill commands are recorded for yanking
- **WHEN** line-based, word-based, or character-based deletion commands are executed
- **THEN** deleted text is pushed to the editor kill-ring and can be yanked later through the yank command

#### Scenario: Yank-pop works after repeated yank in editor
- **WHEN** multiple kill-ring entries exist
- **THEN** repeated yank-pop cycles through previously killed text and replaces the most recent yanked segment in-place

### Requirement: Editor large-paste marker lifecycle
The editor SHALL compact very large pasted blocks into visible paste markers and expand them during submit or explicit user action.

#### Scenario: Large paste inserts visible marker
- **WHEN** a paste operation exceeds the configured large-paste threshold
- **THEN** the rendered editor line contains a compact paste marker token that participates as a single edit unit

#### Scenario: Marker preserves logical text in submit output
- **WHEN** the editor buffer containing a paste marker is submitted
- **THEN** the submitted text contains the full original pasted content at the marker position rather than the marker abbreviation alone

#### Scenario: Compaction can be expanded on demand
- **WHEN** marker expansion is requested before submit
- **THEN** the current logical marker token is replaced with the original pasted content in the buffer and rendered view

### Requirement: Enhanced word navigation and boundary rules
The `Input` and `Editor` components SHALL treat word boundaries consistently across Unicode-visible whitespace and punctuation so cursor motion and word deletion are predictable.

#### Scenario: Navigate by word boundaries
- **WHEN** the user triggers word-left/word-right commands
- **THEN** cursor movement skips punctuation/whitespace boundaries according to visible grapheme boundaries and stops at the next logical word boundary

#### Scenario: Word deletion uses matched boundaries after punctuation runs
- **WHEN** the user deletes a word from a cursor position next to punctuation or mixed scripts
- **THEN** only the expected boundary-consistent segment is removed and cursor position remains valid for subsequent edits

### Requirement: Hardware cursor marker support
When IME and terminal marker workflows are enabled, focused editing components SHALL emit a zero-width cursor marker in front of the visual cursor position while preserving fake-cursor rendering.

#### Scenario: Cursor marker is emitted on focused input
- **WHEN** an `Input` or `Editor` is focused and renders a fake cursor
- **THEN** it includes a terminal cursor marker sequence (or equivalent marker abstraction) immediately before the fake cursor token

#### Scenario: Marker does not alter semantic text
- **WHEN** callbacks observe the submitted editor value
- **THEN** no marker sequence appears in logical text values returned by `onSubmit` or `text` getters
