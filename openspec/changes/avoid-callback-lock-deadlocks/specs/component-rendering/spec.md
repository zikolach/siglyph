## MODIFIED Requirements

### Requirement: Interactive render scheduling
The TUI runtime SHALL coalesce render requests, serialize component rendering through one drain owner, and invoke no component or application callback while holding lifecycle or terminal-write locks.

#### Scenario: Multiple render requests coalesce
- **WHEN** several requests arrive before the drain renders
- **THEN** at most one render uses the strongest pending force or clear intent

#### Scenario: Reentrant flush does not recurse
- **WHEN** a callback requests and flushes rendering while the current thread owns the drain
- **THEN** follow-up rendering is recorded and processed after that callback

#### Scenario: Concurrent flush does not wait
- **WHEN** another thread owns the drain
- **THEN** flush records work and returns without waiting for application code

#### Scenario: Query callback remains serialized with rendering
- **WHEN** a terminal query completion and render work are pending
- **THEN** the same drain owner executes them in scheduled order without callback/render concurrency

### Requirement: Interactive stop positioning
The runtime SHALL leave the terminal readable after stopping. Retained query callbacks SHALL complete before cleanup and `Stopped`, while queued ordinary render work SHALL be discarded.

#### Scenario: Stop discards queued render
- **WHEN** stop begins while render work is queued but not active
- **THEN** that ordinary render is not executed after stop

#### Scenario: Cleanup positions cursor after retained callbacks
- **WHEN** stop has accepted query completion callbacks
- **THEN** the owner invokes them before terminal cleanup, cursor positioning, and lifecycle `Stopped`

### Requirement: Resize frame consistency
Each render attempt SHALL snapshot positive dimensions and resize generation, and SHALL reject a candidate known to be stale immediately before output or differential-state mutation.

#### Scenario: Resize invalidates candidate
- **WHEN** width, height, or generation changes during frame computation
- **THEN** the candidate is discarded, the committed baseline is preserved, and a forced redraw is scheduled

#### Scenario: Resize consumes no ingress capacity
- **WHEN** resize notifications arrive while ordinary ingress is full
- **THEN** resize invalidation is coalesced without occupying or dropping an ingress event

### Requirement: Root structural mutations are drain-owned
Accepted TUI root additions, removals, and clears SHALL update immutable desired entries and commit root mutations and context hooks on the drain owner in publication order outside runtime locks.

#### Scenario: Duplicate occurrence identity is preserved
- **WHEN** the same component instance is added more than once and removed
- **THEN** removal targets the first desired occurrence by identity and context changes only on committed count transitions

#### Scenario: Clear preserves later additions
- **WHEN** clear is published before a later add
- **THEN** clear removes occurrences committed at its operation point and the later add survives

#### Scenario: Structural hook fails
- **WHEN** attach or detach throws
- **THEN** the committed mutation remains, later ordinary work is discarded, failure is recorded, and cleanup proceeds
