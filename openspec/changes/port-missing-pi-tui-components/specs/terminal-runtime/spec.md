## ADDED Requirements

### Requirement: Image-capable runtime capability handling
The terminal runtime SHALL continue to report image capability from environment and expose enough information for image-capable components to choose rendering strategies.

#### Scenario: Capability remains deterministic
- **WHEN** terminal capability detection is executed for a known Kitty/iTerm2 terminal
- **THEN** the returned capabilities indicate the detected image protocol and true-color/hyperlink status consistently

#### Scenario: Unknown terminals do not pretend image support
- **WHEN** environment variables do not indicate an image-capable terminal
- **THEN** capabilities report no image protocol so image components render fallback text

#### Scenario: Capability reporting is testable
- **WHEN** a test injects terminal capability values
- **THEN** image-dependent code follows the injected protocol path deterministically

### Requirement: Output for protocol escapes is bounded by runtime safety expectations
The runtime shall treat image escape sequences as render output and preserve existing synchronization/sanitization protections for all lines.

#### Scenario: Image escapes do not bypass width/sanitization flow
- **WHEN** a frame contains image-related output
- **THEN** the runtime applies synchronized output wrapping and over-wide sanitization semantics according to the established render safety rules

#### Scenario: Output remains restorable on stop
- **WHEN** interactive runtime stops while protocol-specific output may have altered cursor state
- **THEN** existing shutdown behavior restores terminal cursor/state safely without assuming any image-library runtime hook
