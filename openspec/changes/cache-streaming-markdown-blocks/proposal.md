## Why

Streaming text currently misses the one-entry Markdown component cache and repeats all styling, wrapping and highlighting. Stable blocks and width-independent code highlighting can be reused without changing the renderer boundary.

## What Changes

- Add bounded deterministic block-output and highlighted-code LRU caches to the baseline renderer.
- Preserve explicit invalidation, generation and session capability behavior.
- Add shared JVM/Native work-counter and eviction regressions and document limits.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `markdown-rendering`: bounded reuse during append and resize with explicit refresh semantics.

## Impact

Only the optional Markdown implementation, its tests and documentation change. No dependencies, parser features, custom renderer contract changes, or typed output changes are required.
