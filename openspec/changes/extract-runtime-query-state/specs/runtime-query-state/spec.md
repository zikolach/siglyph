## ADDED Requirements

### Requirement: Query state remains single-flight and cancellation-safe

The runtime SHALL share one reserved or emitted flight per query kind, preserve subscription order, and retain an empty flight after all subscribers cancel. Cancellation SHALL be silent, idempotent, and ineffective after callback claim.

#### Scenario: Last subscriber cancels before a reply
- **WHEN** the final active subscriber cancels and another subscriber registers before flight removal
- **THEN** the runtime reuses the existing flight without emitting another request

### Requirement: Query extraction preserves publication and emission boundaries

The coordinator SHALL perform bookkeeping without introducing a lock, queue, or thread. TUI SHALL write requests outside lifecycleLock and reconcile only the matching flight identity. Ingress classifiers SHALL snapshot completions before publication commit clears the flight.

#### Scenario: Reply arrives before emission reconciliation
- **WHEN** ingress publication removes a reserved flight during the terminal write
- **THEN** emission reconciliation releases its reservation without recreating that flight

#### Scenario: Classification precedes publication
- **WHEN** a query reply is classified
- **THEN** its completion snapshot is constructed without removing the flight until publication commits

### Requirement: Stop and callback ordering remain unchanged

Stop SHALL retain completions for emitted background-color and color-scheme flights only, leaving reserved flights for write reconciliation and preserving existing cursor behavior. TUI SHALL retain cleanup cutoff and reentrant ordering, claim callbacks under lifecycleLock, invoke them outside it, and finalize their state under the lock.

#### Scenario: Stop races a reserved request
- **WHEN** stop occurs before a reserved request finishes writing
- **THEN** cleanup waits for its reservation and emission reconciliation retains its stopped completions

#### Scenario: Cancellation races callback execution
- **WHEN** a completion has already been claimed
- **THEN** cancellation does not prevent invocation and callback finalization marks it completed
