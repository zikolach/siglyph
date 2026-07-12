## MODIFIED Requirements

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
