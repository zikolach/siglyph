## MODIFIED Requirements

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
- **THEN** terminal mode and cursor restoration have completed exactly once

### Requirement: Safe interactive shutdown
The TUI SHALL use single-owner cleanup and SHALL discard queued ordinary work after stop while retaining accepted control output and required query completion callbacks.

#### Scenario: Uncontended stop restores synchronously
- **WHEN** no startup, drain, or query reservation owner is active
- **THEN** stop invokes retained completions and restores terminal state before returning

#### Scenario: Deferred stop preserves only retained work
- **WHEN** stop occurs while another owner or query reservation is active
- **THEN** later ordinary work is rejected, queued ordinary work is discarded, and the owner invokes retained query callbacks and accepted title/progress output before cleanup

## ADDED Requirements

### Requirement: Runtime ingress is ordered, lossless, and bounded
The TUI SHALL use one ordered ordinary ingress FIFO with capacity 4096 terminal events. One ordinary input SHALL consume one event, one protocol reply callback batch SHALL consume one event, and resize SHALL remain coalesced without consuming a slot.

#### Scenario: Running ingress preserves publication order
- **WHEN** ordinary input, query reply batches, and notifications are accepted while running
- **THEN** application-visible callbacks execute in accepted FIFO order

#### Scenario: Full ingress applies backpressure
- **WHILE** lifecycle is `Starting` or `Running` and 4096 events are queued
- **WHEN** another backend publisher submits an event
- **THEN** it waits in a lifecycle condition loop without holding the terminal write lock and no input is dropped

#### Scenario: Dequeue wakes publishers
- **WHEN** the drain removes an ingress event and frees capacity
- **THEN** it notifies blocked publishers so one can enqueue and preserve order

#### Scenario: Stop wakes and rejects publishers
- **WHEN** lifecycle changes to `Stopping` or `Stopped`
- **THEN** all blocked publishers wake, the ordinary ingress queue is cleared, and blocked and later publication is rejected

#### Scenario: Failure wakes publishers
- **WHEN** runtime or startup failure clears ordinary ingress
- **THEN** blocked publishers wake and observe lifecycle rejection

### Requirement: Callback query flights are correlated without application ingress code
The runtime SHALL maintain one reserved or emitted flight per query protocol, reserve direct request output for the first subscriber, and queue completion batches without invoking application code on ingress.

#### Scenario: First subscriber reserves and emits once
- **WHEN** a query subscribes while running with no flight
- **THEN** it reserves one flight and one write reservation, emits exactly one request through the terminal write boundary, releases the reservation, and returns cancellation

#### Scenario: Existing flight accepts subscriber
- **WHILE** a reserved or emitted flight exists
- **WHEN** another caller subscribes
- **THEN** it joins that flight and no request is emitted

#### Scenario: Reply correlation queues callbacks
- **WHEN** a valid or recognized strict invalid reply matches a flight
- **THEN** ingress clears the flight under short lifecycle state and queues one ordered callback batch without invoking application code

#### Scenario: Unrelated input remains ordinary
- **WHEN** input does not match a recognized protocol frame
- **THEN** it remains normally ordered ordinary ingress

### Requirement: Query callbacks are drain-owned and race-defined
Each completion SHALL be claimed under short lifecycle state and invoked outside lifecycle and terminal-write locks. Application callbacks SHALL never run concurrently.

#### Scenario: Cancellation removes active subscriber
- **WHEN** cancellation wins before callback claim
- **THEN** the callback is never invoked

#### Scenario: Claim prevents cancellation removal
- **WHEN** callback claim wins first
- **THEN** callback runs exactly once and cancellation has no effect

#### Scenario: Callback failure continues batch
- **WHEN** one completion callback throws
- **THEN** runtime failure is recorded, every remaining eligible callback is attempted, and cleanup proceeds

### Requirement: Query stop and failure ordering
Cleanup SHALL not overtake query write reservations or accepted retained query completions.

#### Scenario: Emitted flight stops
- **WHEN** stop observes an emitted query flight
- **THEN** active subscribers receive retained `Stopped` before cleanup

#### Scenario: Reserved flight emission succeeds after stop
- **WHEN** a reserved request emits successfully after lifecycle became stopping
- **THEN** its active subscribers receive retained `Stopped` before cleanup

#### Scenario: Reserved flight emission fails after stop
- **WHEN** reserved request emission fails
- **THEN** subscribers receive retained `Failed`, runtime failure is recorded, all completion callbacks continue, and cleanup follows reservation release

#### Scenario: Ordinary queued work remains discarded
- **WHEN** stop or failure progresses with retained query callbacks
- **THEN** the drain invokes those callbacks before cleanup but does not invoke queued ordinary input, notification, render, action, or other ordinary work

### Requirement: Runtime work remains live across application locks
The runtime SHALL serialize application callbacks, rendering, and terminal output without lifecycle/application lock inversion.

#### Scenario: Application lock and render request do not deadlock
- **WHEN** one thread holds application state and publishes render work while the owner needs that state
- **THEN** publication does not wait for the active owner and the owner continues after application state is released

### Requirement: Resize rendering rejects known stale dimensions
The runtime SHALL compare resize generation and positive dimensions immediately before frame output and differential baseline mutation.

#### Scenario: Stale candidate is rejected
- **WHEN** generation, width, or height differs from the render snapshot
- **THEN** no stale frame is committed and a forced redraw uses the latest dimensions

### Requirement: Built-in terminal callback separation
VirtualTerminal, StreamTerminal, SttyTerminal, and PosixTerminal SHALL satisfy start-stack and output-side callback separation.

#### Scenario: Portable and backend contract suites execute
- **WHEN** JVM and Scala Native terminal tests run
- **THEN** controlled callback probes show no synchronous callback from `start` or any output-side method
#### Scenario: Stop invalidates ordered input delivery
- **WHILE** a read or flush batch waits behind an earlier callback
- **WHEN** stop invalidates its terminal-start generation
- **THEN** the waiter returns without callback or counter advancement, and a later start uses reset counters and rejects old-generation delivery
#### Scenario: Stale input parsing is rejected before evaluation
- **WHILE** an old read or flush thread retains a parse operation
- **WHEN** stop and start replace its generation
- **THEN** the old parse operation is not evaluated and cannot consume or mutate the new generation's parser state
#### Scenario: Old loops cannot affect a restarted backend
- **WHILE** a read or flush loop belongs to an invalidated start generation
- **WHEN** a later generation is active
- **THEN** the old loop stops without parsing, delivering callbacks, or changing the later generation's running state

#### Scenario: Restart waits for exclusive input-reader ownership
- **WHILE** a stopped generation's input reader remains alive
- **WHEN** restart is requested
- **THEN** the backend throws `IllegalStateException` before creating another reader, and restart succeeds after the old reader terminates

#### Scenario: Interrupted ordered delivery callback fails
- **WHILE** an ordered delivery waiter has observed interruption
- **WHEN** its accepted callback throws
- **THEN** batch ordering advances and the thread's interrupted status is restored

### Requirement: Terminal input parsing is bounded byte streaming
The runtime SHALL parse `TerminalInputChunk` values without whole-stream strings, retain at most 4096 typed-candidate bytes, five paste-end prefix bytes, and three incomplete UTF-8 bytes, and emit exact ordered bounded stream events.

#### Scenario: Paste streams without accumulation
- **WHILE** paste mode is active
- **WHEN** arbitrary bytes arrive, including markers split at any boundary
- **THEN** start and end markers are omitted, every data byte appears once in chunks of at most 4096 bytes, and complete paste content is never retained
#### Scenario: Periodic flush preserves active paste
- **WHILE** paste mode is active
- **WHEN** periodic fragment flush runs with paste content or a partial end marker buffered
- **THEN** flush emits nothing and preserves paste mode and the partial marker until later bytes complete the end marker

#### Scenario: Typed protocol exceeds its bound
- **WHEN** byte 4097 arrives before an incomplete typed protocol terminates
- **THEN** retained bytes are emitted as raw chunks, later bytes remain raw without typed reclassification, and termination reports `LimitExceeded`

#### Scenario: Text decoding is incremental
- **WHEN** UTF-8 scalars cross chunk boundaries or malformed or incomplete text is flushed
- **THEN** the text decoder carries at most three bytes and emits U+FFFD where required while raw input bytes remain exact

### Requirement: Cleanup has an explicit finite Cleaning phase
The lifecycle SHALL progress `Stopped -> Starting -> Running -> Stopping -> Cleaning -> Stopped`, commit cleanup atomically after startup and query-write ownership ends, and prevent query registration from postponing restoration.

#### Scenario: Stopping query precedes cleanup commit
- **WHEN** a query registration linearizes in `Stopping` before cleanup commit
- **THEN** its `Stopped` completion belongs to the finite pre-cleanup set and runs before restoration

#### Scenario: Cleaning query follows restoration
- **WHEN** a query registers after cleanup commit and before restoration cutoff
- **THEN** its `Stopped` completion runs after restoration and cannot delay restoration

#### Scenario: Continuous registration cannot extend cleanup
- **WHEN** queries register continuously during Cleaning
- **THEN** restoration detaches one finite post-restoration list, later registrations use stopped behavior, and lifecycle reaches Stopped

### Requirement: Protocol correlation uses exact ingress slot accounting
Correlation-only raw fragments SHALL consume zero ingress slots. A recognized final protocol completion or notification batch SHALL consume exactly one slot regardless of subscriber count. Reconstructed ordinary raw events SHALL each consume one slot.

#### Scenario: Correlation fragment needs no capacity
- **WHILE** an eligible raw reply is incomplete
- **WHEN** `RawStart` or `RawChunk` produces no application work
- **THEN** correlation state advances without waiting for or consuming FIFO capacity

#### Scenario: Completion is one batch slot
- **WHEN** a recognized reply completes any number of query subscribers or notification listeners
- **THEN** its final application callback batch consumes exactly one FIFO slot

#### Scenario: Replay uses ordinary capacity
- **WHEN** a correlated stream proves unrelated or exceeds the correlation bound
- **THEN** every reconstructed raw stream event is published through normal bounded capacity in exact order, one event per slot, with bytes and required active query flight preserved

#### Scenario: Maximal replay starts draining before continuation completion
- **WHILE** replay exceeds the 4096-slot FIFO capacity and no drain is active
- **WHEN** classification atomically commits the correlation transition and bounded replay continuation
- **THEN** the publisher claims the drain before waiting, each dequeued event admits the next replay event, and publication completes without self-deadlock

#### Scenario: Later ingress cannot overtake replay continuation
- **WHILE** a bounded replay continuation remains
- **WHEN** another publisher submits ordinary ingress
- **THEN** it waits until replay admission completes or lifecycle rejection occurs, and accepted later ingress follows every replay event

#### Scenario: Stop discards replay continuation
- **WHILE** a bounded replay continuation remains
- **WHEN** stop or failure clears ordinary ingress
- **THEN** the continuation is discarded and all blocked publishers wake to observe lifecycle rejection

#### Scenario: Blocked classification is non-mutating
- **WHEN** classification needs an output slot and ingress is full
- **THEN** it waits without changing correlation, notification, or query-flight state and atomically commits the transition with the first enqueued output after capacity becomes available

### Requirement: Starting backpressure does not retain backend start
`Terminal.start` SHALL return independently of callbacks invoked from other threads. Application callbacks accepted during `Starting` SHALL remain deferred until `Running`, even when bounded Starting ingress backpressures publishers.

#### Scenario: Full Starting ingress blocks a publisher
- **WHILE** Starting ingress is full
- **WHEN** a publisher blocks
- **THEN** backend start returns independently, transitions can reach Running, and Running can drain the blocked ingress without invoking application callbacks during Starting

### Requirement: Cleaning callback sets are finite and globally serialized
Cleaning SHALL have one finite pre-restoration callback set and one finite post-restoration callback set, SHALL serialize both through the single owner, and SHALL prevent later query registration from overlapping callbacks or extending restoration.

#### Scenario: Late query registers during detached callbacks
- **WHILE** detached Cleaning callbacks execute
- **WHEN** a late query registers
- **THEN** the runtime prevents concurrent application callback execution and the registration cannot extend restoration

#### Scenario: Restoration seals post-restoration work
- **WHEN** restoration completion detaches the post-restoration callback set
- **THEN** that finite set runs serially and later registrations use stopped behavior

### Requirement: Escape Alt framing uses flush as its sequence boundary
The parser SHALL frame Escape followed by one multibyte UTF-8 scalar as one Alt input without duplicating or losing bytes when the complete scalar arrives before parser flush. Parser flush SHALL end pending non-paste Escape and Alt framing.

#### Scenario: Alt scalar crosses read fragments before flush
- **WHEN** Escape and the bytes of a multibyte UTF-8 scalar arrive in separate read fragments before parser flush
- **THEN** the parser emits exactly one Alt input for that scalar

#### Scenario: Flush follows pending Escape
- **WHEN** parser flush occurs after Escape and before another scalar byte arrives
- **THEN** flush emits standalone Escape and later bytes start a separate sequence

#### Scenario: Flush follows incomplete Alt scalar
- **WHEN** parser flush occurs after Escape and an incomplete multibyte UTF-8 scalar prefix
- **THEN** flush emits that framing with incomplete raw termination and later bytes start a separate sequence

### Requirement: Ordered delivery validates every event and restart excludes old workers
Ordered delivery SHALL recheck terminal-start generation before each event, and restart SHALL reject a live old-generation reader, flush worker, or backend resize worker.

#### Scenario: Generation changes within a batch
- **WHEN** generation becomes stale after one event in an ordered batch
- **THEN** delivery rejects every remaining event before invoking its callback

#### Scenario: Old worker remains live
- **WHILE** an old-generation reader, flush worker, or backend resize worker remains live
- **WHEN** restart is requested
- **THEN** restart throws `IllegalStateException` before starting new-generation workers

### Requirement: Finite stream EOF preserves only complete non-paste framing
At EOF, finite StreamTerminal input SHALL deliver the complete final non-paste event vector before invalidating the generation and SHALL discard incomplete paste framing without a synthetic end event.

#### Scenario: EOF flushes pending framing
- **WHEN** EOF occurs
- **THEN** the stream backend delivers the complete final non-paste event vector before generation invalidation and discards incomplete paste framing

#### Scenario: EOF interrupts paste
- **WHILE** bracketed paste has started without its end marker
- **WHEN** EOF occurs
- **THEN** the backend emits no synthetic `PasteEnd`

### Requirement: Backend cleanup obligations are independently retained
JVM and Native terminal backends SHALL retain and retry each failed cleanup obligation independently, SHALL not repeat successful obligations, and SHALL reject restart while any obligation remains.

#### Scenario: One cleanup obligation fails
- **WHEN** one cleanup obligation fails after another succeeds
- **THEN** retry executes only the failed obligation and restart remains rejected until it succeeds

#### Scenario: PrintStream suppresses cleanup output failure
- **WHEN** configured PrintStream output records an error without throwing during cleanup output and replaces `System.out` while writing
- **THEN** the backend checks the same PrintStream that performed the write, throws `IOException` before clearing that cleanup obligation, and rejects restart while it remains pending

### Requirement: Missing controlling terminal is actionable
SttyTerminal SHALL report an actionable initial missing `/dev/tty` error, preserve its cause, and SHALL not fall back to another terminal source.

#### Scenario: Initial `/dev/tty` open fails
- **WHEN** SttyTerminal cannot open `/dev/tty` during initial startup
- **THEN** startup fails with an actionable diagnostic whose cause is the original error and no fallback is attempted
