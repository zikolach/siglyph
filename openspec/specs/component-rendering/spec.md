# component-rendering Specification

## Purpose
Defines the component rendering contract, ANSI-aware text utilities, differential rendering behavior, focus flow, overlays, and editor visual layout expectations for terminal UI output.
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
The TUI runtime SHALL support rendering overlay components above base content with configurable width, maximum height, hybrid position, margins, visibility, z-order, and focus behavior. Overlay layout MUST be re-resolved every render against the current terminal dimensions, and final placement MUST be clamped to terminal bounds instead of producing invalid output.

#### Scenario: Overlay draws over base content
- **WHEN** an overlay is visible at a specified row and column
- **THEN** overlay lines replace the corresponding visible cells of base content for that frame

#### Scenario: Non-capturing overlay preserves focus
- **WHEN** a non-capturing overlay is shown
- **THEN** keyboard focus remains on the previous focused component

#### Scenario: Hybrid positioning resolves like pi-tui
- **WHEN** an overlay specifies absolute or percentage row and column values, an anchor, offsets, and margins
- **THEN** absolute row and column values take precedence over percentage values, percentage values take precedence over anchor-derived values, offsets are applied, and the final position is clamped by margins and terminal bounds

#### Scenario: Overlay width and height are constrained
- **WHEN** an overlay specifies width, minimum width, or maximum height options
- **THEN** the runtime resolves those values against the current terminal dimensions and renders or clips the overlay within the available viewport

#### Scenario: Top overlay replaces lower overlay cells
- **WHEN** multiple visible overlays overlap
- **THEN** overlays are composited from lower to higher z-order and the top overlay replaces cells from lower overlays and base content within its rectangle

#### Scenario: Overlay spaces are literal cells
- **WHEN** an overlay line contains visible spaces
- **THEN** those spaces replace base content cells rather than acting as transparent background

#### Scenario: Hidden overlay is not composited
- **WHEN** an overlay handle temporarily hides an overlay
- **THEN** the overlay is omitted from rendering until it is shown again

#### Scenario: No overlay preserves base height
- **WHEN** no visible overlays are being composited
- **THEN** the renderer preserves the base component line count instead of padding output to the terminal height

#### Scenario: Initial start preserves scrollback
- **WHEN** an interactive TUI starts and renders its first frame
- **THEN** it writes the first frame without emitting terminal clear-scrollback sequences

#### Scenario: Overlay extends only to required rows
- **WHEN** visible overlays are composited at resolved rows above the terminal bottom
- **THEN** the renderer extends output only to the deepest overlay row instead of padding every overlay frame to the full terminal height

#### Scenario: Visibility predicate controls rendering
- **WHEN** an overlay visibility predicate returns false for the current terminal dimensions
- **THEN** the overlay is not composited for that frame and does not capture input

#### Scenario: Overlay line is ANSI-safely clipped
- **WHEN** an overlay component returns a line wider than the resolved overlay width
- **THEN** the runtime clips or truncates the overlay line using visible width while preserving terminal escape correctness

### Requirement: Interactive render scheduling
The TUI runtime SHALL coalesce render requests generated by input, resize, and application state changes so live applications avoid redundant immediate renders while still updating promptly.

#### Scenario: Multiple render requests coalesce
- **WHEN** several render requests are made before the scheduler flushes
- **THEN** the runtime performs at most one render for that batch

#### Scenario: Input requests render
- **WHEN** a focused component handles a terminal input event and mutates its state
- **THEN** the runtime schedules a render after the input handler completes

### Requirement: Interactive focus flow
The TUI runtime SHALL route live terminal input to the focused component and expose an application-level way to change focus during event handling.

#### Scenario: Focused input receives typed characters
- **WHEN** an `Input` component is focused and the user types printable characters
- **THEN** only the focused input receives those typed events and the rendered value updates

#### Scenario: Focus changes between components
- **WHEN** application logic changes focus from one component to another
- **THEN** the old focusable component is marked unfocused and the new focusable component is marked focused before the next render

### Requirement: Interactive stop positioning
The TUI runtime SHALL leave the terminal in a readable state after stopping an interactive session.

#### Scenario: Stop moves below rendered content
- **WHEN** the TUI stops after rendering content
- **THEN** it positions the cursor below the rendered content before returning control to the shell

### Requirement: Editor visual layout
The editor SHALL compute a width-aware visual layout that maps logical buffer lines and cursor positions to wrapped terminal lines.

#### Scenario: Long logical line wraps
- **WHEN** an editor logical line exceeds the available render width
- **THEN** the editor layout wraps it into multiple visual lines that each fit within the requested width

#### Scenario: Cursor maps to wrapped visual position
- **WHEN** the logical cursor is on a wrapped logical line
- **THEN** the layout maps it to the correct visual row and display column

#### Scenario: Wide Unicode layout
- **WHEN** editor text contains CJK, combining marks, or emoji grapheme clusters
- **THEN** wrapping and cursor placement use display width rather than byte, code unit, or code point counts

### Requirement: Editor fake cursor rendering
The editor SHALL render a visible fake cursor using inverse-video styling within the component output.

#### Scenario: Cursor on character
- **WHEN** the cursor is positioned before an existing grapheme cluster
- **THEN** the editor renders that cluster with inverse-video styling

#### Scenario: Cursor at line end
- **WHEN** the cursor is positioned at the end of a visual line
- **THEN** the editor renders an inverse-video space at the cursor position

#### Scenario: Cursor hidden when unfocused
- **WHEN** the editor is not focused
- **THEN** it renders text without the inverse-video cursor marker

### Requirement: Editor render width contract
The rendered editor SHALL satisfy the component render contract for all output lines.

#### Scenario: Rendered editor lines fit width
- **WHEN** the editor is rendered at a requested width
- **THEN** every returned line has visible width less than or equal to that width after ANSI and non-printing escape sequences are ignored

### Requirement: Component width contract with runtime safety net
Components SHALL continue to render lines within the requested width, and the TUI runtime SHALL protect terminal sessions from final over-wide output.

#### Scenario: Component contract remains testable
- **WHEN** a project component is rendered directly in tests
- **THEN** every returned line is expected to have visible width less than or equal to the requested width

#### Scenario: Runtime clamps violating final output
- **WHEN** a component violates the width contract during a TUI render
- **THEN** the runtime sanitizes the final output before writing rather than crashing the terminal session

### Requirement: Narrow-width demo rendering
Project interactive demos SHALL render safely at narrow terminal widths.

#### Scenario: Interactive demo renders at narrow widths
- **WHEN** the shared interactive demo is rendered at widths including 1, 10, 22, 40, and 80
- **THEN** every line written by the TUI has visible width less than or equal to the active terminal width

#### Scenario: Static labels fit narrow widths
- **WHEN** demo headings, control hints, and section labels exceed the requested width
- **THEN** they are wrapped, truncated, or replaced with width-safe fallback text

### Requirement: ANSI-safe truncation for sanitized output
Runtime sanitization SHALL preserve terminal escape correctness while limiting visible width.

#### Scenario: Styled over-wide line is sanitized
- **WHEN** an over-wide rendered line contains ANSI styling or non-printing escape sequences
- **THEN** truncation accounts for visible width rather than byte or code-unit length and does not leak styling into following lines

#### Scenario: Unicode over-wide line is sanitized
- **WHEN** an over-wide rendered line contains CJK, combining marks, or emoji grapheme clusters
- **THEN** truncation avoids splitting visible grapheme clusters where the Unicode utilities can identify them

### Requirement: Overlay handle lifecycle
The TUI runtime SHALL return an overlay handle when an overlay is shown, and that handle SHALL control the overlay's permanent removal, temporary hidden state, focus state, and render scheduling.

#### Scenario: Handle hides overlay permanently
- **WHEN** an application calls hide on an overlay handle
- **THEN** the overlay is removed from the stack, focus is restored to an eligible target, and a render is requested

#### Scenario: Handle toggles hidden state
- **WHEN** an application toggles an overlay handle hidden state
- **THEN** the overlay remains owned by the handle but is included or omitted from rendering according to the hidden value

#### Scenario: Handle focuses overlay
- **WHEN** an application focuses a visible capturing overlay through its handle
- **THEN** that overlay receives keyboard focus and is brought to the top visual order

