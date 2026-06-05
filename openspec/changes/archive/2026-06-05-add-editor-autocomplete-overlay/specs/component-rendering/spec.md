## MODIFIED Requirements

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

## ADDED Requirements

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
