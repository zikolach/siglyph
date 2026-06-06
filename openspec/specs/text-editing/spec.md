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

### Requirement: Single-line Input supports advanced editing history
The `Input` component SHALL provide user-visible undo and kill-ring style editing commands in addition to existing control-key editing, using current `pi-tui` default keybindings unless explicitly configured otherwise.

#### Scenario: Undo restores prior input state
- **WHEN** the user types text and then triggers Undo with the configured undo key binding (`Ctrl+-` by default, matching `pi-tui`)
- **THEN** `Input` reverts to the previous text and cursor cluster position and continues accepting input from that restored state

#### Scenario: Redo is not invented beyond upstream parity
- **WHEN** applications use the default editing model for `Input`
- **THEN** no redo command is exposed unless a future upstream-compatible design adds redo semantics explicitly

#### Scenario: Kill-ring tracks word deletions
- **WHEN** the user invokes a word deletion command (`Ctrl+W` or `Alt+Backspace` by default, matching `pi-tui`) repeatedly
- **THEN** removed segments are stored in a kill-ring list so later yank operations can recover the most recent removed content

#### Scenario: Yank restores killed content
- **WHEN** the user performs yank after one or more kill operations
- **THEN** `Input` inserts the most recent killed text at the cursor position and updates the cursor accordingly

#### Scenario: Yank-pop cycles kill-ring entries
- **WHEN** the user performs yank repeatedly without an intervening insertion command
- **THEN** successive yank-pop operations rotate kill-ring candidates and replace the previously inserted yanked segment with the next candidate

### Requirement: Editor supports undo and kill-ring commands
The editor SHALL expose undo and kill-ring behavior through the rendered `Editor` API, preserving buffer-cursor consistency, callback ordering, and current `pi-tui` default keybindings unless explicitly configured otherwise.

#### Scenario: Undo restores previous editor buffer
- **WHEN** editable multiline text is changed and Undo is invoked with the configured undo key binding (`Ctrl+-` by default, matching `pi-tui`)
- **THEN** the editor restores the prior `EditorBuffer` snapshot, including cursor line/column, and continues rendering from that state

#### Scenario: Redo is not invented beyond upstream parity
- **WHEN** applications use the default editing model for `Editor`
- **THEN** no redo command is exposed unless a future upstream-compatible design adds redo semantics explicitly

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

### Requirement: Editing command bindings follow pi-tui defaults
The `Input` and `Editor` components SHALL map advanced editing commands to the current upstream `pi-tui` defaults where the typed terminal input model can represent those keys.

#### Scenario: Kill and yank bindings are recognized
- **WHEN** users press `Ctrl+W`, `Alt+Backspace`, `Alt+D`, `Alt+Delete`, `Ctrl+Y`, or `Alt+Y`
- **THEN** the matching word-delete, word-delete-forward, yank, or yank-pop command is invoked according to the same default behavior as `pi-tui`

#### Scenario: Movement and line-editing bindings are recognized
- **WHEN** users press `Ctrl+A`, `Ctrl+E`, `Ctrl+K`, `Ctrl+U`, `Alt+Left`, `Ctrl+Left`, `Alt+B`, `Alt+Right`, `Ctrl+Right`, or `Alt+F`
- **THEN** the matching line movement, line deletion, or word movement command is invoked according to the same default behavior as `pi-tui`

#### Scenario: Unsupported terminal-specific encodings are documented
- **WHEN** a terminal or parser cannot distinguish an upstream key combination reliably
- **THEN** the Scala implementation documents the deviation and preserves the closest typed-input behavior without changing logical editing semantics

### Requirement: Hardware cursor marker support
When IME and terminal marker workflows are enabled, focused editing components SHALL emit a zero-width cursor marker in front of the visual cursor position while preserving fake-cursor rendering, and the marker SHALL be suitable for runtime hardware cursor positioning.

#### Scenario: Cursor marker is emitted on focused input
- **WHEN** an `Input` or `Editor` is focused and renders a fake cursor
- **THEN** it includes a terminal cursor marker sequence (or equivalent marker abstraction) immediately before the fake cursor token

#### Scenario: Marker does not alter semantic text
- **WHEN** callbacks observe the submitted editor value
- **THEN** no marker sequence appears in logical text values returned by `onSubmit` or `text` getters

#### Scenario: Unfocused editors do not claim hardware cursor
- **WHEN** an `Input` or `Editor` is not focused
- **THEN** it does not emit a cursor marker for hardware cursor positioning

#### Scenario: Autocomplete ownership suppresses editor marker when appropriate
- **WHEN** editor autocomplete has a focus-capturing suggestion overlay that owns keyboard input
- **THEN** the editor does not emit a stale hardware cursor marker that would compete with the active overlay focus target

