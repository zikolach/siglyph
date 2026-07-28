## 1. Characterize Append Contracts

- [ ] 1.1 Add failing shared virtual-terminal tests for appending a text component above one retained normal-screen frame, reserving rows, restoring the live frame and hardware cursor, and completing through `flushRender`.
- [ ] 1.2 Add failing shared tests for Kitty and iTerm2 `Image` components that use current `TUIContext` cell dimensions, preserve typed control placement, and expose no raw control encoder.
- [ ] 1.3 Add failing validation tests for out-of-bounds controls, duplicate active Kitty ids, cursor placements, render failures, pre-start requests, stopping-state requests, and alternate-screen requests; assert no append bytes are published.
- [ ] 1.4 Add failing concurrency tests for append versus input callback, retained render, structural mutation, overlay activity, resize, terminal query, and multiple append requests with deterministic ordering and no recursive drain.
- [ ] 1.5 Add failing ownership tests proving appended Kitty controls survive later render, resize, child removal, and stop while retained Kitty controls keep existing replacement and shutdown cleanup.

## 2. Add Shared Append Work

- [ ] 2.1 Add the public normal-screen append operation and a dedicated append work value to shared TUI runtime ingress without changing the terminal backend API.
- [ ] 2.2 Enforce running normal-screen lifecycle admission and deterministic rejection before startup, during shutdown, and in alternate-screen mode.
- [ ] 2.3 Claim append work through the existing single work-drain owner and include accepted append work in `flushRender` completion and runtime-failure propagation.
- [ ] 2.4 Attach the current `TUIContext` and render origin, render exactly once at claimed terminal width, detach temporary context on every outcome, and reject structured cursor placements explicitly.
- [ ] 2.5 Validate complete `ComponentRender` geometry and trusted metadata and sanitize ordinary lines before any append publication.

## 3. Integrate Output and Cleanup Ownership

- [ ] 3.1 Build one append output plan that clears only the replaceable live-frame area, emits append lines and controls at column one, reserves their rows, and restores one retained live frame below them.
- [ ] 3.2 Preserve retained children, overlay geometry, active typed controls, selected hardware cursor, and current input target while relocating or redrawing the live frame.
- [ ] 3.3 Keep successfully appended controls out of `previousFrame`, replacement cleanup, resize retransmission, retained child state, and shutdown cleanup collections without storing append history.
- [ ] 3.4 Preserve existing retained-control cleanup and map pre-publication and backend-write failures through current bounded runtime failure and terminal restoration semantics.
- [ ] 3.5 Add redaction-safe append diagnostics containing bounded structural outcome metadata and no component text, payload, filename, protocol bytes, or terminal write contents.

## 4. Validate Portability and Publication

- [ ] 4.1 Run the shared append, component render, typed control, overlay, cursor, TUI concurrency, diagnostics, image, and terminal lifecycle suites on JVM and Scala Native.
- [ ] 4.2 Add and run JVM PTY smoke coverage proving text and image output remain in normal-screen scrollback while the live frame continues rendering and terminal state is restored on exit.
- [ ] 4.3 Update public API documentation, normal/alternate-screen guidance, image examples, porting notes, and changelog with append-only ownership and failure semantics.
- [ ] 4.4 Run formatting, lint, terminal conformance, JVM/Native full validation, and strict OpenSpec validation before publishing the next Siglyph release.
