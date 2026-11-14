## 1. Implementation

- [x] Inspect baseline parser/renderer and pinned upstream reuse implementation.
- [x] Add deterministic bounded block and highlight LRU caches with generation/context invalidation.
- [x] Preserve custom renderer contracts and typed component output.

## 2. Validation and documentation

- [x] Add reproducible work-counter, spacing, mutable-theme, invalidation and eviction tests.
- [x] Document cache ownership, budgets, upstream pin and whole-document parsing limitation.
- [x] Run complete JVM and Native Markdown suites after final edits (37 tests on each platform).
- [x] Validate OpenSpec artifacts strictly (this change passes; repository-wide validation reports an unrelated missing delta in add-negotiated-program-status).

Final integrated verification: quality and all-module compilation pass; 2,254 JVM/Native tests plus ten dedicated real-PTY tests pass; strict OpenSpec validation23/23 passes.
Independent review reproduced callback/cache lock inversion. Immutable render sessions and generation-checked short-lock publication fix it; parser/highlighter invalidation and concurrent-miss tests pass (41 Markdown tests per platform).
