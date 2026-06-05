## MODIFIED Requirements

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

## ADDED Requirements

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
The editor autocomplete API SHALL allow applications to position suggestion overlays adjacent to the editor input area rather than forcing a terminal-global default placement.

#### Scenario: Demo suggestions appear next to editor
- **WHEN** slash-command suggestions are shown in the shared interactive demo
- **THEN** the suggestion overlay appears immediately after the rendered editor area, clamped by normal overlay bounds, instead of at the terminal bottom by default

#### Scenario: Application updates autocomplete overlay placement
- **WHEN** an application can compute the editor's current rendered row during layout
- **THEN** it can update editor autocomplete overlay options before compositing so suggestions track the editor position on resize
