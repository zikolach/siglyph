# text-editing Specification

## Purpose
Defines single-line input and multiline editor behavior, including Unicode-aware editing, pure editor-buffer delegation, paste handling, callbacks, configurable Enter semantics, and autocomplete follow-up scope.
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
The editor SHALL integrate with an autocomplete provider for slash commands, file paths, attachment-prefixed paths, and application-defined suggestion sources, and SHALL expose a selectable suggestion list rendered through the TUI overlay stack and controlled by typed keyboard input.

#### Scenario: Tab requests file suggestions
- **WHEN** the editor receives Tab in a file-completion context
- **THEN** it requests suggestions from the autocomplete provider and displays matching options

#### Scenario: Selecting suggestion applies completion
- **WHEN** a suggestion is selected from the autocomplete list
- **THEN** the editor replaces the matched prefix with the selected completion and moves the cursor to the resulting position

#### Scenario: Suggestions render as overlay
- **WHEN** the editor has current autocomplete suggestions
- **THEN** it shows a focus-capturing or editor-owned overlay containing a selectable suggestion list rather than appending suggestions as ordinary editor text

#### Scenario: Autocomplete navigation uses typed keys
- **WHEN** autocomplete suggestions are visible and the editor receives Up or Down input routed to the suggestion overlay
- **THEN** the selected suggestion changes and a render is requested

#### Scenario: Escape cancels suggestions
- **WHEN** autocomplete suggestions are visible and Escape is received by the suggestion overlay or editor autocomplete state
- **THEN** the autocomplete overlay is hidden or removed without changing editor text

#### Scenario: Enter accepts selected suggestion
- **WHEN** autocomplete suggestions are visible and Enter is received by the suggestion overlay or editor autocomplete state
- **THEN** the selected suggestion is applied through the autocomplete provider completion contract

#### Scenario: Tab accepts or refreshes suggestions
- **WHEN** autocomplete suggestions are visible and Tab is received
- **THEN** the editor either accepts the selected suggestion or refreshes autocomplete according to the configured autocomplete mode

#### Scenario: Stale asynchronous suggestions are ignored
- **WHEN** an autocomplete request completes after editor text or cursor position has changed from the request snapshot
- **THEN** the editor ignores those suggestions and does not alter the current autocomplete overlay

#### Scenario: Application slash commands are suggested
- **WHEN** the editor is configured with application-supplied slash-command helpers and the text before the cursor is a slash-command prefix
- **THEN** matching commands are displayed as autocomplete suggestions with labels and optional descriptions

#### Scenario: Slash command execution remains application-owned
- **WHEN** a slash-command suggestion is accepted or submitted
- **THEN** the editor updates text or invokes existing submit/change callbacks, and the TUI runtime does not execute application commands

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

### Requirement: Editor autocomplete configuration
The editor public API SHALL expose configuration for autocomplete providers, autocomplete maximum visible suggestions, and autocomplete trigger behavior without requiring platform-specific code.

#### Scenario: Editor is created with provider
- **WHEN** an application creates an editor with an autocomplete provider in its options
- **THEN** the editor can request and display suggestions using shared core APIs

#### Scenario: Provider can be changed
- **WHEN** application code replaces or clears the editor autocomplete provider
- **THEN** any pending autocomplete request is cancelled and visible autocomplete UI is closed

#### Scenario: Maximum visible suggestions constrains overlay
- **WHEN** autocomplete suggestions exceed the configured maximum visible count
- **THEN** the suggestion overlay renders a scrollable or clipped list within that maximum

### Requirement: Editor autocomplete overlay lifecycle
The editor SHALL manage the lifecycle of its autocomplete overlay through the TUI context or overlay host and SHALL clean up the overlay when autocomplete ends or the editor is detached from autocomplete state.

#### Scenario: Suggestions create overlay
- **WHEN** a current autocomplete request produces non-empty suggestions
- **THEN** the editor shows or updates an overlay containing those suggestions and requests a render

#### Scenario: Empty suggestions close overlay
- **WHEN** a current autocomplete request produces no suggestions
- **THEN** the editor closes any visible autocomplete overlay and requests a render if visible state changed

#### Scenario: Editor mutation refreshes autocomplete
- **WHEN** editor text changes while autocomplete is active
- **THEN** the editor cancels stale requests and requests fresh suggestions for the new cursor snapshot

#### Scenario: Submit closes autocomplete
- **WHEN** the editor submits text through its configured submit behavior
- **THEN** any visible autocomplete overlay is closed and pending autocomplete requests are cancelled

### Requirement: Editor autocomplete overlay placement
The editor autocomplete API SHALL position suggestion overlays adjacent to the rendered editor input area by default, without requiring application code to compute terminal-global rows manually. Applications MUST still be able to override autocomplete overlay placement when they need custom positioning.

#### Scenario: Demo suggestions appear next to editor
- **WHEN** slash-command suggestions are shown in the shared interactive demo
- **THEN** the suggestion overlay appears immediately after the rendered editor area, clamped by normal overlay bounds, instead of at the terminal bottom by default

#### Scenario: Application updates autocomplete overlay placement
- **WHEN** an application can compute the editor's current rendered row during layout
- **THEN** it can update editor autocomplete overlay options before compositing so suggestions track the editor position on resize

#### Scenario: Editor default placement tracks rendered height
- **WHEN** the editor renders multiple wrapped or logical lines and autocomplete suggestions are visible
- **THEN** the editor positions the suggestion overlay after the rendered editor area using its current visual height

#### Scenario: Demo does not compute autocomplete row manually
- **WHEN** the shared interactive demo renders the editor with autocomplete enabled
- **THEN** demo layout code does not manually calculate terminal row values for the editor's suggestion overlay

#### Scenario: Custom placement override is preserved
- **WHEN** an application configures explicit autocomplete overlay options
- **THEN** the editor respects those options instead of replacing them with default adjacent placement

#### Scenario: Resize updates adjacent placement
- **WHEN** terminal width changes and editor wrapping changes while autocomplete is visible
- **THEN** the suggestion overlay is repositioned next to the editor's newly rendered visual area

