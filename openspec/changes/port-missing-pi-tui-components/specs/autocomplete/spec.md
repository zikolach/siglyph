## ADDED Requirements

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
