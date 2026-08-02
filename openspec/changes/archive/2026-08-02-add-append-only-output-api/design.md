## Context

Siglyph 0.6 introduced closed `ComponentRender` output so ordinary application strings cannot acquire terminal-control or hardware-cursor authority. Typed controls are encoded only while `TUI` assembles owned output, and Kitty controls in the retained frame remain cleanup-owned by that frame.

That ownership is correct for live widgets but does not cover normal-screen conversational and build interfaces that keep one editable/status frame at the bottom while committing completed rich output to shell scrollback. Direct `Terminal.write` remains application authority, but the typed image API intentionally exposes no raw encoder.

The current runtime already provides a synchronous single-owner work drain, a private typed-control encoder, retained frame state, structured diagnostics, mouse frame-origin tracking, and shared JVM/Scala Native sources. It also has constraints this design must preserve:

- `flushRender()` drains synchronously only when uncontended; reentrant and concurrent calls are non-waiting.
- Normal-screen resize clears scrollback by default unless `PreserveScrollback` is selected.
- Component-provided `TerminalCapabilities` remain separate from the session-owned image cell dimensions exposed by `TUIContext`.
- Resize can invalidate a render candidate after rendering and before publication.
- Kitty image IDs may be caller-configured and retained cleanup deletes by semantic image ID.
- Ordinary work currently has five fair scheduling categories, each bounded to service within five ordinary selections.

## Goals / Non-Goals

**Goals:**
- Append one detached component above the current normal-screen live frame.
- Preserve the established flush, callback, lifecycle, focus, overlay, mouse, and terminal-write contracts.
- Validate and encode typed controls only at the final TUI output boundary.
- Serialize append work with every existing runtime work category.
- Bound queued component and image-payload retention under concurrent append publishers.
- Keep append-only controls outside retained replacement and shutdown cleanup.
- Keep append ownership metadata bounded and retain no component, text, payload, filename, geometry, or replay history.
- Work in shared core on JVM and Scala Native without a runtime dependency.

**Non-Goals:**
- Exposing a public raw `TerminalRenderControl` encoder or arbitrary trusted escape writer.
- Turning `TUI` into a transcript store, replay log, persistence layer, or virtualized scrollback model.
- Making appended output interactive, focusable, removable, or addressable after publication.
- Appending before a live frame exists, in alternate-screen mode, or while the normal resize policy can clear scrollback.
- Guaranteeing Kitty/iTerm2 persistence beyond documented terminal-emulator behavior.
- Transactional rollback after bytes reach a backend boundary.
- Making `TUI` detect terminal capabilities on behalf of an `Image`; capabilities remain component configuration.
- Protecting append-only Kitty IDs across a fully stopped TUI lifecycle, process restart, or unrelated terminal application.

## Decisions

### 1. Use callback-completed append work and preserve `flushRender`

Expose an operation shaped like:

```scala
def appendToScrollback(
    component: Component,
    onComplete: AppendResult => Unit = _ => ()
): Unit
```

The public result model is shaped as:

```scala
enum AppendResult:
  case Published(rowCount: Int, controlCount: Int)
  case Rejected(reason: AppendRejection)
  case Failed(cause: Throwable)

enum AppendRejection:
  case LifecycleUnavailable(state: TUIDiagnosticLifecycleState)
  case AlternateScreen
  case ScrollbackClearingResizePolicy
  case NoCommittedFrame
  case AttachedComponent
  case StoppedBeforeClaim
  case StoppedBeforePublication
  case QueueCapacityExceeded
  case RetainedITerm2Control
```

A result callback is invoked exactly once. Rejection may complete synchronously; accepted work completes on the runtime drain after publication succeeds, fails, or is rejected by shutdown before claim. Completion may occur before the method returns, matching existing callback APIs. `Failed(cause)` is delivered only to the application-owned callback; diagnostics classify and redact that failure without retaining its message.

The method remains safe from runtime callbacks: it queues follow-up work and never recursively drains. `flushRender()` is unchanged. An uncontended append normally completes before the publishing call or a following uncontended flush returns, but a reentrant or concurrent `flushRender()` remains non-waiting. Applications that require acknowledgment use `onComplete`, not `flushRender()`.

Append-result callback failures follow existing application-callback failure handling and cannot prevent terminal restoration.

Accepted requests occupy a dedicated FIFO Append work category with a fixed capacity of 64 accepted incomplete operations, including any operation currently rendering or waiting for a resize retry. Append is the sixth ordinary category beside Structural, Action, Ingress, Control, and Render; each continuously ready category is serviced within six ordinary selections. Capacity is released only by exactly-once completion, so claim/requeue races cannot create a hidden 65th retained component. Capacity rejection does not enqueue or retain the component and completes exactly once with `QueueCapacityExceeded` through the existing serialized callback owner.

Alternative: make append blocking or change `flushRender()` to wait. Rejected because it conflicts with the promoted non-waiting contended-flush contract and can invert application locks.

### 2. Admit only an append-compatible lifecycle

Append requests are accepted only when all of these are true at publication:

- lifecycle state is `Running`;
- `TUIScreenMode.Normal` is active;
- `NormalResizeClearPolicy.PreserveScrollback` is configured; and
- a retained live frame has already been committed;
- that retained frame has no iTerm2 inline image control; and
- fewer than 64 append operations are accepted and incomplete.

Other requests complete with a typed rejection and emit no append bytes. Capacity admission is atomic, and mutable retained-frame conditions are rechecked when the owner claims work so intervening frame commits cannot bypass them. A retained iTerm2 control produces `RetainedITerm2Control`: iTerm2 provides no typed deletion primitive with which the runtime can reliably remove the old placement before relocating that frame.

All append callbacks, including capacity and lifecycle rejection, use the existing single callback owner and never run concurrently. A rejection retains no component or payload. When an external caller cannot claim the drain, its minimal rejection completion consumes one existing 4096-slot bounded ingress position and applies the existing lifecycle-aware backpressure without holding runtime or terminal-write locks; stop promotes that completion before discarding ordinary ingress. When the active owner itself requests an append that is rejected, it invokes the rejection callback synchronously outside runtime locks without recursively draining. Cleaning and stopped lifecycle rejections follow the existing finite callback-cutoff model.

Pending accepted append work that has not been claimed when stop or runtime failure wins has its component and payload references discarded. Only a bounded completion record remains; the owner invokes its callback exactly once with `StoppedBeforeClaim` before terminal cleanup and final shutdown completion. If stop wins after claim but before the synchronized publication boundary, the active body is discarded as soon as component code returns and completes with `StoppedBeforePublication`; it emits no append bytes. Callback failure does not prevent the remaining retained append and query callbacks or cleanup.

Requiring `PreserveScrollback` avoids promising append persistence while the runtime is configured to emit `CSI 3 J` on resize. The initial normal-screen clear occurs before any append can be admitted because a committed live frame is required.

### 3. Render a detached component in a restricted one-shot context

The passed component is a one-shot render input. It must not be an active retained child, retained descendant known to the latest layout, or overlay component of the same TUI. Detectable reuse is rejected before attachment. The public contract requires callers to supply a detached component; the TUI never installs it in retained children, focus, overlays, or mouse layout.

A contextual component receives a restricted append context that exposes the owning session's current `imageCellDimensions`. All render attempts for one append share an operation-scoped, thread-safe violation latch. Operations that would mutate retained runtime ownership—focus, overlays, exit, nested flush, or render scheduling—atomically latch a forbidden-operation violation and throw. The latch is checked after rendering and detachment, so a component cannot catch the exception and permit publication, including when an asynchronously retained context from an earlier resize attempt is used during a later attempt. This permits `Image` sizing without allowing one-shot rendering to acquire lasting UI authority.

Terminal capabilities remain component-provided, as they are today. The TUI does not infer or replace an `Image` component's configured `TerminalCapabilities`.

Context attachment and detachment use `try/finally`; detachment is attempted on every success, retry, rejection, and failure. Before `component.tuiContext_=(None)` is called, the restricted context is permanently revoked so retained or asynchronously used references cannot mutate or inspect the runtime. Every method observes revocation, latches the violation, and throws. A detachment exception or any latched forbidden/revoked access fails the candidate before publication, even if component code caught an earlier exception. Cursor placements are rejected because append-only output cannot own the retained hardware cursor.

### 4. Retry only unpublished candidates when resize invalidates geometry

When append work reaches the head of its FIFO, the runtime claims the resize generation, width, height, retained frame, and session dimensions. It renders and validates a candidate without writing terminal bytes, then rechecks generation and dimensions.

If geometry changed, the candidate is discarded and the same append is requeued ahead of later appends. The handler then yields to the six-category ordinary scheduler rather than retrying in place, so resize churn cannot create an owner-local spin or starve another continuously ready category. A component may therefore render more than once, consistent with normal retained rendering. Exactly one candidate is published and exactly one result callback completes.

Alternative: promise exactly one render attempt. Rejected because terminal dimensions can change concurrently while component code runs, and stale one-shot output cannot be repaired after publication.

### 5. Plan append output and retained-frame restoration as one owned write

After all validation and identity planning succeeds, one synchronized output buffer:

1. moves from the current retained-frame cursor to frame row zero;
2. clears only the replaceable live-frame region;
3. relocates retained Kitty controls using existing cleanup/retransmission semantics;
4. emits append-only lines and controls at column one;
5. terminates and reserves the complete append height;
6. redraws the unchanged prepared retained frame below the append;
7. restores its selected hardware cursor and logical `cursorRow`; and
8. updates `latestFrameStartRow` for terminal scrolling so coordinate-aware mouse routing still targets the retained layout.

The semantic `previousFrame`, retained layouts, children, overlays, focus, and input target remain unchanged. Only the live frame's physical terminal origin changes. Empty append renders publish no terminal bytes and complete successfully with zero rows and controls.

Multiple appends remain FIFO. A resize or other runtime category is ordered by the existing single owner; no second lock, output thread, or backend side channel is added.

A retained frame containing iTerm2 inline controls is never relocated by this planner: admission or claim rejects the append before rendering or output. Appended iTerm2 controls remain permitted because they are emitted once and never become part of a retained frame.

### 6. Remap and bound append-owned Kitty identities

Append rendering rejects `KittyCleanup` controls. Such a control is destructive rather than append-only and could delete retained or historical output.

Every appended `KittyImage` control is internally copied to a fresh runtime-allocated ID before encoding. Allocation excludes all Kitty IDs active in the claimed retained frame, all IDs already in the append ledger, and all remapped IDs selected earlier in the same append. This exclusion applies even when an active retained ID was manually configured and happens to equal the allocator's next candidate. The original component ID is not transferred, so reusing the component later cannot target the append placement. Internal construction remains `private[scalatui]`; no raw encoder or new public control constructor is exposed.

After successful publication, the TUI records only the remapped IDs in an append-ownership ledger. The ledger:

- contains at most 4096 IDs per TUI lifecycle;
- stores no payload, filename, component, text, placement, geometry, or output bytes;
- is never used for replacement or shutdown cleanup; and
- is cleared only when that TUI lifecycle is fully stopped.

An append needing more IDs than remaining capacity fails before publication. Before retained-frame publication, a manually configured retained Kitty ID that collides with the ledger is rejected before output. IDs allocated through the shared allocator remain naturally unique; the collision check protects caller-configured IDs.

This bounded ledger is ownership metadata, not a transcript. It is necessary because Kitty delete-by-image-ID can remove every placement sharing that ID.

### 7. Preserve typed authority and failure atomicity

The complete append render is validated before publication. Ordinary lines use the existing sanitization and line-reset path. Typed controls use the private exhaustive encoder. Validation rejects out-of-bounds controls, duplicate input Kitty IDs, cursor placements, Kitty cleanup controls, retained-ID collisions, and ledger overflow before append bytes are written.

Rendering, context attachment, latched context violation, context detachment, validation, or planning failure records the normal runtime failure, completes the append with `Failed`, and enters normal cleanup. No append bytes are published. If the single backend write fails after accepting a prefix, the operation reports failure and restores terminal lifecycle state without claiming rollback.

Successfully appended controls are absent from `previousFrame`, replacement cleanup, resize retransmission, and shutdown cleanup. Existing retained controls keep current lifecycle behavior.

### 8. Keep diagnostics structural and redaction-safe

Append diagnostics report bounded event kind, outcome/failure category, row count, control count, screen mode, and resize generation. They never retain component lines, exception messages, payloads, filenames, control bytes, remapped IDs, or terminal write contents.

The application-owned completion callback may receive the operation failure according to the public result model; diagnostic redaction remains independent.

### 9. Test portable semantics and separate emulator claims

Shared JVM/Native tests use an extended `VirtualTerminal` model to assert exact append ordering, six-category fairness, queue capacity, row reservation, retained-frame restoration, retained-iTerm2 rejection, mouse origin, cursor state, validation atomicity, fair-yielding resize retry, callback cardinality, restricted-context latching/revocation, Kitty collision-safe remapping/ledger limits, and cleanup ownership.

Automated JVM PTY tests assert emitted byte order, lack of forbidden cleanup, and terminal lifecycle restoration. A raw PTY is not a terminal emulator and cannot prove Kitty/iTerm2 scrollback persistence. Real Kitty and iTerm2 persistence remains documented manual smoke coverage on supported emulators.

## Risks / Trade-offs

- **Live-frame relocation can corrupt output** → one fully planned synchronized write plus virtual-terminal and PTY sequence assertions.
- **Default resize clearing contradicts persistence** → reject append unless `PreserveScrollback` is configured.
- **Kitty ID reuse deletes history** → fresh remapping, bounded ownership ledger, retained collision validation, and cleanup-control rejection.
- **Concurrent publishers retain unbounded components or payloads** → fixed 64-operation accepted-incomplete bound and typed capacity rejection.
- **Retained iTerm2 image cannot be reliably erased before relocation** → reject append before rendering while such a control is retained.
- **Resize invalidates one-shot geometry** → discard and retry only before publication; publish one candidate.
- **One-shot context catches a forbidden-operation exception or retains the context** → latched violations and permanent revocation before detachment.
- **Mouse routes to the old physical frame** → update frame-origin accounting in the append commit.
- **Backend accepts only a prefix** → fail and restore lifecycle without rollback claims.
- **Terminal emulators differ** → automate protocol invariants and keep emulator persistence a manual compatibility check.

## Migration Plan

1. Add failing shared tests for result callbacks, bounded admission, text output, typed controls, identity ownership, fair-yielding resize retry, frame restoration, retained-iTerm2 rejection, mouse origin, and cleanup.
2. Introduce append result/rejection values and the capacity-64 sixth FIFO work category in shared core, updating the ordinary fairness bound to six selections.
3. Add the latched, revocable restricted append context and detached-component checks.
4. Implement pre-publication rendering, validation, collision-safe Kitty remapping, and the bounded ownership ledger.
5. Implement the synchronized append/redraw planner and committed frame-origin updates.
6. Add diagnostics, JVM PTY sequence coverage, manual emulator smoke documentation, public API docs, examples, and changelog entries.
7. Run JVM, Scala Native, terminal conformance, formatting, lint, and strict OpenSpec validation.

Rollback removes the public append operation and work category before release. It must not expose the private control encoder as a fallback.

## Open Questions

None. The operation is intentionally callback-completed, normal-screen-only, preserve-scrollback-only, one-shot, typed, non-interactive, non-removable, and bounded for both pending work and Kitty ownership.
