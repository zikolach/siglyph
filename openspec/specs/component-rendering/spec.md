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
The TUI runtime SHALL coalesce render requests, serialize component rendering through one drain owner, and invoke no component or application callback while holding lifecycle or terminal-write locks.

#### Scenario: Multiple render requests coalesce
- **WHEN** several requests arrive before the drain renders
- **THEN** at most one render uses the strongest pending force or clear intent

#### Scenario: Reentrant flush does not recurse
- **WHEN** a callback requests and flushes rendering while the current thread owns the drain
- **THEN** follow-up rendering is recorded and processed after that callback

#### Scenario: Concurrent flush does not wait
- **WHEN** another thread owns the drain
- **THEN** flush records work and returns without waiting for application code

#### Scenario: Query callback remains serialized with rendering
- **WHEN** a terminal query completion and render work are pending
- **THEN** the same drain owner executes them in scheduled order without callback/render concurrency

### Requirement: Interactive focus flow
The TUI runtime SHALL route live terminal input to the focused component and expose an application-level way to change focus during event handling.

#### Scenario: Focused input receives typed characters
- **WHEN** an `Input` component is focused and the user types printable characters
- **THEN** only the focused input receives those typed events and the rendered value updates

#### Scenario: Focus changes between components
- **WHEN** application logic changes focus from one component to another
- **THEN** the old focusable component is marked unfocused and the new focusable component is marked focused before the next render

### Requirement: Interactive stop positioning
The runtime SHALL leave the terminal readable after stopping. Retained query callbacks SHALL complete before cleanup and `Stopped`, while queued ordinary render work SHALL be discarded.

#### Scenario: Stop discards queued render
- **WHEN** stop begins while render work is queued but not active
- **THEN** that ordinary render is not executed after stop

#### Scenario: Cleanup positions cursor after retained callbacks
- **WHEN** stop has accepted query completion callbacks
- **THEN** the owner invokes them before terminal cleanup, cursor positioning, and lifecycle `Stopped`

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

### Requirement: Final frame cursor marker handling
The renderer SHALL treat cursor markers as zero-width runtime metadata during final frame preparation, after base content and overlays have been composited.

#### Scenario: Overlay composition determines marker visibility
- **WHEN** a base component emits a cursor marker but a visible overlay covers that cell in the final composited frame
- **THEN** hardware cursor marker scanning uses only the marker sequences that remain in the final composited output

#### Scenario: Marker does not affect line width contract
- **WHEN** a component line contains a cursor marker before a fake cursor token
- **THEN** component and runtime visible-width calculations treat the marker as non-printing metadata

#### Scenario: Differential rendering compares cleaned frames
- **WHEN** the TUI stores frame state for later differential rendering
- **THEN** it stores and compares marker-stripped lines so marker metadata does not leak into viewport snapshots or cause marker-only redraws

### Requirement: SelectList rich configuration
The `SelectList` component SHALL expose options and theme hooks for selected/unselected prefixes, selected text styling, description styling, no-match text, scroll information, and primary-label truncation while preserving width-safe rendering.

#### Scenario: SelectList applies selected theme
- **WHEN** a `SelectList` renders the selected item with a configured selected-text theme
- **THEN** the selected row includes that styled output and remains within the requested visible width

#### Scenario: SelectList reports selection changes
- **WHEN** keyboard navigation changes the selected item
- **THEN** the component invokes a selection-change callback with the newly highlighted item

#### Scenario: SelectList filters items
- **WHEN** a filter query is applied to a `SelectList`
- **THEN** only matching items are rendered and an empty result renders the configured no-match text

### Requirement: Selector fuzzy filtering
Selector-style components SHALL be able to use the shared fuzzy ranking utility to filter and order candidate rows while retaining deterministic behavior for equal scores.

#### Scenario: Fuzzy filter ranks item labels
- **WHEN** a selector filters candidates with a fuzzy query
- **THEN** matching labels are ordered by fuzzy score and stable input order is preserved for equal scores

#### Scenario: Fuzzy filtering remains optional
- **WHEN** a selector is configured for simple containment or no filtering
- **THEN** it does not apply fuzzy ranking to item order

### Requirement: SettingsList submenus
The `SettingsList` component SHALL support settings rows that open application-provided submenu components through the existing component and overlay contracts rather than through a platform-specific runtime.

#### Scenario: Enter opens submenu row
- **WHEN** the selected settings row defines a submenu component and the user activates it with Enter
- **THEN** the settings list requests or exposes that submenu component without cycling a scalar value

#### Scenario: Submenu selection updates setting
- **WHEN** an application-provided submenu returns a selected value for a settings row
- **THEN** the settings list updates that row and invokes the existing change callback with the row id and new value

#### Scenario: Escape cancels submenu
- **WHEN** a settings submenu is visible and the user cancels it
- **THEN** focus returns to the settings list and the setting value remains unchanged

### Requirement: SettingsList fuzzy ranking
The `SettingsList` component SHALL support optional fuzzy filtering across id, label, current value, and description in addition to its existing dependency-free containment filtering.

#### Scenario: Fuzzy query ranks settings rows
- **WHEN** fuzzy filtering is enabled and the user enters a query
- **THEN** rows matching the query are rendered in fuzzy-ranked order while preserving width-safe row rendering

#### Scenario: Existing containment filtering remains available
- **WHEN** fuzzy filtering is disabled and filtering is enabled
- **THEN** settings rows continue using the existing case-insensitive containment behavior

### Requirement: Tab display width parity
ANSI-aware text utilities and component rendering SHALL treat tab characters as display width 3 unless existing project documentation explicitly defines a different width before implementation.

#### Scenario: Visible width counts tab as three columns
- **WHEN** visible width is measured for text containing one tab character
- **THEN** that tab contributes 3 display columns

#### Scenario: Wrapped text accounts for tabs
- **WHEN** text containing tabs is wrapped to a requested width
- **THEN** each wrapped line fits the requested width using tab width 3

#### Scenario: Truncated text accounts for tabs
- **WHEN** text containing tabs is truncated to a requested width
- **THEN** the output visible width is less than or equal to the requested width using tab width 3

### Requirement: Wide-cell slicing remains terminal-safe
ANSI-aware slicing and truncation SHALL avoid emitting partial wide grapheme clusters when a slice boundary falls inside a wide visible cell.

#### Scenario: Slice starts inside wide cell
- **WHEN** a slice start column falls inside a CJK wide cell
- **THEN** the slice output does not emit a partial grapheme cluster for that cell

#### Scenario: Slice ends inside wide cell
- **WHEN** a slice end column falls inside a CJK wide cell
- **THEN** the slice output does not emit a partial grapheme cluster for that cell

#### Scenario: ANSI style is preserved across slice
- **WHEN** styled text is sliced around wide grapheme clusters
- **THEN** required ANSI state is preserved for emitted text and does not leak styling into later output

### Requirement: Overlay composition handles wide-cell boundaries
Overlay composition SHALL produce valid terminal output when an overlay begins, ends, or overlaps inside the display columns occupied by a wide base cell.

#### Scenario: Overlay begins inside base wide cell
- **WHEN** an overlay starts at the second column of a two-column CJK base cell
- **THEN** the final composited line contains valid cell-aligned output without a split wide glyph

#### Scenario: Overlay ends before base wide cell completes
- **WHEN** an overlay replacement would expose only part of a wide base cell
- **THEN** the final composited line does not emit a partial wide glyph

#### Scenario: Styled overlay over wide base remains width-safe
- **WHEN** a styled overlay covers wide-cell base content
- **THEN** the final line remains ANSI-safe and visible width does not exceed terminal width

### Requirement: Shrink and narrow rendering remain safe
Component rendering and runtime sanitization SHALL remain safe at zero, one-column, and narrow positive widths.

#### Scenario: Zero requested width returns safe output
- **WHEN** an internal text utility receives zero requested width
- **THEN** it returns safe empty or minimal output instead of throwing

#### Scenario: One-column render remains width-safe
- **WHEN** components or overlays are rendered at width 1
- **THEN** final output lines have visible width less than or equal to 1

#### Scenario: Shrinking terminal does not corrupt frame
- **WHEN** a rendered frame is followed by a much narrower terminal width
- **THEN** the next render emits valid width-safe terminal output

### Requirement: Editor visual layout micro-parity audit
The project SHALL audit upstream editor visual layout behaviors against local component rendering tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Visual layout behavior is already covered
- **WHEN** upstream editor wrapping, cursor placement, or Unicode layout behavior has an equivalent local test and matching behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Visual layout lacks only a test
- **WHEN** local editor rendering behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Visual layout behavior gap is found
- **WHEN** local editor rendering differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Visual layout intentional deviation is found
- **WHEN** local rendering intentionally differs from upstream because of fake cursor, hardware cursor marker, or typed component contracts
- **THEN** the audit records intentional deviation and updates porting notes

### Requirement: Resize frame consistency
Each render attempt SHALL snapshot positive dimensions and resize generation, and SHALL reject a candidate known to be stale immediately before output or differential-state mutation.

#### Scenario: Resize invalidates candidate
- **WHEN** width, height, or generation changes during frame computation
- **THEN** the candidate is discarded, the committed baseline is preserved, and a forced redraw is scheduled

#### Scenario: Resize consumes no ingress capacity
- **WHEN** resize notifications arrive while ordinary ingress is full
- **THEN** resize invalidation is coalesced without occupying or dropping an ingress event

### Requirement: Root structural mutations are drain-owned
Accepted TUI root additions, removals, and clears SHALL update immutable desired entries and commit root mutations and context hooks on the drain owner in publication order outside runtime locks.

#### Scenario: Duplicate occurrence identity is preserved
- **WHEN** the same component instance is added more than once and removed
- **THEN** removal targets the first desired occurrence by identity and context changes only on committed count transitions

#### Scenario: Clear preserves later additions
- **WHEN** clear is published before a later add
- **THEN** clear removes occurrences committed at its operation point and the later add survives

#### Scenario: Structural hook fails
- **WHEN** attach or detach throws
- **THEN** the committed mutation remains, later ordinary work is discarded, failure is recorded, and cleanup proceeds

### Requirement: Ordinary work selection is deterministic and fair
The runtime SHALL retain one serialized owner, prioritize retained query completions and stop or cleanup progression as urgent work, and select ordinary Structural, Action, Ingress, Control, and Render work in deterministic cyclic order. Queued categories SHALL remain FIFO, and Render SHALL remain coalesced.

#### Scenario: Continuously ready ordinary category is bounded
- **WHILE** ordinary categories remain continuously ready
- **WHEN** the owner selects ordinary work
- **THEN** the runtime services each category within five ordinary selections

#### Scenario: Urgent work precedes ordinary work
- **WHILE** retained query completions or stop or cleanup progression is ready
- **WHEN** the owner selects work
- **THEN** it selects urgent work before an ordinary category without creating a second owner

### Requirement: Structural claim and overlay restoration are publication-ordered
The owner SHALL commit structural model state atomically when claiming Structural work, invoke root mutation and hooks outside runtime locks, and capture overlay restoration focus in owner publication order.

#### Scenario: Structural work is claimed
- **WHEN** the owner claims an accepted structural operation
- **THEN** committed model state changes atomically before root mutation or hooks run outside locks

#### Scenario: Overlay restoration focus is captured
- **WHEN** overlay and focus work is published in an owner-defined order
- **THEN** restoration uses the focus captured at the overlay operation's publication point and later work does not retroactively change it

### Requirement: Filter paste render scheduling is completion-based
SelectList and SettingsList SHALL return `NoRender` for accepted paste chunks and SHALL request at most one render when a non-empty paste session commits.

#### Scenario: Multi-chunk fuzzy paste
- **WHILE** fuzzy filter paste chunks arrive
- **WHEN** rendering occurs after a chunk is accepted
- **THEN** the component shows accepted query text where it exposes a query prompt, filters and renders candidates from the committed query, and performs no intermediate selection callback or clamp-visible state change

#### Scenario: Final filter commit
- **WHEN** a non-empty filter paste ends
- **THEN** final filtering and selection or clamp update occur once and at most one render is requested
