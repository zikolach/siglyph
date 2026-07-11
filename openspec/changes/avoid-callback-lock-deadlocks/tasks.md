## 1. Deterministic Regression Tests

- [x] 1.1 Add a barrier-based render lock-inversion test that fails when component rendering holds the TUI lifecycle lock while a concurrent application-locked thread flushes a render.
- [x] 1.2 Add a barrier-based global and focused input lock-inversion test that verifies callbacks run without the lifecycle lock and later input remains ordered and accepted.
- [x] 1.3 Add a gated repeated width-and-height resize test that rejects frames observed stale at the pre-commit check, redraws for later resize invalidation, preserves differential-render baselines, and remains interactive.
- [x] 1.4 Add reentrant and concurrent `flushRender()` tests for synchronous uncontended draining, non-recursive follow-up work, coalescing, and non-blocking active-drain behavior.
- [x] 1.5 Add application-hook isolation tests for overlay visibility, context attachment and detachment, focus setters, runtime notifications, and callback failure cleanup.
- [x] 1.6 Add deterministic startup-owner, uncontended, reentrant, and concurrent stop tests that verify documented return behavior, cancellation of later startup work, single-owner cleanup, idempotent restoration, and `run()` waiting for `Stopped` after deferred final cleanup.
- [x] 1.7 Add raw output-order tests for startup, frame, protocol, title, progress, cursor, and cleanup writes, including accepted title/progress before later stop, so no active-lifecycle TUI-owned output is concurrent or silently discarded.
- [x] 1.8 Add the original deterministic JVM and Scala Native protocol-query regression coverage for reply correlation, output serialization, stop ordering, and failed request emission. Callback-query replacement coverage is tracked in section 5.
- [x] 1.9 Add JVM and Scala Native contract tests proving built-in terminal output, cursor, title, progress, and protocol operations do not synchronously invoke registered input or resize callbacks, using automated PTY fixtures where interactive startup is required.
- [x] 1.10 Add a `coreNative.test` Scala Native test target that executes the canonical portable deterministic TUI concurrency suite with the existing test-only MUnit dependency.
- [x] 1.11 Add a `terminalNative.test` Scala Native test target for Native backend contracts with the existing test-only MUnit dependency, and keep JVM backend coverage in `terminalJvm.test`.

## 2. Runtime Work Serialization

- [x] 2.1 Introduce the minimal runtime-owned queued input, pending render intent, and drain-ownership state protected by short `lifecycleLock` sections.
- [x] 2.2 Route terminal input, resize invalidation, and explicit flush operations through one synchronous work drain while preserving input order and render-request coalescing.
- [x] 2.3 Move all application-controlled invocations, including input handlers, rendering, context hooks, focus setters, overlay predicates, and notifications, outside `lifecycleLock` without allowing concurrent component or renderer execution.
- [x] 2.4 Preserve reentrant focus, overlay, exit, protocol-response, and render requests by publishing follow-up state or work through the same runtime boundary.
- [x] 2.5 Add a backend-edge terminal write boundary with a strict no-lifecycle-lock and no-application-callback acquisition rule, and route startup, frame, protocol, title, progress, cursor, and cleanup operations through it.
- [x] 2.6 Preserve drain ordering for scheduled output and ensure every title/progress operation that returns true executes before any later cleanup.
- [x] 2.7 Implement the reusable query write-reservation, direct terminal write-boundary emission, reservation release, and no-application-code reply-ingress foundations. Callback flights and completion ownership are tracked in section 5.

## 3. Frame and Lifecycle Safety

- [x] 3.1 Track observed resize generations, snapshot positive dimensions for each render attempt, and recheck generation and dimensions immediately before terminal output or differential-state mutation.
- [x] 3.2 Discard frames known to be stale at the pre-commit check, preserve the last committed renderer baseline, and force a redraw for later observed resizes in normal-screen and alternate-screen modes.
- [x] 3.3 Implement `Starting`, `Running`, `Stopping`, and `Stopped` ownership transitions so startup ownership precedes output, concurrent startup stop cancels later startup work, uncontended running stop is synchronous, reentrant or concurrent stop remains non-waiting with single-owner idempotent cleanup, and `run()` waits for `Stopped` before returning.
- [x] 3.4 Integrate runtime failures with drain ownership so later ordinary work is rejected and the active owner performs ordered terminal cleanup.
- [x] 3.5 Audit every application-controlled invocation, protocol reply ingress path, and TUI-owned terminal operation in `TUI`, then add focused coverage for any remaining callback-under-lock, query self-stall, or concurrent-output path.

## 4. Public Contract and Validation

- [x] 4.1 Update `Component`, `TUIContext`, `Terminal`, optional terminal output capabilities, global input listener, overlay/focus hooks, `flushRender()`, and `stop()` Scaladoc with callback isolation, backend callback separation, and uncontended, reentrant, and concurrent scheduling semantics. Revised callback-query and start contracts are tracked in section 5.
- [x] 4.2 Update runtime and porting documentation with the deadlock-prevention model, resize liveness guarantees, and explicit non-goals.
- [x] 4.3 Run `mill core.test.testOnly scalatui.core.TUISuite`, `mill core.test`, `mill coreNative.test`, `mill terminalJvm.test`, `mill terminalNative.test`, `mill __.compile`, `mill scalafmtCheck`, and `mill scalafixCheck`.
- [x] 4.4 Run `openspec validate --all --strict` and complete manual JVM and Scala Native resize smoke coverage for repeated font-size-like width and height changes during animated or asynchronous updates.

## 5. Approved Callback Query and Bounded Ingress Revision

- [x] 5.1 Revise proposal, design, affected specs, and tasks to remove synchronous waiting and timeout ownership and specify the approved callback ADT, cancellation, single-flight, late reply, bounded ingress, startup callback separation, and stop/failure ordering.
- [x] 5.2 Add public covariant `TerminalQueryResult[+A]` and replace both timeout-based query methods with callback methods returning idempotent silent cancellation, without overloads, compatibility methods, timers, executors, futures, effects, or dependencies.
- [x] 5.3 Implement immutable reserved/emitted single-flight state per protocol, ordered independently cancellable subscribers, strict invalid replies, empty-flight late reply consumption, direct reserved emission, and exact `Starting`/`Stopping`/`Stopped` behavior.
- [x] 5.4 Replace separate input and notification queues with one ordered ingress FIFO bounded at 4096 events, condition-loop backpressure, wake on dequeue and lifecycle rejection, coalesced slot-free resize, and asynchronous startup publication without replay accommodation.
- [x] 5.5 Run query callbacks only on the drain owner outside lifecycle/write locks, claim each callback against cancellation, retain required query completions across stop/failure, continue after callback failure, and preserve discard of all queued ordinary work after stop.
- [x] 5.6 Replace blocking, reflection-based, and synchronous-start tests with portable observable callback and controlled asynchronous tests covering all four outcomes, races, exactly once, single-flight, late reply, reserved/emitted stop, emission/callback failure, FIFO order, capacity 4096, publisher wake/rejection, and unchanged stop discard behavior.
- [x] 5.7 Strengthen `Terminal.start` Scaladoc and VirtualTerminal, StreamTerminal, SttyTerminal, and PosixTerminal contract tests for start-stack and output-side callback separation on JVM and Scala Native.
- [x] 5.8 Update README, interactive demo, interactive smoke notes, porting notes, and relevant terminal resize note wording for direct callbacks, ordering, cancellation, single-flight, caller-owned timeout, bounded backpressure, and strengthened start contract.
- [x] 5.9 Run focused suites, then all required JVM, Scala Native, PTY-wrapped backend, compile, formatting, lint, strict OpenSpec, and diff checks. Record manual smoke as not run unless performed in this pass.

Manual JVM and Scala Native resize smoke was not run during the independent validation pass on 2026-07-11.

## 6. Expanded bounded streaming, Cleaning, and structural revision

- [x] 6.1 Revise proposal, design, terminal-runtime, developer-api, component-rendering, and tasks artifacts with the approved expanded architecture.
- [x] 6.2 Add copied bounded `TerminalInputChunk`, streaming paste/raw ADTs, and incremental `TerminalUtf8Decoder`; remove whole-string input APIs.
- [x] 6.3 Replace terminal parsing and buffering with byte-bounded streaming behavior, exact raw reconstruction, typed protocol preservation, and exhaustive portable tests.
- [x] 6.4 Update JVM and Native backends to parse 4096-byte reads before UTF-8 conversion and invoke callbacks after releasing parser locks.
- [x] 6.5 Update protocol correlation for bounded eligible raw replies, unrelated and overlong streams, and active-flight preservation.
- [x] 6.6 Implement explicit `Cleaning`, atomic cleanup commitment, finite query cutoffs, restoration ordering, and lifecycle race tests.
- [x] 6.7 Implement desired and committed root child entries with drain-owned structural operations, duplicate identity semantics, hook failure cleanup, and concurrency tests.
- [x] 6.8 Update Editor, Input, SelectList, SettingsList, demos, KeyTester, pattern matches, README, interop examples, Scaladoc, and runtime documentation without whole-paste reassembly.
- [x] 6.9 Run all focused and final JVM, Native, backend, compile, format, lint, OpenSpec, and diff validation; leave manual smoke unchecked unless performed in this pass.

## 7. Final review blockers

- [x] 7.1 Correct protocol correlation classification and bounded FIFO publication for zero-slot fragments, one-slot recognized batches, and one-slot-per-event raw replay.
- [x] 7.2 Add portable TUI capacity tests for correlation fragments, protocol completion batches, and exact overlong or unrelated replay ordering.
- [x] 7.3 Implement one Editor streamed-paste transaction with incremental newline and grapheme accounting plus minimal EditorBuffer chunk insertion.
- [x] 7.4 Add shared parser-to-Editor, Editor, EditorBuffer, Unicode-boundary, marker, submit, expansion, callback, render, undo, empty, and interruption tests.
- [x] 7.5 Update README, porting notes, and smoke claims for exact ingress slot and whole-paste behavior.
- [x] 7.6 Run focused, full JVM, full Native, PTY backend, compile, format, lint, strict OpenSpec, and diff validation. Keep manual smoke status unchanged unless run in this pass.

## 8. Final maximal replay self-deadlock

- [x] 8.1 Replace all-at-once replay publication with one fixed bounded continuation that refills FIFO slots on dequeue and prevents later ingress overtaking.
- [x] 8.2 Add shared portable maximal replay and stop-discard regressions, including exact 4096 one-byte chunk order and blocked-publisher wakeup.
- [x] 8.3 Update design and terminal-runtime requirements for replay continuation ownership, bounds, linearization, ordering, and stop behavior.
- [x] 8.4 Run focused, full JVM, full Native, PTY backend, compile, format, lint, strict OpenSpec, and diff validation. Keep manual smoke status unchanged unless run in this pass.
