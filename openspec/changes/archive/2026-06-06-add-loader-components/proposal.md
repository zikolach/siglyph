## Why

`scala-tui` now has utility text and settings widgets, but it still lacks `pi-tui`'s loader widgets for long-running work. Adding loader components gives applications a dependency-free way to show progress, update messages, and expose cancellation while preserving JVM and Scala Native portability.

## What Changes

- Add a `Loader` component that renders a spinner/indicator plus styled message, with configurable frames, interval, color/style functions, message mutation, and idempotent `start()`/`stop()` lifecycle.
- Add a `CancellableLoader` component or cancellation-enabled loader variant that handles Escape, exposes a dependency-free cancellation token/state, and invokes cancellation callbacks.
- Integrate loader render updates with existing `TUIContext.requestRender()` without making the whole runtime effect-polymorphic or adding dependencies.
- Define cross-platform timer behavior suitable for shared `core`; if automatic timers are not viable on both JVM and Scala Native, ship a manual/tickable loader API and document automatic scheduling as deferred.
- Add tests for rendering, message/indicator mutation, tick advancement, cancellation, idempotent lifecycle, and width safety.
- Update docs and porting notes for `pi-tui` parity and any intentional deviations from Node `setInterval`/`AbortSignal` semantics.

## Capabilities

### New Capabilities

<!-- No new standalone capability; this change extends existing component, API, and runtime contracts. -->

### Modified Capabilities
- `component-rendering`: Add render, animation/tick, and cancellation input behavior requirements for loader components.
- `developer-api`: Add public API, lifecycle, cancellation-token, dependency, Scaladoc, and documentation requirements for loader components.
- `terminal-runtime`: Add requirements for safe render-request integration from contextual/timed components without compromising terminal lifecycle.

## Impact

- Affected core APIs: new `Loader`, loader options/indicator models, cancellation token or handle types, and `CancellableLoader` or equivalent cancellation-enabled component.
- Affected runtime APIs: likely uses existing `ContextualComponent`/`TUIContext`; only add scheduler abstractions if necessary and dependency-free.
- Affected tests: component rendering/input tests plus runtime/context tests for render requests and lifecycle behavior if automatic timers are included.
- Affected docs: README, porting notes, post-MVP plan, and follow-up loader design note.
- Dependencies: no new third-party runtime dependencies; JVM-only timer APIs are not acceptable for shared public behavior unless isolated behind a cross-platform abstraction.
