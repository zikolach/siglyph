## ADDED Requirements

### Requirement: Bounded lifecycle-owned timer registration
A scope SHALL bound scheduled registrations separately from immediate updates. Scope/TUI closure SHALL disarm and cancel owned registrations without closing their shared scheduler. User delivery SHALL wait for provider handle publication.

#### Scenario: Early provider callback
- **WHEN** a provider fires before returning the cancellation handle
- **THEN** no user callback runs until publication and close can still disarm delivery

#### Scenario: Shared scheduler
- **WHEN** one scope closes
- **THEN** its timers are cancelled without cancelling another scope's registrations

### Requirement: Non-overlapping fixed-delay recurrence
Recurring timers SHALL rearm only after their previous UI delivery completes. Rejected delivery or failed callback/rearm SHALL stop recurrence and expose its outcome.

#### Scenario: Pending UI delivery
- **WHEN** an occurrence waits behind runtime work
- **THEN** no additional occurrence is registered until that delivery completes

### Requirement: Retryable cleanup without skipped terminal restoration
Failed or still-publishing cancellation obligations SHALL remain observable and prevent restart until explicit cleanup succeeds. Timer cleanup failure SHALL NOT skip backend or screen restoration.

#### Scenario: Cancellation fails
- **WHEN** stopping a TUI encounters a throwing timer cancellation
- **THEN** terminal restoration is attempted, restart is rejected and stop can retry the timer without repeating completed terminal cleanup
