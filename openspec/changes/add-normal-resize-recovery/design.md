## Context

`TUI.appendToScrollback` can commit detached durable output above one retained normal-screen frame, but a `PreserveScrollback` resize clears the active viewport before repainting that frame. Siglyph intentionally retains no append transcript, so it cannot reconstruct durable rows that occupied the invalidated viewport. Applications may retain the semantic transcript and reflow its newest tail, but today they cannot publish that tail through the single runtime owner.

The existing runtime already has the required authority boundary: one drain owner serializes application code and render work; one terminal-write lock protects complete writes; `ComponentRender` ordinary lines are sanitized; typed controls remain private until final encoding; resize generation rejects stale candidates; `previousFrame`, `cursorRow`, layouts, and `latestFrameStartRow` describe only the retained live frame. The change must preserve those invariants and the append callback/FIFO contract on JVM and Scala Native.

There is an unavoidable terminal boundary: Siglyph cannot inspect emulator scrollback or know which semantic transcript rows survived terminal-specific resize reflow. Recovery can therefore provide a strict budget and safe publication point, but the application remains responsible for selecting only the newest durable tail that belonged in the cleared viewport.

## Goals / Non-Goals

**Goals:**

- Add an opt-in, TUI-owned recovery phase for geometry-changing normal-screen `PreserveScrollback` redraws.
- Let an application reflow a bounded semantic transcript at current width without transferring retained component authority.
- Publish recovery and the retained frame in one synchronized render write.
- Keep the semantic differential baseline, typed controls, cursor, layout, focus, and input ownership attached only to the live frame.
- Preserve chronological placement for a later `appendToScrollback` operation.
- Reject stale, oversized, or failed candidates before output where possible and use normal fail-fast cleanup otherwise.
- Expose bounded, redacted diagnostics and shared JVM/Native behavior without a runtime dependency.

**Non-Goals:**

- Retaining appended components, payloads, output bytes, or a semantic transcript inside `TUI`.
- Detecting which terminal scrollback rows survived resize or guaranteeing emulator-specific reflow behavior.
- Recovering on startup, forced renders, ordinary differential redraws, appends, font/cell-size updates without terminal geometry change, alternate-screen redraws, or `ClearScrollback` redraws.
- Asynchronous recovery, cancellation, completion callbacks, or a new scheduler category.
- Typed recovery controls, images, cursor placements, focus, overlays, mouse targets, or interactive recovered content in the first version.
- Rolling back a backend write that accepts only a prefix.

## Decisions

### 1. Expose a synchronous text-only provider through `TUIOptions`

Add shared-core public types shaped as:

```scala
trait NormalResizeRecoveryProvider:
  def render(context: NormalResizeRecoveryContext): Vector[String]

final case class NormalResizeRecoveryContext(
    width: Int,
    height: Int,
    maxRows: Int,
    previousWidth: Int,
    previousHeight: Int,
    previousMaxRows: Int
)

final case class TUIOptions(
    // existing fields,
    normalResizeRecovery: Option[NormalResizeRecoveryProvider] = None
)
```

The provider receives positive current and previous terminal dimensions, the maximum durable prefix
that could have occupied the old viewport above its live frame, and a positive strict publication
budget bounded by both old and new capacities. It returns ordinary lines in oldest-to-newest display
order. Returning `Vector[String]` rather than `Component`, `ComponentRender`, or raw terminal bytes
makes the first contract structurally text-only: recovery cannot attach context, retain component
identity, acquire cursor ownership, or introduce a typed control whose later cleanup would need
historical ownership.

Ordinary lines still support the existing bounded SGR and OSC 8 allowlist. Every other terminal-looking sequence remains inert, and final width sanitization and line resets use the same path as retained and append output.

The provider runs synchronously as application-controlled render code on the existing drain owner and outside lifecycle and terminal-write locks. It may be called more than once when geometry invalidates a candidate, so it must derive output from current semantic state rather than perform one-shot side effects. It has no completion callback because its success is part of the resize redraw commit.

A configured provider is valid only with `TUIScreenMode.Normal` and `NormalResizeClearPolicy.PreserveScrollback`. `TUI.start()` fails before backend startup or terminal output when the option combination is incompatible, rather than silently ignoring a misconfigured provider.

Alternatives considered:

- Return `ComponentRender`: rejected for version one because controls and cursors would then need rejection despite being representable, and image identity/cleanup semantics would duplicate append complexity.
- Accept a detached `Component`: rejected because recovery needs a row budget and no retained or restricted context authority.
- Accept raw terminal bytes: rejected because it bypasses the trusted-output boundary.
- Register a mutable provider after startup: rejected because immutable options provide one lifecycle-stable source and avoid registration races.

### 2. Track resize recovery eligibility separately from generic force/clear rendering

`publishResize` continues to coalesce resize work and increment `resizeGeneration`, but records that the latest pending forced-clear render came from terminal geometry change. Generic `requestRender(force = true)`, initial rendering, image cell-dimension changes, appends, and ordinary full redraws do not set recovery eligibility.

A redundant resize callback whose positive width and height equal the committed dimensions remains
observable through ordinary resize diagnostics but is not a geometry-changing recovery event. It
does not invoke the provider or perform the destructive viewport clear; any coalesced forced render
may still repaint through the ordinary owned path.

When Render work is claimed, it snapshots the eligible resize generation. A later resize supersedes that marker. Recovery is attempted only when all of these hold:
Once any pending callback reports geometry different from the committed render geometry, the runtime
keeps that invalidation until a redraw commits. A later coalesced callback that returns to the
committed dimensions does not erase the invalidation because the intermediate terminal resize may
already have truncated or reflowed viewport content.

The render owner may also observe a geometry delta through `columns` or `rows` before a backend
resize callback publishes its generation. That delta still requires the configured normal-screen
resize clear to remove stale rows. The force and clear intent survives a stale candidate even when a
later callback returns to the committed geometry. It does not invoke recovery because provider
eligibility remains tied to callback-originated invalidation.


- lifecycle is `Running`;
- a prior live frame is committed;
- the current work represents a terminal width or height change;
- normal screen and `PreserveScrollback` are active; and
- a provider is configured.

This keeps provider invocation tied to the event that destroys the active viewport and avoids treating every forced redraw as durable-history loss.

Alternative: infer recovery from `force && clear`. Rejected because startup, alternate-screen work, and application-forced clear/render paths can share those flags without representing an invalidated normal viewport.

### 3. Render the live frame first and enforce a strict row budget

The owner renders, composes, validates, and prepares the current live frame before invoking recovery. The strict budget is:

```text
liveFrameFootprintRows = max(1, liveFrameRowCount)
currentMaxRows = max(0, terminalHeight - liveFrameFootprintRows)
previousLiveFrameFootprintRows = max(1, previousLiveFrameRowCount)
previousMaxRows = max(0, previousTerminalHeight - previousLiveFrameFootprintRows)
maxRows = min(currentMaxRows, previousMaxRows)
```

`ComponentRender.lines` already includes rows reserved by typed controls, so a non-empty
`liveFrameRowCount` is the complete retained frame height. An empty retained frame still needs one
physical cursor anchor: append and differential movement must have a live-frame row below recovery
rather than treating the final recovery row as replaceable live output. If the resulting footprint
fills or exceeds the viewport, recovery is empty and the provider is not invoked.

For a positive budget, the provider receives current width and height, previous width and height,
`previousMaxRows`, and `maxRows`. The old capacity lets it identify the newest semantic entries that
could have occupied the invalidated old viewport before reflowing those entries at current width.
Bounding `maxRows` by both old and current capacities prevents a large viewport growth from
replaying rows that necessarily predated the old viewport tail. Siglyph sanitizes every returned
line at current width, adds the normal line reset, and rejects the candidate if the provider returned
more than `maxRows`. Recovery sanitization increments the existing aggregate count but does not
retain provider source or sanitized text in the content-bearing `lastSanitizedLine` sample. It does
not silently truncate or `takeRight`, because only the application knows semantic entry boundaries
and whether dropping a prefix would split an entry. Empty output is valid and introduces no extra
blank row.

The provider is contractually responsible for using `context.previousWidth` and
`context.previousMaxRows` to select the semantic entries that could have occupied the invalidated
old viewport, reflowing that selected tail at `context.width`, selecting at most the newest
`context.maxRows` current-width rows, and returning them oldest-to-newest. Siglyph does not ask for
or retain the complete transcript and does not infer survivors from terminal scrollback.

Alternatives:

- Budget before live rendering: rejected because overlays and current component state determine the actual live row count.
- Clamp oversized output: rejected because silent row loss can split semantic output and obscure provider bugs.
- Expose an exact survivor count: rejected because terminal emulators can reflow scrollback
  differently. Previous geometry and the old live-frame-derived maximum are structural upper bounds,
  not claims about emulator scrollback survivors.

### 4. Publish clear, recovery, and live frame as one render commit

For an eligible committed candidate, one synchronized output buffer performs:

1. synchronized-output start and autowrap disable;
2. normal active-viewport clear and home, without `CSI 3 J`;
3. existing retained Kitty replacement cleanup required by the new live frame;
4. zero or more sanitized recovery rows;
5. exactly one row transition when non-empty recovery is followed by live output or its empty-frame anchor;
6. the prepared retained live frame and its typed controls;
7. retained structured hardware-cursor restoration; and
8. synchronized-output end and autowrap restoration.

No bytes are written between recovery and live output, and recovery never enters `previousFrame`. After success:

- `previousFrame` is the newly prepared live frame only;
- `previousWidth` and `previousHeight` are current dimensions;
- retained base/overlay layouts and focus remain live-frame-only;
- `cursorRow` remains frame-relative to the live frame;
- `latestFrameStartRow` becomes the physical start of the live frame after accounting for the recovery prefix and any terminal scrolling; and
- the committed recovery row count is emitted diagnostically.

For a clear starting at terminal row zero, live-frame origin is computed from the combined recovery/live write and then offset by recovery row count. An empty live frame emits or moves to its reserved blank anchor after recovery, so later append cannot overwrite the last recovered row. This also handles an over-height live frame, for which recovery is zero and the origin may be conceptually above the visible viewport as today.

Keeping `cursorRow` live-relative is essential: later differential redraw and append code can move to live row zero and clear only the replaceable frame. The recovery prefix then remains above that region.

Alternative: concatenate recovery into `previousFrame`. Rejected because ordinary redraw would repaint durable history and later append would place new output above recovered old output.

### 5. Preserve append chronology without changing append admission or identity ownership

After a successful recovery commit, `appendToScrollback(B)` continues to operate on the semantic live frame. It moves to live row zero, clears only the live region, emits `B`, and redraws the live frame. The physical order is therefore:

```text
recovered durable tail -> B -> retained live frame
```

Recovery has no Kitty/iTerm2 controls or ownership ledger, so append admission, retained-iTerm2 rejection, append Kitty ID remapping, the 64-operation bound, callbacks, and FIFO ordering remain unchanged. Successfully recovered text is detached one-shot output and is not addressed by cleanup.

Repeated resizes may ask the provider to reconstruct the same semantic newest tail because the active viewport is cleared each time. That is intentional recovery of invalidated viewport content, not TUI transcript replay. The provider must not return older rows already known to be outside that tail. Real emulator reflow can still preserve or move rows differently; docs and smoke tests state that limitation rather than promise impossible deduplication.

### 6. Reject stale candidates at the existing pre-write boundary

The render attempt snapshots generation, width, and height before live-frame and provider rendering. Immediately before output and baseline mutation, it compares all three with current runtime state.

If stale, Siglyph emits no candidate bytes, leaves the prior semantic baseline and layouts committed, coalesces a forced clear render for latest dimensions, and returns to the ordinary scheduler. The provider may be invoked again for the latest geometry. There is no owner-local retry loop and no new fairness category because recovery remains part of Render work.

If stop or runtime failure wins before commit, the candidate is discarded with queued ordinary render work. No recovery callback must be retained during Cleaning.

### 7. Use fail-fast failures and redacted recovery diagnostics

Add bounded diagnostic models shaped around:

```scala
enum TUIDiagnosticResizeRecoveryOutcome:
  case Completed, Discarded, Failed

enum TUIDiagnosticResizeRecoveryFailure:
  case StaleGeometry, Provider, RowBudget, Write

case ResizeRecovery(
    outcome: TUIDiagnosticResizeRecoveryOutcome,
    failure: Option[TUIDiagnosticResizeRecoveryFailure],
    maxRows: Int,
    rowCount: Int,
    resizeGeneration: Long
)
```

Exact names may follow local naming conventions, but the public event remains additive and structural. A successful eligible redraw emits `Completed`, including zero recovered rows. A stale attempt may emit `Discarded/StaleGeometry`; the later committed attempt emits its own completion. Provider exceptions and row-budget violations emit `Failed` before output and enter normal runtime failure/cleanup. A backend write failure emits `Failed/Write`, restores terminal state, and makes no rollback claim.

Diagnostics never contain provider lines, transcript entries, exception messages, terminal bytes, SGR/OSC contents, controls, or application object references. Existing Resize, Redraw, and Write events remain available and retain their current meaning.

Alternative: add a recovery row field to the existing `Redraw` enum case. Rejected because changing a public enum-case arity is more disruptive than adding a new event case and cannot represent failed/discarded attempts cleanly.

### 8. Validate portable semantics and terminal-boundary behavior

Shared `VirtualTerminal` tests cover provider eligibility, budget, chronology, baseline separation, ordinary redraw non-invocation, repeated/coalesced resize, stale retry, zero budget, over-budget/provider failure, diagnostics, hardware cursor, mouse origin, stop races, and JVM/Native parity.

JVM PTY coverage verifies clear/recovery/live/append byte ordering, absence of `CSI 3 J`, synchronized output, and terminal restoration. PTY tests do not claim emulator scrollback persistence or deduplication. Manual Kitty, iTerm2, and one conventional terminal smoke checks exercise preserved scrollback and repeated width/height changes.

## Risks / Trade-offs

- **Terminal resize reflow can preserve different physical rows across emulators** → Define recovery as provider-selected reconstruction, avoid survivor inference, and document/manual-smoke emulator behavior.
- **A slow provider blocks input and other callbacks** → Keep the provider synchronous, bounded in output, owner-serialized, and document that it must be fast and side-effect-light.
- **Provider state locking can reproduce application lock inversion** → Invoke outside runtime locks and document the same application-lock discipline as component rendering.
- **Oversized recovery could overwrite live-frame geometry** → Validate row count strictly before write and fail fast rather than truncate.
- **Viewport growth could offer space for history older than the invalidated old tail** → Bound the
  publication budget by both previous and current live-frame-derived capacities and expose previous
  width/capacity for application semantic selection.
- **Live frame fills the viewport** → Compute zero budget, skip provider invocation, and perform the existing resize redraw safely.
- **Resize changes during provider execution** → Discard before output and retry through coalesced Render scheduling.
- **Backend accepts only part of the combined write** → Run normal terminal restoration and report write-category diagnostics without rollback claims.
- **Adding a provider to `TUIOptions` changes binary shape** → Keep source-compatible trailing default, document the pre-1.0 additive API, and release it in a minor version.

## Migration Plan

1. Add shared public provider/context and diagnostic models with Scaladoc, plus incompatible-option validation before terminal startup.
2. Add resize-origin tracking that distinguishes geometry-change recovery from generic forced rendering.
3. Add failing shared tests for budget computation, invocation boundaries, stale candidates, failure handling, and diagnostics.
4. Implement text-only provider rendering, sanitization, strict row validation, and the combined recovery/live full-render planner.
5. Update live-frame physical-origin, cursor, mouse, differential baseline, append chronology, and cleanup tests.
6. Add JVM PTY ordering/restoration coverage and manual terminal-emulator smoke instructions.
7. Update README, runtime diagnostics, porting notes if parity differs, Scaladoc, and changelog.
8. Run full JVM/Native, PTY, formatting, lint, and strict OpenSpec validation.

Rollback removes the provider/context option and recovery diagnostic cases before release. It restores the existing preserve-scrollback full redraw path; no transcript migration or persisted runtime state exists.

## Open Questions

None. Version one is intentionally synchronous, text-only, resize-only, normal-screen-only, preserve-scrollback-only, provider-selected, strictly row-bounded, and outside retained frame state.
