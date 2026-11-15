## 1. Implementation
- [x] Add scoped admission results and observable closed/pending/quiescent state.
- [x] Integrate bounded delivery with the existing TUI effect coordinator.
- [x] Invalidate scopes at stop and implement explicit/self-close semantics.

## 2. Verification
- [x] Add deterministic FIFO, overload, cancellation, failure and restart tests.
- [x] Add latch-controlled close/concurrency tests on JVM and Native.
- [x] Run regressions, formatting/lint, docs and strict OpenSpec checks.

## 3. Documentation and tracking
- [x] Document inline execution, shared capacity, failure observation and external-resource ownership.
- [x] Update #99 with this verified slice while keeping scheduler work pending.
