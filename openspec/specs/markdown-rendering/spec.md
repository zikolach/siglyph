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
The markdown module SHALL provide a concrete dependency-free basic renderer that converts markdown text into width-safe terminal lines through the existing `MarkdownRenderer` contract, including direct render requests at width zero.

#### Scenario: Markdown renders to terminal lines
- **WHEN** `render(markdown, width)` is called with headings, bullet lists, code spans, and block quotes at a positive width
- **THEN** it returns a non-empty `Vector[String]` where each rendered line is ANSI-aware width-safe for the requested width

#### Scenario: Width requests are respected during rendering
- **WHEN** the renderer receives a small positive width
- **THEN** headings, fences, and list rows are wrapped or truncated so every line remains within width when measured by visible width semantics

#### Scenario: Direct rendering honors zero width
- **WHEN** `render(markdown, 0)` is called directly
- **THEN** every returned line has zero visible width and rendering does not throw

### Requirement: Parser abstraction remains pluggable
The markdown module SHALL keep parser strategy pluggable so dependency-free, JVM-specific, and Native-specific implementations can evolve independently of the shared component API. The dependency-free basic parser SHALL recover only from exceptions matched by `scala.util.control.NonFatal` and SHALL NOT catch fatal `Throwable` values.

#### Scenario: Shared API remains stable across implementations
- **WHEN** a parser implementation is swapped for another backend-compatible implementation
- **THEN** the public `MarkdownRenderer` API and rendered module surface remain unchanged

#### Scenario: Parser errors do not crash rendering
- **WHEN** the selected parser reports a non-fatal invalid or unsupported markdown input failure
- **THEN** rendering emits readable fallback text rather than throwing from component rendering

#### Scenario: Fatal parser failure is not intercepted
- **WHEN** the dependency-free basic parser encounters a failure that `scala.util.control.NonFatal` does not match
- **THEN** the parser does not convert that failure into readable fallback output

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

### Requirement: Markdown list marker preservation option
The Markdown renderer SHALL preserve current normalized list rendering by default and SHALL provide an option to preserve source list markers where the baseline parser identifies them.

#### Scenario: Default unordered marker remains normalized
- **WHEN** Markdown containing `+ item`, `* item`, and `- item` is rendered with default options
- **THEN** each unordered list row uses the current normalized unordered marker

#### Scenario: Source unordered marker is preserved by option
- **WHEN** Markdown containing `+ item`, `* item`, and `- item` is rendered with source marker preservation enabled
- **THEN** each unordered list row uses the marker from the source line

#### Scenario: Source ordered marker is preserved by option
- **WHEN** Markdown containing ordered list items with source markers such as `3.` and `7.` is rendered with source marker preservation enabled
- **THEN** each rendered ordered list row uses the corresponding source marker text

### Requirement: Markdown task list text preservation
The Markdown renderer SHALL preserve task-list markers as visible text without adding semantic checkbox behavior.

#### Scenario: Incomplete task marker remains visible
- **WHEN** Markdown contains an unordered task item with `[ ]` after the list marker
- **THEN** rendered output includes `[ ]` before the task item text

#### Scenario: Complete task marker remains visible
- **WHEN** Markdown contains an unordered task item with `[x]` or `[X]` after the list marker
- **THEN** rendered output includes the source task marker before the task item text

### Requirement: Markdown loose-list spacing
The Markdown renderer SHALL preserve blank-line spacing inside baseline-supported loose lists while keeping output width-safe.

#### Scenario: Loose list keeps item separation
- **WHEN** Markdown contains blank lines between list items in a baseline-supported list
- **THEN** rendered output includes readable separation between those list items

#### Scenario: Tight list remains compact
- **WHEN** Markdown contains adjacent list items without blank lines
- **THEN** rendered output does not insert extra blank lines between those items beyond current list spacing behavior

### Requirement: Markdown wrapped list continuation indentation
The Markdown renderer SHALL indent wrapped continuation lines so they align with the list item text rather than the marker.

#### Scenario: Unordered list item wraps after marker
- **WHEN** an unordered list item exceeds the render width
- **THEN** continuation lines start at the text column after the unordered marker prefix

#### Scenario: Ordered list item wraps after marker
- **WHEN** an ordered list item exceeds the render width
- **THEN** continuation lines start at the text column after the ordered marker prefix

#### Scenario: Task list item wraps after task marker
- **WHEN** a task list item exceeds the render width
- **THEN** continuation lines align with the task text after the list marker and task marker

### Requirement: Markdown list fidelity remains dependency-free
Baseline Markdown list fidelity improvements SHALL NOT add third-party runtime dependencies.

#### Scenario: Markdown module dependency list remains unchanged
- **WHEN** this change is complete and the Mill build is inspected
- **THEN** the baseline `markdown` module has no new third-party runtime dependency

#### Scenario: Unsupported list construct remains readable
- **WHEN** Markdown contains a list construct outside the baseline parser scope
- **THEN** rendered output remains readable text instead of throwing from component rendering

### Requirement: Markdown streaming fenced-code stability
The Markdown renderer SHALL keep fenced-code block rendering stable while markdown content is streaming and partial closing fences are received.

#### Scenario: Partial closing fence keeps code block stable
- **WHEN** markdown content contains an opening fenced-code block and a trailing partial closing fence that is not yet a complete fence
- **THEN** the renderer keeps the content in the code block and does not shrink the block as if the fence had closed

#### Scenario: Complete closing fence closes code block
- **WHEN** markdown content contains an opening fenced-code block followed by a complete closing fence
- **THEN** the renderer closes the code block and renders following markdown using normal block parsing

#### Scenario: Streaming fence behavior remains width-safe
- **WHEN** streaming fenced-code input is rendered at a narrow width
- **THEN** every output line remains ANSI-aware width-safe for the requested width

### Requirement: Native baseline Markdown artifact
The baseline dependency-free Markdown renderer SHALL be published for Scala Native using the same public renderer and component contracts as the JVM Markdown artifact.

#### Scenario: Native application depends on Markdown module
- **WHEN** a Scala Native application adds the Native `siglyph-markdown` artifact
- **THEN** it can construct and render the baseline `Markdown` component through the same public API as JVM applications

#### Scenario: Native Markdown uses shared sources
- **WHEN** the Native Markdown module is built
- **THEN** it compiles the canonical `markdown/src` source tree through Mill source-root configuration rather than a duplicated Native implementation

#### Scenario: Native Markdown keeps dependency-free baseline
- **WHEN** the Native Markdown artifact is published
- **THEN** it does not add a third-party parser, syntax highlighter, or runtime dependency to the baseline Markdown module

