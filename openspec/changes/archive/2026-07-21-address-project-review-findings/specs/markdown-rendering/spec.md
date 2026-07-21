## ADDED Requirements

### Requirement: Markdown component render caching
The Markdown component SHALL cache its most recent successful rendered result using every input that can affect visible output, SHALL invalidate the cache when any such input changes, and SHALL keep caching bounded so repeated static renders do not retain unbounded documents or widths. Parser and renderer errors SHALL preserve the existing safe fallback contract.

#### Scenario: Identical render reuses cached output
- **WHEN** the same Markdown component renders unchanged text with the same width, padding, renderer identity, and renderer configuration
- **THEN** it returns the cached component output without parsing or rendering the document again

#### Scenario: Text mutation invalidates cache
- **WHEN** the component text changes after a cached render
- **THEN** the next render parses and renders the new text and cannot return the prior document's rows

#### Scenario: Width or padding change invalidates cache
- **WHEN** an input affecting layout differs from the cached render
- **THEN** the component recomputes width-safe rows using the new layout input

#### Scenario: Renderer configuration cannot reuse stale cache
- **WHEN** the parser, renderer, theme, capability, highlighter, or rendering option affecting output changes
- **THEN** the component does not reuse output produced by the previous configuration

#### Scenario: Cache remains bounded
- **WHEN** a component is rendered at many widths or with many successive documents
- **THEN** retained cached state remains within the documented fixed bound

#### Scenario: Native and JVM behavior match
- **WHEN** the shared Markdown component runs on JVM and Scala Native
- **THEN** both platforms apply the same cache key, invalidation, fallback, and bounded-retention rules without a new runtime dependency
