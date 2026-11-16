## Scope
Implement #93 Phase A, not the entire application-building roadmap. Use generated event steps so behavior is reproducible without sleeps, workers or file IO. Ctrl+N is a logical source tick producing 20 records. Production scheduling remains an explicit #94 decision.

## Model and resources
An application-owned queue retains at most a configurable 1..10,000 events (default 200). Generated records have bounded fixed-format messages and monotonically increasing Long identities. Each advance accepts at most 1,000 events. Eviction increments a counter; there is no hidden archive. Filtering is ASCII case-insensitive over retained records only. Committed filters are limited to 256 UTF-16 units; longer input is rejected without changing the previous filter. The existing Input editor still owns unrestricted draft text/history, so bounded-event retention is NOT a claim of globally bounded application memory for arbitrary user input.

Selection follows the newest matching event while follow mode is active. Arrow/page navigation disables follow mode. Incoming data preserves a selected identity while retained and visible; eviction falls back to the oldest matching row. A new filter intentionally selects the first matching row in browse mode or the last in follow mode. Empty results have no selection. Pausing ignores source ticks without consuming IDs. Cancellation is terminal for this session and ignores later ticks, including after stop.

## UI and ownership
Existing fullscreen launchers select this example only for `--log-explorer`. Terminal stdin remains keyboard input; generated data has no stream or file descriptor. All UI mutations originate from typed input callbacks. The model exposes immutable snapshots and never invokes TUI APIs while holding its private monitor. The app owns projection, selection-to-scroll synchronization, status/error text and cleanup. Rendering materializes the bounded filtered set; it is explicitly not a virtualized table. Text uses the existing safe component-output boundary.

The filter Input remains focused. Up/Down and PageUp/PageDown navigate rows; Ctrl+N advances, Ctrl+P pauses, Ctrl+G follows latest, Ctrl+X cancels generation, Enter commits a filter, Esc/Ctrl+C exits. A key release must not trigger commands. Ctrl+X leaves the retained data inspectable. Run/stop cancel the model even on terminal failure; the existing TUI owns terminal restoration.

## Discovery output and follow-ups
Document costs and glue rather than invent a public API: repeated bounded projection/layout motivates #95; source ownership/cancellation and future serialized update delivery belong to #94; typed input/resize and assertions motivate #96. Propose only minimum candidate contracts, not a settled scheduler architecture. A real background producer must later test admission, ordering, epochs and shutdown before any claims about asynchronous lifecycle safety.

## Validation
Shared model and VirtualTerminal interaction tests run on JVM and Native. A 100,000-event deterministic test asserts retention, eviction and selection behavior, not machine-speed thresholds. Compile both launchers, validate OpenSpec and docs, and run formatting/lint. Virtual output is not proof of emulator appearance; record real-terminal smoke separately when performed.
