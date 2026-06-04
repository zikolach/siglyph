## ADDED Requirements

### Requirement: Terminal abstraction
The library SHALL define a terminal backend abstraction for starting and stopping terminal control, writing output, reading input events, observing resize events, querying dimensions, and controlling cursor and clear operations.

#### Scenario: Backend starts with callbacks
- **WHEN** an application starts a TUI with a terminal backend
- **THEN** the backend enters interactive mode and delivers input and resize notifications through registered callbacks

#### Scenario: Backend restores terminal state
- **WHEN** the application stops the TUI
- **THEN** the backend restores terminal modes and cursor visibility before returning control to the shell

### Requirement: Scala Native POSIX backend
The initial Native backend SHALL target Scala Native on macOS and Linux and use native POSIX terminal APIs for raw mode, window-size queries, signal handling, and non-buffered input.

#### Scenario: Raw mode receives unbuffered input
- **WHEN** the Scala Native backend is running in raw mode
- **THEN** a single key press is delivered without waiting for a newline

#### Scenario: Resize updates dimensions
- **WHEN** the terminal window is resized
- **THEN** the backend updates columns and rows and requests a render

### Requirement: JVM stty backend
The initial JVM backend SHALL target macOS and Linux terminals using an `stty`-based compatibility layer for raw mode and terminal restoration.

#### Scenario: JVM backend enters raw mode
- **WHEN** the JVM backend starts on a Unix-like system with `stty` available
- **THEN** it configures the terminal for unbuffered interactive input

#### Scenario: JVM backend restores stty state
- **WHEN** the JVM backend stops
- **THEN** it restores the previously captured terminal settings

### Requirement: Shared backend compatibility
The library SHALL keep the terminal backend interface independent from target-specific implementation details so Native and JVM backends use the same component and renderer APIs.

#### Scenario: Shared core works across backends
- **WHEN** the same component tree is rendered through a Native backend and a JVM backend
- **THEN** the component rendering API and output semantics remain the same

### Requirement: Terminal protocol support
The terminal runtime SHALL support bracketed paste, synchronized output, xterm-compatible modified keys, Kitty keyboard protocol negotiation where available, and OSC hyperlinks. Kitty/iTerm2 image escape emission SHALL be planned behind the same terminal abstraction but is not part of the first usable milestone.

#### Scenario: Bracketed paste event is preserved
- **WHEN** a terminal sends bracketed paste start and end markers around pasted content
- **THEN** the runtime delivers the pasted content as a paste-aware input event or equivalent editor-safe sequence

#### Scenario: Synchronized output wraps frame writes
- **WHEN** the renderer flushes a frame
- **THEN** it wraps the write with terminal synchronized output enable and disable sequences when supported

#### Scenario: Kitty keyboard sequence is normalized
- **WHEN** the terminal sends a Kitty CSI-u key sequence with modifiers
- **THEN** the runtime normalizes it to the library key event model

### Requirement: Virtual terminal test backend
The library SHALL provide a virtual terminal backend for tests that records writes, simulates dimensions, accepts scripted input, and exposes viewport state for assertions.

#### Scenario: Virtual terminal records frame output
- **WHEN** a test renders a TUI frame through the virtual backend
- **THEN** the backend exposes the written escape stream and rendered viewport for assertions
