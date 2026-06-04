## ADDED Requirements

### Requirement: Rendered multiline editor component
The library SHALL provide a rendered multiline `Editor` component that delegates text storage and mutation to `EditorBuffer`.

#### Scenario: Editor renders buffer lines
- **WHEN** an editor with multiple logical lines is rendered
- **THEN** it returns terminal lines representing the current buffer contents within the requested width

#### Scenario: Editor delegates printable insertion
- **WHEN** the editor receives a printable typed character event
- **THEN** it inserts that character through `EditorBuffer` and schedules a render

#### Scenario: Editor delegates paste insertion
- **WHEN** the editor receives a paste event containing newlines and Unicode text
- **THEN** it inserts the paste through `EditorBuffer` preserving logical line breaks and Unicode content

### Requirement: Configurable editor Enter behavior
The editor SHALL expose configurable Enter behavior for prompt-like and editor-like applications.

#### Scenario: Submit on Enter mode
- **WHEN** the editor is configured to submit on plain Enter
- **THEN** plain Enter invokes the submit callback with the current buffer text

#### Scenario: Shift Enter inserts newline in submit mode
- **WHEN** the editor is configured to submit on plain Enter and receives Shift+Enter
- **THEN** it inserts a newline through `EditorBuffer` instead of submitting

#### Scenario: Newline on Enter mode
- **WHEN** the editor is configured to insert newline on plain Enter
- **THEN** plain Enter inserts a newline through `EditorBuffer`

#### Scenario: Cmd Enter submits in newline mode
- **WHEN** the editor is configured to insert newline on plain Enter and receives Cmd/Super+Enter
- **THEN** it invokes the submit callback with the current buffer text

### Requirement: Editor editing key handling
The editor SHALL handle MVP multiline editing keys using typed terminal input events.

#### Scenario: Cursor movement keys
- **WHEN** the editor receives Left, Right, Up, Down, Home, End, Ctrl+A, or Ctrl+E
- **THEN** it updates the logical cursor through `EditorBuffer` and renders the new cursor position

#### Scenario: Deletion keys
- **WHEN** the editor receives Backspace, Delete, Ctrl+K, Ctrl+W, or Alt/Ctrl+Backspace
- **THEN** it applies the corresponding `EditorBuffer` mutation and renders the updated text

### Requirement: Editor callbacks
The editor SHALL expose callbacks for text changes and submit events.

#### Scenario: Change callback
- **WHEN** editor input mutates the buffer text
- **THEN** the editor invokes the change callback with the current buffer text

#### Scenario: Submit callback
- **WHEN** the configured submit key is received
- **THEN** the editor invokes the submit callback with the current buffer text without losing Unicode or logical newlines

### Requirement: Multiline editor interactive demo
The project SHALL provide a shared multiline editor demo usable by both JVM and Scala Native launchers.

#### Scenario: JVM editor demo launches
- **WHEN** the JVM interactive editor demo target is launched in a macOS or Linux TTY
- **THEN** it renders the editor, accepts live multiline input, and exits safely on Escape or Ctrl+C

#### Scenario: Native editor demo builds and launches
- **WHEN** the Scala Native interactive editor demo target is built and launched in a macOS or Linux TTY
- **THEN** it renders the same editor UI and interaction logic using the Native backend
