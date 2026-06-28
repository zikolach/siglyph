## ADDED Requirements

### Requirement: Retained component bounds tree
The TUI runtime SHALL retain a component bounds tree for the latest rendered frame without changing the existing `Component.render(width): Vector[String]` output contract.

#### Scenario: Leaf component receives bounds
- **WHEN** a component is rendered through the layout-aware render path
- **THEN** the retained bounds tree records that component with row, column, width, and height for its rendered output

#### Scenario: Existing render contract remains valid
- **WHEN** an existing component implements only `render(width): Vector[String]`
- **THEN** it continues to render as a leaf component in the retained bounds tree

#### Scenario: Bounds update after rerender
- **WHEN** a component renders at a different row, column, width, or height on a later frame
- **THEN** the retained bounds tree reflects the later frame only

### Requirement: Nested container bounds
Container-style components SHALL record nested child bounds in visual render order when they compose child component output.

#### Scenario: Vertical container records child rows
- **WHEN** a vertical container renders two child components with heights 2 and 3 from row 0
- **THEN** the retained bounds tree records the first child at row 0 and the second child at row 2

#### Scenario: Nested container records descendants
- **WHEN** a container renders another container that renders a child component
- **THEN** the retained bounds tree includes the descendant child with terminal-relative bounds

### Requirement: Overlay bounds retention
Overlay rendering SHALL retain final overlay bounds after size resolution, clamping, clipping, and z-order composition.

#### Scenario: Clamped overlay records final bounds
- **WHEN** an overlay position is clamped to terminal bounds during rendering
- **THEN** the retained overlay bounds use the clamped row and column

#### Scenario: Clipped overlay records visible height
- **WHEN** an overlay is clipped by maximum height or terminal height
- **THEN** the retained overlay bounds height matches the visible rendered overlay height

#### Scenario: Overlay z-order is retained
- **WHEN** multiple visible overlays are rendered
- **THEN** the retained layout preserves their visual order from lowest to highest

### Requirement: Bounds use display-cell geometry
Component and overlay bounds SHALL use terminal display-cell rows and columns, independent of ANSI escape bytes and Unicode code units.

#### Scenario: ANSI styling does not change bounds width
- **WHEN** a component renders styled text that has ANSI escape sequences
- **THEN** retained bounds use requested display width and rendered display height rather than raw string length

#### Scenario: Wide Unicode cells remain within bounds
- **WHEN** rendered output contains CJK, emoji, or combining-mark grapheme clusters
- **THEN** retained bounds still describe terminal display cells and do not split wide visible cells
