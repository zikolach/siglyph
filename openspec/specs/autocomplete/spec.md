# autocomplete Specification

## Purpose
TBD - created by archiving change add-editor-autocomplete-overlay. Update Purpose after archive.
## Requirements
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

### Requirement: Combined slash + path autocomplete provider
The autocomplete API SHALL expose a provider that can return suggestions for both slash-command completion and file/attachment path completion from the same typed prefix flow.

#### Scenario: Slash suggestions continue existing prefix behavior
- **WHEN** text before the cursor begins with a command prefix and has no completion-space separator
- **THEN** the provider returns matching slash-command entries with stable display labels and optional argument hints

#### Scenario: File suggestions trigger on explicit path tokens
- **WHEN** the current token indicates a path prefix (for example `/`, `./`, `../`, absolute path start, quoted path start, or `@` attachment marker)
- **THEN** the provider returns path completion items rooted to the applicable directory and preserves quoting rules in replacement text

#### Scenario: Mixed suggestions are deterministic
- **WHEN** both slash and file sources produce candidates for the same input context
- **THEN** the provider returns a deterministic ordering and stable suggestion prefix value that can be applied with `applyCompletion`

### Requirement: Deterministic path prefix parsing
The combined provider SHALL parse completion prefixes from cursor context with deterministic treatment of quotes, spaces, and attachment markers.

#### Scenario: Quoted prefixes are recognized
- **WHEN** users start a quoted path (such as `"` or `@"`)
- **THEN** the provider extracts the active token without the closing quote and excludes unmatched quote segments from path matching

#### Scenario: Path prefix does not leak delimiter tokens
- **WHEN** completion occurs after token delimiters like spaces, equals, punctuation or quoting syntax
- **THEN** the provider replaces exactly the active token prefix and preserves surrounding text unmodified

### Requirement: Autocomplete cancellation and staleness remain editor-safe
The combined provider workflow SHALL integrate with existing cancellable request callbacks and must ignore stale completion responses when cursor or text snapshots no longer match.

#### Scenario: Stale completion response is ignored
- **WHEN** a completion request is inflight and user typing changes the buffer snapshot before callback
- **THEN** the provider result is ignored and does not update visible suggestions

#### Scenario: Request cancellation is propagated
- **WHEN** completion context changes (provider replaced, text changed, or overlay closed)
- **THEN** in-flight request handles are cancelled so only current provider state can drive future overlays

