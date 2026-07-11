## Context

`TUI` already separates short lifecycle-state operations from application callbacks and serializes backend output through a non-nested write boundary. Its remaining synchronous terminal query API waits for replies and owns timeout policy. Query reply ingress mutates waiting objects directly, application input and notifications use separate unbounded queues, and startup replays callbacks from fixtures that invoke callbacks on the `Terminal.start` call stack.

The revised design keeps the completed drain, renderer, resize, lifecycle, and output work. It replaces only the superseded query and ingress architecture while strengthening the backend start contract.

## Goals / Non-Goals

**Goals:**
- Expose dependency-free callback terminal queries with explicit success, invalid-response, stopped, and failure outcomes.
- Keep one request flight per protocol with independently cancellable ordered subscribers.
- Run query callbacks only on the drain owner, outside lifecycle and write locks, and never concurrently with application callbacks.
- Bound ordinary ingress at 4096 lossless events with lifecycle-aware backpressure.
- Preserve accepted ingress order while running and preserve retained query completions across stop/failure cleanup.
- Require `Terminal.start` callback delivery to originate outside its calling stack while allowing another thread to publish before `start` returns.
- Preserve JVM and Scala Native behavior without dependencies, timers, executors, futures, effects, lock nesting, or backend API shape changes.

**Non-Goals:**
- Add timeout policy to core. Callers cancel through the returned function using their own scheduler when needed.
- Add an effect integration module before concrete demand.
- Drain queued ordinary input, notifications, renders, or ordinary work after stop.
- Add compatibility query overloads or synchronous wrappers.
- Add a dispatch thread or drop input when ingress is full.

## Decisions

### Use a public covariant result ADT

`TerminalQueryResult[+A]` has exactly four semantic outcomes: `Success(value)`, `InvalidResponse`, `Stopped`, and `Failed(cause)`. The query methods accept a completion callback and return the existing `() => Unit` cancellation convention.

Completion may occur before the method returns. Cancellation is idempotent and silent. Cancellation wins when it removes an active subscriber before callback claim; a claimed callback wins and runs exactly once. A callback failure is recorded as a runtime failure, later callbacks still run, and cleanup still completes.

### Keep one immutable flight state per protocol

Each protocol has no flight or one `Reserved` or `Emitted` flight. A flight contains subscribers in subscription order. The first running subscriber reserves one flight and one write reservation. Later subscribers replace the flight with an updated subscriber vector and emit no request.

The first caller emits directly through the terminal write boundary. Successful emission changes `Reserved` to `Emitted` and releases the reservation. Failed emission clears the flight, releases the reservation, retains `Failed` completions, records runtime failure, transitions to stopping, and lets lifecycle ownership perform cleanup.

If every subscriber cancels, the empty wire flight remains until a recognized valid or strict invalid reply, stop, or emission failure. A subscriber added before that reply joins the flight without another request. A late reply clears the empty flight and is consumed without callback.

### Correlate replies without application code on ingress

Ingress recognizes protocol frames under short lifecycle-state operations. A valid matching reply clears its flight and publishes one callback batch containing `Success` for active subscribers in subscription order. A recognized strict malformed reply does the same with `InvalidResponse`. Unrelated input remains an ordinary ordered ingress event.

Ingress invokes no application code. The drain owner claims each subscriber under short lifecycle state immediately before invocation, then invokes outside all runtime locks. This defines cancellation-versus-callback races without duplicate completion.

### Retain query completion work separately from ordinary ingress

Running reply batches enter the ordered ingress FIFO so input, query callbacks, and notifications preserve publication order. Stop discards ordinary queued ingress and ordinary work. Before clearing ingress, it extracts accepted query completion batches into a retained completion FIFO. It also converts active subscribers of an emitted flight to retained `Stopped` completions.

A reserved flight remains until emission resolves. Successful emission after stop produces retained `Stopped`; failed emission produces retained `Failed`. The drain owner executes all retained callbacks before terminal cleanup and `Stopped`, even when one callback throws. No queued ordinary input, notification, render, action, or other ordinary work is drained after stop.

Queries started during `Starting` or `Stopping` emit no request and enqueue `Stopped` through the serialized owner path. A query started in `Stopped` has no owner and may invoke `Stopped` synchronously outside locks. These exact behaviors are public and tested.

### Bound one ordered ingress FIFO

One FIFO contains ordinary input and protocol callback/notification batches. Capacity is exactly 4096 events. One ordinary input is one event and one protocol reply callback batch is one event. Resize remains coalesced and consumes no slot.

A publisher waits in a condition loop while lifecycle is `Starting` or `Running` and the FIFO is full. `wait()` releases the lifecycle lock. Publishers never wait while holding the terminal write lock. Dequeue notifies all after freeing capacity. Stop, failure, startup failure, queue clearing, and reservation release notify all so blocked publishers either retry with capacity or reject because lifecycle no longer accepts ingress.

Starting accepts callbacks delivered independently by backend threads. Startup has no callback-ready flags, startup input buffers, or replay loops. The startup owner completes output, transitions to running, then the drain processes accepted events in order.

### Strengthen the terminal callback boundary

`Terminal.start` keeps its signature but must return without synchronously invoking either registered callback on its call stack. A backend may start another thread that invokes callbacks before `start` returns. Every output-side method remains callback-separated. VirtualTerminal, StreamTerminal, SttyTerminal, and PosixTerminal contract tests cover start and output methods. Contract-violating synchronous-start fixtures are removed or replaced with controlled asynchronous fixtures.

### Preserve drain, output, overlay, resize, and stop invariants

One owner serializes application callbacks, components, renderer state, and cleanup progression. `hasOverlay` remains a volatile latest drain-computed visibility snapshot and never waits. Terminal output remains under the separate non-nested write boundary. Resize generation and dimension checks remain unchanged.

Stop still discards queued ordinary ingress, notification, render, action, and other ordinary work. It retains only already accepted title/progress output and accepted query completion callbacks required before cleanup. Cleanup remains idempotent and reaches `Stopped` only after retained callbacks and terminal restoration.

## Risks / Trade-offs

- [Callbacks can complete before query method return] → Document this and require callers to initialize callback-owned state before subscribing.
- [Core no longer supplies timeout] → Return idempotent cancellation and document caller-owned scheduling; add no timer or effect abstraction.
- [A slow application callback delays later callbacks and ingress] → Preserve the existing single drain owner to guarantee non-concurrency and ordering.
- [Bounded ingress can block backend publisher threads] → Use lossless condition-loop backpressure, wake on dequeue and lifecycle transitions, and never hold the write lock while waiting.
- [Stop races with reserved emission] → Keep write reservations; resolve reserved flights only after emission succeeds or fails.
- [Callback failure occurs during stop] → Record failure, continue every retained callback, then complete cleanup without duplicate completion.
- [A custom backend invokes callbacks on the `start` stack] → Make this a public contract violation and test every built-in backend.
- [A partial migration could leave timeout methods or waiting state] → Remove old signatures and pending waiting objects, search all call sites, and test observable callback behavior instead of reflection.

## Validation Strategy

Portable JVM/Native tests use gates and latches, with timeouts only as failure bounds. They cover all four result outcomes, cancellation races, exactly-once callbacks, single-flight, all-cancelled late replies, caller-owned cancellation, stop against reserved/emitted flights, emission failure, callback failure cleanup, input/query/notification FIFO order, capacity 4096, blocked publisher wake on dequeue, blocked publisher wake/rejection on stop, startup publication, and unchanged ordinary-work discard on stop.

Backend suites verify `start` and every output-side method do not synchronously invoke registered callbacks. Final validation runs the focused core suites, full JVM and Native suites, PTY-wrapped backend suites, all-module compile, formatting, lint, strict OpenSpec validation, and diff whitespace checks. Manual smoke is reported only if performed in this pass.

## Expanded bounded-streaming, Cleaning, and structural revision

### Bounded byte input

`TerminalInputChunk` is an immutable public byte value with `MaxBytes = 4096`. Public factories copy input, require lengths from 1 through 4096, and `toArray` returns a copy. `TerminalInput` replaces whole-string paste and raw cases with `PasteStart`, `PasteChunk`, `PasteEnd`, `RawStart`, `RawChunk`, and `RawEnd`. Raw kinds are `Csi`, `Osc`, `Dcs`, `Apc`, `Ss3`, `Escape`, `Utf8`, and `Bytes`. Raw termination is `Complete`, `Malformed`, `Incomplete`, or `LimitExceeded(terminated)`.

The parser accepts only chunks. It retains at most 4096 typed-candidate bytes, five possible paste-end marker prefix bytes, and three incomplete UTF-8 bytes. Paste markers are omitted and paste data bytes are emitted once in ordered chunks. Every raw byte, including introducers and terminators, is emitted once. At byte 4097 of an incomplete typed protocol the retained candidate is emitted and parsing remains raw until its termination; it is never reclassified as typed. Flush emits exact incomplete raw data and no synthetic stream end is produced when stop clears queued input.

`TerminalUtf8Decoder` incrementally decodes text with at most three carried bytes. It replaces malformed and flushed incomplete text with U+FFFD while the terminal input API retains exact bytes. Text components consume paste chunks incrementally and do not assemble a complete paste. StreamTerminal and PosixTerminal keep 4096-byte reads, parse bytes before string conversion, and release parser locks before callbacks.

Protocol correlation buffers at most 4096 bytes only while a raw stream is eligible for an active reply flight. Valid and strict invalid complete replies are correlated. Unrelated or overlong streams become ordinary input; an overlong stream leaves its query flight active.

### Cleaning lifecycle and query cutoff

Lifecycle is exactly `Stopped -> Starting -> Running -> Stopping -> Cleaning -> Stopped`. `Stopping -> Cleaning` commits atomically only when no startup owner, query write reservation, or cleanup owner remains. The commit detaches a finite ordered set of retained query completions and accepted control output, clears ordinary work, wakes publishers, and seals that pre-cleanup set. Those callbacks and outputs run before restoration. No application callback runs during restoration.

Queries registered during `Cleaning` join a post-restoration list. Restoration completion atomically detaches one finite list. Those callbacks run outside locks, after restoration, and before the final `Stopped` commit. Registrations after that cutoff use stopped behavior and cannot extend cleanup. Queries linearized in `Stopping` before cleanup commit remain in the pre-cleanup set.

### Drain-owned root structure

TUI stores immutable desired child entries with unique IDs and a drain-owned committed entry vector. `addChild`, `removeChild`, and `clear` update desired state under lifecycle state and publish ordered `Add(entry)`, `Remove(entryId)`, and `Clear` work. `children` returns the latest accepted desired components without waiting. Duplicate component instances are valid.

The drain updates committed state before invoking hooks outside locks. Context attaches on committed count `0 -> 1` and detaches on `1 -> 0`. An add removed before draining still commits attach then detach in order. Clear removes occurrences committed at its operation point; later adds survive. Removal does not change focus. Stopped, Starting, and Running accept publication; Stopping and Cleaning reject silently. Stop discards uncommitted structural work and resets desired state to committed state.

Hook failure does not roll back the committed mutation. It records runtime failure, discards later ordinary work, and proceeds through cleanup. Structural hooks are serialized with input, rendering, and all application callbacks.

## Final review: exact protocol ingress slots and streamed Editor paste transactions

Protocol ingress classification uses three explicit outcomes. `Consumed` commits correlation-only state and consumes no FIFO slot. `Blocked` performs no state or query-flight mutation when output requires a slot but capacity is unavailable. `Publish(first, remaining)` atomically commits correlation or query state with the first slot-producing event. Replay stores at most 4097 remaining events in one ingress-owned continuation, derived from the maximum 4098-event replay of `RawStart`, 4096 retained one-byte `RawChunk` events, and `RawEnd`. The commit admits as many events as FIFO capacity allows, then the publisher claims or joins the synchronous drain without waiting to enqueue the remainder. Each dequeue refills freed FIFO slots from the continuation before later ingress may publish. Stop or failure clears the continuation with ordinary ingress and wakes blocked publishers. A recognized protocol completion or notification is one batch slot regardless of subscriber count. Unrelated and overlong raw replay preserves every stream event and leaves unrelated active query flights unchanged.

Editor bracketed paste is one transaction from `PasteStart` through `PasteEnd`. The Editor owns one incremental UTF-8 decoder, CR carry, bounded text blocks, aggregate `Long` line and grapheme metrics, and the pre-paste buffer snapshot. Unicode exposes an incremental grapheme counter backed by the existing break rules. Small paste blocks are inserted through a package-scoped EditorBuffer chunk API without joining the whole paste. Large paste creates one normalized immutable whole string only for marker expansion storage. Completion creates one undo entry, invokes `onChange` once, refreshes autocomplete once, and requests one render. Empty paste is a no-op. A non-paste input first commits any unfinished accepted paste content.
