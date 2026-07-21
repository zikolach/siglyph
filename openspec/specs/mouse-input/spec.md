# mouse-input Specification

## Purpose
Defines opt-in typed terminal mouse input, committed-frame coordinate routing, overlay precedence,
and focus-preserving delivery to mouse-capable components.
## Requirements
### Requirement: Public typed mouse input
The library SHALL expose typed mouse input as `TerminalInput.Mouse(...)` with terminal-cell coordinates, action, button or wheel direction, and modifier state.

#### Scenario: Mouse press is represented
- **WHEN** the input parser receives a supported SGR mouse press report
- **THEN** it emits `TerminalInput.Mouse` with action `Press`, a button value, zero-based row and column, and parsed modifier state

#### Scenario: Mouse release is represented
- **WHEN** the input parser receives a supported SGR mouse release report
- **THEN** it emits `TerminalInput.Mouse` with action `Release`, a button value, zero-based row and column, and parsed modifier state

#### Scenario: Mouse wheel is represented
- **WHEN** the input parser receives a supported SGR mouse wheel report
- **THEN** it emits `TerminalInput.Mouse` with action `Wheel` and one of `Up`, `Down`, `Left`, or `Right`

### Requirement: Mouse coordinate normalization
The mouse input model SHALL use zero-based terminal cell coordinates measured against the final rendered frame.

#### Scenario: SGR coordinates convert to zero-based cells
- **WHEN** the parser receives an SGR mouse report with one-based column 1 and row 1
- **THEN** it emits a mouse event with column 0 and row 0

#### Scenario: Invalid coordinates are not guessed
- **WHEN** the parser receives an SGR mouse report with a non-positive row or column
- **THEN** it emits raw input instead of guessing a terminal position

### Requirement: Mouse action value set
The public mouse action model SHALL define the complete action set as `Press(button)`, `Release(button)`, and `Wheel(direction)`.

#### Scenario: Known button codes map to buttons
- **WHEN** SGR mouse button codes for primary, middle, or secondary button events are parsed
- **THEN** they map to `Left`, `Middle`, or `Right`

#### Scenario: Unknown button code is preserved
- **WHEN** an SGR mouse report contains a button code that is valid SGR syntax but has no named button mapping
- **THEN** the emitted mouse event uses `Other(code)` with the button identity code after modifier, motion, and wheel flags are removed

#### Scenario: Extended button does not alias a primary button
- **WHEN** an SGR press or release report contains an extended button identity such as 128 together with any supported modifier flags
- **THEN** the emitted action contains `Other(128)` and the extended identity is not mapped to `Left`, `Middle`, or `Right`

### Requirement: Coordinate-aware mouse routing
The TUI runtime SHALL route mouse events by coordinates using the retained bounds tree for the latest successfully committed visual frame and the visible terminal origin of that frame.

#### Scenario: Deepest child under pointer receives mouse
- **WHEN** a mouse event falls inside a nested child component that opts into mouse handling
- **THEN** the runtime delivers the event to that child before trying ancestor components

#### Scenario: Parent handles when child ignores
- **WHEN** a mouse event falls inside a child that does not handle the event and an ancestor opts into mouse handling
- **THEN** the runtime delivers the event to the ancestor

#### Scenario: Missing layout ignores mouse
- **WHEN** a mouse event arrives before any frame has been rendered
- **THEN** the runtime ignores component mouse routing and preserves focus

#### Scenario: Frame below previous terminal output routes by visible cells
- **WHEN** a mouse-enabled TUI starts below previous terminal output and renders without clearing scrollback
- **THEN** the runtime maps terminal mouse coordinates to the visible TUI frame before hit testing retained component bounds

#### Scenario: Initial render scrolling is accounted for
- **WHEN** the first rendered frame scrolls the terminal viewport while preserving scrollback
- **THEN** the runtime maps mouse coordinates to the retained frame rows that remain visible after scrolling

#### Scenario: Resize-invalidated candidate does not replace routing geometry
- **WHEN** terminal dimensions or the resize generation change while a candidate frame is rendering
- **THEN** mouse input received before the forced replacement render is routed against the previously committed frame rather than the rejected candidate

### Requirement: Overlay-aware mouse routing
The TUI runtime SHALL route mouse events through visible overlays before base components, using topmost visual order first.

#### Scenario: Top overlay receives covered coordinate
- **WHEN** a mouse event falls inside a visible overlay that covers a base component
- **THEN** the runtime tries the overlay layout tree before the base layout tree

#### Scenario: Lower overlay receives uncovered coordinate
- **WHEN** multiple visible overlays exist and the topmost overlay does not contain the mouse coordinate
- **THEN** the runtime tries the next lower visible overlay that contains the coordinate

#### Scenario: Hidden overlay is skipped
- **WHEN** a hidden overlay has retained bounds from an earlier render
- **THEN** mouse routing skips that overlay

### Requirement: Mouse routing preserves keyboard focus by default
Mouse routing SHALL NOT change keyboard focus unless a mouse-capable component explicitly requests focus through the TUI context.

#### Scenario: Wheel does not move focus
- **WHEN** a wheel event scrolls a component under the pointer
- **THEN** the previously focused component remains focused unless the mouse handler requests a focus change

#### Scenario: Unhandled mouse does not move focus
- **WHEN** no mouse-capable component handles a mouse event
- **THEN** the focused component remains unchanged
