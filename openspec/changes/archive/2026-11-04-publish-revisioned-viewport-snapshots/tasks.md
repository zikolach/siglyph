## 1. Implementation

- [x] 1.1 Add immutable viewport snapshot and legacy-compatible publication defaults.
- [x] 1.2 Capture revision before measurement and guard early geometry by child/revision.
- [x] 1.3 Publish ScrollView metadata, selection mapping, and final geometry atomically after identity/revision acceptance.
- [x] 1.4 Keep bounded range refresh and application callbacks outside component locks.

## 2. Verification

- [x] 2.1 Add focused stale child/revision, measurement/render invalidation, and legacy compatibility tests.
- [x] 2.2 Run snapshot, layout, fullscreen, selection, affordance, and search suites (83 tests passed).
- [x] 2.3 Validate OpenSpec change strictly.
