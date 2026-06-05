## Context

`scala-tui` has a shared component model, focusable components, a differential renderer, virtual terminal tests, live resize notifications, and a multiline `Editor`. The promoted specs already describe overlay composition and editor autocomplete as target behavior, but the implementation does not yet expose a generic overlay stack, a public overlay handle API, or autocomplete provider contracts.

Current `pi-tui` behavior is the compatibility reference. Upstream overlays use hybrid positioning: absolute or percentage row/column values override anchor-based defaults, offsets are applied, margins clamp final placement, and each render re-resolves layout against current terminal dimensions. Upstream autocomplete providers are async and cancellable, while current Scala components and runtime are synchronous and dependency-light.

## Goals / Non-Goals

**Goals:**

- Add a generic overlay stack suitable for autocomplete popups, menus, command palettes, and future modal UI.
- Mirror `pi-tui` overlay semantics where practical: handle-based lifecycle, hybrid positioning, margins, visibility predicates, focus-capturing behavior, z-order, and resize-safe layout resolution.
- Add a public TUI context/host capability so components can request renders, exits, focus changes, and overlay operations without depending directly on concrete runtime internals.
- Add async-capable autocomplete contracts without introducing third-party runtime dependencies and without parameterizing `TUI`, `Component`, or `Editor` by an effect type.
- Integrate the multiline editor with autocomplete suggestions rendered through overlays and controlled with typed terminal input.
- Provide slash-command autocomplete helpers where applications supply commands and own command semantics.
- Keep JVM and Scala Native support aligned through shared core APIs and shared demo logic.

**Non-Goals:**

- Do not introduce cats-effect, ZIO, fs2, or any other runtime dependency.
- Do not make the entire component/runtime API tagless-final or effect-polymorphic in this change.
- Do not add built-in application command execution semantics to the TUI runtime.
- Do not implement full file-system or network autocomplete providers beyond contracts, helpers, and demo-safe examples unless they can be done dependency-free and cross-platform.
- Do not add image overlays, transparency semantics, mouse input, modal dialog widgets, or command palettes as complete features in this change.
- Do not implement editor undo/kill-ring, large-paste markers, hardware cursor marker/IME positioning, or fuzzy search beyond what autocomplete suggestions require.

## Decisions

### 1. Overlay API uses handles with stable internal ids

`TUI.showOverlay` will return an `OverlayHandle` that can hide/remove an overlay, temporarily hide/show it, focus/unfocus it, inspect focus/hidden state, and update options/content where needed by autocomplete. Internally each overlay receives a stable id so handles remain valid even if stack order changes.

Alternatives considered:

- Keyed overlays by string id. This is simpler to call but risks accidental collisions and makes ownership ambiguous across components.
- One global `hideOverlay()` only. This matches a convenience operation but is insufficient for component-owned lifecycle management.

### 2. Overlay positioning mirrors pi-tui hybrid resolution

Overlay options will support absolute integer row/column, percentage row/column, anchor values, offsets, width, minimum width, maximum height, margins, and visibility predicates. Resolution order follows upstream behavior: resolve width and max-height, resolve absolute/percentage/anchor position, apply offsets, then clamp to terminal bounds respecting margins. Layout is re-resolved every render using current terminal dimensions.

Alternatives considered:

- Absolute-only coordinates for v1. This would be easier but would immediately diverge from `pi-tui` and make menus/dialogs less reusable.
- Component-anchored geometry only. This is useful for editor popups but too narrow for generic overlays.

### 3. Overlay compositing is rectangular and ANSI-safe

Visible overlays render to lines at their resolved width. During compositing, each overlay line replaces the corresponding base cells within its rectangle. Spaces in overlay output are literal spaces. Overlay output is ANSI-aware clipped/truncated to the overlay width and final runtime sanitization still protects the terminal width contract.

Alternatives considered:

- Transparent spaces. This creates complex ANSI/style blending semantics and is not needed for first parity.
- Let overlay components overflow and rely only on final sanitization. This would make overlay placement and tests less deterministic.

### 4. Z-order follows focus/show order

The top visual overlay is the visible overlay with the newest focus order. Showing or focusing an overlay moves it to the top. Visible overlays are composited from oldest/lowest to newest/highest so later overlays replace earlier ones.

Alternatives considered:

- Explicit numeric z-index. More flexible but unnecessary for the current API and harder to keep deterministic.
- Pure insertion order. This does not support bringing an existing overlay to the front when focused.

### 5. Focus capture is configurable

Overlay options expose focus-capturing behavior. Capturing overlays take keyboard focus when visible and receive input as the active component. Non-capturing overlays render above content but preserve existing focus. When a focused overlay hides, the runtime restores focus to the next visible capturing overlay or the pre-overlay focused component.

Alternatives considered:

- Every overlay captures input. Too limiting for tooltips/status overlays.
- Render-only overlays in v1. Too weak for autocomplete suggestion lists and command palettes.

### 6. Components use a TUI context/host capability

Introduce a shared context/capability abstraction for runtime operations needed by components, such as `requestRender`, `requestExit`, focus changes, and `overlayHost`. The concrete `TUI` can implement the context, and tests can provide fakes. Components that do not need runtime capabilities continue to implement the existing simple component contract.

Alternatives considered:

- Pass concrete `TUI` into all components. This would couple components tightly to runtime internals and make tests harder.
- Keep components unaware of runtime APIs. This blocks editor-owned overlay lifecycle and async autocomplete callbacks from requesting renders.

### 7. Autocomplete uses a cancellable callback boundary, not global Tagless Final

Autocomplete providers will expose request/result models and a cancellable request handle. Providers can complete synchronously, from a `Future`, from a thread, or from another effect runtime by invoking a callback and returning a handle whose `cancel()` prevents stale UI updates where possible. Completion application remains a separate deterministic operation.

This preserves async support for file/network autocomplete without requiring `Component[F[_]]`, `TUI[F[_]]`, or third-party effect dependencies. Future tagless-final adapters can be added later around the callback boundary.

Alternatives considered:

- Synchronous-only providers. This is too limiting for file/network autocomplete and diverges from `pi-tui`.
- `Future` as the primary provider API. This commits to an execution model and has weak cancellation semantics.
- Full tagless-final runtime. This would spread an effect parameter through the component and TUI API before the project has a broader effect story.

### 8. Slash commands are autocomplete helpers, not runtime commands

The autocomplete package may expose `SlashCommand` models and provider helpers that map application-supplied command metadata into suggestions. The TUI runtime will not execute or own slash commands. Applications remain responsible for interpreting submitted editor text or selected completions.

Alternatives considered:

- Built-in TUI command registry. This would blur library/runtime responsibilities and prematurely constrain app semantics.
- No slash helper types. This would miss a visible `pi-tui` parity feature and make demos less representative.

## Risks / Trade-offs

- [Risk] Overlay focus restore can become subtle with multiple overlays and hidden states. → Mitigation: implement focused unit tests for hide/show/focus/unfocus transitions and keep the initial API smaller than upstream where necessary.
- [Risk] ANSI rectangular compositing can corrupt styles if slicing is naive. → Mitigation: reuse existing ANSI-aware utilities, add tests with styled base/overlay lines, and rely on final line resets/sanitization.
- [Risk] Async autocomplete callbacks can update stale editor state after text or cursor changes. → Mitigation: issue monotonically increasing request ids, cancel previous handles, and apply results only if the request snapshot still matches current editor state.
- [Risk] Callback-style async APIs are less idiomatic for pure FP users. → Mitigation: document the boundary and leave room for future `AutocompleteProviderF[F[_]]` adapters without changing component/runtime contracts.
- [Risk] Hybrid overlay options can make v1 implementation larger. → Mitigation: follow upstream resolution order, implement pure layout functions first, and test layout independently from terminal rendering.
- [Risk] Editor autocomplete plus generic overlays may be too broad. → Mitigation: keep provider contracts and keyboard behavior narrow, use existing `SelectList`, and defer full file/network provider implementations.

## Migration Plan

1. Add public overlay, context, and autocomplete model APIs in shared core with Scaladoc.
2. Implement pure overlay layout resolution and compositing utilities with tests before wiring them into live `TUI` rendering.
3. Extend `TUI` focus/input routing and virtual terminal tests for capturing and non-capturing overlays.
4. Add autocomplete provider request lifecycle handling and editor integration using overlays.
5. Update shared interactive demo and documentation.
6. Validate JVM and Scala Native builds/tests.

No data migration is required. The change is additive. Existing components should continue to render and handle input without adopting context or overlay APIs.

## Open Questions

- Should overlay handles expose an explicit `update` method in v1, or should autocomplete close and recreate overlays when suggestions change?
- Should a standard-library `Future` autocomplete adapter ship in the first implementation, or should the callback API be the only public async boundary initially?
- How much slash-command filtering should the helper provide in v1: exact/prefix matching only, or a small fuzzy matcher modeled after `pi-tui`?
- Should editor autocomplete auto-trigger on typing `/`, or should the first implementation require explicit Tab to reduce request churn?
