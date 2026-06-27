## MODIFIED Requirements

### Requirement: Editor supports undo and kill-ring commands
The editor SHALL expose undo and kill-ring behavior through the rendered `Editor` API, preserving buffer-cursor consistency, callback ordering, and current `pi-tui` default keybindings unless explicitly configured otherwise.

#### Scenario: Undo restores previous editor buffer
- **WHEN** editable multiline text is changed and Undo is invoked with the configured undo key binding (`Ctrl+-` by default, matching `pi-tui`)
- **THEN** the editor restores the prior `EditorBuffer` snapshot, including cursor line/column, and continues rendering from that state

#### Scenario: Submit clears editor undo stack
- **WHEN** the editor submits its current text through the configured submit behavior
- **THEN** later undo commands do not restore pre-submit draft snapshots

#### Scenario: Redo is not invented beyond upstream parity
- **WHEN** applications use the default editing model for `Editor`
- **THEN** no redo command is exposed unless a future upstream-compatible design adds redo semantics explicitly

#### Scenario: Kill commands are recorded for yanking
- **WHEN** line-based, word-based, or character-based deletion commands are executed
- **THEN** deleted text is pushed to the editor kill-ring and can be yanked later through the yank command

#### Scenario: Yank-pop works after repeated yank in editor
- **WHEN** multiple kill-ring entries exist
- **THEN** repeated yank-pop cycles through previously killed text and replaces the most recent yanked segment in-place

### Requirement: Enhanced word navigation and boundary rules
The `Input` and `Editor` components SHALL treat word boundaries consistently across Unicode-visible whitespace and punctuation so cursor motion and word deletion are predictable.

#### Scenario: Navigate by word boundaries
- **WHEN** the user triggers word-left/word-right commands
- **THEN** cursor movement skips punctuation/whitespace boundaries according to visible grapheme boundaries and stops at the next logical word boundary

#### Scenario: Word deletion uses matched boundaries after punctuation runs
- **WHEN** the user deletes a word from a cursor position next to punctuation or mixed scripts
- **THEN** only the expected boundary-consistent segment is removed and cursor position remains valid for subsequent edits

#### Scenario: Fullwidth punctuation separates words
- **WHEN** word navigation crosses mixed CJK text separated by fullwidth punctuation
- **THEN** cursor movement and word deletion treat the fullwidth punctuation as a boundary and do not merge adjacent words across that punctuation
