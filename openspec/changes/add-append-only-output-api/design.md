## Context

Siglyph 0.6 introduced closed `ComponentRender` output so ordinary application strings cannot acquire terminal-control or hardware-cursor authority. Typed controls are currently encoded only while `TUI` assembles a retained frame, and Kitty controls remain owned by that frame so replacement and shutdown can emit deterministic cleanup.

That ownership is correct for live widgets but does not cover normal-screen conversational and build interfaces that keep one editable/status frame at the bottom while committing completed rich output to shell scrollback. Direct `Terminal.write` remains application authority, but the typed image API intentionally exposes no raw encoder. A consumer therefore cannot append an `Image` or another typed-control component without either duplicating protocol encoding or keeping all historical output in the retained live frame.

The API must preserve the typed-control trust boundary, normal-screen scrollback, current TUI context and dimensions, callback/render serialization, and active-frame lifecycle. It must work in shared core on JVM and Scala Native and add no dependency.

## Goals / Non-Goals

**Goals:**
- Add one TUI-owned operation for appending a component once above the current normal-screen live frame.
- Render with the owning session's width, capabilities, cell dimensions, and component context.
- Validate and encode typed controls only at the final TUI output boundary.
- Serialize append work with every existing runtime work category and include it in `flushRender` completion.
- Keep appended controls outside retained-frame replacement and shutdown cleanup ownership.
- Preserve active children, overlays, hardware cursor placement, input delivery, resize behavior, and terminal restoration.
- Provide redaction-safe lifecycle diagnostics and shared JVM/Native behavior.

**Non-Goals:**
- Adding a public raw `TerminalRenderControl` encoder or arbitrary trusted escape-string API.
- Turning `TUI` into a transcript store, replay log, persistence layer, or virtualized scrollback model.
- Making appended output interactive, focusable, removable, or addressable after publication.
- Supporting append-only output in alternate-screen mode.
- Guaranteeing terminal-emulator persistence beyond what normal-screen Kitty, iTerm2, and shell scrollback provide.
- Making terminal writes transactionally atomic after bytes reach an operating-system/backend boundary.

## Decisions

### 1. Add append work to the TUI runtime rather than the terminal backend

Expose a normal-screen operation such as `TUI.appendToScrollback(component: Component): Unit`. The operation publishes a dedicated append work item through the same runtime ingress/drain ownership used by structural actions, input, queries, controls, rendering, and cleanup. External callers can use the existing `flushRender()` boundary when they must await all accepted work.

The terminal backend remains a byte transport and gains no component or protocol knowledge.

Alternative: expose `TerminalRenderControlEncoder` or add `Terminal.writeControl`. Rejected because it moves frame geometry, validation, protocol authority, synchronization, and cleanup decisions outside the TUI owner.

Alternative: return a blocking append result. Rejected because an append can be requested reentrantly from a TUI callback; a mandatory synchronous result would either deadlock the drain owner or require a second execution model. The existing queued-work plus `flushRender` contract already handles external acknowledgment.

### 2. Render the component exactly once inside the owning context

When append work is claimed, the TUI supplies its current `TUIContext`, current positive terminal width, session-owned image cell dimensions, and the insertion origin to contextual/origin-aware components. It renders the component once, validates the complete `ComponentRender`, sanitizes ordinary lines through the normal renderer, and prepares typed controls before emitting bytes.

Append-only output has no lasting focus or input ownership. A render containing cursor placements is rejected explicitly rather than silently dropping structured cursor authority or moving the live hardware cursor.

The TUI detaches temporary context after planning even when rendering or validation fails. Component rendering failures follow the normal runtime-failure and terminal-cleanup path.

Alternative: accept a pre-rendered `ComponentRender`. Rejected because callers would have to guess the current width, cell dimensions, origin, and context outside runtime serialization.

### 3. Plan append output and active-frame restoration as one owned operation

The append planner treats the currently visible retained frame and the one-shot component as separate ownership domains. It prepares all geometry and typed controls before the first append byte is written, then:

1. moves/clears only the replaceable live-frame area needed for insertion;
2. emits the validated append-only lines and typed controls at column one;
3. reserves the complete append height in normal-screen output;
4. redraws or repositions the retained live frame below the appended output; and
5. restores the retained frame's selected hardware cursor and committed geometry.

The output planner must preserve active overlays and retained controls rather than classifying their positional redraw as removal. Concurrent resize or structural work is ordered before or after the append by the existing single runtime owner; no second terminal lock or side channel is added.

Alternative: temporarily add and then remove the component as a normal child. Rejected because removal invokes retained-frame cleanup, which can delete the newly appended Kitty image from scrollback.

Alternative: keep historical components as children forever. Rejected because live-frame rendering and diff cost would grow with append history and would turn retained UI state into an unbounded transcript.

### 4. Transfer appended controls out of retained cleanup ownership

Controls emitted by successful append work are not stored in `previousFrame`, retained child state, or shutdown cleanup collections. Later frame replacement, child removal, resize, and TUI shutdown therefore cannot emit cleanup for those append-only controls.

Controls already owned by the retained live frame keep their existing replacement and shutdown cleanup behavior. If planning fails, no appended control becomes published or owned. If a backend write fails after publication begins, the runtime reports its normal failure and restores terminal lifecycle state without claiming rollback of bytes already accepted by the backend.

No append handle or later numeric image-id cleanup API is returned. Removing append-only output is deliberately outside the contract.

### 5. Restrict the operation to a running normal-screen lifecycle

Append requests are accepted only while a TUI with `TUIScreenMode.Normal` is running or while its runtime owner is processing accepted running-state work. Requests before startup, during/after stopping, or in alternate-screen mode fail explicitly and emit no append bytes.

This keeps alternate-screen frame replacement deterministic and avoids ambiguous behavior for output that has no shell scrollback destination.

### 6. Keep diagnostics structural and redaction-safe

When a diagnostic observer is configured, append work reports bounded event kind, outcome, row/control counts, screen mode, and failure category. Diagnostics never retain component lines, payloads, filenames, control bytes, or terminal write contents.

## Risks / Trade-offs

- **[Live-frame relocation can corrupt normal-screen output]** → Build the append and redraw plan under the single runtime owner and add virtual-terminal plus PTY assertions for exact frame ordering, cursor position, and row reservation.
- **[Kitty cleanup removes append-only images]** → Keep appended controls out of retained replacement/shutdown cleanup and test later render, resize, child removal, and stop paths.
- **[Append rendering races terminal resize]** → Claim current geometry only inside ordered runtime work and render exactly once for that claimed state.
- **[Component emits invalid controls or cursor metadata]** → Validate before publication, reject cursor placements explicitly, and publish no append bytes for planning failures.
- **[Terminal write fails after partial backend acceptance]** → Reuse runtime failure and lifecycle cleanup semantics; do not promise byte rollback or report append success.
- **[Consumers use append output as unbounded retained state]** → Keep the API one-shot and non-addressable; the TUI stores no append history.
- **[JVM and Native behavior diverges]** → Implement planning and control encoding in shared core and run the same virtual-terminal contract on both platforms.

## Migration Plan

1. Add failing shared tests for one-shot text and typed-control append semantics, state rejection, validation atomicity, serialization, retained-frame restoration, and cleanup ownership.
2. Introduce the append work model and public normal-screen operation in shared core.
3. Integrate append planning with retained-frame origin, cursor, overlay, resize, diagnostic, and cleanup state.
4. Add image-component coverage for Kitty and iTerm2 plus JVM PTY scrollback smoke validation.
5. Run JVM, Scala Native, terminal conformance, formatting, lint, and strict OpenSpec validation.
6. Publish the capability in the next Siglyph release and document downstream migration from direct protocol writes.

Rollback removes the public append operation and its work category before release. It must not expose the private control encoder as a fallback.

## Open Questions

None. The operation is intentionally normal-screen-only, one-shot, typed, non-interactive, and non-removable.
