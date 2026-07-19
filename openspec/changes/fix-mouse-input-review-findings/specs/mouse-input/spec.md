## MODIFIED Requirements

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
