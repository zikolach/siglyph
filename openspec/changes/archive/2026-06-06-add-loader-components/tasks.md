## 1. Loader Public API

- [x] 1.1 Add public loader configuration types for indicator frames, interval metadata, message styling, indicator styling, and initial message.
- [x] 1.2 Add public `Loader` component in shared core with Scaladoc documenting tick-driven animation, lifecycle, render requests, width contract, platform scope, and non-goals.
- [x] 1.3 Ensure loader public API is JVM/Native shared-core compatible and introduces no third-party runtime dependencies.

## 2. Loader Rendering and Lifecycle

- [x] 2.1 Implement width-safe loader rendering for indicator plus message, including empty-indicator behavior and ANSI-aware truncation.
- [x] 2.2 Implement `start()`, `stop()`, `running`, and idempotent lifecycle state transitions.
- [x] 2.3 Implement `tick()` to advance frames only while running and request render when context is attached.
- [x] 2.4 Implement message and indicator mutation APIs that update render state, reset frame sequence where appropriate, and request render when context is attached.
- [x] 2.5 Add loader tests for default rendering, empty indicator, ANSI styling, Unicode/narrow widths, lifecycle idempotence, stopped ticks, running ticks, and mutation behavior.

## 3. Cancellation API and CancellableLoader

- [x] 3.1 Add dependency-free cancellation token/state types with Scaladoc explaining semantics and non-goals.
- [x] 3.2 Add `CancellableLoader` or equivalent cancellation-enabled loader component with Escape handling, explicit `cancel()`, cancellation state/token access, and at-most-once callback behavior.
- [x] 3.3 Ensure cancellation requests render through context when attached and remain safe without context.
- [x] 3.4 Add tests for Escape cancellation, explicit cancellation, callback idempotence, token state, ignored non-cancel input, and width-safe inherited rendering.

## 4. Runtime Integration

- [x] 4.1 Verify loaders receive and clear `TUIContext` through existing runtime component attachment/removal paths.
- [x] 4.2 Add or update runtime/context tests to prove loader state changes request coalesced renders without adding a scheduler.
- [x] 4.3 Confirm no loader-owned background work is introduced and terminal shutdown behavior remains unaffected.

## 5. Documentation

- [x] 5.1 Update README with loader and cancellable-loader usage, controls, manual tick behavior, and intentional deviation from `pi-tui` automatic intervals.
- [x] 5.2 Update porting notes with `pi-tui` parity status and deviations from Node `setInterval` and `AbortSignal` semantics.
- [x] 5.3 Update post-MVP plan and loader follow-up docs to mark tick-driven loader components as implemented and capture any remaining automatic scheduler follow-up.
- [x] 5.4 Update interactive smoke docs if loaders are added to a demo; otherwise document that loader behavior is covered by unit/runtime tests only.

## 6. Demo Showcase

- [x] 6.1 Add `Loader` and `CancellableLoader` examples to the shared interactive demo actions so ticks/cancellation visibly update the UI.
- [x] 6.2 Update docs to mention that loader components are shown by the interactive demo rather than the static stream demo.

## 7. Validation

- [x] 7.1 Run `mill scalafmtAll` if formatting changes require it.
- [x] 7.2 Run `mill scalafmtCheck`.
- [x] 7.3 Run `mill scalafixCheck`.
- [x] 7.4 Run `mill quality`.
- [x] 7.5 Run `mill __.compile`.
- [x] 7.6 Run `mill core.test`.
- [x] 7.7 Run `mill interactiveDemo.test` if demo/shared runtime behavior changes affect the interactive demo.
- [x] 7.8 Run `mill interactiveJvmDemo.compile` and `mill interactiveNativeDemo.nativeLink`.
- [x] 7.9 Run `openspec validate --all --strict`.
