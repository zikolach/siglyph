## MODIFIED Requirements

### Requirement: Filesystem path completion helper
The autocomplete capability SHALL provide an optional path completion helper that enumerates filesystem paths behind the existing `PathCompletionProvider` boundary without making shell tools or third-party dependencies mandatory. The helper SHALL distinguish the completion current directory from its canonical containment root, SHALL apply explicit policy to parent, home, and absolute paths, and SHALL document that ordering is deterministic within the bounded candidate set while filesystem enumeration membership is implementation-defined when a scan bound is reached.

#### Scenario: Relative path resolves from current directory
- **WHEN** a request completes a relative path token
- **THEN** the helper resolves candidates from the configured completion current directory

#### Scenario: Parent traversal remains contained
- **WHEN** a relative path contains `..` and its canonical target remains inside the configured containment root
- **THEN** the helper may return matching contained suggestions while preserving the user's relative syntax

#### Scenario: Parent traversal cannot escape root
- **WHEN** a relative path, symlink, or canonical target would escape the configured containment root
- **THEN** the helper returns no escaped candidate and discloses no path outside the allowed root

#### Scenario: Home and absolute policies are explicit
- **WHEN** a request uses `~/` or an absolute path
- **THEN** the helper accepts it only when the corresponding option is enabled and the canonical candidate is inside an explicitly allowed root

#### Scenario: Path helper returns bounded ordered results
- **WHEN** a path completion request encounters more candidates than the configured scan or result bound
- **THEN** the helper respects both bounds, sorts the candidate set it evaluated with the documented stable ordering, and returns no more than the configured result maximum

#### Scenario: Path helper preserves attachment syntax
- **WHEN** a completion request uses an attachment prefix such as `@` or `@"`
- **THEN** accepted completion text preserves the attachment marker and required quoting rules

#### Scenario: Path helper can be cancelled
- **WHEN** an in-flight filesystem completion request is cancelled because editor text changed
- **THEN** the helper stops scanning promptly and does not deliver stale suggestions to the editor callback

#### Scenario: Existing base-directory configuration remains source-compatible
- **WHEN** an existing caller supplies only the legacy base-directory option
- **THEN** that directory acts as both completion current directory and containment root unless the caller opts into the new policy fields

## ADDED Requirements

### Requirement: Optional bounded recursive attachment completion
Filesystem autocomplete SHALL provide an opt-in recursive attachment-search mode that remains dependency-free, cancellable, containment-safe, and bounded by configured depth, visited-entry, and result limits.

#### Scenario: Recursive attachment search finds a descendant
- **WHEN** recursive attachment search is enabled and a matching file exists below the current directory within all configured bounds
- **THEN** the provider returns a suggestion preserving attachment syntax and a path relative to the configured completion context

#### Scenario: Recursive search honors every bound
- **WHEN** traversal would exceed its depth, visited-entry, or result limit
- **THEN** the provider stops the corresponding work without continuing an unbounded filesystem walk

#### Scenario: Recursive search is cancelled
- **WHEN** the request handle is cancelled during traversal
- **THEN** filesystem walking stops promptly and no stale callback result is published

#### Scenario: Recursive search cannot follow an escaping symlink
- **WHEN** a traversed symlink resolves outside the configured containment root
- **THEN** the provider neither descends into nor suggests the escaped target
