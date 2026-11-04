## ADDED Requirements

### Requirement: Shared retryable interactive protocol session

Interactive JVM and Native backends SHALL use shared internal state for paste, captured mouse tracking mode, and Kitty protocol negotiation and cleanup. Obligations SHALL be recorded before enable writes and cleared only after successful disable writes.

#### Scenario: Enable or disable output fails

- **WHEN** a protocol write fails after possibly emitting partial output
- **THEN** its cleanup obligation remains available for a later retry
- **AND** a successful disable clears that obligation without repeated disable output

#### Scenario: Mouse configuration changes after enable

- **WHEN** the configured mouse mode changes after a lifecycle enables tracking
- **THEN** cleanup disables the mode captured at enable time

#### Scenario: Platform-specific shutdown ordering

- **WHEN** interactive backends clean up protocol and platform state
- **THEN** JVM retains Kitty, mouse, paste, input, stty ordering
- **AND** Native retains input, Kitty, mouse, paste, termios ordering
- **AND** cleanup continues after individual failures while retaining failed obligations

#### Scenario: Pending Kitty negotiation is canceled

- **WHEN** cleanup cancels negotiation before any Kitty enable attempt
- **THEN** no Kitty disable pop is emitted
