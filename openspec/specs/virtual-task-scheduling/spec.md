# virtual-task-scheduling Specification

## Purpose
Defines cancellable scheduling contracts, deterministic bounded virtual-time advancement and managed test-clock ownership with pending-work diagnostics.

## Requirements

### Requirement: Bounded cancellable scheduling
A manual scheduler SHALL bound pending registrations, reject closed/full admission explicitly and physically remove cancelled callbacks. Cancellation SHALL prevent only unclaimed callbacks, never interrupt running work.

#### Scenario: Capacity reuse
- **WHEN** a pending callback is cancelled at capacity
- **THEN** its reference is cleared and a new registration may be admitted immediately

### Requirement: Deterministic bounded advancement
Manual advancement SHALL run on its owning thread without reentrancy. Due callbacks SHALL run in deadline/registration order outside locks. A callback budget SHALL bound execution even when callbacks schedule more zero-delay work.

#### Scenario: Exhausted callback budget
- **WHEN** due work remains after the callback budget is consumed
- **THEN** time remains at the last processed deadline and the result reports that its target was not reached

#### Scenario: Callback failure
- **WHEN** a callback throws
- **THEN** the original failure propagates, time remains at that deadline and unrelated pending callbacks remain available

### Requirement: Managed test-clock ownership
The test driver SHALL own and close a supplied manual clock. It SHALL cancel leaked callbacks and report their count while still attempting terminal restoration. Primary failures SHALL retain independent cleanup failures as suppressed exceptions.

#### Scenario: Pending work on fixture exit
- **WHEN** a test exits with unclaimed clock registrations
- **THEN** the clock is closed, registrations are discarded, the terminal is restored and the fixture fails with a pending-work diagnostic

#### Scenario: Old session callback
- **WHEN** a virtual callback submits to a scope from a stopped session after restart
- **THEN** the update returns Closed and cannot mutate the new session
