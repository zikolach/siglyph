# Spec delta

## MODIFIED Requirements

### Requirement: Kitty keyboard protocol negotiation
The TUI SHALL automatically request conservative Kitty keyboard negotiation during startup on backends with keyboard negotiation support. The runtime SHALL enable disambiguation only after a complete valid response arrives within the negotiation deadline. Unsupported, stale, or mismatched negotiation SHALL preserve existing input parsing.

#### Scenario: Successful negotiation enables advanced key parsing
- **WHEN** a terminal responds to keyboard protocol negotiation with a valid matching response
- **THEN** the backend records that Kitty keyboard protocol is active and parses subsequent CSI-u keyboard events accordingly

#### Scenario: Stale negotiation response is ignored
- **WHEN** a delayed or mismatched keyboard protocol response is received after negotiation has timed out or changed
- **THEN** the backend ignores that response and does not falsely mark Kitty keyboard protocol active

#### Scenario: Unsupported terminal preserves basic input
- **WHEN** a terminal does not support Kitty keyboard protocol negotiation
- **THEN** basic arrows, printable characters, control keys, and bracketed paste continue to work through existing parsing paths

#### Scenario: TUI requests negotiation automatically
- **WHEN** a TUI starts with a keyboard-negotiation-capable backend
- **THEN** the runtime emits `ESC[?u` without requiring application protocol code and without waiting for a response before startup can complete

#### Scenario: Fragmented response remains protocol input
- **WHEN** a complete valid `ESC[?0u` response arrives across multiple reads within the negotiation deadline
- **THEN** the runtime consumes that reply, enables disambiguation with `ESC[>1u` once, and keeps the reply out of focused-component input

#### Scenario: Negotiated Shift Enter retains Shift
- **WHEN** negotiation succeeds and the terminal sends `ESC[13;2u` followed by ordinary Return
- **THEN** the focused component receives Shift+Enter followed by unmodified Enter in that order

#### Scenario: Duplicate response does not push again
- **WHEN** a terminal sends another valid keyboard negotiation response after the runtime has enabled disambiguation
- **THEN** the runtime consumes the response without pushing another keyboard mode

#### Scenario: Unsupported backend emits no negotiation
- **WHEN** a TUI starts with a backend without keyboard negotiation support
- **THEN** the runtime emits no keyboard negotiation request and preserves existing input behavior

#### Scenario: Malformed and unrelated input remain lossless
- **WHEN** a raw input sequence is incomplete, malformed, or unrelated to keyboard negotiation
- **THEN** the runtime preserves that sequence through its existing bounded input handling without enabling keyboard mode

### Requirement: Terminal abstraction
The library SHALL define terminal lifecycle, input, resize, dimensions, output, cursor, clear, and optional capability operations. `Terminal.start` SHALL return without synchronously invoking either registered callback on its calling stack. Output-side methods SHALL also remain callback-separated.

#### Scenario: Backend publishes during startup independently
- **WHEN** a backend thread observes input or resize before `start` returns
- **THEN** it may publish from that independent thread and the runtime accepts the event during `Starting`

#### Scenario: Backend does not publish on start stack
- **WHEN** `start` is called
- **THEN** neither registered callback is invoked synchronously on that call stack

#### Scenario: Backend restores terminal state
- **WHEN** lifecycle reaches `Stopped` or `run()` returns
- **THEN** terminal mode and cursor restoration have completed; TUI retries failed restoration only through an explicit stop and does not repeat completed teardown

### Requirement: Safe interactive shutdown
The TUI SHALL use single-owner cleanup and SHALL discard queued ordinary work after stop while retaining accepted control output and required query completion callbacks. If restoration fails, TUI SHALL retain incomplete restoration for a later explicit stop and SHALL reject restart until restoration completes.

#### Scenario: Uncontended stop restores synchronously
- **WHEN** no startup, drain, or query reservation owner is active
- **THEN** stop invokes retained completions and attempts terminal restoration synchronously; if restoration fails, stop reports the failure and retains incomplete restoration for explicit retry

#### Scenario: Deferred stop preserves only retained work
- **WHEN** stop occurs while another owner or query reservation is active
- **THEN** later ordinary work is rejected, queued ordinary work is discarded, and the owner invokes retained query callbacks and accepted title/progress output before cleanup

### Requirement: Opt-in alternate-screen lifecycle
The TUI runtime SHALL support an opt-in alternate-screen mode that enters the terminal alternate screen from startup until backend stop and screen exit succeed, while preserving normal-screen mode as the default.

#### Scenario: Default mode does not enter alternate screen
- **WHEN** an application constructs and starts a `TUI` without configuring alternate-screen mode
- **THEN** the terminal output does not contain `ESC[?1049h` or `ESC[?1049l`

#### Scenario: Alternate-screen mode enters before first frame
- **WHEN** an application starts a `TUI` configured for alternate-screen mode
- **THEN** the runtime emits `ESC[?1049h` before writing the first rendered frame

#### Scenario: Alternate-screen mode exits on stop
- **WHEN** an application stops a `TUI` that entered alternate-screen mode
- **THEN** after backend stop succeeds, the runtime emits `ESC[?1049l` before returning control to the shell; if backend stop fails, the runtime retains the alternate screen for explicit stop retry

#### Scenario: Alternate-screen mode skips normal-screen cursor parking
- **WHEN** an application stops a `TUI` that entered alternate-screen mode after rendering content
- **THEN** after backend stop succeeds, the runtime exits alternate screen without writing the normal-screen cursor-below-content parking sequence

#### Scenario: Alternate-screen cleanup runs after runtime failure
- **WHEN** a runtime failure occurs after alternate-screen mode has been entered
- **THEN** the runtime attempts autowrap restoration and cursor display, then stops the backend before exiting alternate screen; if backend stop fails, it retains the screen for explicit stop retry

#### Scenario: Alternate-screen lifecycle is idempotent
- **WHEN** `stop()` is called more than once after an alternate-screen `TUI` has been started
- **THEN** the runtime does not repeat successful screen exit; if exit output fails, a later explicit stop retries only screen exit

### Requirement: Cleanup has an explicit finite Cleaning phase
The lifecycle SHALL progress `Stopped -> Starting -> Running -> Stopping -> Cleaning -> Stopped`, commit cleanup atomically after startup and query-write ownership ends, and prevent query registration from postponing restoration. After failed restoration, the lifecycle SHALL remain Cleaning until an explicit stop completes restoration. The runtime SHALL seal the finite callback cutoff after the first attempt, including failure.

#### Scenario: Stopping query precedes cleanup commit
- **WHEN** a query registration linearizes in `Stopping` before cleanup commit
- **THEN** its `Stopped` completion belongs to the finite pre-cleanup set and runs before restoration

#### Scenario: Cleaning query follows restoration
- **WHEN** a query registers after cleanup commit and before restoration cutoff
- **THEN** its `Stopped` completion runs after the first restoration attempt and cannot delay that attempt

#### Scenario: Continuous registration cannot extend cleanup
- **WHEN** queries register continuously during Cleaning
- **THEN** the first restoration attempt detaches one finite post-restoration list and later registrations use serialized stopped behavior; lifecycle reaches Stopped only after backend stop and screen exit succeed

### Requirement: Cleaning callback sets are finite and globally serialized
Cleaning SHALL have one finite pre-restoration callback set and one finite post-restoration callback set, SHALL serialize both through the single owner, and SHALL prevent later query registration from overlapping callbacks or extending restoration.

#### Scenario: Late query registers during detached callbacks
- **WHILE** detached Cleaning callbacks execute
- **WHEN** a late query registers
- **THEN** the runtime prevents concurrent application callback execution and the registration cannot extend restoration

#### Scenario: Restoration seals post-restoration work
- **WHEN** the first restoration attempt completes or fails and detaches the post-restoration callback set
- **THEN** that finite set runs serially and later registrations use stopped behavior

### Requirement: Unexpected terminal worker failure propagation
Interactive and stream terminal backends SHALL report each unexpected input-reader, fragment-flush, and resize-worker failure to the owning TUI lifecycle at most once per worker and active generation. Independent worker failures SHALL remain reportable. Expected EOF, interruption caused by stop, and stale generation termination SHALL remain normal lifecycle outcomes.

#### Scenario: Input reader fails while running
- **WHEN** the active backend input reader throws an unexpected failure
- **THEN** the TUI records that failure, wakes `run()`, attempts idempotent cleanup, and rethrows the recorded failure from `run()`; successful restoration reaches `Stopped`, while failed backend stop or screen exit remains `Cleaning` for explicit stop retry

#### Scenario: Flush worker fails while running
- **WHEN** the active incomplete-input flush worker throws an unexpected failure
- **THEN** the backend reports it to the TUI instead of silently ending fragment flushing

#### Scenario: Resize worker fails while running
- **WHEN** the active resize worker can no longer poll or publish dimensions because of an unexpected failure
- **THEN** the backend reports it to the TUI instead of leaving a logically running session without resize delivery

#### Scenario: Stop interruption is not a failure
- **WHEN** explicit stop interrupts an active backend worker
- **THEN** the worker terminates without publishing a runtime failure

#### Scenario: First failure wins
- **WHEN** more than one backend worker reports failure during the same lifecycle
- **THEN** the TUI preserves one primary runtime failure, attaches later failures without duplicating completed cleanup, and wakes all lifecycle waiters

## ADDED Requirements

### Requirement: Balanced keyboard negotiation cleanup
The runtime SHALL cancel pending keyboard negotiation on shutdown. Interactive backends SHALL pop only keyboard modes they attempted to push, retain failed cleanup for retry, and reject restart while cleanup remains incomplete. Runtime keyboard protocol output SHALL use the serialized terminal write boundary.

#### Scenario: Stop before response does not pop
- **WHEN** shutdown occurs before any keyboard mode push was attempted
- **THEN** the runtime cancels negotiation without emitting `ESC[<u` and later responses cannot enable keyboard mode

#### Scenario: Active mode is restored once
- **WHEN** shutdown follows a successful keyboard mode push
- **THEN** the backend emits `ESC[<u` once for the completed cleanup obligation and repeated stop emits no additional pop

#### Scenario: Failed enable retains cleanup
- **WHEN** a keyboard enable write fails after output may have reached the terminal
- **THEN** shutdown attempts the matching pop and retains any failed pop obligation for retry

#### Scenario: Startup or runtime failure restores mode
- **WHEN** startup or runtime fails after a keyboard mode push was attempted
- **THEN** the runtime attempts keyboard restoration and all other terminal cleanup obligations

#### Scenario: Stop wins before response handling
- **WHEN** shutdown invalidates a session before a queued negotiation response is handled
- **THEN** the runtime emits no keyboard enable for that response

#### Scenario: Protocol output cannot interleave a frame
- **WHEN** the runtime accepts a keyboard response while another thread writes a frame
- **THEN** the keyboard enable output remains outside that frame's serialized write sequence

#### Scenario: Alternate-screen cleanup restores the correct stack
- **WHEN** the runtime enables keyboard disambiguation on the alternate screen
- **THEN** it attempts the matching pop and backend cleanup retries before leaving the alternate screen, without popping the main-screen keyboard stack

#### Scenario: Repeated keyboard cleanup failure retains screen ownership
- **WHEN** both keyboard disable and backend stop fail to restore alternate-screen keyboard mode
- **THEN** TUI keeps the alternate screen active, reports cleanup failure, and rejects restart

#### Scenario: Stop retries incomplete restoration
- **WHEN** output recovers and the caller retries `TUI.stop()` after failed keyboard cleanup
- **THEN** TUI retries incomplete restoration before leaving the alternate screen, preserves the main-screen keyboard stack, and does not repeat successful cleanup or completed callbacks

#### Scenario: Cleanup callbacks remain finite across restoration retries
- **WHEN** the first restoration attempt fails and applications continue registering queries or appends
- **THEN** TUI seals the first attempt's finite completion set, serializes later lifecycle-rejection callbacks, and retries restoration without repeating completed callbacks

#### Scenario: Run reports failed restoration without waiting for recovery
- **WHEN** a restoration attempt fails during `run()`
- **THEN** `run()` waits for the active attempt and its finite completion set, throws the recorded failure, and leaves restoration available for explicit `TUI.stop()` retry without an implicit retry loop

#### Scenario: Retry requests coalesce
- **WHEN** callers request stop concurrently during an active restoration attempt
- **THEN** TUI keeps one restoration owner and those requests do not start additional attempts

#### Scenario: Screen exit failure retains exit only
- **WHEN** backend stop succeeds but alternate-screen exit output fails
- **THEN** the next explicit stop retries screen exit without repeating backend stop or keyboard pop
