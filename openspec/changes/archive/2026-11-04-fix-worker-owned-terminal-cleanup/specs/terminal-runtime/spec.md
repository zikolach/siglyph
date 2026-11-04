## ADDED Requirements

### Requirement: Worker-owned cleanup preserves caller interruption state
Stopping a terminal from one of its own workers SHALL NOT introduce an interrupt on that worker. Shutdown SHALL still interrupt other active workers. Worker-stop signaling SHALL preserve an interrupt already set on the caller.

#### Scenario: Callback owns terminal restoration
- **WHEN** an input, periodic flush, or resize callback synchronously stops its backend
- **AND** the calling worker was not interrupted
- **THEN** shutdown does not interrupt that calling worker before restoration
- **AND** other workers are still signaled to stop

#### Scenario: Caller was already interrupted
- **WHEN** a worker enters stop with an externally set interrupt flag
- **THEN** worker-stop signaling does not silently clear that flag
- **AND** existing blocking-operation interruption and genuine-failure behavior remains unchanged

### Requirement: Typed Ctrl+C permits clean fullscreen JVM shutdown
A fullscreen JVM session handling typed Ctrl+C SHALL complete normal shutdown without an InterruptedException introduced by its own worker-stop signaling. Genuine backend failures SHALL retain existing propagation and retry behavior.

#### Scenario: Fullscreen JVM Ctrl+C succeeds
- **WHEN** typed Ctrl+C requests exit and no independent backend failure occurs
- **THEN** the demo exits successfully after restoring terminal modes, cursor visibility, paste mode and alternate-screen state
