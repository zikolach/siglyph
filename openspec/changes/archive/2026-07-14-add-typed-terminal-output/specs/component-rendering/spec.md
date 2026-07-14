## MODIFIED Requirements

### Requirement: Component contract
The library SHALL expose a component abstraction whose render operation receives the available terminal width and returns `ComponentRender` containing ordered ordinary terminal lines and positioned semantic terminal controls. Each ordinary line and each control footprint MUST fit within the requested display width, and controls MUST fit within the returned frame rows.

#### Scenario: Component renders within width
- **WHEN** a component is rendered with width 40
- **THEN** every returned ordinary line has visible width less than or equal to 40 and every control footprint ends at or before display column 40

#### Scenario: Component cache invalidation
- **WHEN** a component stores cached render output and its state or theme changes
- **THEN** the component MUST invalidate cached ordinary lines and controls before the next render

#### Scenario: Text-only component uses one contract
- **WHEN** a component has no semantic terminal control
- **THEN** it returns a text-only `ComponentRender` rather than a legacy line vector or parallel render method

## ADDED Requirements

### Requirement: Typed frame composition preserves controls
Vertical component composition SHALL concatenate ordinary lines and rebase each child control placement without converting controls to strings or copying payload content.

#### Scenario: Container renders children sequentially with controls
- **WHEN** a container renders text, an image control with reserved rows, and later text
- **THEN** the result preserves child order, rebases the image control to its final row, and places later text after all reserved rows

#### Scenario: Frame builder keeps frame-local coordinates
- **WHEN** `ComponentFrameBuilder` appends child frames while configured with a non-zero render-awareness origin
- **THEN** it rebases child controls only by locally accumulated child rows, advances by child line count, and uses `startRow` and `startCol` only for `RenderOriginAware` notification

### Requirement: Overlay composition preserves control geometry
Overlay composition SHALL carry visible overlay controls into final frame coordinates and SHALL prevent lower controls from executing through cells replaced by a higher overlay.

#### Scenario: Overlay control is rebased
- **WHEN** an overlay component contains a valid typed control and resolves to a non-zero row and column
- **THEN** the final frame places that control at the resolved origin plus its component-relative placement

#### Scenario: Higher overlay covers lower control
- **WHEN** a higher overlay rectangle intersects the declared footprint of a lower typed control
- **THEN** the lower control is absent from the final frame and does not execute through the overlay

#### Scenario: Overlay clipping cannot emit partial control
- **WHEN** overlay bounds would expose only part of a typed control footprint
- **THEN** the partial control is not encoded and rendering reports the invalid surviving placement before terminal output

### Requirement: Box padding uses one normalized geometry
`Box` SHALL normalize `paddingX` and `paddingY` to non-negative values once and SHALL use the same normalized values for child width, ordinary line padding, control row and column translation, and final frame size.

#### Scenario: Negative padding is zero
- **WHEN** a box with negative horizontal or vertical padding renders a child containing a typed image control
- **THEN** the box treats each negative value as zero consistently for child text, control placement, and frame geometry
