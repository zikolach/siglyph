## Why

The TUI runtime must not invoke application code while holding lifecycle or terminal-write locks. The current synchronous terminal query API blocks callers, owns timeout policy, and complicates stop and callback ordering. Input publication is also unbounded and startup contains replay logic for backends that synchronously violate the intended `Terminal.start` callback boundary.

## What Changes

- Serialize input handling, protocol completion callbacks, notifications, resize invalidation, and rendering through one runtime-owned drain without invoking application code under lifecycle or terminal-write locks.
- Replace blocking color queries with dependency-free callback APIs returning idempotent cancellation functions and the public covariant `TerminalQueryResult[+A]` ADT: `Success(value)`, `InvalidResponse`, `Stopped`, and `Failed(cause)`.
- Use one wire flight per query protocol. Multiple independently cancellable subscribers share one request, including after all earlier subscribers cancel and before the reply arrives.
- Keep timeout policy application-owned. Core adds no timeout argument, timer, executor, `Future`, custom effect type, or effect integration module.
- Use one lossless ordered ingress FIFO bounded at 4096 events. Publishers apply lifecycle-aware backpressure; stop wakes blocked publishers, rejects later ingress, and discards queued ordinary work.
- Retain accepted query completion callbacks required by stop or failure while preserving the existing rule that stop discards queued ordinary input, notifications, renders, and other ordinary work.
- Require `Terminal.start` to return without synchronously invoking either registered callback on its calling stack. Backends may publish independently from another thread before `start` returns.
- Preserve serialized backend output, query write reservations, resize generation checks, current overlay visibility snapshots, cleanup idempotency, JVM/Scala Native compatibility, and dependency-free core.
- Add an explicit `Cleaning` lifecycle phase between `Stopping` and `Stopped`, with atomic cleanup commitment, finite pre-cleanup and post-restoration query callback sets, and restoration that cannot be postponed by continuous query registration.
- Replace whole-string paste and raw input with bounded byte-stream events, copied `TerminalInputChunk` values, incremental UTF-8 decoding for text consumers, and byte-first backend parsing.
- Publish TUI root structural operations through immutable desired entries and apply committed container mutations and context hooks on the drain owner in publication order.
- Update tests, README, interactive demo, runtime documentation, backend contracts, and OpenSpec requirements.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: Preserve drain-owned rendering, callback isolation, and resize consistency while bounded ingress applies backpressure.
- `terminal-runtime`: Define callback query flights, retained completion ordering, bounded ingress, stop rejection, startup callback separation, and backend contract validation.
- `developer-api`: Define the query result ADT, cancellation and callback ordering, caller-owned timeout, single-flight behavior, and start/output callback separation.

## Impact

- Affected implementation: `core/src/scalatui/core/TUI.scala`, `core/src/scalatui/core/TerminalQueryResult.scala`, terminal input/parser/decoder sources, built-in backends, components, demos, and interop examples.
- Affected public API: timeout-based query methods and whole-string paste/raw input are removed and replaced without overloads, fallback, deprecation, or compatibility methods; TUI root `removeChild` now returns `Unit`.
- Affected tests: portable core behavior and concurrency suites plus VirtualTerminal, StreamTerminal, SttyTerminal, and PosixTerminal contract suites.
- Affected call sites and docs: README query examples, shared interactive demo, interactive smoke notes, porting notes, and relevant resize/runtime wording.
- Affected modules: shared core, terminal backend tests, shared interactive demo, JVM build, and Scala Native build.
- Dependencies: no new dependency or effect integration module.
