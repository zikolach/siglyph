## ADDED Requirements

### Requirement: Bounded baseline streaming reuse

The baseline Markdown renderer SHALL reuse output for equal parsed top-level blocks at equal content width and SHALL reuse highlighted code for equal language and code across width changes. It SHALL bound each reuse cache by 256 entries and 1 MiB of deterministic accounted retention including keys and values, evict least-recently-used entries, and bypass oversized entries. Full-document parsing MAY still occur.

#### Scenario: Stable blocks survive appended text
- **WHEN** new text appends a block without changing preceding parsed blocks or width
- **THEN** preceding blocks reuse their styled and wrapped output
- **AND** parser work is measured separately from block rendering work

#### Scenario: Resize preserves highlighting
- **WHEN** content width changes without changing code or language
- **THEN** the renderer reuses highlighted code while recomputing width-dependent block output

#### Scenario: Refresh invalidates both reuse layers
- **WHEN** the component is explicitly invalidated, its context is reassigned, or effective session capabilities change
- **THEN** both baseline reuse caches are refreshed
- **AND** baseline cache generation advances so shared component full-render entries cannot conceal the refresh

#### Scenario: Mutable theme refresh
- **WHEN** mutable theme or highlighter state changes and the caller explicitly invalidates
- **THEN** subsequent output reflects the new state rather than cached styling

#### Scenario: Spacing and eviction remain correct
- **WHEN** parsed list spacing or streamed tails change, or retention exceeds either budget
- **THEN** output matches a fresh render of the current document
- **AND** eviction or oversized bypass cannot return stale output

### Requirement: Preserve Markdown extension and output boundaries

Reuse SHALL NOT alter custom MarkdownRenderer contracts, introduce dependencies, or bypass ComponentRender typed text safety. Unsupported reference-definition grammar SHALL NOT be introduced by this optimization.

#### Scenario: Custom renderer remains in control
- **WHEN** a component uses a custom renderer rather than the baseline
- **THEN** its existing render, attached render, cacheGeneration and explicit component invalidation behavior remains unchanged
