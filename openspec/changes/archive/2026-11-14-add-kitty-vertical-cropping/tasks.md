## 1. Contracts and implementation
- [x] Define validated immutable source-pixel crop metadata and compatible helper overloads.
- [x] Implement vertical clipping and nested composition without escape-string rewriting.
- [x] Preserve metadata through encoding, ID remapping and retained placements.
- [x] Integrate known dimensions from high-level Image rendering; preserve conservative fallbacks.

## 2. Verification and documentation
- [x] Add protocol/geometry and retained-lifecycle regressions.
- [x] Reproduce the high-level partial-image omission before implementation.
- [x] Run shared and image suites on JVM and Native.
- [x] Run an isolated real-terminal smoke or document the exact environment limitation.
- [x] Update API/behavior docs, changelog and targeted compatibility notes.
- [x] Run full compilation/tests, quality and strict OpenSpec validation.

Verification: baseline high-level tests fail2/3; corrected source passes 2,290 JVM/Native tests plus11 dedicated PTY tests, quality and all-module compilation. Strict OpenSpec24/24 passes. Independent review found no actionable issue. Real-emulator pixels remain unverified: isolated Ghostty launch/control did not start the demo, and further desktop attempts were stopped; this limitation and manual smoke artifacts are documented.
