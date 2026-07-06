## ADDED Requirements

### Requirement: Opt-in alternate-screen lifecycle
The TUI runtime SHALL support an opt-in alternate-screen mode that enters the terminal alternate screen for the lifetime of a running `TUI` instance while preserving normal-screen mode as the default.

#### Scenario: Default mode does not enter alternate screen
- **WHEN** an application constructs and starts a `TUI` without configuring alternate-screen mode
- **THEN** the terminal output does not contain `ESC[?1049h` or `ESC[?1049l`

#### Scenario: Alternate-screen mode enters before first frame
- **WHEN** an application starts a `TUI` configured for alternate-screen mode
- **THEN** the runtime emits `ESC[?1049h` before writing the first rendered frame

#### Scenario: Alternate-screen mode exits on stop
- **WHEN** an application stops a `TUI` that entered alternate-screen mode
- **THEN** the runtime emits `ESC[?1049l` before returning control to the shell

#### Scenario: Alternate-screen mode skips normal-screen cursor parking
- **WHEN** an application stops a `TUI` that entered alternate-screen mode after rendering content
- **THEN** the runtime exits alternate screen without writing the normal-screen cursor-below-content parking sequence

#### Scenario: Alternate-screen cleanup runs after runtime failure
- **WHEN** a runtime failure occurs after alternate-screen mode has been entered
- **THEN** the runtime restores autowrap if needed, shows the cursor, exits alternate screen, and stops the terminal backend

#### Scenario: Alternate-screen lifecycle is idempotent
- **WHEN** `stop()` is called more than once after an alternate-screen `TUI` has been started
- **THEN** alternate-screen exit is emitted at most once for that lifecycle

### Requirement: Alternate-screen resize redraws
The TUI runtime SHALL keep resize redraws full-frame and width-safe while respecting the active screen mode.

#### Scenario: Normal-screen resize keeps pi-tui full clear
- **WHEN** terminal dimensions change after a previous render in normal-screen mode
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Alternate-screen resize redraws inside alternate screen
- **WHEN** terminal dimensions change after a previous render while alternate-screen mode is active
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the active alternate-screen viewport, homes the cursor, and writes the recomputed frame without emitting another alternate-screen enter sequence

#### Scenario: Alternate-screen resize preserves normal scrollback intent
- **WHEN** terminal dimensions change while alternate-screen mode is active
- **THEN** the resize redraw does not emit `CSI 3 J`
