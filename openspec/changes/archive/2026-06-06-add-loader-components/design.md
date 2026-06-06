## Context

`scala-tui` has a shared component model, `TUIContext`, typed input, and deterministic tests for JVM and Scala Native. Upstream `pi-tui` provides `Loader` and `CancellableLoader` implemented with Node timers and `AbortSignal`; those exact primitives are not portable to dependency-light Scala core.

The prior loader follow-up note recommended a focused change that answers timer lifecycle, render scheduling, cancellation API, JVM/Native compatibility, and cleanup before exposing loader widgets. To keep this first loader change portable and testable, it will add tickable loader components in shared `core` and avoid component-owned background threads or runtime scheduler APIs.

## Goals / Non-Goals

**Goals:**

- Add a dependency-free `Loader` component with configurable frames, interval metadata, message styling, indicator styling, and width-safe rendering.
- Add explicit `start()`, `stop()`, `running`, `tick()`, `setMessage(...)`, and `setIndicator(...)` behavior.
- Add `CancellableLoader` or an equivalent cancellation-enabled loader that handles typed Escape input and exposes dependency-free cancellation state/token semantics.
- Use `TUIContext.requestRender()` when context is available after message changes, indicator changes, ticks, start/stop, or cancellation.
- Keep implementation in shared `core`, compiling for JVM and Scala Native without third-party dependencies.
- Document intentional deviation from `pi-tui`: first pass is tick-driven instead of owning a Node-style interval.

**Non-Goals:**

- Do not add a global TUI scheduler, background daemon thread, `java.util.Timer`, cats-effect, ZIO, or any other runtime dependency.
- Do not make `TUI` or components effect-polymorphic.
- Do not promise wall-clock-accurate animation intervals in this change.
- Do not integrate loaders into the interactive demo unless it remains trivial and non-disruptive.
- Do not port JavaScript `AbortSignal` literally; model cancellation with Scala-owned types.

## Decisions

### 1. Ship tick-driven loader animation first

`Loader.start()` marks the loader running and requests render; `Loader.tick()` advances the frame only while running and requests render; `Loader.stop()` marks it stopped and is idempotent. `intervalMs` remains public configuration metadata for applications or future schedulers.

Alternatives considered:

- Own a background thread/timer in the component. This would better match `pi-tui` but risks Native compatibility and lifecycle leaks.
- Add a TUI scheduler capability now. This is likely useful later, but it is a cross-cutting runtime API and should be justified by more than one widget.

### 2. Use `ContextualComponent` for render requests

Loader components should implement `ContextualComponent` and call `TUIContext.requestRender()` when state changes. If no context is attached, state changes still update future renders without failing.

Alternatives considered:

- Require a concrete `TUI` constructor parameter like upstream. This would couple components to the runtime and make tests less direct.
- Require applications to request render manually after every mutation. This is inconvenient and unlike existing contextual autocomplete/editor behavior.

### 3. Model cancellation with a small Scala token

A cancellable loader should expose `cancel()`, `cancelled`/`aborted`, and a token-like view such as `CancellationToken` with `isCancelled`. Escape input should call `cancel()` once and invoke `onCancel`/`onAbort` once.

Alternatives considered:

- Recreate `AbortController`/`AbortSignal`. This copies JS terminology too closely and may imply APIs Scala core cannot provide.
- Use `Future` cancellation. Scala `Future` has no standard cancellation and would add unhelpful semantics.

### 4. Render through existing ANSI/Unicode helpers

The loader line should use ANSI-aware truncation and padding like other components. Indicator frames may be empty to hide the spinner; styles are plain `String => String` functions and may add ANSI escapes.

Alternatives considered:

- Extend `Text` directly. Composition keeps loader behavior explicit and avoids inheriting wrapping semantics that a single-line loader does not need.

## Risks / Trade-offs

- [Risk] Tick-driven animation is less automatic than `pi-tui`'s `setInterval`. → Mitigation: document the deviation and leave `intervalMs` available for future scheduler integration.
- [Risk] Applications may forget to call `tick()`. → Mitigation: provide simple API docs and tests; consider a scheduler follow-up once runtime needs are clearer.
- [Risk] Cancellation callbacks can fire more than once. → Mitigation: make `cancel()` idempotent and test repeated Escape/cancel calls.
- [Risk] Styled spinner/message output can overflow. → Mitigation: use ANSI-aware truncation and add narrow-width tests.
- [Risk] Loader render requests could occur after runtime stop. → Mitigation: `TUIContext.requestRender()` is already coalesced and context clearing on component removal should make late calls harmless; no background work is introduced.

## Migration Plan

1. Add loader option, indicator, and cancellation token public types in `core`.
2. Implement `Loader` as a contextual, tick-driven, width-safe component.
3. Implement `CancellableLoader` or cancellation-enabled loader behavior on top of `Loader`.
4. Add tests for rendering, tick/state lifecycle, render-request integration, and cancellation.
5. Update README, porting notes, post-MVP plan, and loader follow-up docs.
6. Validate formatting, compile, JVM/Native targets, tests, and OpenSpec.

No existing API changes are required. If automatic timers are added later, they should preserve the tick-driven API as the deterministic base.

## Open Questions

- Should the first implementation expose `CancellableLoader` as a subclass/wrapper, or should `Loader` take an optional cancellation mode?
- Should `tick()` return `InputResult`/Boolean to indicate whether a frame advanced, or simply mutate/request render?
- Should a later scheduler capability live on `TUIContext`, or should applications own scheduled calls to `tick()`?
