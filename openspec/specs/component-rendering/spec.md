# component-rendering Specification

## Purpose
TBD - created by archiving change scala-tui-port. Update Purpose after archive.
## Requirements
### Requirement: Component contract
The library SHALL expose a component abstraction whose render operation receives the available terminal width and returns ordered terminal lines. Each returned line MUST fit within the requested display width after ANSI and non-printing escape sequences are ignored.

#### Scenario: Component renders within width
- **WHEN** a component is rendered with width 40
- **THEN** every returned line has visible width less than or equal to 40

#### Scenario: Component cache invalidation
- **WHEN** a component stores cached render output and its state or theme changes
- **THEN** the component MUST invalidate cached output before the next render

### Requirement: Container composition
The library SHALL provide a container component that renders child components in insertion order and concatenates their rendered lines.

#### Scenario: Container renders children sequentially
- **WHEN** a container has two children that each render one line
- **THEN** the container renders two lines in the same order as the children

### Requirement: ANSI-aware text utilities
The library SHALL provide pure Scala utilities for measuring, slicing, truncating, wrapping, and padding terminal text while preserving ANSI SGR and OSC hyperlink state where required.

#### Scenario: ANSI styles do not affect width
- **WHEN** text contains ANSI color escape sequences around five printable characters
- **THEN** visible width returns 5

#### Scenario: Wrapped styled text preserves style
- **WHEN** styled text wraps across multiple terminal lines
- **THEN** each wrapped line contains the necessary escape sequences to render the intended style without leaking styling into later lines

### Requirement: Differential renderer
The TUI runtime SHALL render component output using a differential strategy that minimizes writes after the first frame while preserving terminal correctness.

#### Scenario: First render writes all lines
- **WHEN** the TUI renders for the first time
- **THEN** it writes the complete frame without clearing scrollback

#### Scenario: Changed tail re-renders partially
- **WHEN** only lines at or below the first changed line differ from the previous frame
- **THEN** the renderer moves to the first changed line, clears from cursor, and writes the changed tail

#### Scenario: Width change triggers full redraw
- **WHEN** terminal width changes between frames
- **THEN** the renderer performs a full redraw to prevent wrapping artifacts

### Requirement: Overlay composition
The TUI runtime SHALL support rendering overlay components above base content with configurable width, height, position, margins, visibility, and focus behavior.

#### Scenario: Overlay draws over base content
- **WHEN** an overlay is visible at a specified row and column
- **THEN** overlay lines replace the corresponding visible cells of base content for that frame

#### Scenario: Non-capturing overlay preserves focus
- **WHEN** a non-capturing overlay is shown
- **THEN** keyboard focus remains on the previous focused component

