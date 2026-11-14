## 1. Implementation
- [x] 1.1 Wire existing select page commands within list state/effect ownership.
- [x] 1.2 Add bounded deterministic wheel policy and explicit original-event integration.
- [x] 1.3 Add optional grapheme-safe path joiners for double-click and word drag.
- [x] 1.4 Document integration, source compatibility, upstream pin and limits.

## 2. Verification
- [x] 2.1 Add focused paging, filtering, wheel gesture, nested overscroll and selection regressions.
- [x] 2.2 Run focused JVM suites.
- [x] 2.3 Run focused Native suites.
- [x] 2.4 Validate OpenSpec change strictly.

Final integrated verification: quality and all-module compilation pass; 2,254 JVM/Native tests plus ten dedicated real-PTY tests pass; strict OpenSpec validation23/23 passes.
Runtime policy installation is now integrated through TUI.wheelScrollPolicy and the injected mouse clock before remainder routing. New real-runtime tests pass. Reproduced Unicode nonbreaking-space and timestamp-overflow edge cases are fixed.
