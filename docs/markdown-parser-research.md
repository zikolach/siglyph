# Markdown Parser Research

Markdown is intentionally isolated in the `markdown` module. No parser dependency has been added yet.

## Requirements

- Works with Scala 3.
- Prefer support for both JVM and Scala Native.
- Dependency must be explicitly approved before adoption.
- Parser implementation should sit behind `scalatui.markdown.MarkdownRenderer` or a lower-level parser abstraction.

## Candidate options

### Java CommonMark (`commonmark-java`)

- JVM only.
- Mature CommonMark parser.
- Good option for JVM-specific Markdown module variants.
- Not suitable as the only implementation if Native support is required.

### flexmark-java

- JVM only.
- Feature-rich Markdown parser with tables and many extensions.
- Larger dependency surface than commonmark-java.
- Useful if JVM feature parity matters more than dependency size.

### Laika

- Scala documentation/markup toolkit with Markdown support.
- Needs follow-up verification for current Scala Native support and module size.
- Potentially attractive if it cross-builds cleanly.

### Custom minimal parser

- Could support the subset needed by `pi-tui`: headings, paragraphs, emphasis, inline code, fenced code, lists, links, blockquotes, horizontal rules, and tables.
- Works on JVM and Native with no runtime dependency.
- Highest implementation and maintenance effort.

## Recommendation for now

Start with the pluggable API only. Before adding a dependency, verify cross-platform support and decide whether Markdown should have separate JVM and Native implementations.
