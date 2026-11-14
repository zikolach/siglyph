## 1. Reproduce

- [x] Pin upstream fixtures and add deterministic GraphicsLifecycleSuite tests.
- [x] Run before implementation: four failures (initial ordering, covered-row repaint, forced ED2, resize ED2); overlay uncover already passes.

## 2. Implement

- [x] Paint fullscreen graphics after text/erase and include intersecting footprints.
- [x] Reuse retained uploads on covered-row repaint and avoid destructive ED2.
- [x] Preserve typed controls, bounded FrameOutput, serialized writes, and overlay behavior.
- [x] Document invariants, pin, and deferred pixel clipping.

## 3. Verify

- [x] JVM: GraphicsLifecycleSuite (5), FullscreenViewportSuite (13), TUITypedControlSuite (24), all pass.
- [x] Native: the same three focused suites pass after the owning worker fixed unrelated ProgramStatus feed/Locale compile-link blockers.
- [x] Format the three changed Scala files with scalafmt.
- [x] Strict OpenSpec validation passes for this change; all-workspace validation attempted twice (latest: 21 pass, unrelated add-negotiated-program-status missing delta while in progress).

Final integrated verification: quality and all-module compilation pass; 2,254 JVM/Native tests plus ten dedicated real-PTY tests pass; strict OpenSpec validation23/23 passes.
The bounded-frame byte oracle was updated to assert the intended graphics-last/per-row-clear sequence while retaining exact concatenation and chunk-size checks.
