# Streaming Markdown

**Experimental; available since 0.13.0** in `siglyph-markdown` on Maven Central.

`StreamingMarkdown` is an opt-in JVM/Scala Native component for one bounded live response.
Existing `Markdown`, `MarkdownParser` plugins and complete-document output are unchanged.
Transport, AI providers, retries and conversation storage remain application-owned; no new
runtime dependency is introduced.

```scala
import scalatui.markdown.StreamingMarkdown

val response = StreamingMarkdown(maxSourceUnits = 64 * 1024)
assert(response.append("Hello **stream"))
assert(response.append("ing** [link](https://example.org)"))
response.finish()
```

Use mutations from the owning application scope/UI update. `append` returns false without
changing source when a chunk would exceed the configured UTF-16 limit (default 1 Mi units),
or after `finish`. Empty chunks are admitted before finish. `reset` starts an empty response,
clearing counters/checkpoints and caches. `text` always contains original admitted source.
A bound is not truncation or a rolling history policy; applications decide what to do on rejection.
`finish` removes provisional projection and performs a full ordinary parse on the next render.

## Live display and grammar limits

Only active baseline paragraph/heading text is provisionally projected: an unmatched simple
emphasis/code-span delimiter is closed for display, and `[label](unfinished destination`
shows `label` without an incomplete hyperlink. Ordinary bracket text remains literal. Projection
operates after block parsing, so multiline paragraphs are joined first and list/task/quote/table
syntax and fenced/indented code bodies stay untouched. The baseline already renders an unfinished
fence as a code block with a display closing fence; it does not append a fence to source.

Set `provisionalInline = false` for exact ordinary baseline output at every accepted prefix.
Final output always matches complete-document rendering. Projection is not CommonMark recovery:
nested/mixed inline delimiters use the baseline's simple matching rules, and provisional tail
formatting may change when delimiters or blank-line boundaries arrive. Width changes may reflow
any block. Custom parsers always receive whole original source, without projection/reuse assumptions.

## Reuse and measured work

Completed prefixes can be reused only after a complete blank line outside fences. Fence recognition
matches the baseline parser. Lists can absorb arbitrarily many blank lines; quotes and indented
code also continue across blanks. On their first occurrence the scanner conservatively stops
committing that suffix, even if later text could prove a more advanced boundary. The entire active
suffix is reparsed after every changed source. CRLF split across chunks is normalized consistently;
a trailing CR is never committed until the next chunk resolves it. Reset discards checkpoints;
render/theme/context invalidation preserves them. Custom parser callbacks run outside checkpoint locks.

`stats` exposes cumulative deterministic `parseCalls`, `parsedUnits`, `scannedUnits` (checkpoint
boundary scanning), `reusedBlocks` and `fallbackParses` (custom-parser/finalization full parses).
Counts are UTF-16 work units, not timings, heap measurements or end-to-end complexity estimates.
Repeated same-source renders/resize/invalidation do not reparse baseline input. For `one\n\n`,
then `two`, then `\n\nthree`, counters are `(2,5,5,0,0)`, `(3,8,8,1,0)`, `(5,18,18,2,0)`.
The shared work regression appends 100 `paragraph\n\n` chunks: 1,100 parsed and scanned units,
with 4,950 cumulative block reuses. Twenty `- item\n\n` chunks instead parse 1,680 units
(the sum of all growing suffix lengths) and reuse zero blocks.

Source concatenation, normalization, prefix comparison, immutable block-vector assembly and full
rendering still visit/copy retained document data; projection scans the active paragraph/heading.
There is **no universal O(delta)** guarantee and no full CommonMark claim. Very long unfinished
blocks and ambiguous suffixes can cost quadratic total work across tiny chunks. Source admission
bounds retained input; renderer LRU retention uses the [existing budgets](markdown-streaming-cache.md).

## Runnable viewer

From a checkout:

```bash
mill fullscreenJvmDemo.run --streaming-markdown
mill fullscreenNativeDemo.nativeLink
out/fullscreenNativeDemo/nativeLink.dest/out --streaming-markdown
```

Enter a prompt in the existing multiline editor to start a local simulated response. The bounded
fixture contains four 12-row data tables and eight larger fenced Scala/JSON blocks, with long lines,
Unicode cells, inline formatting and links. It spans more than three response viewports even at
160 × 50, so scrolling can be exercised without shrinking the window. Its table output follows the
baseline grammar, not a full CommonMark alignment contract.

A scoped recurring task delivers 256 UTF-16 units every 160 ms (1,600 units/second before rendering cost);
the application owns its executor and shuts
it down after TUI cleanup. A new prompt cancels the previous timer and resets this single response.
No network is accessed. Wheel scrolling away from the bottom suspends following; the existing
Jump to latest indicator restores it. Appends never call `jumpToEnd` or replace the child.

The viewer preserves selection only when its entire preceding rendered plain-text grapheme prefix
is unchanged at the current width; otherwise it clears selection rather than claiming source anchors.
This covers selection in stable earlier paragraphs, not universal selection preservation under reflow.
The scroll view retains an active drag's original range and granularity across appends, validating
normalized text and cell positions through both endpoints and that original range before continuing.
Multiple appends before repaint use the last complete bounded mapping; moving before repaint refreshes
it first. Changed prefixes cancel the selection. Explicit invalidation, replacement and width changes
reset the active gesture; the stream is not paused while selecting.
The viewer calls `ScrollView.invalidateSearchDocument(invalidateChild = false)` after source mutation,
which already invalidates the component output. Stable block/highlight LRUs survive appends; the
original no-argument method still invalidates the child, including mutable custom renderer state.
`StreamingMarkdown` reuses its width-constrained rendered document and exposes visible row ranges,
so unchanged-source scrolling does not validate every offscreen row. Selection mapping is built once
per document/width change when needed, and selection decorations visit only viewport rows. Prefix
guards use the same normalized graphemes and newline-free offsets as selection mapping. Changed
source still requires full document layout. An active selection also rebuilds its bounded full-document
mapping once per content revision; its guard compares only through the retained selection/gesture range, not the full
document. This is not incremental layout or a frame-rate guarantee.

## Verification

`StreamingMarkdownSuite` runs on both targets and covers every-character literal-prefix equivalence,
all two-chunk split positions (including surrogate/CRLF splits), ambiguous continuation, provisional
syntax, deterministic counters, bounds, reset, finish and parser callbacks outside locks.
`StreamingResponseViewerSuite` uses virtual time/terminals for bottom-follow, manual scroll retention,
stable-prefix selection, composer delivery and scoped cancellation. The expanded demo regressions
also split every code-fence delimiter, check bounded output at 20/80/160 columns, compare finished
output with a cold baseline render, and verify scrolling at 80×24, 120×40 and 160×50.
Manual real-TTY appearance is
separate from these deterministic tests.
