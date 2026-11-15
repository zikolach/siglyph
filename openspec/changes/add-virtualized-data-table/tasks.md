## 1. Contracts and implementation
- [x] Implement immutable typed row data with validated stable IDs.
- [x] Implement width-safe range rendering, revision, focus and selection effects.
- [x] Add keyboard navigation and explicit model-replacement semantics.

## 2. Integration and validation
- [ ] Integrate the table into the bounded log explorer without rebuilding IDs on navigation (dependent PR).
- [x] Add shared tests including 100,000-row visible-render counters and reentrancy.
- [x] Run JVM/Native tests, Native linking and PTY smoke.
- [x] Validate formatting/lint, OpenSpec and documentation.

## 3. Documentation
- [x] Provide a runnable generic table example and describe scroll ownership and costs.
- [x] Record experimental scope and remaining roadmap work.
