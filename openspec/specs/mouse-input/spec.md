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
The public mouse action model SHALL define raw pointer actions as `Press(button)`, `Release(button)`, `Move(buttonState)`, and `Wheel(direction)`. The runtime SHALL derive semantic click and drag events only from ordered raw actions and committed layout geometry.

#### Scenario: Known button codes map to buttons
- **WHEN** SGR mouse button codes for primary, middle, or secondary button events are parsed
- **THEN** they map to `Left`, `Middle`, or `Right`

#### Scenario: Unknown button code is preserved
- **WHEN** an SGR mouse report contains a button code that is valid SGR syntax but has no named button mapping
- **THEN** the emitted mouse event uses `Other(code)` with the button identity code after modifier, motion, and wheel flags are removed

#### Scenario: Extended button does not alias a primary button
- **WHEN** an SGR press, release, or motion report contains an extended button identity such as 128 together with any supported modifier flags
- **THEN** the emitted action contains `Other(128)` and the extended identity is not mapped to `Left`, `Middle`, or `Right`

#### Scenario: Motion preserves pressed state
- **WHEN** SGR mouse input reports pointer motion with or without a pressed button
- **THEN** typed input identifies the current button state without inventing a click or drag before runtime gesture classification

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

### Requirement: Semantic mouse gestures
The TUI SHALL derive component-local move, click, multi-click, drag, and release gestures from typed mouse actions. Gesture events SHALL include absolute terminal coordinates, local component coordinates, modifiers, committed bounds, button identity, and click count where applicable.

#### Scenario: Press and release produce click
- **WHEN** a primary press and release complete within the configured movement and timing bounds on the same target
- **THEN** that target receives one click gesture after release

#### Scenario: Movement produces drag
- **WHEN** a pressed pointer moves beyond the click threshold
- **THEN** the captured target receives drag gestures and no click is emitted for that press

#### Scenario: Repeated clicks report count
- **WHEN** consecutive clicks satisfy the configured multi-click boundary
- **THEN** semantic click events report increasing click counts without changing the raw input parser contract

### Requirement: Mouse capture and focus results
A mouse-capable component SHALL be able to report handled state, render intent, pointer capture, and keyboard focus intent as one typed result. Capture SHALL route later drag and release gestures to the captured component until release, removal, invisibility, stop, or explicit cancellation.

#### Scenario: Component captures drag
- **WHEN** a component handles pointer press and requests capture
- **THEN** later movement and release for that pointer sequence route to the component even when coordinates leave its bounds

#### Scenario: Component requests keyboard focus
- **WHEN** a component handles a pointer gesture and requests focus
- **THEN** the runtime applies focus through its serialized focus path before later keyboard input

#### Scenario: Captured component disappears
- **WHEN** the captured component is removed or no longer visible
- **THEN** capture is cancelled without routing later input to stale layout state

### Requirement: Reusable mouse region
The component library SHALL provide a `MouseRegion` wrapper that preserves its child's render output and retained geometry while delegating semantic mouse gestures to an application callback.

#### Scenario: Mouse region preserves child frame
- **WHEN** a `MouseRegion` wraps a component
- **THEN** direct and viewport rendering preserve the child's text, controls, cursor candidates, and bounds

#### Scenario: Region callback handles click
- **WHEN** a click targets the region and its callback reports handled state
- **THEN** ancestor mouse fallback stops and the callback's render, capture, and focus intents are applied

### Requirement: Pointer-targeted scrolling
Viewport wheel input SHALL first target the deepest eligible scroll view or mouse handler under the pointer, then chain unconsumed delta through ancestor scroll views according to overscroll policy. Holding Alt SHALL apply a documented accelerated scroll multiplier.

#### Scenario: Inner scroll consumes wheel
- **WHEN** the pointer is over an inner scroll view that can consume the complete wheel delta
- **THEN** no outer scroll view moves

#### Scenario: Alt wheel accelerates scrolling
- **WHEN** a viewport receives an Alt-modified wheel event
- **THEN** the target scroll view applies the documented accelerated line delta while preserving overscroll bounds
