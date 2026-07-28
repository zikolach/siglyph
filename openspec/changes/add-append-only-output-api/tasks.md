## 1. Characterize Public and Lifecycle Contracts

- [ ] 1.1 Add failing shared tests for typed append results, exactly-once callback completion, completion-before-return, and callback failure isolation.
- [ ] 1.2 Add failing tests proving `flushRender()` remains synchronous only when uncontended and remains non-waiting when reentrant or concurrently owned.
- [ ] 1.3 Add failing admission tests for pre-start, missing committed frame, alternate screen, `ClearScrollback`, stopping, cleaning, and stopped states with no append bytes.
- [ ] 1.4 Add failing shutdown-race tests proving admitted but unclaimed appends complete once with rejection in FIFO order and cannot postpone restoration indefinitely.
- [ ] 1.5 Add failing tests for detached contextual components, detectable attached-component rejection, restricted focus/overlay/exit/render/flush authority, and guaranteed context detachment on every outcome.

## 2. Characterize Rendering, Identity, and Restoration

- [ ] 2.1 Add failing shared virtual-terminal tests for text and empty append output above one retained frame, row reservation, frame redraw, hardware cursor restoration, and unchanged retained semantic state.
- [ ] 2.2 Add failing resize-generation tests proving unpublished candidates may rerender, stale bytes are never published, later appends do not overtake, and one result completes.
- [ ] 2.3 Add failing Kitty and iTerm2 image tests using component-provided capabilities and current TUI-owned cell dimensions.
- [ ] 2.4 Add failing validation tests for out-of-bounds controls, duplicate input Kitty IDs, cursor placements, Kitty cleanup controls, render failures, and output-planning failures with no append publication.
- [ ] 2.5 Add failing Kitty ownership tests for fresh ID remapping, component/original-ID reuse, retained-ID collision rejection, the 4096-ID limit, no retained payload/history, and survival across render, resize, removal, and stop.
- [ ] 2.6 Add failing mouse tests proving append relocation and terminal scrolling update `latestFrameStartRow` while retained layout and focus remain unchanged.
- [ ] 2.7 Add failing concurrency tests for append versus input callback, structural mutation, overlay activity, retained render, resize, terminal query/control work, and multiple FIFO appends with no recursive drain or concurrent writes.

## 3. Add Shared Append Runtime Work

- [ ] 3.1 Add public append result, rejection, and failure models with Scaladoc covering synchronous completion, callback ordering, and the unchanged flush contract.
- [ ] 3.2 Add the public callback-completed append operation and a dedicated FIFO append work category to the shared runtime drain without changing terminal backend APIs.
- [ ] 3.3 Enforce running normal-screen, `PreserveScrollback`, committed-frame, and detached-component admission before enqueueing or context mutation.
- [ ] 3.4 Retain and complete rejected pending append callbacks through stopping/cleanup with exactly-once state transitions analogous to existing query completion ownership.
- [ ] 3.5 Implement a restricted one-shot `TUIContext` exposing current image cell dimensions while rejecting retained focus, overlay, exit, render, and nested-flush mutations.
- [ ] 3.6 Attach context, render and validate against claimed geometry, detach in `finally`, and retry only unpublished candidates invalidated by resize while preserving append FIFO.

## 4. Integrate Typed Output and Ownership

- [ ] 4.1 Reuse ordinary-line sanitization, line resets, complete `ComponentRender` validation, and the private exhaustive control encoder without exposing raw authority.
- [ ] 4.2 Reject cursor placements and Kitty cleanup controls before publication and preserve current fail-fast runtime failure semantics.
- [ ] 4.3 Add an internal Kitty-control ID remapping helper and a per-lifecycle append ownership ledger capped at 4096 IDs with no payload, component, text, filename, placement, or geometry retention.
- [ ] 4.4 Reject retained caller-configured Kitty IDs that collide with append ownership while preserving existing cleanup for non-colliding retained controls.
- [ ] 4.5 Build one synchronized append plan that clears only the live-frame region, relocates retained controls, emits and reserves append rows, redraws the retained frame, and restores its logical/hardware cursor.
- [ ] 4.6 Commit physical frame-origin changes for mouse routing while leaving `previousFrame`, children, overlays, layouts, focus, and input ownership semantically unchanged.
- [ ] 4.7 Map pre-publication and backend-write failures to exactly-once append completion and existing terminal restoration without rollback claims.
- [ ] 4.8 Add redaction-safe append diagnostics containing bounded structural metadata and no application content, exception messages, protocol bytes, image IDs, or write contents.

## 5. Validate Portability and Publication

- [ ] 5.1 Extend `VirtualTerminal` only as needed to model append viewport/scroll behavior deterministically without treating plain output history as an emulator guarantee.
- [ ] 5.2 Run append, render, typed-control, overlay, cursor, mouse, diagnostics, concurrency, image, and terminal lifecycle suites on JVM and Scala Native.
- [ ] 5.3 Add automated JVM PTY coverage for exact output ordering, forbidden cleanup absence, and terminal restoration.
- [ ] 5.4 Add documented manual Kitty and iTerm2 smoke steps for real scrollback persistence, resize, continued live rendering, and exit restoration.
- [ ] 5.5 Update README/API documentation, normal-screen resize guidance, image examples, porting notes, and changelog with admission, completion, ownership, and emulator limits.
- [ ] 5.6 Run `git diff --check`, formatting, lint, full JVM/Native tests, Linux/macOS terminal conformance, and `openspec validate --all --strict` before marking implementation complete.
