## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Final frame cursor marker handling
**Reason**: String cursor markers and final-frame marker scanning were removed and are fully superseded by structured frame-relative cursor metadata.
**Migration**: Emit `CursorPlacement` candidates in `ComponentRender.cursorPlacements`; use `Overlay cursor occlusion` to remove covered candidates and `Prepared and differential cursor rendering` for final selection and opt-in hardware cursor movement.
