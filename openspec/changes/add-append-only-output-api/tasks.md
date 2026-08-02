## 1. Characterize Public and Lifecycle Contracts

- [x] 1.1 Add failing shared tests for typed append results, exactly-once callback completion, completion-before-return, and callback failure isolation.
- [x] 1.2 Add failing tests proving `flushRender()` remains synchronous only when uncontended and remains non-waiting when reentrant or concurrently owned.
- [x] 1.3 Add failing admission tests for pre-start, missing committed frame, alternate screen, `ClearScrollback`, stopping, cleaning, and stopped states with no append bytes.
- [x] 1.4 Add failing shutdown-race tests proving unclaimed append bodies release component/payload references with `StoppedBeforeClaim`, claimed unpublished bodies complete with `StoppedBeforePublication`, and bounded callbacks complete once in FIFO order before restoration.
- [x] 1.5 Add failing tests for detached contextual components, detectable attached-component rejection, restricted focus/overlay/exit/render/flush authority, operation-scoped caught-exception violation latching across resize attempts, revocation before detachment, detachment failure, and retained-context isolation.
- [x] 1.6 Add failing capacity tests for the 64-operation accepted-incomplete bound, claim/retry slot retention, completion release, concurrent final-slot admission, rejected component-reference release, owner-safe synchronous rejection, external 4096-slot callback backpressure/promotion, and exactly-once completion.
- [x] 1.7 Add failing work-selection tests updating the promoted five-category/five-selection guarantee to Structural, Action, Ingress, Control, Append, and Render within six selections.

## 2. Characterize Rendering, Identity, and Restoration

- [x] 2.1 Add failing shared virtual-terminal tests for text and empty append output above one retained frame, row reservation, frame redraw, hardware cursor restoration, and unchanged retained semantic state.
- [x] 2.2 Add failing resize-generation tests proving unpublished candidates may rerender, stale bytes are never published, retries yield through six-category fair selection, later appends do not overtake, and one result completes.
- [x] 2.3 Add failing Kitty and iTerm2 image tests using component-provided capabilities and current TUI-owned cell dimensions, including retained-iTerm2 typed rejection before append rendering or output.
- [x] 2.4 Add failing validation tests for out-of-bounds controls, duplicate input Kitty IDs, cursor placements, Kitty cleanup controls, render failures, and output-planning failures with no append publication.
- [x] 2.5 Add failing Kitty ownership tests for fresh ID remapping that excludes active retained, append-ledger, and same-append IDs—including allocator collision with a manually configured current retained ID—plus component/original-ID reuse, retained-ID collision rejection, the 4096-ID limit, no retained payload/history, and survival across render, resize, removal, and stop.
- [x] 2.6 Add failing mouse tests proving append relocation and terminal scrolling update `latestFrameStartRow` while retained layout and focus remain unchanged.
- [x] 2.7 Add failing concurrency tests for append versus input callback, structural mutation, overlay activity, retained render, resize, terminal query/control work, and multiple FIFO appends with no recursive drain or concurrent writes.

## 3. Add Shared Append Runtime Work

- [x] 3.1 Add public append result, rejection, and failure models with Scaladoc covering synchronous completion, callback ordering, and the unchanged flush contract.
- [x] 3.2 Add the public callback-completed append operation and a capacity-64 FIFO Append category as the sixth shared ordinary-work category, changing the deterministic fairness bound from five to six selections without changing terminal backend APIs.
- [x] 3.3 Enforce running normal-screen, `PreserveScrollback`, committed-frame, retained-iTerm2 incompatibility, atomic accepted-incomplete capacity, and detached-component admission before enqueueing or context mutation, then recheck mutable retained-frame conditions at claim.
- [x] 3.4 On stopping/failure, discard pending append bodies, promote pending rejection records from bounded ingress accounting, retain only bounded completions, and invoke every linearized append callback exactly once through finite cleanup sets alongside retained query completions.
- [x] 3.5 Implement a thread-safe violation-latching restricted `TUIContext` exposing current image cell dimensions, rejecting retained mutations, and permanently revoking every method before component detachment.
- [x] 3.6 Attach context, render and validate against claimed geometry, revoke before detaching in `finally`, fail on latched or detachment errors, and requeue resize-invalidated candidates ahead of later appends while yielding to fair work selection.

## 4. Integrate Typed Output and Ownership

- [x] 4.1 Reuse ordinary-line sanitization, line resets, complete `ComponentRender` validation, and the private exhaustive control encoder without exposing raw authority.
- [x] 4.2 Reject cursor placements and Kitty cleanup controls before publication and preserve current fail-fast runtime failure semantics.
- [x] 4.3 Add an internal Kitty-control ID remapping helper that excludes current retained IDs, append-ledger IDs, and IDs selected earlier in the same append, plus a per-lifecycle ownership ledger capped at 4096 IDs with no payload, component, text, filename, placement, or geometry retention.
- [x] 4.4 Reject retained caller-configured Kitty IDs that collide with append ownership while preserving existing cleanup for non-colliding retained controls.
- [x] 4.5 Build one synchronized append plan that clears only the live-frame region, relocates retained controls, emits and reserves append rows, redraws the retained frame, and restores its logical/hardware cursor.
- [x] 4.6 Commit physical frame-origin changes for mouse routing while leaving `previousFrame`, children, overlays, layouts, focus, and input ownership semantically unchanged.
- [x] 4.7 Map pre-publication and backend-write failures to exactly-once append completion and existing terminal restoration without rollback claims.
- [x] 4.8 Add redaction-safe append diagnostics containing bounded structural metadata and no application content, exception messages, protocol bytes, image IDs, or write contents.

## 5. Validate Portability and Publication

- [x] 5.1 Extend `VirtualTerminal` only as needed to model append viewport/scroll behavior deterministically without treating plain output history as an emulator guarantee.
- [x] 5.2 Run append, render, typed-control, overlay, cursor, mouse, diagnostics, concurrency, image, and terminal lifecycle suites on JVM and Scala Native.
- [x] 5.3 Add automated JVM PTY coverage for exact output ordering, forbidden cleanup absence, and terminal restoration.
- [x] 5.4 Add documented manual Kitty and iTerm2 smoke steps for real scrollback persistence, resize, continued live rendering, exit restoration, and retained-iTerm2 append rejection without frame relocation or disturbance.
- [x] 5.5 Update README/API documentation, normal-screen resize guidance, image examples, porting notes, and changelog with admission, completion, ownership, and emulator limits.
- [x] 5.6 Run `git diff --check`, formatting, lint, full JVM/Native tests, Linux/macOS terminal conformance, and `openspec validate --all --strict` before marking implementation complete.
