## ADDED Requirements

### Requirement: Editor programmatic insertion
The multiline editor SHALL expose a public method that inserts application-supplied text at the current cursor using the same logical mutation path as user text insertion.

#### Scenario: Programmatic insertion updates text at cursor
- **WHEN** application code inserts text through the editor programmatic insertion API
- **THEN** the text is inserted at the current cursor and the cursor moves to the end of the inserted content

#### Scenario: Programmatic multiline insertion normalizes line endings
- **WHEN** application code inserts text containing CRLF or CR line endings
- **THEN** the editor normalizes them to logical newline separators before mutating the buffer

#### Scenario: Programmatic insertion creates one undo step
- **WHEN** application code inserts text through one call to the programmatic insertion API
- **THEN** one editor undo operation reverts the complete insertion

#### Scenario: Programmatic insertion invokes change callback
- **WHEN** programmatic insertion changes the editor text
- **THEN** the editor invokes the configured change callback with the current logical text

#### Scenario: Programmatic insertion requests render when attached
- **WHEN** programmatic insertion changes visible editor state while the editor has a TUI context
- **THEN** the editor requests a render through that context

#### Scenario: Programmatic insertion preserves large paste marker behavior
- **WHEN** application code inserts text that exceeds the configured large-paste threshold
- **THEN** the editor uses the same large-paste marker behavior as paste insertion
