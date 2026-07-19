# component-rendering Specification

## Purpose
Defines the component rendering contract, ANSI-aware text utilities, differential rendering behavior, focus flow, overlays, and editor visual layout expectations for terminal UI output.
## Requirements
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

### Requirement: Container composition
The library SHALL provide a container component that renders child components in insertion order and concatenates their rendered lines.

#### Scenario: Container renders children sequentially
- **WHEN** a container has two children that each render one line
- **THEN** the container renders two lines in the same order as the children

### Requirement: ANSI-aware text utilities
The library SHALL provide pure shared JVM/Native Scala utilities for measuring, slicing, truncating, wrapping, and padding terminal text while preserving supported ANSI SGR and OSC 8 hyperlink state where required. The utilities SHALL treat Unicode 17.0.0 UAX #29 default extended grapheme clusters as atomic text units and SHALL delegate text-boundary decisions to the shared segmenter. Only fully validated atomic ESC-form SGR and OSC 8 open/close metadata SHALL remain executable, and each complete sequence SHALL be limited to 4096 UTF-8 bytes including introducer and terminator. Every other CSI, OSC, APC, DCS, SOS, PM, unsupported ESC form, C0, DEL, and C1 value SHALL be visible inert text. Rejected string controls SHALL be consumed atomically through their defined terminator, or through the remaining input when unterminated, without rescanning embedded metadata-looking prefixes. Controls SHALL use uppercase `\uXXXX`; other code points SHALL remain exact. Application text SHALL remain unlimited.

#### Scenario: ANSI styles do not affect width
- **WHEN** text contains ANSI color escape sequences around five printable characters
- **THEN** visible width returns 5

#### Scenario: Wrapped styled text preserves style
- **WHEN** styled text wraps across multiple terminal lines
- **THEN** each wrapped line contains the necessary escape sequences to render the intended style without leaking styling into later lines

#### Scenario: ANSI geometry preserves grapheme clusters
- **WHEN** slicing, truncation, wrapping, or padding reaches a boundary inside a Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequence
- **THEN** the utility does not emit a partial Unicode 17.0.0 default extended grapheme cluster

#### Scenario: ANSI metadata surrounds a grapheme boundary
- **WHEN** SGR or OSC hyperlink metadata occurs within or adjacent to text that forms one default extended grapheme cluster
- **THEN** metadata remains non-printing and correctly preserved while text boundaries match the shared segmenter

#### Scenario: Display-width policy is preserved
- **WHEN** a complete default extended grapheme cluster is measured or placed by an ANSI utility
- **THEN** the utility applies the existing display-width policy without redefining width from UAX #29

#### Scenario: Exact metadata bound is accepted
- **WHEN** a supported complete metadata sequence is exactly 4096 UTF-8 bytes including introducer and terminator
- **THEN** it is recognized and remains non-printing

#### Scenario: Metadata above the bound is inert
- **WHEN** a complete metadata sequence is 4097 UTF-8 bytes
- **THEN** the complete sequence is visible inert text and no part executes or persists

#### Scenario: Metadata bound counts UTF-8 bytes
- **WHEN** non-ASCII OSC content reaches the boundary at different UTF-16 and UTF-8 lengths
- **THEN** acceptance uses complete-sequence UTF-8 bytes

#### Scenario: Unsupported SGR is atomic inert text
- **WHEN** an SGR is unsupported, private, malformed, or contains an out-of-range compound part
- **THEN** the complete source escape is visible inert text and effective SGR state is unchanged

#### Scenario: Effective SGR replay is normalized
- **WHEN** any number of supported SGR updates produce the same active attributes
- **THEN** replay contains only fixed effective fields ordered by their latest updates rather than source history

#### Scenario: Selective SGR reset
- **WHEN** a supported selective reset follows multiple active SGR fields
- **THEN** only its modeled fields are cleared and other fields remain active

#### Scenario: OSC closes and reopens at boundaries
- **WHEN** an active bounded OSC 8 hyperlink crosses slice or wrapped-line boundaries
- **THEN** each emitted boundary closes it and the next line reopens it only while source state remains active

#### Scenario: SGR and OSC ordering is preserved
- **WHEN** supported SGR and OSC 8 updates occur in either source order before a wrapped boundary
- **THEN** normalized replay preserves their relative latest-update order and does not leak after closure

#### Scenario: Metadata state and processing remain bounded and linear
- **WHEN** thousands or millions of supported SGR updates and a bounded OSC opener precede wrapped text
- **THEN** retained state is fixed-field plus one at-most-4096-byte opener and processing/output are `O(N + produced output)`

#### Scenario: Unterminated string control is emitted once
- **WHEN** an OSC, DCS, SOS, PM, or APC candidate is unterminated and contains later escape-looking prefixes
- **THEN** the remaining candidate is visible inert text once and suffixes are not rescanned or executed

#### Scenario: String controls use their defined terminators
- **WHEN** OSC contains BEL or ST
- **THEN** that control terminates the OSC candidate
- **AND WHEN** DCS, SOS, PM, or APC contains BEL before ST
- **THEN** BEL remains candidate content and only ST terminates the candidate
- **AND WHEN** that candidate has no later ST
- **THEN** the complete remaining candidate is visible inert text once and embedded escape-looking suffixes do not execute

#### Scenario: C1 string controls remain atomic and inert
- **WHEN** a C1-introduced OSC, DCS, SOS, PM, or APC contains supported SGR or OSC 8-looking payload
- **THEN** the complete candidate remains visible inert text
- **AND** ST may be the 7-bit `ESC \\` form or C1 ST

#### Scenario: Omitted clusters advance bounded metadata
- **WHEN** an omitted over-wide cluster contains recognized bounded SGR or OSC updates before later fitting text
- **THEN** later output receives the resulting normalized effective state

#### Scenario: Application text remains unlimited
- **WHEN** ordinary application text exceeds 4096 UTF-8 bytes
- **THEN** no metadata limit truncates, drops, replaces, or rejects that text

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
The editor SHALL scan each complete joined logical line once through the existing forward ANSI scanner and compute width-aware visual layout from sanitized final Unicode 17.0.0 UAX #29 graphemes. Every projected unit SHALL retain its original half-open source-grapheme owner range. Supported bounded SGR and OSC 8 SHALL remain executable atomic metadata with bounded replay/close state. Rejected controls MAY expand to several display units or rows with the same owner range, and every visible expansion character SHALL appear exactly once in order. Editor SHALL retain no projection globally and SHALL NOT use another parser, scan source graphemes separately, mutate EditorBuffer source, or infer ownership from output bytes.

#### Scenario: Rejected control expansion wraps completely
- **WHEN** a retained source grapheme sanitizes to several visible final graphemes
- **THEN** layout wraps every expansion grapheme exactly once and in order
- **AND** every resulting unit retains the source grapheme's half-open owner range

#### Scenario: Supported metadata spans source graphemes
- **WHEN** supported SGR or OSC 8 spans several source graphemes
- **THEN** one complete-line scan recognizes it atomically
- **AND** row output replays and closes bounded state as existing ANSI geometry does

#### Scenario: Cursor boundary is inside executable metadata
- **WHEN** a source cursor boundary lies inside supported executable metadata
- **THEN** layout maps it to a deterministic boundary outside the complete metadata
- **AND** focused inverse-video bytes do not divide the metadata

#### Scenario: Long logical line wraps
- **WHEN** an editor logical line exceeds the available render width
- **THEN** the editor layout wraps it into multiple visual lines that each fit within the requested width

#### Scenario: Cursor maps to wrapped visual position
- **WHEN** the logical cursor is on a wrapped logical line
- **THEN** the layout maps it to the correct visual row and display column

#### Scenario: Wide Unicode layout
- **WHEN** editor text contains CJK, combining marks, or emoji grapheme clusters
- **THEN** wrapping and cursor placement use display width rather than byte, code unit, or code point counts

#### Scenario: Complex clusters remain atomic
- **WHEN** editor text contains Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequences at a wrap or cursor boundary
- **THEN** layout, cursor placement, and fake-cursor rendering do not divide a Unicode 17.0.0 default extended grapheme cluster

#### Scenario: Editor layout preserves width policy
- **WHEN** the editor lays out a complete default extended grapheme cluster
- **THEN** it uses the existing display-width policy for that cluster

#### Scenario: Zero-width content precedes an over-wide cluster
- **WHEN** a buffered zero-width cluster is followed by an over-wide cluster and then fitting text
- **THEN** layout flushes the zero-width content before the over-wide blank owner row
- **AND** logical ownership, visual order, and cursor mapping follow source order

### Requirement: Editor fake cursor rendering
The editor SHALL render a visible fake cursor using inverse-video styling within the component output. When the cursor-owned complete grapheme cluster fits the requested width, existing fake-cursor behavior SHALL remain unchanged. When that cluster cannot fit atomically on an empty requested row, the editor SHALL omit its printable output while preserving logical ownership, deterministic zero-width cursor placement, and unchanged buffer text. It SHALL NOT emit a partial cluster, replacement glyph, fallback glyph, or over-wide line.

#### Scenario: Cursor on character
- **WHEN** the cursor is positioned before an existing grapheme cluster that fits the requested row
- **THEN** the editor renders that cluster with inverse-video styling
- **AND** it attaches the structured frame-relative cursor coordinate

#### Scenario: Cursor at line end
- **WHEN** the cursor is positioned at the end of a visual line with room for the fake cursor
- **THEN** the editor renders an inverse-video space at the cursor position
- **AND** it attaches the structured frame-relative cursor coordinate

#### Scenario: Cursor-owned cluster cannot fit atomically
- **WHEN** the cursor-owned complete grapheme cluster is wider than an otherwise empty requested row
- **THEN** the editor emits no printable cluster, partial cluster, replacement glyph, or fallback glyph for that row
- **AND** the blank visual row retains logical ownership `[column, column + 1)` and attaches structured cursor metadata at visual column zero
- **AND** the editor buffer text remains unchanged

#### Scenario: Cursor hidden when unfocused
- **WHEN** the editor is not focused
- **THEN** it renders text without inverse-video cursor output or cursor metadata

#### Scenario: Non-positive width suppresses Editor cursor
- **WHEN** Editor renders at width zero or below
- **THEN** it emits no printable output or cursor placement
- **AND** the returned frame validates at that width

#### Scenario: Width-zero child remains valid in Box
- **WHEN** a default-padded width-one Box renders a focused Editor with an over-wide source cluster
- **THEN** the translated outer frame remains valid without filtering candidates or weakening validation

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
- **WHEN** local rendering intentionally differs from upstream because of fake cursor, structured hardware cursor metadata, or typed component contracts
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

### Requirement: Strict ordinary-line terminal metadata allowlist
In ordinary `ComponentRender.lines`, only bounded validated atomic ESC-form SGR and OSC 8 open/close metadata SHALL remain executable. Rejected ESC-form and C1-introduced string controls SHALL remain one inert candidate through their defined terminator or the remaining unterminated input. Former cursor APC bytes SHALL remain visible inert text and SHALL NOT influence cursor metadata. Closed typed `TerminalRenderControl` values SHALL remain separate from ordinary strings and SHALL be encoded through the trusted semantic channel at the final TUI output boundary. Structured cursor metadata SHALL encode no protocol bytes and SHALL NOT use `TerminalRenderControl`.

#### Scenario: Rejected terminal controls are visible
- **WHEN** an ordinary rendered string contains private CSI, non-SGR CSI, non-OSC-8 OSC, APC, DCS, SOS, PM, unsupported ESC forms, C0, DEL, or C1 values
- **THEN** every control is visible uppercase `\uXXXX` text and no rejected metadata executes

#### Scenario: Typed image controls use the semantic channel
- **WHEN** a frame contains a valid typed Kitty or iTerm2 image control
- **THEN** ordinary-line sanitization does not convert it to inert text
- **AND** the TUI encodes it through the final semantic control path

#### Scenario: Former cursor APC bytes are inert
- **WHEN** direct lines, Input paste, or Editor paste contain the former cursor APC bytes in focused or unfocused output
- **THEN** sanitization renders those bytes as visible inert text
- **AND** complete or unterminated ESC-form and C1 string candidates remain inert
- **AND** a legitimate structured cursor candidate remains the only source of hardware cursor placement

### Requirement: Structured component cursor metadata
Shared JVM/Native core SHALL remove the public `CursorMarker` object, including `Sequence`, `Position`, `ScanResult`, and `stripAndLocate`. Direct migration SHALL use public `CursorPlacement(row: Int, column: Int)` values in `ComponentRender.cursorPlacements`; no compatibility API SHALL exist. Coordinates SHALL be non-negative, zero-based, frame-relative display cells with translation. `ComponentRender` SHALL require explicit `lines`, `controls`, and `cursorPlacements` fields. Text-only factories MAY construct explicit empty metadata. No default field, old arity, overload, adapter, conversion, or deprecation path SHALL exist.

#### Scenario: Cursor construction rejects negative coordinates
- **WHEN** a cursor placement or translation would produce a negative row or column
- **THEN** construction fails

#### Scenario: Cursor validation is bounded
- **WHEN** a cursor row is outside frame rows or its column is at least `max(0, width)`
- **THEN** frame validation fails before synchronized output
- **AND** diagnostics retain bounded geometry and no application text

#### Scenario: Ordinary strings have no cursor authority
- **WHEN** ordinary text contains terminal-control-looking or former cursor bytes
- **THEN** it creates no `CursorPlacement` and gains no cursor authority

### Requirement: Cursor geometry propagation
Frame composition SHALL translate cursor candidates with content geometry while keeping terminal controls separate. `ComponentFrameBuilder` SHALL apply only accumulated local row offsets to child candidates. Box SHALL add body-row and normalized horizontal-padding offsets.

#### Scenario: Builder origins remain notifications
- **WHEN** `ComponentFrameBuilder` uses `startRow` or `startCol`
- **THEN** those values notify `RenderOriginAware` components
- **AND** returned cursor candidates receive only accumulated local row offsets

#### Scenario: Box rebases cursor candidates
- **WHEN** a padded Box renders a child cursor candidate
- **THEN** the candidate receives the child body row and normalized horizontal padding offsets

### Requirement: Child-frame metadata confinement
Every child `ComponentRender` SHALL use child-local frame-relative geometry. A composing parent SHALL
validate both `TerminalControlPlacement` and `CursorPlacement` metadata against the child's own rows
and requested child width before translation or sibling composition. Invalid child metadata SHALL
fail with bounded redacted diagnostics; it SHALL NOT be moved, dropped, clipped, or made valid by
parent padding or sibling rows. Final composed-frame validation SHALL remain required.

#### Scenario: Builder rejects metadata before sibling composition
- **WHEN** `ComponentFrameBuilder.addRender` receives cursor or terminal-control metadata that is
  invalid for the child rows or builder width
- **THEN** validation fails before translation or appending the child
- **AND** no later sibling can supply rows that make the metadata valid

#### Scenario: Box rejects metadata before padding translation
- **WHEN** Box receives cursor or terminal-control metadata that is invalid for the child rows or
  `innerWidth`
- **THEN** validation fails before body-row or padding translation
- **AND** outer rows and width cannot make the child metadata valid

#### Scenario: Invalid child metadata fails before synchronized output
- **WHEN** malformed child metadata reaches TUI composition
- **THEN** it fails with existing bounded redacted diagnostics before synchronized terminal output

### Requirement: Overlay raw-frame validation and clipping
TUI SHALL validate each raw overlay frame against its raw rows and resolved overlay width before
configured max-height clipping. Invalid raw metadata SHALL fail and SHALL NOT be hidden by clipping
or parent rows. After raw validation, explicit max-height clipping SHALL remove valid metadata
anchored only in removed rows with those rows. Metadata surviving clipping and the final composed
frame SHALL still be validated.

#### Scenario: Invalid raw overlay metadata cannot be hidden
- **WHEN** a raw overlay cursor or terminal control is invalid for the raw rows or resolved width
- **THEN** TUI fails before max-height clipping, parent composition, or synchronized output

#### Scenario: Valid clipped-row metadata is removed
- **WHEN** valid raw overlay metadata is anchored only in rows removed by configured max-height
  clipping
- **THEN** TUI removes that metadata with the clipped rows
- **AND** retained metadata and the final composed frame still validate

### Requirement: Direct overlay compositor metadata confinement
`OverlayRenderer.composite` SHALL independently validate its base frame against terminal width,
every overlay frame against its own rows and resolved overlay width before translation or occlusion,
and the final composed frame against terminal width. Empty-overlay calls SHALL still validate the
base and final frame. Invalid cursor or terminal-control metadata SHALL fail with existing bounded
redacted diagnostics and SHALL NOT become valid through translation or parent composition. TUI raw,
clipped, and final validation SHALL remain unchanged.

#### Scenario: Empty overlay stack validates the base
- **WHEN** direct composition receives an invalid base frame and no overlays
- **THEN** it rejects the base against terminal width

#### Scenario: Overlay metadata is invalid in child-local geometry
- **WHEN** a direct overlay frame contains cursor or terminal-control metadata invalid for its rows
  or resolved overlay width
- **THEN** composition rejects it before translation or occlusion
- **AND** parent rows or terminal width cannot make it valid

#### Scenario: Final composition is invalid
- **WHEN** independently valid base and overlay metadata becomes invalid in the final composed frame
- **THEN** composition rejects the result against terminal width

### Requirement: Overlay cursor occlusion
Overlay composition SHALL process lower to higher opaque half-open rectangles. A higher rectangle SHALL remove covered lower cursor candidates and append translated surviving candidates from the higher frame. Non-overlapping and boundary candidates SHALL survive. Height clipping SHALL remove candidates in clipped rows.

#### Scenario: Higher overlay covers a lower cursor
- **WHEN** a lower candidate lies inside a higher overlay rectangle
- **THEN** the lower candidate is removed
- **AND** translated higher candidates are appended

#### Scenario: Half-open boundaries survive
- **WHEN** a lower candidate lies on the right or bottom boundary of a higher rectangle
- **THEN** the lower candidate survives

#### Scenario: Later lower candidate is a fallback
- **WHEN** a higher overlay removes an earlier lower candidate but does not cover a later one
- **THEN** the later candidate remains available for final selection

### Requirement: Prepared and differential cursor rendering
TUI frame preparation SHALL sanitize lines independently and select the first surviving row-major structured cursor candidate, preserving vector order for equal coordinates. Prepared-frame comparison SHALL retain the selected cursor. Cursor-only changes SHALL move the hardware cursor without repainting unchanged lines or typed controls. Disabled hardware positioning SHALL emit no hardware cursor movement.

#### Scenario: Cursor-only differential update
- **WHEN** only the selected cursor changes
- **THEN** the TUI moves the hardware cursor without repainting lines or typed controls

#### Scenario: Hardware positioning is disabled
- **WHEN** cursor metadata exists and hardware positioning is disabled
- **THEN** the TUI emits no hardware cursor movement

#### Scenario: Typed controls precede final cursor positioning
- **WHEN** a frame contains typed controls and a structured cursor
- **THEN** typed controls retain semantic output order
- **AND** final hardware cursor positioning occurs after frame content

### Requirement: SelectList reports precise handling results
The `SelectList` component SHALL return `Ignored` when no action matches an input event, `NoRender` when a recognized action requires no repaint, and `Render` when an action mutates repaint-requiring selection or filter state or invokes an existing callback whose current contract requires repaint. Result precision SHALL NOT change callback conditions or callback cardinality and SHALL NOT imply parent input bubbling.

#### Scenario: Unsupported input is ignored
- **WHEN** `SelectList` receives a terminal input event that matches no supported or configured action
- **THEN** it returns `Ignored` without invoking selection or activation callbacks

#### Scenario: Recognized boundary navigation needs no render
- **WHEN** `SelectList` recognizes a navigation action but selection and visible state remain unchanged at a boundary
- **THEN** it returns `NoRender` and does not add a callback invocation

#### Scenario: Selection mutation requests render
- **WHEN** `SelectList` navigation changes the selected item
- **THEN** it returns `Render` and invokes the existing selection-change callback exactly as before

#### Scenario: Filter mutation requests render
- **WHEN** `SelectList` handles input that changes its visible filter state
- **THEN** it returns `Render` while preserving existing filtering and callback conditions

#### Scenario: Activation preserves callback behavior
- **WHEN** `SelectList` receives its configured activation action
- **THEN** it returns the result required by the existing activation behavior and invokes the activation callback under exactly the existing conditions and number of times

#### Scenario: Ignored does not promise parent routing
- **WHEN** `SelectList` returns `Ignored`
- **THEN** the result states only that `SelectList` did not handle the event and does not claim that a parent component receives it

### Requirement: Retained component bounds tree
The TUI runtime SHALL retain a component bounds tree for the latest rendered frame without changing the existing `Component.render(width): ComponentRender` output contract.

#### Scenario: Leaf component receives bounds
- **WHEN** a component is rendered through the layout-aware render path
- **THEN** the retained bounds tree records that component with row, column, width, and height for its rendered output

#### Scenario: Existing render contract remains valid
- **WHEN** an existing component implements only `render(width): ComponentRender`
- **THEN** it continues to render as a leaf component in the retained bounds tree

#### Scenario: Bounds update after rerender
- **WHEN** a component renders at a different row, column, width, or height on a later frame
- **THEN** the retained bounds tree reflects the later frame only

### Requirement: Nested container bounds
Container-style components, including padded vertical `Box` composition, SHALL record nested child bounds in visual render order using the same normalized geometry as their rendered output.

#### Scenario: Vertical container records child rows
- **WHEN** a vertical container renders two child components with heights 2 and 3 from row 0
- **THEN** the retained bounds tree records the first child at row 0 and the second child at row 2

#### Scenario: Nested container records descendants
- **WHEN** a container renders another container that renders a child component
- **THEN** the retained bounds tree includes the descendant child with terminal-relative bounds

#### Scenario: Padded Box translates descendant bounds
- **WHEN** a `Box` with normalized horizontal and vertical padding renders a child or nested container
- **THEN** the retained tree records every descendant at the padded column and accumulated padded body row used for its visible output

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

