## Context

`TerminalInputBuffer.flush()` is an explicit parser boundary. StreamTerminal and PosixTerminal currently call it on a 75 ms periodic tick. A read immediately before a tick may be split even when continuation bytes arrive soon; a standalone Escape's latency varies from nearly zero to nearly 75 ms.

## Decision

The shared parser records the last arrival time only while non-paste framing is pending. Its clock-aware, package-private methods expose the remaining idle time and conditionally flush when due. A lone ESC has 35 ms; all other pending non-paste framing, including CSI, SS3, OSC/DCS/APC, incomplete Alt UTF-8, and overlong raw streams, has 75 ms. Each read containing pending input restarts its deadline. Completed input has no timer. Active bracketed paste has no timer, including a partial end marker. Explicit `flush()` remains unconditional for EOF and zero-length stream reads.

The existing `OrderedInputDelivery` lock serializes parser reads, deadline checks, flushes, and generation invalidation. Reads notify its waiting flush worker after parser state changes. The worker sleeps until the nearest deadline, capped at 75 ms to retain existing worker-failure checks without a second timer. It reserves ordered batches under the lock and invokes callbacks outside it. Stop/restart wake waiters and reject stale generations.

## Alternatives

- A faster fixed periodic poll still has phase-dependent early splits and adds idle wakeups.
- A separate timer/monitor duplicates parser locking and introduces a lost-wakeup path.
- Shortening every incomplete sequence to 35 ms would regress fragmented CSI and UTF-8 Alt input.

## Scope and evidence

The 35 ms cutoff is an input-framing tradeoff: Esc followed by a continuation after that idle window is intentionally standalone Esc plus later input. Tests use an injected monotonic clock for exact boundaries and bounded read-gap/stop fixtures for worker behavior. Timing numbers describe parser eligibility, not a hard real-time callback guarantee. No output framing or protocol bytes change.

## Local timing probe

An identical temporary `StreamTerminal` fixture measured callback latency for 16 lone-Escape trials per revision on this Linux host, with first-read delays of 0/20/40/60 ms after worker startup (four trials each). Baseline periodic-flush samples were `116,75,75,75 / 55,55,55,55 / 39,35,35,35 / 15,15,15,15` ms; idle-deadline samples were `64,35,35,35 / 35,35,35,35 / 35,35,35,35 / 35,36,35,35` ms. The first trial in each run includes cold-start scheduling. This shows a stable approximately 35 ms warm cutoff and improves the slower baseline phases, while the old late phase (15 ms) is slower by design. It is a local scheduling observation, not a cross-host latency guarantee. The probe was removed before final validation.
