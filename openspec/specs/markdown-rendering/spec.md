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

### Requirement: Markdown theme hooks
The Markdown module SHALL expose theme hooks for supported block and inline constructs so applications can style headings, links, code, code blocks, quotes, horizontal rules, lists, tables, and emphasis without replacing the renderer contract.

#### Scenario: Theme styles heading output
- **WHEN** Markdown containing a heading is rendered with a theme that styles headings
- **THEN** the rendered heading line includes the theme output and remains within the requested visible width

#### Scenario: Theme styles code block output
- **WHEN** Markdown containing a fenced code block is rendered with a theme that styles code blocks
- **THEN** code block lines include the configured style output without leaking styling into following lines

### Requirement: Markdown OSC 8 link rendering
The Markdown renderer SHALL support rendering Markdown links as OSC 8 hyperlinks when terminal capabilities indicate hyperlink support, while preserving readable fallback text when unsupported.

#### Scenario: Hyperlink-capable terminal receives OSC 8 link
- **WHEN** Markdown containing `[label](https://example.com)` is rendered with hyperlink-capable terminal settings
- **THEN** the output includes an OSC 8 hyperlink for `label` and remains width-safe

#### Scenario: Unsupported terminal receives readable link fallback
- **WHEN** Markdown containing a link is rendered without hyperlink capability
- **THEN** the output contains readable label and URL text rather than hiding the URL

### Requirement: Optional Markdown parser adapters
The Markdown module SHALL allow optional parser adapters to provide richer Markdown parsing without adding those parser dependencies to `core` or the baseline Markdown module.

#### Scenario: Adapter preserves renderer contract
- **WHEN** an application selects an optional Markdown parser adapter
- **THEN** Markdown content still renders through the same `MarkdownRenderer` and `Markdown` component contracts

#### Scenario: Adapter failure falls back safely
- **WHEN** an optional parser adapter reports a parsing error
- **THEN** component rendering emits readable fallback text instead of throwing from the TUI render path

### Requirement: Optional syntax highlighting
The Markdown renderer SHALL support an optional syntax-highlighting hook for fenced code blocks while keeping unhighlighted code readable and width-safe.

#### Scenario: Highlighter returns styled lines
- **WHEN** a fenced code block has a language and a configured highlighter returns styled output
- **THEN** those styled lines are rendered within the requested width and do not leak styling across lines

#### Scenario: Missing highlighter keeps plain code
- **WHEN** no highlighter is configured for a fenced code block
- **THEN** the code block is rendered as readable plain code within the requested width

### Requirement: Markdown parity documentation
The Markdown module SHALL document baseline-supported constructs, adapter-supported constructs, and intentional deviations from `pi-tui` Markdown behavior.

#### Scenario: Deferred construct is documented
- **WHEN** a Markdown construct is intentionally unsupported by the baseline renderer
- **THEN** documentation identifies it as deferred or adapter-dependent and tests verify it remains readable
