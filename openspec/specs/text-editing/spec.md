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

#### Scenario: Fullwidth punctuation separates words
- **WHEN** word navigation crosses mixed CJK text separated by fullwidth punctuation
- **THEN** cursor movement and word deletion treat the fullwidth punctuation as a boundary and do not merge adjacent words across that punctuation

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

### Requirement: Editor prompt history navigation parity
The editor SHALL support upstream-compatible prompt history navigation for submitted prompts using Up and Down when the editor is empty or already browsing history at visual boundaries.

#### Scenario: Submitted prompt can be added to history
- **WHEN** application code records a submitted prompt in editor history
- **THEN** empty or whitespace-only prompts are ignored, consecutive duplicate prompts are ignored after trimming, and retained history is capped at 100 entries

#### Scenario: Up enters history from empty editor
- **WHEN** the editor is empty and receives the `tui.editor.cursorUp` command while history contains prompts
- **THEN** it loads the most recent prompt, moves the cursor to the end of that prompt, invokes change callbacks for the new text, and records an undo snapshot for the pre-history state

#### Scenario: Down returns from history to current prompt state
- **WHEN** the editor is browsing history and receives the `tui.editor.cursorDown` command at the last visual line until it moves past the newest entry
- **THEN** it returns to the current prompt state according to upstream behavior and resets the history index

#### Scenario: Edits leave history browsing mode
- **WHEN** the user mutates editor text after loading a history entry
- **THEN** subsequent editing commands operate on the loaded text and the editor no longer treats Up/Down as continuing the previous history browse unless history navigation is re-entered

### Requirement: Editor page navigation parity
The editor SHALL handle PageUp and PageDown through the configured `tui.editor.pageUp` and `tui.editor.pageDown` commands by moving the logical cursor by a visible page while respecting wrapped visual lines.

#### Scenario: PageUp moves cursor by visible page
- **WHEN** the editor receives the page-up command with multiple visual lines above the cursor
- **THEN** it moves the cursor upward by the editor page size and clamps to the first visual line if fewer lines are available

#### Scenario: PageDown moves cursor by visible page
- **WHEN** the editor receives the page-down command with multiple visual lines below the cursor
- **THEN** it moves the cursor downward by the editor page size and clamps to the last visual line if fewer lines are available

#### Scenario: Page movement preserves desired column where possible
- **WHEN** PageUp or PageDown moves between wrapped visual lines of different widths
- **THEN** the cursor lands on the closest valid display column according to existing editor layout rules

### Requirement: Editor jump-to-character parity
The editor SHALL implement upstream-compatible jump-to-character mode through `tui.editor.jumpForward` and `tui.editor.jumpBackward` commands.

#### Scenario: Jump forward waits for printable character
- **WHEN** the editor receives the jump-forward command
- **THEN** it enters jump mode and the next printable character moves the cursor to the first matching occurrence after the current cursor across following lines

#### Scenario: Jump backward waits for printable character
- **WHEN** the editor receives the jump-backward command
- **THEN** it enters jump mode and the next printable character moves the cursor to the first matching occurrence before the current cursor across preceding lines

#### Scenario: Repeating jump command cancels jump mode
- **WHEN** the editor is waiting for a jump character and receives either jump command again
- **THEN** it cancels jump mode without moving the cursor or mutating text

#### Scenario: Missing jump target leaves cursor unchanged
- **WHEN** the editor receives a printable jump character that does not occur in the requested direction
- **THEN** jump mode exits and the cursor remains at its previous position

### Requirement: Input and editor use configurable keybindings
The `Input` and `Editor` components SHALL dispatch editing, submit, newline, tab/autocomplete, undo, kill-ring, and movement commands through the configured keybinding manager while preserving current `pi-tui` defaults.

#### Scenario: Custom submit binding affects editor
- **WHEN** an application configures submit to `Ctrl+Enter` and plain Enter is not in the resolved submit binding
- **THEN** the editor submits on `Ctrl+Enter` and does not submit on plain Enter unless another configured command maps plain Enter to submit

#### Scenario: Default editing aliases still work
- **WHEN** the default keybinding manager is used
- **THEN** existing aliases for movement, deletion, undo, yank, and yank-pop continue to invoke the same editing behavior as before this change

#### Scenario: Unsupported bindings are documented
- **WHEN** an upstream default key combination cannot be represented reliably by the typed terminal input model or common terminal encodings
- **THEN** the implementation documents the deviation and preserves the closest available editing command behavior

### Requirement: Text editing key micro-parity audit
The project SHALL audit upstream editor and input editing key behaviors against local text-editing tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Editing key behavior is already covered
- **WHEN** upstream movement, deletion, history, undo, yank, or submit behavior has an equivalent local test and matching behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Editing key behavior lacks only a test
- **WHEN** local editing key behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Editing key behavior gap is found
- **WHEN** local editing key behavior differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Editing key intentional deviation is found
- **WHEN** local editing key behavior intentionally differs from upstream because a terminal encoding cannot be represented reliably
- **THEN** the audit records intentional deviation and updates porting notes

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

### Requirement: Editor streamed paste transaction
The multiline Editor SHALL treat one `PasteStart` through `PasteEnd` stream as one logical edit while keeping parser and runtime transit bounded.

#### Scenario: Whole paste semantics span chunks
- **WHEN** bracketed paste bytes cross parser, UTF-8 decoder, CRLF, and Unicode grapheme boundaries
- **THEN** newline normalization, aggregate line and grapheme thresholds, marker content, and cursor placement use the complete normalized paste

#### Scenario: Paste completion has one edit effect
- **WHEN** a non-empty paste completes
- **THEN** the Editor captures one undo snapshot, invokes `onChange` once, refreshes active autocomplete once, and requests one render

#### Scenario: Empty paste has no effect
- **WHEN** `PasteStart` is followed by `PasteEnd` without accepted content
- **THEN** text, cursor, undo, callbacks, autocomplete, and rendering remain unchanged

#### Scenario: Non-paste input commits an unfinished paste
- **WHEN** any non-paste input arrives during an unfinished paste session
- **THEN** the Editor commits all accepted normalized paste content as one edit before handling that input

#### Scenario: Large paste remains expandable
- **WHEN** aggregate normalized content exceeds 10 lines or 1000 grapheme clusters
- **THEN** one marker stores the complete normalized content and submit or expansion recovers it exactly

### Requirement: Paste cursor accounting spans chunk boundaries
Input paste handling SHALL carry bounded Unicode 17.0.0 UAX #29 default extended grapheme cluster state across `PasteChunk` and fragmented UTF-8 boundaries and place the cursor after the complete grapheme sequence.

#### Scenario: Grapheme spans paste chunks
- **WHEN** the code points of one Unicode 17.0.0 default extended grapheme cluster arrive in different paste chunks
- **THEN** cursor accounting counts that cluster once and places the cursor after it

#### Scenario: UTF-8 spans paste chunks
- **WHEN** the UTF-8 bytes of code points in one Unicode 17.0.0 default extended grapheme cluster arrive in different transport chunks
- **THEN** decoding and cursor accounting preserve the complete cluster and place the cursor after it

#### Scenario: Required rule interactions span chunks
- **WHEN** Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequences cross paste chunk boundaries
- **THEN** Input cursor accounting produces the same cluster count and final cursor as whole-string segmentation

#### Scenario: Chunking does not change accounting
- **WHEN** the same paste is delivered whole, at every single code-point split, one code point per chunk, or with fragmented UTF-8
- **THEN** Input and Editor place the cursor at the same final Unicode grapheme position

#### Scenario: Paste accounting state remains bounded
- **WHEN** streamed paste content grows without a core content-size limit
- **THEN** grapheme accounting state remains bounded independently of the application-owned pasted content

### Requirement: Input paste appends incrementally
Input SHALL use one mutable paste session containing a prefix builder, appended decoded and newline-normalized paste text, and one retained suffix. It SHALL finalize that session once at `PasteEnd` or before handling a non-paste interruption.

#### Scenario: Accepted chunks append once
- **WHILE** paste chunks arrive
- **WHEN** each decoded non-empty segment is accepted
- **THEN** Input appends that segment once without rebuilding prior pasted text or rescanning the accumulated prefix

#### Scenario: Active paste is publicly observable without intermediate rendering
- **WHILE** a paste session is active
- **WHEN** `value` or `render` is called after accepted chunks
- **THEN** it exposes the accepted prefix, pasted text, retained suffix, and incremental grapheme cursor while paste start and chunks request no render

#### Scenario: Paste finalizes once
- **WHEN** `PasteEnd` or a non-paste input ends an active paste
- **THEN** Input publishes one immutable value, preserves exact text and one undo boundary, and requests no chunk-driven intermediate render

#### Scenario: Grapheme state spans every insertion position
- **WHEN** combining marks, ZWJ sequences, or regional-indicator pairs cross chunks at the start, middle, or end cursor position
- **THEN** incremental grapheme state places backspace immediately after the complete pasted grapheme

### Requirement: Filter paste appends without per-chunk filtering
`SelectList` and `SettingsList` SHALL use one shared package-private paste session built from `StringBuilder` and `TerminalUtf8Decoder`. The session SHALL retain the initial committed query `String` by reference, store only newly decoded normalized pasted text in its appended builder, and cache at most one immutable full-query snapshot.

#### Scenario: Filter chunks stream
- **WHILE** a filter paste streams
- **WHEN** chunks arrive
- **THEN** the component decodes, normalizes CR and LF to spaces, and appends each decoded segment once without filtering, clamping, selecting, callbacks, or rendering

#### Scenario: Filter query snapshot is reused
- **WHILE** no non-empty normalized decoded text has been accepted since the last query snapshot
- **WHEN** query, render, or commit reads the query
- **THEN** the session returns the same cached `String` reference without rebuilding it

#### Scenario: Accepted filter text invalidates the snapshot
- **WHEN** non-empty normalized decoded text is appended or emitted by decoder flush
- **THEN** the session immediately releases any stale combined snapshot, points cached storage to `initialQuery`, marks the query dirty without rebuilding it, and the next read builds one exact combined snapshot without an intermediate appended snapshot

#### Scenario: Additional accepted filter text remains lazy
- **WHILE** the query is dirty
- **WHEN** additional non-empty normalized decoded segments are accepted
- **THEN** the session retains only `initialQuery` plus appended mutable text and does not materialize a combined snapshot until the next query read

#### Scenario: Filter paste completes or is interrupted
- **WHEN** paste ends or a non-paste input arrives
- **THEN** the component flushes and commits accepted text once, recomputes final filter selection state once, and handles any later input after that commit

#### Scenario: Active filter paste is observed
- **WHILE** a filter paste is active
- **WHEN** query or render is called explicitly
- **THEN** query and the Settings search prompt expose accepted text while rendered candidates remain filtered against the committed query without changing selection or clamp state

#### Scenario: Filter paste framing is irregular
- **WHEN** paste is empty, a chunk or end is orphaned, or start repeats
- **THEN** empty and orphan events are no-ops and repeated start commits prior accepted content before opening a new session

### Requirement: Insertion cursor follows final grapheme segmentation
Input, EditorBuffer, and Editor SHALL place the cursor at the first final grapheme boundary at or after the insertion end after resegmenting the resulting value.

#### Scenario: Inserted text joins neighboring graphemes
- **WHEN** typed, programmatic, or streamed inserted code points join the left neighbor, right neighbor, or both through combining, prepend, Hangul, Indic, GB11, or regional-indicator rules
- **THEN** the cursor is a valid boundary in the final segmentation immediately after the resulting joined cluster
- **AND** immediate backward deletion removes the cluster before that cursor

### Requirement: Input yank-pop restores the pre-yank state
Input SHALL retain the exact pre-yank `Input.State` after a successful yank. Yank-pop SHALL restore
that state before inserting the rotated kill-ring candidate. Another completed editing action SHALL
clear the retained yank base.

#### Scenario: Yanked text joins neighboring graphemes
- **WHEN** yanked text joins a left or right neighbor through combining, prepend, Hangul, Indic, GB11, or regional-indicator rules
- **THEN** yank-pop restores the original value and cursor before inserting the rotated candidate
- **AND** original neighboring content survives replacement

#### Scenario: Repeated yank-pop uses one base state
- **WHEN** yank-pop runs repeatedly after one successful yank
- **THEN** each rotated candidate replaces the prior yank from the same exact pre-yank state

#### Scenario: Another edit ends the yank chain
- **WHEN** another editing action completes after yank
- **THEN** the retained pre-yank state is cleared and yank-pop does not replace text

### Requirement: Editing cursor metadata follows visible fake cursor ownership
Focused Input and Editor SHALL attach structured frame-relative cursor metadata only when they own
input. Normal rows SHALL attach a candidate only when the complete fake cursor token survives width
truncation. A focused Editor positive impossible-width owner row SHALL attach column-zero metadata
without printable fake-cursor content. Editor width zero or below, unfocused rendering, and
autocomplete-owned rendering SHALL attach no cursor metadata.

#### Scenario: Focused normal-width cursor survives
- **WHEN** a focused Input or Editor fake cursor fits within the requested width
- **THEN** rendering attaches its zero-based display-cell coordinate

#### Scenario: Normal cursor token is truncated
- **WHEN** width truncation omits the complete fake cursor token
- **THEN** the normal row contains no cursor metadata

#### Scenario: Editor impossible-width row owns the cursor
- **WHEN** a focused Editor cursor owns a blank row for an over-wide cluster and autocomplete does not own input
- **THEN** rendering attaches cursor metadata at column zero without printable fake-cursor content

#### Scenario: Editing component does not own input
- **WHEN** Input or Editor is unfocused, or Editor autocomplete owns input
- **THEN** rendering attaches no cursor metadata

### Requirement: EditorBuffer source remains exact through display projection
EditorBuffer SHALL retain exact unlimited source text and Unicode 17.0.0 source-grapheme cursor
positions. ANSI sanitization and Editor display projection SHALL NOT mutate, truncate, replace, or
cap retained source.

#### Scenario: Rejected source expands to several display units
- **WHEN** one retained source range sanitizes to several visible graphemes or wrapped rows
- **THEN** EditorBuffer text and cursor positions remain exact
- **AND** display projection owns every output unit with the original half-open source range
