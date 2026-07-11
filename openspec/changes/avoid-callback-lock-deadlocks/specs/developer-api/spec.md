## ADDED Requirements

### Requirement: Public callback isolation contract
The public TUI runtime SHALL invoke application-controlled code without holding its lifecycle state lock or terminal write lock, and SHALL serialize application callbacks through one drain owner.

#### Scenario: Callback can request follow-up work
- **WHEN** an input, render, notification, overlay, focus, context, or query callback requests runtime work
- **THEN** the request does not recurse, wait for itself, or run concurrently with another application callback

#### Scenario: Callback failure uses runtime cleanup
- **WHEN** application-controlled code throws
- **THEN** the runtime records the failure, continues required query completions, and performs idempotent cleanup

### Requirement: Public callback terminal query API
The TUI SHALL expose `queryTerminalBackgroundColor(onComplete: TerminalQueryResult[RgbColor] => Unit): () => Unit` and `queryTerminalColorScheme(onComplete: TerminalQueryResult[TerminalColorScheme] => Unit): () => Unit`, and SHALL expose covariant `TerminalQueryResult[+A]` with exactly `Success(value)`, `InvalidResponse`, `Stopped`, and `Failed(cause)` outcomes.

#### Scenario: Completion can precede method return
- **WHEN** completion is available through a safe serialized path or the runtime is already stopped
- **THEN** the callback may run before the query method returns and runs outside runtime locks

#### Scenario: Cancellation is idempotent and silent
- **WHEN** the returned cancellation function is called one or more times before callback claim
- **THEN** the subscriber is removed once and no callback is invoked for cancellation

#### Scenario: Callback claim wins cancellation race
- **WHEN** the drain claims a subscriber before cancellation removes it
- **THEN** the callback runs exactly once

#### Scenario: Cancellation wins callback race
- **WHEN** cancellation removes a subscriber before the drain claims it
- **THEN** the callback never runs

#### Scenario: Caller owns timeout
- **WHEN** an application needs a query timeout
- **THEN** it schedules and invokes cancellation outside core because core exposes no timeout argument, timer, executor, `Future`, or effect type

### Requirement: Public query single-flight contract
The TUI SHALL maintain one terminal request flight per query protocol and SHALL preserve subscriber order.

#### Scenario: Subscribers share an existing flight
- **WHILE** a reserved or emitted flight exists
- **WHEN** another caller subscribes
- **THEN** the runtime adds that independently cancellable subscriber and emits no second terminal request

#### Scenario: Matching valid reply completes subscribers
- **WHEN** a valid matching reply arrives
- **THEN** the runtime clears the flight and queues `Success` exactly once for each uncancelled subscriber in subscription order

#### Scenario: Strict invalid reply completes subscribers
- **WHEN** a recognized matching reply frame has an invalid payload
- **THEN** the runtime clears the flight and queues `InvalidResponse` exactly once for each uncancelled subscriber

#### Scenario: Empty flight remains on wire
- **WHEN** every current subscriber cancels before a reply
- **THEN** the wire flight remains and a later subscriber joins it without a second request

#### Scenario: Late reply for empty flight is consumed
- **WHEN** a valid or strict invalid reply arrives for an empty flight
- **THEN** the runtime clears and consumes the flight without invoking a callback

### Requirement: Public query lifecycle and failure contract
Query completion callbacks SHALL run only on the drain owner while an owner exists, outside runtime locks, and before cleanup when retained by stop or failure.

#### Scenario: Query outside running lifecycle is stopped
- **WHEN** a query starts during `Starting`, `Stopping`, or `Stopped`
- **THEN** it emits no request and completes with `Stopped` through a serialized owner path, except that `Stopped` may invoke synchronously outside locks because no owner exists

#### Scenario: Stop completes emitted flight
- **WHEN** stop observes an emitted flight with active subscribers
- **THEN** it retains ordered `Stopped` completions and invokes them before cleanup and `Stopped`

#### Scenario: Stop waits for reserved emission outcome
- **WHEN** stop observes a reserved flight
- **THEN** successful emission retains `Stopped`, failed emission retains `Failed`, and cleanup cannot overtake the reservation

#### Scenario: Emission failure completes and cleans up
- **WHEN** terminal request emission fails
- **THEN** the runtime clears the flight, releases its reservation, retains `Failed`, records runtime failure, continues callbacks despite callback failures, cleans up, and reaches `Stopped`

#### Scenario: Query callback failure does not prevent completion
- **WHEN** a query callback throws
- **THEN** the runtime records failure, continues remaining callbacks, triggers or continues stop, and does not duplicate completion

### Requirement: Public flush and stop scheduling contract
The public TUI SHALL document synchronous uncontended draining, non-waiting reentrant or concurrent scheduling, and single-owner cleanup.

#### Scenario: Uncontended flush drains synchronously
- **WHEN** no owner is active and pending work exists
- **THEN** `flushRender()` drains it before returning

#### Scenario: Contended flush publishes without waiting
- **WHEN** another owner is active
- **THEN** `flushRender()` records work and returns without waiting for application code

#### Scenario: Stop discards ordinary work but retains required completions
- **WHEN** stop begins
- **THEN** it discards queued ordinary input, notification, render, action, and other ordinary work, retains accepted title/progress output and required query completions, and invokes retained query callbacks before cleanup

### Requirement: Public terminal callback-separation contract
`Terminal.start` and output-side terminal methods SHALL not synchronously invoke registered input or resize callbacks on their calling stack.

#### Scenario: Start callback delivery is independent
- **WHEN** a backend starts terminal control
- **THEN** `start` returns without invoking either registered callback synchronously, while another backend thread may publish before `start` returns

#### Scenario: Output remains callback-separated
- **WHEN** output, cursor, title, progress, drain, or protocol output is invoked
- **THEN** the method returns without synchronously invoking registered input or resize callbacks

### Requirement: Public terminal input is bounded byte streaming
The API SHALL expose copied `TerminalInputChunk` values of length 1 through 4096, streaming paste and raw cases, exact raw kind and termination enums, and incremental `TerminalUtf8Decoder`; it SHALL expose no whole-string paste/raw process or parse compatibility path.

#### Scenario: Chunk ownership is isolated
- **WHEN** a caller creates a chunk or requests `toArray`
- **THEN** input and output arrays are copied and later caller mutation cannot change the chunk

#### Scenario: Streaming cases are exhaustive
- **WHEN** terminal input is pattern matched
- **THEN** paste uses `PasteStart`, `PasteChunk`, `PasteEnd` and raw uses `RawStart`, `RawChunk`, `RawEnd`

### Requirement: Bounded transport does not limit application-owned content
The 4096-byte chunk, parser, and ingress limits SHALL bound transport and runtime transit state only. `Input` and other application-owned component values SHALL have no core content-size limit. Applications that need content limits SHALL validate or reject content before retaining it. Core SHALL NOT truncate or drop content, add a fixed content limit, add limit configuration, or add an overflow callback in this change.

#### Scenario: Bounded input is documented
- **WHEN** bounded input is documented
- **THEN** the documentation distinguishes transport and runtime transit bounds from retained application content

#### Scenario: Application requires a content limit
- **WHEN** an application requires a content-size limit
- **THEN** the application validates or rejects content before retaining it without relying on core truncation, dropping, configuration, or overflow callbacks

### Requirement: TUI root structural API publishes desired state
The TUI SHALL expose `addChild(component): Unit`, `removeChild(component): Unit`, `clear(): Unit`, and `children: Vector[Component]`, while Container and Box retain their local Boolean removal APIs.

#### Scenario: Children observes accepted publication
- **WHEN** a structural call is accepted
- **THEN** `children` immediately reports the latest desired state without waiting for committed hooks

### Requirement: Editor treats streamed bracketed paste as one edit
The Editor SHALL treat one `PasteStart` through `PasteEnd` stream as one logical edit while preserving normalized content, aggregate marker thresholds, Unicode grapheme cursor placement, callbacks, rendering, and undo behavior.

#### Scenario: Streamed paste completes
- **WHEN** paste bytes cross arbitrary parser, UTF-8, CRLF, and grapheme boundaries
- **THEN** the Editor normalizes CRLF, CR, and LF across the whole stream, applies aggregate line and grapheme thresholds, places the cursor after the final grapheme, captures one undo snapshot, invokes `onChange` once, refreshes autocomplete once, and renders once at completion

#### Scenario: Empty streamed paste completes
- **WHEN** `PasteStart` is followed by `PasteEnd` without content
- **THEN** editor text, cursor, undo, callbacks, autocomplete, and rendering remain unchanged

#### Scenario: Paste is interrupted
- **WHEN** a non-paste input arrives before `PasteEnd`
- **THEN** the Editor first commits all accepted paste content as one edit and then handles the non-paste input

#### Scenario: Large streamed paste remains expandable
- **WHEN** aggregate paste content exceeds 10 lines or 1000 grapheme clusters
- **THEN** marker metrics use the whole normalized paste and submit or expansion recovers the exact normalized content

### Requirement: Query ownership and paste cardinality are documented
The public API documentation SHALL state that the runtime retains a query wire flight until reply, stop, or failure, each subscriber owns cancellation and timeout scheduling, and one bracketed paste contains zero or more `PasteChunk` events.

#### Scenario: Subscriber leaves a wire flight
- **WHEN** a subscriber no longer wants a query result
- **THEN** it invokes its idempotent cancellation function while the runtime retains the wire flight independently

#### Scenario: Empty paste is represented
- **WHEN** bracketed paste has no content
- **THEN** input delivery contains `PasteStart` followed by `PasteEnd` with zero `PasteChunk` events

### Requirement: Query exit routing cancels before runtime stop
Applications that no longer need active query subscriptions on exit SHALL disable applicable built-in exit handling before startup and SHALL install cancellation-aware exit routing only after cancellation functions are established.

#### Scenario: Input arrives before cancellation is established
- **WHILE** cancellation functions are not installed
- **WHEN** input arrives
- **THEN** the example does not trigger built-in runtime exit

#### Scenario: Cancellation-aware listener handles exit
- **WHEN** the cancellation-aware listener handles exit
- **THEN** the example cancels active subscribers before requesting exit

### Requirement: Demo query subscriptions are bounded
The interactive demo SHALL retain at most one unanswered subscription per query protocol and SHALL not use a timer to enforce that bound.

#### Scenario: Query remains unanswered
- **WHILE** one demo subscription for a protocol remains unanswered
- **WHEN** the demo considers another query for that protocol
- **THEN** it does not create a second subscription
