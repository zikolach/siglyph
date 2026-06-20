# autocomplete Specification

## Purpose
Defines backend-independent autocomplete models, provider contracts, completion application, path and slash-command helpers, cancellation behavior, overlay interaction, and keybinding integration.
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

### Requirement: Autocomplete keybinding precedence
When editor autocomplete suggestions are visible, the editor and suggestion overlay SHALL apply selection/autocomplete keybindings before ordinary text-editing commands according to upstream `pi-tui` behavior.

#### Scenario: Cancel binding closes suggestions
- **WHEN** autocomplete suggestions are visible and the configured selection-cancel command is received
- **THEN** the suggestion overlay closes without changing editor text

#### Scenario: Up and Down navigate suggestions
- **WHEN** autocomplete suggestions are visible and the configured select-up or select-down command is received
- **THEN** the selected suggestion changes instead of moving the editor cursor or navigating prompt history

#### Scenario: Tab accepts selected suggestion
- **WHEN** autocomplete suggestions are visible and the configured input-tab command is received
- **THEN** the editor applies the selected suggestion through the autocomplete completion contract instead of inserting a tab character

#### Scenario: Enter completion may fall through to slash submit
- **WHEN** autocomplete suggestions are visible and the configured select-confirm command accepts a slash-command suggestion
- **THEN** the editor applies the completion, closes autocomplete, and follows upstream submit behavior for slash-command completion

### Requirement: Autocomplete uses configurable select keybindings
Autocomplete suggestion navigation SHALL use the configured keybinding manager for selection commands rather than hard-coded key names.

#### Scenario: Custom select-down binding navigates suggestions
- **WHEN** an application configures `tui.select.down` to a custom key and suggestions are visible
- **THEN** that key moves the selected suggestion down

#### Scenario: Default selection bindings remain pi-tui-compatible
- **WHEN** no custom keybindings are configured
- **THEN** Up, Down, PageUp, PageDown, Enter, Escape, and `Ctrl+C` behave according to the default selection command mappings where implemented by the suggestion component

### Requirement: Fuzzy ranking utility
The autocomplete capability SHALL provide a dependency-free fuzzy matching utility that can rank candidate items by ordered character matches, word-boundary matches, consecutive matches, exact matches, and tokenized multi-word queries.

#### Scenario: Exact match ranks before loose match
- **WHEN** fuzzy filtering is applied to candidates `model`, `my-model`, and `markdown` with query `model`
- **THEN** the exact `model` candidate is ranked before candidates that only match through separators or gaps

#### Scenario: Tokenized query requires all tokens
- **WHEN** fuzzy filtering is applied with a query containing multiple whitespace-separated tokens
- **THEN** only candidates matching every token are returned and they are ordered by total fuzzy score

### Requirement: Filesystem path completion helper
The autocomplete capability SHALL provide an optional path completion helper that enumerates filesystem paths behind the existing `PathCompletionProvider` boundary without making shell tools or third-party dependencies mandatory.

#### Scenario: Path helper returns bounded results
- **WHEN** a path completion request is made for a directory with more candidates than the configured maximum result count
- **THEN** the helper returns at most the configured maximum number of deterministic suggestions

#### Scenario: Path helper preserves attachment syntax
- **WHEN** a completion request uses an attachment prefix such as `@` or `@"`
- **THEN** accepted completion text preserves the attachment marker and required quoting rules

#### Scenario: Path helper can be cancelled
- **WHEN** an in-flight filesystem completion request is cancelled because editor text changed
- **THEN** the helper stops delivering stale suggestions to the editor callback

### Requirement: Autocomplete stacked trigger prefixes
The combined autocomplete provider SHALL support configurable natural trigger prefixes in addition to slash commands and path/attachment completion, including at least `#` as an application-owned trigger prefix.

#### Scenario: Hash trigger requests configured suggestions
- **WHEN** the editor text before the cursor begins an active `#` trigger token
- **THEN** the combined provider requests suggestions from the configured trigger source and returns selectable items for that prefix

#### Scenario: Trigger completion remains application-owned
- **WHEN** a `#` trigger completion is accepted
- **THEN** the editor applies the selected completion text and does not execute application commands itself

### Requirement: Autocomplete refresh is debounced and cancellable
Autocomplete refreshes caused by rapid typing SHALL cancel stale provider work and MAY debounce expensive providers while preserving prompt responsiveness.

#### Scenario: Rapid typing cancels prior lookup
- **WHEN** the user types additional characters while a previous autocomplete lookup is in flight
- **THEN** the previous lookup handle is cancelled and its eventual result is ignored

#### Scenario: Visible suggestions survive refresh until replacement
- **WHEN** autocomplete suggestions are visible and a refresh request starts for a refined prefix
- **THEN** the editor keeps the current suggestions visible until the current request completes, returns no suggestions, or is explicitly cancelled
