## MODIFIED Requirements

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

## ADDED Requirements

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
