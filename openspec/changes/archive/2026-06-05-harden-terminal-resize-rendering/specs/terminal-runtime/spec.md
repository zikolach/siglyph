## ADDED Requirements

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
