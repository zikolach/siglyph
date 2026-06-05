## ADDED Requirements

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
