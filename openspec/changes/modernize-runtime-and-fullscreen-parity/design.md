## Context

Siglyph currently uses one `TUI` class for lifecycle, ordered ingress, queries, focus, overlays, append-only output, frame preparation, differential output, and both screen-buffer modes. Alternate-screen mode enters and exits the alternate buffer but keeps the same unbounded width-only document model. Current `pi-tui` adds a separate fixed-height viewport renderer, constrained stack layout, nested scrolling, search, selection, and richer mouse behavior.

The existing runtime has strong invariants that must remain intact. One drain owner serializes application work. Lifecycle and terminal-write locks are separate. Typed `ComponentRender`, cursor metadata, and terminal controls preserve authority boundaries. Shared sources compile for JVM and Scala Native without third-party runtime dependencies.

Two reliability gaps must be addressed before fullscreen work. Backend worker failures have no path into the TUI lifecycle, and scheduler-driven built-in component mutation does not have one documented serialization rule. The design stages those corrections before adding viewport behavior.

## Goals / Non-Goals

**Goals:**

- Make unexpected backend worker failure stop the runtime, wake `run()`, restore terminal state, and report the original failure.
- Define one JVM and Scala Native threading and callback-isolation contract for built-in mutable components.
- Keep existing `TUI(terminal)` and width-only components source-compatible.
- Separate normal-screen and fullscreen rendering policy without duplicating lifecycle, input, query, overlay, or output ownership.
- Add deterministic height-aware layout, nested scrolling, transcript navigation, search, scroll affordances, selection, and richer typed mouse interaction.
- Keep viewport image placement typed, clipped, cache-aware, session-owned, and safe across overlays and scrolling.
- Add instance-scoped capability overrides and current terminal detection.
- Establish deterministic performance workloads before claiming current fullscreen parity.

**Non-Goals:**

- Runtime renderer switching within one started TUI lifecycle.
- Node timers, `AbortSignal`, promises, futures, or an effect-runtime requirement in core.
- Process-based completion helpers or process-global mutable capability overrides.
- V8-specific output workarounds, Windows native support, or Windows console helpers.
- Built-in LaTeX parsing or a new Markdown dependency.
- Arbitrary trusted terminal strings.
- A mandatory operating-system clipboard dependency.
- Replacing `Component.render(width): ComponentRender`.

## Decisions

### 1. Report backend failure through a source-compatible start overload

Add a concrete three-callback `Terminal.start` overload that accepts `onFailure: Throwable => Unit` and delegates to the existing two-callback method by default. Existing third-party terminal implementations keep compiling. Built-in terminals override the richer overload and report unexpected failures from active input, fragment-flush, and resize workers.

EOF, explicit-stop interruption, and stale generation termination remain normal outcomes. Each worker reports an unexpected failure at most once per active generation. Independent failures from other workers remain reportable. Failure publication is lifecycle control work, not ordinary bounded ingress, so it cannot wait behind the 4096-event FIFO.

The TUI records the first failure, attaches later cleanup or worker failures as suppressed errors, transitions to stopping, wakes lifecycle waiters and blocked publishers, preserves required retained completions, and runs existing single-owner cleanup. Cleanup never joins the worker that is reporting its own failure.

An optional capability trait was considered. Rejected because every terminal backend needs the same failure contract and a concrete overload preserves implementer source compatibility without runtime type tests.

### 2. Use snapshot, effect, and commit phases in mutable components

Built-in mutable components use one private state boundary. Each operation follows these phases:

1. Validate, mutate, or capture an immutable snapshot under the state boundary.
2. Release the state boundary.
3. Invoke application callbacks, providers, cancellation handles, theme hooks, and `TUIContext` methods.
4. Re-enter the state boundary only to commit a result. Guard asynchronous results with a generation or state snapshot.
5. Request rendering after releasing the state boundary.

Rendering captures a coherent immutable snapshot before formatting output. Public scheduler-driven methods such as `Loader.tick`, message updates, and cancellation use the same boundary. Runtime-owned input and render callbacks remain serialized by the TUI drain, while public calls from other execution contexts are linearized by the component boundary.

Holding component monitors while invoking callbacks was considered. Rejected because it permits application lock inversion and makes slow providers block unrelated component state access.

### 3. Extract renderer policies behind the existing TUI facade

Keep the public `TUI` facade and existing constructors. Extract internal shared runtime services for lifecycle, ingress, queries, overlays, focus, diagnostics, append coordination, failure handling, capability session state, and the terminal write boundary.

Rendering policy has two implementations:

- `NormalScreenPolicy` preserves current scrollback, append-only output, differential redraw, cursor parking, and resize-clear behavior.
- `FullscreenViewportPolicy` owns fixed-height alternate-screen layout, viewport-relative painting, application-owned scrolling, selection, and fullscreen fallback commands.

The current `TUIOptions(screenMode = TUIScreenMode.Alternate)` path keeps its documented width-only behavior. A new explicit viewport factory or typed option selects `FullscreenViewportPolicy` and requires a layout root. The exact constructor names are chosen during implementation without removing existing call shapes.

Replacing the public `TUI` class with unrelated renderer classes was considered. Rejected because it would create an avoidable source break and duplicate mature lifecycle logic.

### 4. Represent viewport composition with an optional layout algebra

Keep `Component.render(width)` unchanged. Add an optional viewport layout provider implemented by `VStack`, `HStack`, and `ScrollView`. The provider exposes immutable layout intent. A dedicated layout engine resolves that intent for positive terminal width and height.

A width-only component remains a leaf. The engine renders it once per component identity and width within a frame, validates its local metadata, measures intrinsic height, assigns a rectangle, and clips rows to the assigned height. Direct rendering of stack and scroll components returns the complete unbounded document.

Stack entries support basis, grow, shrink, minimum size, maximum size, gap, alignment, and viewport-dependent visibility. Allocation is deterministic:

1. Resolve visible entries.
2. Measure automatic basis values.
3. Clamp each basis to its minimum and maximum.
4. Distribute spare size by grow weight.
5. Remove excess size by shrink weight without crossing minimum size.
6. Assign integer remainder in stable insertion order.
7. Clip deterministically if minimum sizes still exceed available space.

Each layout box retains assigned rectangle, effective clip rectangle, parent, children, component identity, and optional scroll owner. Text rows, `CursorPlacement`, `TerminalControlPlacement`, and mouse bounds use the same geometry. Invalid child metadata fails before translation. Wide graphemes are never divided to fill a horizontal boundary.

Adding height to every component render method was considered. Rejected because it would break every component and force ordinary document components to own viewport policy.

### 5. Make ScrollView own scroll state, not child content

`ScrollView` has exactly one content child. It tracks content height, viewport height, scroll offset, optional follow-end state, primary designation, overscroll policy, and scrollbar mode. It does not mutate or copy child content.

When following the end, content growth keeps the final row visible. User movement away from the end disables following. Jump-to-end restores it. Resize and content shrink clamp the offset. Nested scrolling starts at the deepest committed scroll view under the pointer. Unconsumed delta chains to ancestors only when overscroll policy permits it.

The compatibility path may render a width-only child completely before clipping. Large transcript components can implement an optional range-rendering contract that returns visible rows and stable document metadata without formatting every offscreen row. The first viewport milestone does not require every component to virtualize.

Semantic transcript navigation uses typed document markers from an optional provider. A `PromptStart` marker identifies a normalized document offset or row before viewport clipping. Layout translates markers with the same document geometry used for scrolling, and navigation targets the nearest preceding or following marker. Ordinary OSC 133-looking text and unsupported terminal controls remain inert and create no marker authority.

### 6. Index search over normalized rendered document text

Fullscreen search operates on the primary scroll view's normalized plain-text document at its current content width. ANSI metadata and typed terminal controls do not become searchable text. The index is cached by content revision, width, and query. Visible highlights are produced only for current viewport rows.

Search UI is a focus-capturing viewport layer with typed open, close, next, and previous commands. Manual scrolling does not reset the current query. Content or width changes invalidate only affected index state.

Requiring every component to expose an application-specific logical document model was considered. Rejected because generic `Text`, Markdown, containers, and third-party components must remain searchable through rendered output.

### 7. Store selection as normalized document offsets

Selection maps visible terminal cells to plain-text grapheme boundaries in the committed primary scroll document. Anchors use normalized document offsets rather than raw ANSI byte positions or stale terminal coordinates. Reflow rebuilds the display mapping from those offsets when possible. Content replacement that invalidates an anchor clamps or clears selection deterministically.

Primary press can begin selection when no component handles the gesture. Drag extends selection and can trigger bounded edge scrolling. Double-click selects a word-like run. Triple-click selects one normalized document line. Token boundary rules preserve common path and kebab-case characters where current `pi-tui` does.

Copy is explicit. Core exposes a typed clipboard target owned by the TUI session. A host callback is the portable path. Optional OSC 52 output, if included, uses a bounded typed operation, standard base64 payload encoding, explicit capability configuration, and the existing serialized output boundary. Ordinary component strings cannot gain clipboard authority. Unsupported copy reports failure rather than success.

### 8. Derive semantic mouse gestures in the runtime

Extend low-level typed mouse input with motion and pressed-button state. The runtime derives move, click, multi-click, drag, and release gestures from ordered low-level events and committed layout geometry. Multi-click classification uses an injectable monotonic clock and configured time and cell-distance bounds. It requires no background timer.

Interactive backends select the minimum xterm tracking mode required by configured behavior. Basic press, release, and wheel input uses mode `1000`. Drag motion uses button-motion mode `1002`. Hover or uncaptured move delivery uses all-motion mode `1003` only when explicitly requested and not disabled by conservative multiplexer policy. Start and stop enable and disable the selected tracking mode with SGR coordinates as one lifecycle obligation.

A typed mouse result contains handled state, render intent, capture intent, and focus intent. One optional capture owner receives later movement and release even outside its bounds. Release, component removal, overlay removal, hidden state, stop, or failure clears capture. Wheel and hover preserve focus unless a handler explicitly requests focus.

`MouseRegion` wraps one child, preserves the child's typed render metadata and layout, and delegates semantic mouse events to an application callback. Overlays retain topmost-first routing.

### 9. Clip image controls before encoding and retain bounded Kitty state

Viewport and overlay clipping operate on typed image footprints before final encoding. Fully clipped images emit no transmission control. A partially visible image emits only when a typed protocol helper can represent validated cropping or placement. Otherwise the renderer omits the control or uses documented fallback output. Raw protocol rewriting is forbidden.

Kitty image data retention is per TUI. A bounded cache tracks runtime-owned image ID, semantic source identity, protocol, geometry, last visible generation, and cleanup state. Recently offscreen unchanged images can reuse uploaded data when they return. Deterministic count and generation-age bounds evict old entries. Stop clears session state and emits supported cleanup once.

iTerm2 placement lacks equivalent relocation and cleanup guarantees. Partial or relocated iTerm2 images use the safe omission or fallback policy rather than crossing sticky or overlay regions.

### 10. Resolve capabilities once per TUI session

At startup, each TUI builds effective capabilities from environment detection and typed per-instance overrides. Each capability override is tri-state: detected value, forced value, or disabled. Explicit component capabilities remain fixed local choices. Runtime-aware components read effective session capabilities and cell dimensions through `TUIContext`.

Add current Zed detection with focused fixtures. Multiplexer restrictions remain conservative unless the application explicitly overrides them. No cache or override is process-global.

### 11. Separate deterministic performance gates from timing reports

Add fixed workloads for large transcript layout and append, small-tail differential redraw, Unicode wrapping and reflow, overlays, nested scrolling, search, selection mapping, and image-heavy clipping and cache reuse.

Correctness tests assert deterministic counters such as rendered components, measured rows, painted rows, scanned search rows, encoded controls, and terminal writes. Those counters provide stable regression gates across machines. Dedicated benchmark targets add warmup and repeated timing reports for JVM and representative Scala Native smoke runs. Ordinary unit tests do not depend on wall-clock thresholds.

A mandatory timing threshold in the normal test suite was considered. Rejected because host load would make correctness validation flaky.

### 12. Refresh parity evidence as part of completion

Update the pinned comparison to `earendil-works/pi` commit `da840b6216578c2a571d0374ac6a2091a83f9d91` or a later explicitly reviewed commit. The matrix separates current full behavior, partial behavior, intentional deviations, and Siglyph extensions. Each row cites local tests or documentation.

README no longer claims complete current component coverage unless the refreshed matrix supports it. Public Scaladoc and examples cover renderer choice, component threading, viewport layout, scrolling, search, mouse capture, selection, clipboard behavior, capability overrides, and JVM and Native scope.

## Risks / Trade-offs

- [Backend failure reporting can recurse from a failing worker] -> Publish failure without ordinary ingress or terminal output, mark each generation once, and never join the reporting worker during cleanup.
- [Component callback isolation can reorder visible state] -> Define snapshot and commit points explicitly and guard asynchronous results with generations.
- [Policy extraction can regress mature normal-screen output] -> Characterize current output first and require byte-level protocol and viewport-equivalent regression fixtures before moving logic.
- [Constrained layout can become ambiguous] -> Specify allocation, remainder, clipping, and visibility order and test over-constrained nested stacks.
- [Width-only fallback can render large offscreen documents] -> Keep it for compatibility, add optional range rendering, and measure both paths.
- [Search indexes can retain application text] -> Keep indexes per TUI and per content revision, bound cached revisions, and clear them on stop or replacement.
- [Selection can drift after reflow or replacement] -> Use normalized grapheme offsets and deterministic clamp or clear rules.
- [Mouse capture can target stale components] -> Tie capture to committed component identity and clear it on structural or visibility changes.
- [Clipboard output can cross a terminal trust boundary] -> Keep copy explicit, capability-gated, size-bounded, base64-encoded, and outside ordinary component strings.
- [Image caching can retain large payloads] -> Retain references and bounded semantic metadata rather than duplicate payload text, and evict deterministically.
- [iTerm2 cannot safely match Kitty placement behavior] -> Document the protocol deviation and choose omission or fallback over incorrect clipping.
- [Benchmark timing can vary by host] -> Gate deterministic work counters in tests and keep timing comparison in a controlled dedicated job.
- [The change is large] -> Implement and validate each migration stage in order while keeping normal-screen construction operational.

## Migration Plan

1. Refresh compatibility evidence and capture current normal-screen and alternate-buffer behavior with characterization tests.
2. Add terminal failure reporting and component callback isolation before changing rendering architecture.
3. Add session capability overrides and Zed detection.
4. Extract shared runtime services and `NormalScreenPolicy` without user-visible output changes.
5. Add the layout algebra, `VStack`, `HStack`, viewport policy, and basic `ScrollView` behind opt-in construction.
6. Add richer typed mouse parsing, semantic gestures, capture, `MouseRegion`, and backend tracking-mode lifecycle before any pointer-driven viewport widget.
7. Add follow-end behavior, nested navigation, scrollbar geometry, then scrollbar pointer interaction and jump-to-end on the completed mouse contract.
8. Add search, selection, edge scrolling, and explicit clipboard targets.
9. Add viewport image clipping and bounded Kitty retention.
10. Establish benchmark counters, dedicated timing targets, PTY coverage, JVM and Native tests, examples, Scaladoc, and final compatibility documentation.

Rollback is stage-based. Existing normal-screen construction and width-only components remain valid throughout. If a later fullscreen layer must be reverted, the preceding viewport foundation remains usable without silently changing existing TUI behavior.

## Open Questions

None. Public names and benchmark baseline values are implementation details to select within the specified compatibility and measurement contracts.
