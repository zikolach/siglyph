## ADDED Requirements

### Requirement: Component-adjacent overlay placement
Overlay placement support SHALL allow component-owned UI such as editor autocomplete to position overlays adjacent to the component's rendered area without forcing parent application code to compute absolute terminal rows manually.

#### Scenario: Component supplies adjacent placement
- **WHEN** a component can determine its rendered origin and visual height during rendering
- **THEN** it can derive overlay placement that starts immediately after its rendered area

#### Scenario: Adjacent placement clamps like other overlays
- **WHEN** component-adjacent placement would extend beyond terminal bounds
- **THEN** the overlay is clamped or clipped using the existing overlay bounds behavior

#### Scenario: Adjacent placement does not expand to full terminal height
- **WHEN** an adjacent overlay is visible above the terminal bottom
- **THEN** the renderer extends output only to the deepest required overlay row and does not pad the frame to the full terminal height

#### Scenario: Adjacent placement updates on resize
- **WHEN** a terminal resize changes the component's rendered height or position
- **THEN** the next render can update the adjacent overlay placement before compositing
