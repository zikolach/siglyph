## ADDED Requirements

### Requirement: Opt-in terminal mouse protocol lifecycle
Interactive terminal backends SHALL enable xterm normal mouse tracking and SGR mouse coordinate reporting only when mouse input is enabled through public TUI options.

#### Scenario: Mouse disabled by default
- **WHEN** an interactive JVM or Native TUI starts without mouse input enabled
- **THEN** the backend does not emit mouse reporting enable sequences

#### Scenario: Mouse enabled on start
- **WHEN** an interactive JVM or Native TUI starts with mouse input enabled
- **THEN** the backend enables normal mouse tracking with `CSI ? 1000 h` and SGR mouse coordinates with `CSI ? 1006 h`

#### Scenario: Mouse disabled on stop
- **WHEN** an interactive JVM or Native TUI with mouse input enabled stops
- **THEN** the backend disables normal mouse tracking with `CSI ? 1000 l` and SGR mouse coordinates with `CSI ? 1006 l` before returning control to the shell

#### Scenario: Startup failure restores mouse mode
- **WHEN** startup fails after mouse reporting has been enabled
- **THEN** the backend disables mouse reporting before restoring terminal state or propagating the failure

### Requirement: SGR mouse input buffering
The terminal runtime SHALL buffer fragmented SGR mouse reports and emit one logical typed input event after a complete report is available.

#### Scenario: Split SGR mouse report
- **WHEN** a mouse report such as `CSI < 64 ; 10 ; 5 M` arrives across multiple raw read chunks
- **THEN** the input buffer emits one parsed `TerminalInput.Mouse` event after the final `M` byte arrives

#### Scenario: Incomplete SGR mouse report flushes as raw input
- **WHEN** an SGR mouse report remains incomplete beyond the configured incomplete escape timeout
- **THEN** the runtime flushes the incomplete data as raw input without blocking later input

### Requirement: SGR mouse parsing
The terminal input parser SHALL parse xterm SGR mouse reports for button press, button release, and wheel input.

#### Scenario: SGR press parses
- **WHEN** the parser receives `CSI < 0 ; 3 ; 2 M`
- **THEN** it emits a left-button press at column 2 and row 1

#### Scenario: SGR release parses
- **WHEN** the parser receives `CSI < 0 ; 3 ; 2 m`
- **THEN** it emits a left-button release at column 2 and row 1

#### Scenario: SGR wheel up parses
- **WHEN** the parser receives `CSI < 64 ; 3 ; 2 M`
- **THEN** it emits a wheel-up event at column 2 and row 1

#### Scenario: SGR modifiers parse
- **WHEN** the parser receives an SGR mouse report whose modifier bits include shift, alt, and ctrl
- **THEN** it emits a mouse event whose `KeyModifiers` has `shift`, `alt`, and `ctrl` set to true and `superKey` set to false

### Requirement: Virtual terminal mouse support
The virtual terminal backend SHALL support tests that enable mouse input, feed typed or raw mouse events, and assert written protocol sequences.

#### Scenario: Virtual terminal records mouse mode sequences
- **WHEN** a TUI with mouse input enabled starts and stops on a virtual terminal
- **THEN** the test can assert that mouse enable and disable sequences were written

#### Scenario: Virtual terminal drives mouse routing
- **WHEN** a test sends a mouse event to the virtual terminal after rendering a frame
- **THEN** the test can observe which component handled the routed event


### Requirement: Mouse frame origin tracking
The terminal runtime SHALL track the visible origin of mouse-enabled TUI frames without clearing terminal scrollback on initial render.

#### Scenario: Cursor position seeds mouse frame origin
- **WHEN** a mouse-enabled interactive TUI starts
- **THEN** the runtime queries the terminal cursor position before the first frame render and uses the response to map mouse coordinates to retained frame rows

#### Scenario: Missing cursor position does not guess routing offset
- **WHEN** the terminal does not provide a usable cursor-position report before rendering
- **THEN** coordinate-aware component mouse routing is ignored until a frame origin is known

### Requirement: Mouse reporting captures terminal wheel scrollback
Interactive terminal mouse reporting SHALL document that wheel events are delivered to the application rather than normal terminal scrollback while mouse input is enabled.

#### Scenario: Unhandled wheel cannot be passed back
- **WHEN** a wheel event is delivered to the application by terminal mouse reporting and no component handles it
- **THEN** the runtime preserves focus and does not attempt to pass that event back to terminal scrollback
