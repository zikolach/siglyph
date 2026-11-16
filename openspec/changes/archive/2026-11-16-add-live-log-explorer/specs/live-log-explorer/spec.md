## ADDED Requirements

### Requirement: Opt-in bounded automatic generation
Both launchers SHALL expose --live-log-explorer while preserving the existing step-driven mode. Automatic ticks SHALL use the existing bounded model and serialized scoped updates.

#### Scenario: Automatic batches
- **WHEN** three virtual deadlines elapse with a batch size of five
- **THEN** fifteen generated events are added without exceeding configured retained capacity

### Requirement: Pause and terminal cancellation
Pause SHALL consume no event IDs. Source cancellation SHALL prevent further generation and cancel scheduled work while retaining inspectable rows. Cancellation during setup SHALL not install a live source afterward.

#### Scenario: Paused source
- **WHEN** deadlines elapse while paused
- **THEN** generated count and indexed table data remain unchanged

#### Scenario: Cancel before setup
- **WHEN** source cancellation occurs before streaming setup completes
- **THEN** no callback can later generate events through that source

### Requirement: Explicit producer cleanup
The application SHALL own its executor, adapter and source scope. Every exit/failure path SHALL attempt source cancellation, terminal restoration and worker cleanup; primary failure SHALL retain independent cleanup failures as suppressed errors.

#### Scenario: Registration failure
- **WHEN** the provider rejects initial scheduling
- **THEN** the terminal is restored, producer cleanup is attempted and the registration error remains observable
