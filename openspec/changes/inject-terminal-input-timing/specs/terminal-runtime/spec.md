## ADDED Requirements

### Requirement: Controllable input framing time
Terminal input framing SHALL use a monotonic time source and SHALL preserve existing production escape and incomplete-sequence deadlines. Tests SHALL be able to control clock advancement and flush wakeups without wall-clock sleeps.

#### Scenario: CSI continuation before its deadline
- **WHEN** a CSI prefix is parsed and a continuation arrives at controlled time 50 ms before the 75 ms deadline
- **THEN** the terminal delivers one complete typed key and no incomplete raw sequence

#### Scenario: Incomplete sequence deadline reached
- **WHEN** controlled monotonic time reaches the existing incomplete-sequence deadline and the flush worker wakes
- **THEN** the pending sequence is delivered as incomplete raw input

#### Scenario: Production monitor synchronization
- **WHEN** production input arrives while a flush worker waits
- **THEN** parsing and deadline evaluation remain ordered by the existing delivery monitor and wakeup protocol
