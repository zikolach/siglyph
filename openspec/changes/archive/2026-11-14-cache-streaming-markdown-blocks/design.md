## Context

The baseline parser returns immutable MarkdownBlock values. Rendering is block-local, including list spacing; document assembly trims edge separators. Unlike upstream pi-tui, the baseline has no reference-definition resolution or following-token spacing rule. The custom parser still parses full source before block lookup.

## Goals / Non-Goals

Reuse stable block output during append and highlighted code during resize. Bound retained keys and values identically on JVM and Native. Preserve custom renderer and typed output contracts. Incremental parsing, CommonMark expansion, math and HTML are non-goals.

## Decisions

Use renderer-local synchronized LRU maps keyed by (complete block, content width) and (language, code). Each map admits 256 entries and 1 MiB of accounted bytes, including structural charges and UTF-16 payloads. Evict oldest access first; bypass oversized entries. Store highlighted/styled lines before width truncation. Keep document-edge trimming outside the cached blocks.

Expose baseline-only invalidateCaches, clearing both maps and advancing cacheGeneration. Component explicit invalidation and context reassignment call it only for the baseline implementation. Text/padding updates preserve reusable entries. Effective capability changes clear caches before rendering. Mutable style/highlighter state requires explicit invalidation, preserving the existing contract.

## Risks / Trade-offs

Shared renderers share bounded retention and conservative invalidation across components. Monitor serialization trades concurrency for consistent cache/options state. Accounting is deterministic rather than exact heap sizing. Parsing and final width truncation still run on misses; workloads with little stable content need not improve. The existing full-component entry remains outside the new budgets.

## Validation

Shared tests measure parser, paragraph styling/wrap-path and highlighting calls, compare cached/cold outputs for spacing and tail changes, and force entry/byte eviction and oversized bypass. Run all Markdown suites on JVM and Native and validate OpenSpec strictly.
