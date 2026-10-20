# Design

## Context

See proposal.md for motivation. `KittyKeyboardProtocolTerminal` exposes request, response acceptance, state, and disable operations. Both interactive backends implement these hooks. The shared parser already recognizes modified Enter encodings.

`TUI` owns bounded raw reply correlation and serializes output through `RuntimeTerminalServices`. It currently recognizes color, cell-size, and cursor replies, but has no keyboard reply branch. Backend response acceptance writes the keyboard enable sequence. Both backends retain a cleanup obligation before that write, which supports recovery after partial output failure.

## Goals / Non-goals

Goals are automatic negotiation during TUI startup, exact reply recognition, and balanced cleanup under stop and failure races. Preserve shared JVM and Native behavior and the runtime's output ownership.

Non-goals are adding keyboard protocols, changing editor bindings, guessing modifiers from ordinary Return, enabling release-event reporting, publishing a release, or editing Sigma. Direct backend consumers retain the explicit negotiation hooks.

## Decisions

### D1. TUI owns automatic orchestration

After backend startup, TUI requests negotiation through the optional capability under the existing terminal write boundary. TUI handles replies through ordered runtime work. Response acceptance runs outside the lifecycle lock and inside the terminal write boundary.

This follows existing runtime query ownership. Backend-local automatic interception would duplicate correlation logic and could write protocol output concurrently with a rendered frame. Application-local orchestration would duplicate library behavior in Sigma and other consumers.

### D2. Reuse bounded raw correlation

Recognize only complete exact `ESC[?<decimal flags>u` frames with an integer flags value. Use existing bounded raw correlation rather than scanning arbitrary input strings. Consume valid keyboard replies on capable backends even when stale or duplicate, but let the backend's deadline and state decide whether to enable. Preserve malformed, incomplete, oversized, unrelated, and paste input through existing paths.

Record response arrival time when ingress accepts the complete reply. Check both the negotiation deadline and session lifecycle before enable work. Queue protocol work in FIFO order so later keys cannot overtake it. Avoid invoking output hooks under the ingress lifecycle lock.

### D3. Preserve conservative flag selection

Keep the existing request `ESC[?u` and push `ESC[>1u`. Flag 1 requests disambiguation sufficient for Shift+Enter. A response of zero flags is valid because it describes the terminal's current keyboard mode. Preserve the public state representation and existing explicit hook signatures.

No response means legacy parsing continues. No new timeout thread or dependency is needed. Do not add modifyOtherKeys fallback in this fix.

### D4. Separate pending cancellation from active pop

A query does not push keyboard mode. Disable therefore cancels Pending without emitting a pop. The backend retains the pop obligation before attempting enable and clears it only after successful disable output. An active explicit hook invocation continues to support stop cleanup even without TUI ownership.

During TUI cleanup, cancel outstanding negotiation before backend stop. Complete backend stop before leaving alternate screen so that the backend retries a failed pop on the screen where it pushed the mode. The Kitty keyboard protocol defines independent main-screen and alternate-screen stacks. Keep cancellation and mode cleanup idempotent. Existing backend retry obligations remain authoritative after output failure. Ensure concurrent response handling and shutdown cannot enable a mode after restoration.

Protocol reference: https://sw.kovidgoyal.net/kitty/keyboard-protocol/#progressive-enhancement

### D5. Retain screen ownership until cleanup succeeds

The user approved O2-1. If keyboard cleanup remains incomplete after disable and backend stop, TUI keeps the alternate screen active. A later `TUI.stop()` retries restoration. TUI rejects restart until restoration completes. Successful cleanup operations and completed callback batches must not run again during a retry. TUI retains single-owner cleanup and finite callback cutoffs.

Use the existing `Cleaning` lifecycle state for incomplete restoration. Track only the remaining backend stop or screen exit stage and one coalesced explicit retry request. A successful backend stop is the conservative boundary before screen exit because a thrown stop can contain several backend obligation failures. Preserve the renderer's exit obligation until exit output succeeds. Retry only the retained stage. One-time teardown and completed callback batches remain complete.

Seal the finite Cleaning callback cutoff after the first restoration attempt, including a failed attempt. Keep all callbacks under the same drain owner. An explicit stop can request one attempt while cleanup is idle. Concurrent calls during an active attempt coalesce. Automatic input, resize, render, and failure work cannot trigger retry loops. Prioritize an admitted retry over later stopped-query work.

If restoration fails, `run()` waits for that attempt and its finite completion set, then throws the recorded failure while restoration remains pending. Preserve the original startup or runtime failure and attach cleanup failures. The internal shutdown path in `run()` must not silently retry failed restoration. A successful explicit retry returns without rethrowing earlier attempt failures.

This approach keeps screen ownership in TUI. It avoids backend-driven screen switches. On cleanup failure, the caller must retry `TUI.stop()` before normal shell display returns. The runtime must report failure without spinning or waiting indefinitely for output recovery.

## Risks / Trade-offs

- A terminal or Herdr may not answer the query. Preserve legacy input and document that Siglyph cannot recover a Shift modifier already removed upstream.
- A delayed complete reply could wait behind application work. Retain its arrival time and test deadline boundaries deterministically.
- Startup, reply handling, and stop can overlap. Use existing owner and write serialization, and test stop-before-reply and enable-output failure.
- Backend tests require interactive TTY access. Use virtual capability fakes for shared behavior and run backend suites through a pseudo-terminal when required.
- Automated encoded input does not establish physical Ghostty behavior. Record the remaining physical Ghostty-through-Herdr smoke check explicitly.

## Migration plan

Applications using TUI with either interactive backend receive negotiation automatically after upgrading Siglyph. StreamTerminal remains unchanged. Remove any consumer-side duplicate negotiation when migrating a consumer that already performs it. Reverting the scoped implementation restores the previous manual-hook behavior.
