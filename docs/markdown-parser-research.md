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

## Current baseline

`markdown` now includes a dependency-free line-oriented baseline behind
`scalatui.markdown.MarkdownRenderer`:

- `MarkdownParser` is the parser strategy boundary.
- `BasicMarkdownParser` supports the portable first subset without third-party dependencies.
- `BasicMarkdownRenderer` converts parsed blocks to width-safe terminal lines.
- `MarkdownTheme` exposes hooks for headings, paragraphs, links, inline code, fenced code, quotes, horizontal rules, lists, tables, emphasis, and strong text.
- `MarkdownRenderOptions` carries theme, terminal hyperlink capability, an optional fenced-code highlighter, and `preserveSourceListMarkers` for opt-in `-`, `*`, `+`, and ordered marker preservation.
- `Markdown` is a normal component wrapper with padding support.

The dependency-free subset is intentionally conservative: headings, paragraphs, inline emphasis, inline code, links, fenced/indented code, ordered/unordered lists, block quotes, horizontal rules, and simple pipe tables remain readable and width-safe. Markdown links render as readable `label (url)` text by default and as OSC 8 hyperlinks when the renderer is configured with hyperlink-capable terminal settings. List rendering normalizes markers by default; applications can enable `MarkdownRenderOptions(preserveSourceListMarkers = true)` to preserve baseline-detected unordered and ordered source markers. Task-list markers such as `[ ]`, `[x]`, and `[X]` remain visible text rather than interactive checkbox state. Blank lines between baseline-supported loose list items are preserved. Nested lists, multi-paragraph list items, and full CommonMark list edge cases remain deferred to optional parser adapters. Unsupported constructs fall back to readable plain/minimal formatting rather than throwing during component rendering.

## Optional adapter boundary

Richer Markdown support should be added as optional modules rather than mandatory dependencies:

- A future JVM adapter can wrap `commonmark-java` or `flexmark-java` behind `MarkdownParser` or
  `MarkdownRenderer`.
- A future Native adapter can use a Native-compatible parser if one is approved.
- Adapter modules must preserve the public renderer/component contract so applications can swap implementations without changing UI code.
- Adapter failures must return a parser error so the renderer can emit readable fallback text instead of throwing from component rendering.
- No parser dependency should be added to `core` or to the baseline `markdown` module without explicit dependency approval.

## Recommendation for optional parser work

Before adding an adapter dependency, verify cross-platform support, artifact size, license, and
whether JVM and Native should have separate implementation modules.
