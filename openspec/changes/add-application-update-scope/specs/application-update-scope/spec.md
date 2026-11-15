## ADDED Requirements

### Requirement: Bounded serialized application updates
A running TUI SHALL allow bounded ApplicationScopes. Submissions SHALL determine admission before executing application code and report Accepted, Full or Closed. Accepted callbacks MAY execute inline before submit returns. Accepted callbacks SHALL execute through the existing serialized runtime work boundary and outside lifecycle locks.

#### Scenario: Scope saturation
- **WHEN** an executing callback fills its scope's pending limit
- **THEN** additional submissions return Full without running or retaining their callbacks

#### Scenario: Successful update
- **WHEN** an admitted callback completes successfully
- **THEN** its render request is processed through normal TUI rendering

### Requirement: Lifecycle cutoff and generation isolation
Closing a scope or stopping its TUI SHALL reject new submissions and clear unclaimed callback references. Old handles SHALL remain closed after restart.

#### Scenario: Stop with pending work
- **WHEN** TUI stop begins before queued callbacks are claimed
- **THEN** those callbacks never run

#### Scenario: Restart
- **WHEN** the same TUI starts a new session
- **THEN** a fresh scope can accept updates but old scopes return Closed

### Requirement: Observable cooperative closure
A scope SHALL distinguish closed admission from quiescence. External close SHALL wait for a claimed callback to finish; self-close SHALL establish the cutoff without waiting for itself. No callback submitted through that scope SHALL remain capable of starting after quiescence is reached. Independent component effects and external work are not implicitly owned by the scope.

#### Scenario: Close during delivery
- **WHEN** another thread closes a scope with one callback in flight
- **THEN** close returns only after that callback finishes and queued work is discarded

#### Scenario: Self-close
- **WHEN** a callback closes its own scope
- **THEN** close returns without deadlock and the scope becomes quiescent after the callback returns

### Requirement: Existing failure channel
An application-update failure SHALL enter TUI's established failure/cleanup path. Admission acknowledgement SHALL NOT be documented as successful completion.

#### Scenario: Callback throws
- **WHEN** an admitted callback throws
- **THEN** the original error is observable through TUI.run or the managed test driver and later scope submissions are rejected
