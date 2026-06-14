## ADDED Requirements

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
