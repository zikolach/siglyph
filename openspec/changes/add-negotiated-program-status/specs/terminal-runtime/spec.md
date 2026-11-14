## ADDED Requirements

### Requirement: Optional negotiated root program status

The runtime SHALL expose typed OSC 7501 status through an optional capability without adding abstract methods to Terminal or replacing OSC 9;4 progress. Reports SHALL require a timely positive query echo and unsupported reports SHALL emit nothing.

#### Scenario: Positive and missing negotiation
- **WHEN** a supporting backend receives a valid echo within its query deadline
- **THEN** typed status reports are enabled
- **AND** missing, malformed or expired replies do not enable reports

#### Scenario: Late reply consumption
- **WHEN** a complete valid OSC 7501 reply arrives after expiration or without capability support
- **THEN** the runtime consumes it as protocol input without application callback delivery on ingress

### Requirement: Bounded status encoding

The formatter SHALL accept typed states and blocked reasons, omit invalid app identifiers, sanitize controls, and limit messages to 2048 UTF-8 bytes without splitting code points.

#### Scenario: Hostile message
- **WHEN** a report contains controls, a long multibyte message or invalid app identifier
- **THEN** controls become spaces, the bounded message is base64 encoded, and invalid identity is omitted

### Requirement: Serialized retryable status cleanup

Status reports SHALL use the existing TUI output boundary. Cleanup SHALL independently attempt status reset and backend shutdown, preserving the first error and suppressing later failures. Failed clears SHALL remain retryable without repeating a successful backend shutdown; screen exit SHALL wait for both obligations.

#### Scenario: Partial report and failed clear
- **WHEN** report output or cleanup output fails
- **THEN** restoration preserves the cleanup obligation and original lifecycle failure
- **AND** successful later cleanup clears the obligation without duplicate clears

#### Scenario: Status clear fails before backend shutdown
- **WHEN** writing a status clear fails
- **THEN** backend shutdown is still attempted so input and terminal-mode restoration can proceed
- **AND** the status error remains primary with later backend failures suppressed

#### Scenario: Only status reset remains
- **WHEN** backend shutdown succeeds but a failed status-clear obligation remains
- **THEN** restart is rejected and screen exit is deferred until explicit retry clears status
- **AND** successful backend shutdown is not repeated

### Requirement: Conservative terminal profile hints

Detection SHALL recognize Herdr, Rex and Alacritty fallback hints, require explicit Orca Kitty advertisement, and preserve multiplexer conservatism and disabled overrides.

#### Scenario: Leaked outer Kitty hint
- **WHEN** Orca lacks ORCA_IMAGE_PROTOCOL=kitty but inherits KITTY_WINDOW_ID
- **THEN** image support remains disabled

#### Scenario: Multiplexer profile
- **WHEN** a recognized terminal profile is inside TMUX or screen
- **THEN** existing multiplexer image and hyperlink restrictions remain in force
