# Loader Components Follow-up

`add-loader-components` implemented the first shared-core loader pass as deterministic, tick-driven components.

## Implemented behavior

- `Loader` renders a width-safe optional indicator frame plus message.
- Loader options include frames, interval metadata, style functions, initial message, padding, and leading blank-line behavior.
- `start()` / `stop()` are idempotent and own no background timer.
- `tick()` advances frames only while running and requests render through `TUIContext` when attached.
- `setMessage(...)` and `setIndicator(...)` mutate display state and request render when attached.
- `CancellableLoader` handles Escape, exposes a dependency-free `CancellationToken`, supports explicit `cancel()`, and invokes `onCancel` at most once.

## Upstream behavior still intentionally different

`pi-tui`'s loader owns a Node `setInterval` and `CancellableLoader` exposes JavaScript `AbortSignal`. `scala-tui` does not copy those primitives into shared core. Applications drive ticking themselves or adapt the cancellation token to their chosen async/effect runtime.

## Remaining follow-up: optional scheduler integration

Only propose another loader/runtime change if real applications need runtime-owned ticking. That follow-up should answer:

1. Should scheduling live on `TUIContext`, on a separate scheduler service, or outside the library?
2. Can JVM and Scala Native share the same timer/thread semantics without runtime dependencies?
3. How are scheduled tasks stopped when a component is removed or the TUI stops?
4. How should render coalescing interact with high-frequency ticks?
5. Can the current manual `tick()` API remain the deterministic base for tests?
