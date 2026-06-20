## ADDED Requirements

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
