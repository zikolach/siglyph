## ADDED Requirements

### Requirement: Autocomplete item and suggestion models
The library SHALL expose backend-independent autocomplete data models for request context, selectable items, suggestion groups, and completion results in the shared core API.

#### Scenario: Application constructs suggestions
- **WHEN** an application creates autocomplete items with values, labels, and optional descriptions
- **THEN** the editor autocomplete API can display those items without platform-specific code or third-party runtime dependencies

#### Scenario: Completion result moves cursor
- **WHEN** a provider applies a selected completion
- **THEN** the result includes updated editor lines and a cursor position that can be applied to the editor buffer

### Requirement: Cancellable autocomplete provider contract
The autocomplete provider contract SHALL support asynchronous suggestion lookup through a cancellable request handle and callback boundary without requiring `TUI`, `Component`, or `Editor` to be parameterized by an effect type.

#### Scenario: Provider completes request asynchronously
- **WHEN** an autocomplete provider starts a suggestion request and later invokes its completion callback
- **THEN** the editor receives the suggestions and schedules a render through the TUI context if the request is still current

#### Scenario: Stale request is cancelled
- **WHEN** editor text, cursor position, or autocomplete mode changes before a pending provider request completes
- **THEN** the editor cancels the previous request handle and ignores any stale completion result

#### Scenario: Provider reports failure
- **WHEN** a provider reports an autocomplete failure through the callback
- **THEN** the editor clears or preserves autocomplete UI according to the current request state without crashing the terminal runtime

### Requirement: Synchronous provider adapter
The autocomplete API SHALL provide a way to implement simple synchronous providers using the same public contract as asynchronous providers.

#### Scenario: Synchronous provider returns suggestions immediately
- **WHEN** a synchronous provider has matching suggestions for the current request
- **THEN** it completes the callback during the request call and returns a no-op cancellable handle

#### Scenario: Synchronous provider has no suggestions
- **WHEN** a synchronous provider has no matching suggestions
- **THEN** it reports no suggestions and the editor does not show an autocomplete overlay

### Requirement: Slash command helper models
The autocomplete package SHALL provide slash-command helper models or providers where applications supply command names, descriptions, and optional argument completion logic, while command execution semantics remain outside the TUI runtime.

#### Scenario: Application supplies slash commands
- **WHEN** an application supplies slash-command metadata to an autocomplete helper
- **THEN** the helper can produce suggestions for editor text beginning with `/`

#### Scenario: TUI does not execute slash command
- **WHEN** a slash-command completion is selected or submitted
- **THEN** the TUI library updates editor text or emits callbacks only, and application code remains responsible for interpreting the command

### Requirement: Completion application is deterministic
Autocomplete completion application SHALL be deterministic for a given request snapshot, selected item, and matched prefix.

#### Scenario: Prefix is replaced by selected item
- **WHEN** a selected item is applied to a request whose cursor still matches the original prefix snapshot
- **THEN** the matched prefix is replaced with the selected completion and surrounding text is preserved

#### Scenario: Changed editor state rejects stale completion
- **WHEN** the editor content or cursor no longer matches the request snapshot
- **THEN** the stale completion is not applied to the current buffer
