## ADDED Requirements

### Requirement: Borrowed bounded executor scheduling
The adapter SHALL borrow an executor without changing its configuration or shutting it down. Registrations and unresolved cancellation obligations SHALL remain bounded. Registration failures SHALL release reservations and propagate unchanged.

#### Scenario: Capacity exhausted
- **WHEN** pending registrations reach the adapter limit
- **THEN** another schedule request is rejected before invoking the executor

### Requirement: Safe claim and cancellation
Callbacks SHALL be claimed atomically and run outside adapter locks. Cancel/close SHALL disarm unclaimed callbacks without interrupting claimed callbacks. Futures published after close SHALL be cancelled when available, while unresolved publication remains accounted for.

#### Scenario: Callback precedes handle publication
- **WHEN** an executor invokes a callback on another thread before schedule returns
- **THEN** binding its future does not reintroduce a completed registration

#### Scenario: Close precedes publication
- **WHEN** close races with an executor schedule call still in progress
- **THEN** its callback is disarmed and its eventual future is cancelled

### Requirement: Explicit failure and cleanup reporting
Asynchronous callback failures SHALL reach an explicit observer and bounded latest-failure storage. Observer failures SHALL be suppressed onto the original error. Cancel failures SHALL remain retryable and SHALL NOT prevent other cancellation attempts.

#### Scenario: Cancellation failure
- **WHEN** one future cancellation throws
- **THEN** close attempts the others, reports the failure and retains the failed obligation for retry
