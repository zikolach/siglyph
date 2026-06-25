## ADDED Requirements

### Requirement: Optional terminal input drain capability
The terminal runtime SHALL expose an optional input-drain capability for backends that can safely flush pending terminal input or protocol replies before shutdown.

#### Scenario: Supported backend drains before stop completes
- **WHEN** a running TUI is stopped with a backend that supports input draining
- **THEN** the runtime invokes the drain capability before terminal state restoration completes

#### Scenario: Unsupported backend stops without drain
- **WHEN** a running TUI is stopped with a backend that does not support input draining
- **THEN** shutdown proceeds through the existing stop path without emitting unsupported drain operations

#### Scenario: Drain is bounded
- **WHEN** a backend drain operation waits for pending input to become idle
- **THEN** the operation uses a bounded timeout so shutdown cannot block indefinitely

#### Scenario: Stop remains idempotent
- **WHEN** stop is called more than once on a backend with input-drain support
- **THEN** input draining and terminal restoration remain safe and terminal state is restored at most once

### Requirement: Insert key identity
The terminal input model SHALL represent the Insert key as a first-class typed key identity.

#### Scenario: Insert escape sequence parses to typed key
- **WHEN** the input parser receives the standard Insert key escape sequence `CSI 2 ~`
- **THEN** it emits a typed key event with `TerminalKey.Insert`

#### Scenario: Modified Insert sequence preserves modifiers
- **WHEN** the input parser receives a supported modified Insert key sequence
- **THEN** it emits `TerminalKey.Insert` with the parsed modifier state

#### Scenario: Insert is not reported as unknown
- **WHEN** a supported Insert key sequence is parsed
- **THEN** the parser does not emit `TerminalKey.Unknown("insert")` for that sequence
