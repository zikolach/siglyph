# markdown-rendering Specification

## Purpose
Defines the separate pluggable Markdown rendering module, parser-selection constraints, initial Markdown feature scope, and pi-tui Markdown parity tracking expectations.
## Requirements
### Requirement: Separate Markdown module
The library SHALL provide Markdown rendering as a separate module from the core renderer and terminal backends.

#### Scenario: Core builds without Markdown
- **WHEN** the core module is compiled without the Markdown module
- **THEN** core components, renderer, terminal abstractions, and MVP components remain available

#### Scenario: Markdown module integrates as component
- **WHEN** an application includes the Markdown module
- **THEN** it can render Markdown content through the same component contract as other components

### Requirement: Pluggable parser strategy
The Markdown module SHALL support a parser abstraction so platform-appropriate parser implementations can be selected for JVM and Native after explicit dependency approval.

#### Scenario: Parser dependency is proposed
- **WHEN** a Markdown parser dependency is considered
- **THEN** the dependency, target support, license, size, alternatives, and reason for selection are documented before adoption

#### Scenario: Parser implementation is replaceable
- **WHEN** the Markdown parser implementation changes
- **THEN** the public Markdown component API remains stable for applications

### Requirement: Initial Markdown feature scope
The Markdown renderer SHALL plan support for headings, paragraphs, emphasis, inline code, fenced code blocks, lists, links, blockquotes, horizontal rules, and tables, while allowing incremental delivery after the first usable milestone.

#### Scenario: Unsupported Markdown construct
- **WHEN** Markdown content contains a construct not yet implemented
- **THEN** the renderer falls back to readable plain text rather than failing the TUI render

#### Scenario: Rendered Markdown respects width
- **WHEN** Markdown content is rendered at a given terminal width
- **THEN** every output line respects that width after ANSI escapes are ignored

### Requirement: pi-tui Markdown parity tracking
The Markdown module SHALL document which `pi-tui` Markdown behaviors are matched and which are intentionally deferred or different.

#### Scenario: Markdown parity fixture added
- **WHEN** a `pi-tui` Markdown test is ported
- **THEN** the Scala test identifies the corresponding upstream behavior or documents the reason for deviation

### Requirement: Markdown renderer implementation and width-safe output
The markdown module SHALL provide a concrete dependency-free basic renderer that converts markdown text into width-safe terminal lines through the existing `MarkdownRenderer` contract.

#### Scenario: Markdown renders to terminal lines
- **WHEN** `render(markdown, width)` is called with headings, bullet lists, code spans, and block quotes
- **THEN** it returns a non-empty `Vector[String]` where each rendered line is ANSI-aware width-safe for the requested width

#### Scenario: Width requests are respected during rendering
- **WHEN** the renderer receives a small width
- **THEN** headings, fences, and list rows are wrapped or truncated so every line remains within width when measured by visible width semantics

### Requirement: Parser abstraction remains pluggable
The markdown module SHALL keep parser strategy pluggable so dependency-free, JVM-specific, and Native-specific implementations can evolve independently of the shared component API.

#### Scenario: Shared API remains stable across implementations
- **WHEN** a parser implementation is swapped for another backend-compatible implementation
- **THEN** the public `MarkdownRenderer` API and rendered module surface remain unchanged

#### Scenario: Parser errors do not crash rendering
- **WHEN** the selected parser reports invalid or unsupported markdown input
- **THEN** rendering emits readable fallback text rather than throwing from component rendering

#### Scenario: Third-party parser adapters are optional
- **WHEN** an application wants richer Markdown parsing than the dependency-free baseline
- **THEN** it can opt into an approved adapter module without adding that third-party parser dependency to `core` or to the baseline `markdown` module

#### Scenario: Adapter availability differs by platform
- **WHEN** JVM and Scala Native require different parser strategies
- **THEN** optional adapter modules preserve the same public renderer/component contract while selecting platform-compatible internals

### Requirement: Feature parity roadmap is explicit and testable
The renderer SHALL document supported and intentionally unsupported markdown constructs, and unsupported constructs must remain readable.

#### Scenario: Unsupported construct remains visible
- **WHEN** markdown contains an unsupported construct
- **THEN** the output falls back to readable plain or minimal formatting and test coverage identifies that construct as deferred or unsupported

#### Scenario: Supported subset is declared for migration planning
- **WHEN** tests run against the markdown module
- **THEN** the supported subset includes at minimum headings, paragraphs, code, lists, inline emphasis, blockquotes, and horizontal/inline separators

