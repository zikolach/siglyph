# Streaming Markdown reuse

The baseline renderer now keeps two deterministic LRU caches: top-level block output
(keyed by the complete parsed block and content width), and styled highlighted code
(keyed by language and code, independent of width). Each cache allows at most 256
entries and 1 MiB of accounted retention. Accounting charges 64 bytes per structural
node/string plus two bytes per UTF-16 character, including keys and output lines;
it is a deterministic retention budget, not a claim about exact platform heap size.
Oversized entries bypass retention. No WeakRef or runtime dependencies are used.

Stable blocks skip styling/wrapping during append. Resize still renders/wraps blocks
but reuses highlighted code, including plain fallback when a highlighter declines.
Ordinary `Markdown` still reparses the entire document on a full-render-cache miss. These
render caches alone provide no incremental parsing or universal speedup claim. The separate
opt-in [StreamingMarkdown](streaming-markdown.md) component adds conservative parse-prefix reuse. The existing one-entry component
cache is unchanged in shape and is not covered by the new reuse budgets.

The baseline grammar renders blocks independently and appends their separator before
trimming document edges. Complete parsed keys include loose-list spacing and source
markers; changed following blocks therefore cannot preserve stale edge trimming.
Reference definitions are not supported by this grammar. Custom parsers still run
on the whole source, so their resolved block values determine reuse; no new grammar
or parser contract is introduced.

`BasicMarkdownRenderer.invalidateCaches()` clears both caches and advances
`cacheGeneration`. `Markdown.invalidate()` and context attachment changes invoke it
for baseline renderers only. Text/padding updates retain reusable entries. Effective
session capability changes also clear both caches. Theme/highlighter closures whose
state changes must explicitly invalidate, as before. New renderer options/theme
instances have separate caches. Custom `MarkdownRenderer` hooks are unchanged.
Caches belong to the renderer, so components sharing a renderer (including the
default singleton) share its bounded budget; invalidating one conservatively refreshes
all its users through generation checks. Only cache lookup, publication and generation
changes hold the renderer-local monitor on JVM and Native. Parsing, theme hooks,
highlighting and retention weighing run outside it; callbacks may await invalidation
from another thread without a renderer/component lock inversion. Each render keeps
immutable session options. Invalidation or effective-capability changes prevent older
in-flight work from reading or publishing entries in the new generation (the caller
may still receive its original snapshot). Concurrent misses may compute twice, but
publication retains one entry and charges its bytes only once.

Cached strings continue through the existing `ComponentRender.text` typed safety
boundary and width truncation; this change does not introduce raw control output.

## Reproduction

Run `mill markdown.test` and separately `mill markdownNative.test`.
`MarkdownStreamingCacheSuite` counts parser, paragraph-style/wrap-path and highlighter
work: append changes counts from (1,1,1) to (2,2,1), resize to (3,4,1), explicit
invalidate to (4,6,2), and renderer generation invalidation to (5,8,3). Tests also
cover mutable themes, context reassignment, changing spacing/tails, cold-render
comparisons, entry/byte eviction and oversized bypass. Finite latch-based regressions
exercise parser/theme callbacks awaiting renderer invalidation, a highlighter awaiting
component invalidation before reading component state, stale-publication rejection,
and overlapping same-key misses with exact retained-byte accounting.

Inspired by pi-tui commit `8636e211e7834e4ca46a22aec1d65dfd2eac8677`,
`packages/tui/src/components/markdown.ts:300–463` and its highlight-reuse regression.
Unlike upstream, retention is explicitly bounded rather than weakly held. This does
not promote the repository compatibility-matrix pin.
