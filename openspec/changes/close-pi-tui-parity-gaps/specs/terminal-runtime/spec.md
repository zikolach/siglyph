## ADDED Requirements

### Requirement: Kitty keyboard protocol negotiation
Interactive terminal backends SHALL support conservative Kitty keyboard protocol negotiation where available and SHALL fall back to existing key parsing when negotiation is unsupported, stale, or mismatched.

#### Scenario: Successful negotiation enables advanced key parsing
- **WHEN** a terminal responds to keyboard protocol negotiation with a valid matching response
- **THEN** the backend records that Kitty keyboard protocol is active and parses subsequent CSI-u keyboard events accordingly

#### Scenario: Stale negotiation response is ignored
- **WHEN** a delayed or mismatched keyboard protocol response is received after negotiation has timed out or changed
- **THEN** the backend ignores that response and does not falsely mark Kitty keyboard protocol active

#### Scenario: Unsupported terminal preserves basic input
- **WHEN** a terminal does not support Kitty keyboard protocol negotiation
- **THEN** basic arrows, printable characters, control keys, and bracketed paste continue to work through existing parsing paths

### Requirement: Key release and repeat input events
The terminal input model SHALL represent key press, release, and repeat metadata for protocols that report it, while preserving press-only behavior for terminals that do not.

#### Scenario: Key release is delivered to interested component
- **WHEN** a focused component opts into key-release handling and the terminal reports a key-release event
- **THEN** the TUI routes the typed key event with release metadata to that component

#### Scenario: Key release is ignored by default
- **WHEN** a focused component does not opt into key-release handling and the terminal reports a key-release event
- **THEN** the TUI does not deliver that release event as an ordinary key press

#### Scenario: Key repeat is distinguishable
- **WHEN** the terminal reports a repeated key event
- **THEN** the parsed terminal input identifies it as a repeat rather than an initial press

### Requirement: Platform modifier fallbacks
Terminal backends SHALL provide best-effort platform-specific modifier fallbacks for known terminals where practical without compromising normal raw input behavior.

#### Scenario: Apple Terminal modified Enter fallback
- **WHEN** Apple Terminal sends plain Return while platform state indicates a modified Enter combination that should insert a newline
- **THEN** the backend or parser emits a typed Enter event with the appropriate modifier when that fallback is available

#### Scenario: Fallback unavailable uses plain key
- **WHEN** platform modifier state cannot be queried safely
- **THEN** the backend emits the ordinary parsed key event and does not block input waiting for modifier metadata

### Requirement: Keyboard protocol diagnostics
Keyboard protocol support SHALL be testable and diagnosable without requiring a live terminal session.

#### Scenario: Parser tests cover protocol event metadata
- **WHEN** tests feed CSI-u sequences containing press, release, and repeat event metadata
- **THEN** the parser returns typed terminal input values that expose the expected event metadata

#### Scenario: Backend exposes protocol state for tests
- **WHEN** a test backend simulates keyboard protocol negotiation outcomes
- **THEN** tests can verify whether advanced keyboard mode is active without inspecting private backend state
