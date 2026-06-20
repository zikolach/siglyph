## ADDED Requirements

### Requirement: Optional terminal title support
The terminal runtime SHALL expose optional support for setting the terminal window title without adding a required method to the base `Terminal` abstraction.

#### Scenario: Supported terminal sets title
- **WHEN** application code requests a terminal title on a backend that supports title operations
- **THEN** the backend emits the terminal title sequence for the sanitized title text

#### Scenario: Unsupported terminal reports no title support
- **WHEN** application code requests a terminal title on a backend without title support
- **THEN** the operation reports unsupported status and does not emit a title escape sequence

### Requirement: Optional terminal progress support
The terminal runtime SHALL expose optional fire-and-forget progress indicator support without adding a required method to the base `Terminal` abstraction.

#### Scenario: Supported terminal activates progress
- **WHEN** application code requests active terminal progress on a backend that supports progress operations
- **THEN** the backend emits OSC 9;4 active progress output and does not promise terminal display state

#### Scenario: Supported terminal clears progress
- **WHEN** application code requests inactive terminal progress on a backend that supports progress operations
- **THEN** the backend emits OSC 9;4 clear progress output

#### Scenario: Unsupported terminal reports no progress support
- **WHEN** application code requests terminal progress on a backend without progress support
- **THEN** the operation reports unsupported status and does not emit a progress escape sequence

### Requirement: Runtime-owned terminal background color query
The TUI runtime SHALL query terminal default background color through OSC 11 request/response handling owned by `TUI`.

#### Scenario: OSC 11 RGB response is parsed
- **WHEN** `TUI` queries terminal background color and receives a valid OSC 11 response
- **THEN** it returns red, green, and blue channel values as integers in the inclusive range 0 through 255

#### Scenario: OSC 11 query times out
- **WHEN** `TUI` queries terminal background color and no valid response arrives before the configured timeout
- **THEN** it returns an empty result and continues routing later user input normally

#### Scenario: Invalid OSC 11 response is ignored
- **WHEN** `TUI` receives an OSC 11 response with an invalid color payload
- **THEN** it ignores that payload and does not guess a background color

### Requirement: Runtime-owned terminal color-scheme query
The TUI runtime SHALL query terminal color scheme through request/response handling owned by `TUI` and SHALL represent the finite schemes as `dark` and `light`.

#### Scenario: Color-scheme report returns dark
- **WHEN** `TUI` receives a valid terminal color-scheme report for dark mode
- **THEN** it returns the `dark` color-scheme value

#### Scenario: Color-scheme report returns light
- **WHEN** `TUI` receives a valid terminal color-scheme report for light mode
- **THEN** it returns the `light` color-scheme value

#### Scenario: Color-scheme query times out
- **WHEN** `TUI` queries terminal color scheme and no valid report arrives before the configured timeout
- **THEN** it returns an empty result and keeps the TUI interactive

### Requirement: Terminal color-scheme notifications
The TUI runtime SHALL allow applications to enable or disable terminal color-scheme notifications and subscribe to parsed color-scheme changes.

#### Scenario: Notification listener receives scheme
- **WHEN** notifications are enabled and the terminal sends a valid color-scheme report
- **THEN** each subscribed listener receives the parsed `dark` or `light` value

#### Scenario: Disabled notifications do not notify listeners
- **WHEN** notifications are disabled and the terminal sends a color-scheme report
- **THEN** the runtime consumes or ignores the protocol report without notifying listeners as a user key event

#### Scenario: Listener unsubscribe stops callbacks
- **WHEN** a listener unsubscribes from color-scheme changes
- **THEN** later color-scheme reports do not invoke that listener

### Requirement: Runtime protocol reply interception
The TUI runtime SHALL intercept terminal protocol replies used by runtime services before routing input to focused components.

#### Scenario: Background response is not component input
- **WHEN** an OSC 11 background color response arrives during an interactive session
- **THEN** the runtime consumes it for query handling and does not deliver it as typed input to the focused component

#### Scenario: Color-scheme report is not component input
- **WHEN** a terminal color-scheme report arrives during an interactive session
- **THEN** the runtime consumes it for query or notification handling and does not deliver it as typed input to the focused component

#### Scenario: Unrelated input still reaches component
- **WHEN** user keyboard input arrives while a terminal query is pending
- **THEN** unrelated input is routed through normal focused-component handling
