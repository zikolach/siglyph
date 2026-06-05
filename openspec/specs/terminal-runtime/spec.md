# terminal-runtime Specification

## Purpose
Defines the terminal backend abstraction, JVM and Scala Native runtime behavior, input parsing, resize handling, protocol support, and virtual terminal test backend expectations.
## Requirements
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
The initial JVM backend SHALL target macOS and Linux terminals using an `stty`-based compatibility layer for raw mode and terminal restoration when attached to an interactive TTY.

#### Scenario: JVM backend enters raw mode
- **WHEN** the JVM backend starts on a Unix-like interactive TTY with `stty` available
- **THEN** it configures the terminal for unbuffered interactive input

#### Scenario: JVM backend fails clearly without stty
- **WHEN** raw-mode JVM startup is requested and `stty` is unavailable
- **THEN** the backend fails with a clear diagnostic instead of leaving the terminal in an unknown state

#### Scenario: JVM backend restores stty state
- **WHEN** the JVM backend stops after configuring raw mode
- **THEN** it restores the previously captured terminal settings

### Requirement: Non-interactive stream operation
The terminal runtime SHALL support non-interactive operation where practical by allowing stream-backed input and output without requiring raw terminal mode.

#### Scenario: Non-interactive output renders frames
- **WHEN** a stream-backed terminal is created with a non-TTY output stream
- **THEN** the renderer can write frames to that stream without invoking `stty`

#### Scenario: Non-interactive dimensions use fallback
- **WHEN** terminal dimensions cannot be queried from an interactive TTY
- **THEN** the backend uses configured dimensions or environment fallbacks such as `COLUMNS` and `LINES`

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

### Requirement: Buffered terminal input
The terminal runtime SHALL buffer raw input chunks and emit complete logical input sequences before parsing them into typed terminal input events.

#### Scenario: Split escape sequence
- **WHEN** a terminal backend receives an arrow-key escape sequence split across multiple read chunks
- **THEN** the runtime emits one typed arrow-key event after the complete sequence is available

#### Scenario: Split bracketed paste
- **WHEN** bracketed paste start, content, and end markers arrive across multiple chunks
- **THEN** the runtime emits one paste event containing the full pasted content

#### Scenario: Incomplete escape timeout
- **WHEN** an escape sequence remains incomplete beyond the configured buffer timeout
- **THEN** the runtime flushes the incomplete data as a raw or best-effort input event without blocking future input forever

### Requirement: Control-key normalization
The terminal runtime SHALL normalize raw ASCII control bytes into typed key events with control modifiers where those bytes represent common control-key combinations.

#### Scenario: Ctrl+C normalization
- **WHEN** the runtime receives raw byte `0x03`
- **THEN** it emits a typed key event equivalent to Ctrl+C

#### Scenario: Readline shortcut normalization
- **WHEN** the runtime receives raw control bytes for Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, or Ctrl+W
- **THEN** it emits typed key events with the corresponding character and control modifier

### Requirement: Terminal protocol lifecycle
Interactive terminal backends SHALL enable terminal protocols needed for interactivity on start and disable them on stop.

#### Scenario: Bracketed paste enabled on start
- **WHEN** an interactive JVM or Native terminal backend starts
- **THEN** it enables bracketed paste mode before delivering input to the TUI

#### Scenario: Bracketed paste disabled on stop
- **WHEN** an interactive JVM or Native terminal backend stops
- **THEN** it disables bracketed paste mode before returning control to the shell

### Requirement: Safe interactive shutdown
Interactive terminal backends and the TUI runtime SHALL restore terminal state when the application exits normally, exits from Escape or Ctrl+C, or encounters an exception during startup or event handling.

#### Scenario: Stop restores terminal
- **WHEN** an interactive application requests exit
- **THEN** the runtime shows the cursor, disables bracketed paste, and restores terminal raw-mode state exactly once

#### Scenario: Exception restores terminal
- **WHEN** an exception occurs after raw mode has been enabled
- **THEN** the runtime restores terminal state before propagating or reporting the exception

### Requirement: JVM and Native interactive parity
The project SHALL provide interactive runtime behavior and demo launch targets for both JVM and Scala Native backends.

#### Scenario: JVM interactive demo launches
- **WHEN** the JVM interactive demo is launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C

#### Scenario: Native interactive demo launches
- **WHEN** the Scala Native interactive demo is built and launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C

### Requirement: Positive render dimensions
The TUI runtime SHALL clamp terminal render dimensions to positive minimums before rendering.

#### Scenario: Zero width is clamped
- **WHEN** a terminal backend reports zero columns
- **THEN** the TUI renders using at least one column instead of passing zero width to components

#### Scenario: Zero height is clamped
- **WHEN** a terminal backend reports zero rows
- **THEN** the TUI tracks at least one row for redraw and viewport decisions

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders.

#### Scenario: Width resize triggers full redraw
- **WHEN** terminal width changes after a previous render
- **THEN** the TUI performs a full redraw that clears the viewport and scrollback artifacts

#### Scenario: Height resize triggers full redraw
- **WHEN** terminal height changes after a previous render
- **THEN** the TUI performs a full redraw that clears stale viewport content

### Requirement: Runtime sanitizes over-wide output
The TUI runtime SHALL sanitize final rendered non-image lines before writing to the terminal so normal interactive rendering does not throw because a line exceeds terminal width.

#### Scenario: Over-wide line is truncated before write
- **WHEN** a component returns a rendered line whose visible width exceeds the current terminal width
- **THEN** the TUI writes an ANSI-safe truncated line whose visible width is less than or equal to the terminal width

#### Scenario: Over-wide line does not crash input handling
- **WHEN** a focused component input event causes an over-wide rendered line
- **THEN** input handling completes without an uncaught exception on the backend input thread

#### Scenario: Sanitization remains diagnosable
- **WHEN** the TUI sanitizes an over-wide line
- **THEN** tests or debug tooling can observe that sanitization occurred without adding runtime dependencies

### Requirement: JVM live resize notifications
The JVM interactive terminal backend SHALL notify the TUI when terminal dimensions change during an interactive session.

#### Scenario: JVM backend detects changed stty size
- **WHEN** the JVM backend observes a different terminal size while running
- **THEN** it updates reported columns and rows and invokes the resize callback

#### Scenario: JVM resize polling stops
- **WHEN** the JVM backend stops
- **THEN** any resize polling resources are stopped idempotently

### Requirement: Native live resize notifications
The Scala Native interactive terminal backend SHALL notify the TUI when terminal dimensions change during an interactive session.

#### Scenario: Native backend detects changed ioctl size
- **WHEN** the Native backend observes a different terminal size while running
- **THEN** it updates reported columns and rows and invokes the resize callback

#### Scenario: Native resize polling stops
- **WHEN** the Native backend stops
- **THEN** any resize polling resources are stopped idempotently

### Requirement: Resize rendering preserves terminal state
Interactive resize rendering SHALL avoid leaving the terminal in a broken state if rendering encounters recoverable width issues.

#### Scenario: Narrow resize remains interactive
- **WHEN** an interactive terminal is resized to a narrow positive width
- **THEN** the TUI redraws valid terminal output and remains able to handle later input

#### Scenario: Restore still happens on unrecoverable failure
- **WHEN** an unrecoverable rendering failure occurs during interactive input or resize
- **THEN** the TUI stops the backend and restores cursor/terminal state through the normal shutdown path

### Requirement: Overlay input routing
The TUI runtime SHALL route terminal input to the topmost visible focus-capturing overlay when one exists, otherwise to the currently focused base component.

#### Scenario: Capturing overlay receives input
- **WHEN** a visible focus-capturing overlay is topmost and terminal input is received
- **THEN** the overlay component receives the input instead of the previously focused base component

#### Scenario: Non-capturing overlay does not receive input
- **WHEN** only non-capturing overlays are visible and terminal input is received
- **THEN** input is routed to the previously focused base component

#### Scenario: Topmost capturing overlay wins
- **WHEN** multiple visible focus-capturing overlays exist
- **THEN** the topmost overlay by focus order receives terminal input

### Requirement: Overlay focus restoration
The TUI runtime SHALL restore focus predictably when overlays are hidden, removed, temporarily made invisible, or unfocused through a handle.

#### Scenario: Removing focused overlay restores previous focus
- **WHEN** the focused overlay is permanently removed
- **THEN** focus moves to the next visible capturing overlay or to the component that was focused before the removed overlay was shown

#### Scenario: Hiding focused overlay restores previous focus
- **WHEN** the focused overlay is temporarily hidden
- **THEN** focus moves to the next eligible target and the hidden overlay no longer receives input

#### Scenario: Refocusing overlay brings it to front
- **WHEN** a visible capturing overlay is focused through its handle
- **THEN** it becomes the input target and top visual overlay

### Requirement: Overlay resize redraws
The TUI runtime SHALL perform full redraws on terminal dimension changes and SHALL re-resolve, clamp, and composite overlays against the new positive render dimensions.

#### Scenario: Width resize repositions overlay
- **WHEN** terminal width changes while an overlay is visible
- **THEN** the next render recomputes the overlay width and column position before writing the frame

#### Scenario: Height resize repositions overlay
- **WHEN** terminal height changes while an overlay is visible
- **THEN** the next render recomputes the overlay maximum height and row position before writing the frame

#### Scenario: Narrow resize clips overlay safely
- **WHEN** a visible overlay is larger than the resized terminal viewport
- **THEN** the runtime clips or clamps the overlay output to positive terminal dimensions and remains interactive

### Requirement: Virtual terminal overlay testing
The virtual terminal backend SHALL support assertions for overlay rendering, overlay focus routing, and resize-safe overlay redraws.

#### Scenario: Virtual terminal records composited overlay frame
- **WHEN** a test renders base content with a visible overlay through the virtual terminal
- **THEN** the recorded viewport reflects the composited overlay cells

#### Scenario: Virtual terminal drives overlay input
- **WHEN** a test sends terminal input while a focus-capturing overlay is topmost
- **THEN** the test can observe that the overlay component handled the input

