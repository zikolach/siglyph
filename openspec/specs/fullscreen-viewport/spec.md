# fullscreen-viewport Specification

## Purpose
Defines height-aware alternate-screen viewport layout, scrolling, navigation, search, and selection.

## Requirements

### Requirement: Height-aware fullscreen viewport
The alternate-screen viewport renderer SHALL own a fixed terminal-height viewport and SHALL render an explicit layout root within the current positive terminal width and height. Existing width-only components SHALL remain usable as measured leaf nodes.

#### Scenario: Viewport fills terminal height
- **WHEN** an alternate-screen viewport renders a layout root in a terminal with positive dimensions
- **THEN** the committed viewport contains exactly the current terminal row count without changing the width-only component contract

#### Scenario: Width-only leaf remains usable
- **WHEN** a layout contains a component that only implements `render(width): ComponentRender`
- **THEN** layout measures its rendered rows and clips or allocates those rows within the assigned rectangle

### Requirement: Constrained stack layout
The viewport SHALL provide vertical and horizontal stack components with gap, basis, grow, shrink, minimum size, maximum size, alignment, and viewport-dependent visibility. Allocation SHALL remain deterministic and SHALL respect child bounds and terminal dimensions.

#### Scenario: Vertical stack allocates remaining height
- **WHEN** a vertical stack contains a growing transcript region and an intrinsic-height editor region
- **THEN** the editor receives its constrained intrinsic height and the transcript receives the remaining viewport rows

#### Scenario: Horizontal stack respects minimum size
- **WHEN** a horizontal stack has insufficient width for every intrinsic child size
- **THEN** shrink allocation does not reduce a child below its configured minimum size

#### Scenario: Responsive child is hidden
- **WHEN** a stack child's visibility predicate is false for the current viewport
- **THEN** that child receives no rectangle and consumes no gap or allocated size

### Requirement: Nested scroll views
The viewport SHALL provide vertical scroll views with bounded scroll position, optional follow-end behavior, primary designation, nested overscroll chaining, and hidden, automatic, or always-visible scrollbars.

#### Scenario: Follow-end tracks appended content
- **WHEN** content grows while a follow-end scroll view is following the end
- **THEN** its viewport remains aligned to the new content end

#### Scenario: Manual upward scroll disables following
- **WHEN** the user scrolls a follow-end view away from the content end
- **THEN** later appended content does not move that view until the user returns to the end

#### Scenario: Nested overscroll chains
- **WHEN** a targeted inner scroll view cannot consume the complete wheel or keyboard delta and allows chaining
- **THEN** the unconsumed delta is offered to its nearest eligible outer scroll view

#### Scenario: Scroll position clamps after shrink
- **WHEN** content height or viewport height changes so the current scroll position exceeds the new maximum
- **THEN** the scroll position is clamped without exposing rows outside content

### Requirement: Typed transcript navigation markers
The viewport SHALL accept semantic transcript markers only through a typed optional document-metadata provider. A prompt marker SHALL identify a normalized document row or offset before clipping, and layout SHALL translate it with the same geometry used for scrolling. Ordinary rendered strings and terminal-control-looking bytes SHALL create no marker authority.

#### Scenario: Typed prompt marker supports navigation
- **WHEN** scroll content provides typed prompt-start markers
- **THEN** previous-prompt and next-prompt navigation targets the nearest eligible marker in document order

#### Scenario: Marker survives viewport clipping
- **WHEN** a typed marker belongs to an offscreen document row
- **THEN** scrolling and navigation retain its document position without rendering a terminal escape sequence

#### Scenario: OSC-looking text stays inert
- **WHEN** ordinary component text contains OSC 133-looking bytes
- **THEN** those bytes follow ordinary sanitization and do not create a prompt marker

### Requirement: Fullscreen transcript navigation
The viewport renderer SHALL route page, half-page, single-line, document-edge, and semantic prompt navigation commands to the primary scroll view when no focused overlay or component consumes them.

#### Scenario: Page navigation scrolls primary transcript
- **WHEN** the primary scroll view has overflow and the viewport page-down command is invoked
- **THEN** it advances by the configured page amount without moving editor cursor state

#### Scenario: Focused overlay receives navigation first
- **WHEN** a focused visible overlay can consume a viewport navigation command
- **THEN** the overlay receives the command before primary transcript fallback

### Requirement: Fullscreen transcript search
The viewport SHALL provide incremental search over primary scroll-view content with current-match tracking, next and previous navigation, visible-match highlighting, and bounded work for unchanged large transcripts.

#### Scenario: Search opens and finds matches
- **WHEN** the search command opens search and the user enters a query present in transcript content
- **THEN** the viewport reports match count, highlights visible matches, and scrolls the current match into view

#### Scenario: Unchanged transcript reuses search index
- **WHEN** only viewport position changes while transcript content and query remain unchanged
- **THEN** search does not rescan every transcript row or highlight offscreen matches

#### Scenario: Closing search restores normal routing
- **WHEN** the user closes search
- **THEN** search input stops capturing keys and ordinary focused-component routing resumes

### Requirement: Scroll affordances
The viewport SHALL support proportional scrollbars and a configurable jump-to-end indicator for a primary follow-end scroll view. Pointer interaction SHALL use committed viewport geometry.

#### Scenario: Scrollbar thumb represents visible range
- **WHEN** scrollable content exceeds viewport height
- **THEN** the scrollbar thumb position and size represent the visible range within the full content

#### Scenario: Jump-to-end resumes following
- **WHEN** the primary follow-end view is away from the end and the user activates the jump-to-end indicator
- **THEN** the view moves to the content end and resumes follow-end behavior

### Requirement: Fullscreen text selection
The alternate-screen viewport SHALL support terminal-cell text selection across visible wrapped content, scrolling, and ANSI metadata without splitting wide grapheme cells. Selection copy SHALL be explicit and capability-aware.

#### Scenario: Drag selects visible text
- **WHEN** a primary-button drag crosses selectable viewport cells
- **THEN** the viewport records the corresponding text range without including ANSI metadata as copied text

#### Scenario: Plain click clears text selection
- **WHEN** an unmodified primary-button click without a drag is routed to the primary scroll view's text area, trailing space, or blank viewport row
- **THEN** the existing text selection is cleared and its highlight is repainted
- **AND** subsequent drags start a new selection while double and triple clicks still select words and lines
- **AND** scrollbar controls, jump-to-end indicators, child-handled clicks, and covering overlays retain their existing routing and focus behavior

#### Scenario: Selection crosses scroll boundary
- **WHEN** drag selection reaches a scroll-view edge and content remains beyond that edge
- **THEN** bounded edge scrolling extends selection through newly visible content

#### Scenario: Copy reports unsupported capability
- **WHEN** selection copy is requested and neither terminal nor host clipboard support is configured
- **THEN** the operation reports unsupported status without claiming success or emitting an unsupported escape sequence

### Requirement: Revision-aware atomic viewport publication

The shared viewport layout engine SHALL capture the scroll document revision before measurement and submit an immutable publication containing rendered rows, complete document metadata, child identity, revision, and measured geometry. ScrollView SHALL reject publications for a replaced child or invalidated revision before mutating final document state, and SHALL publish accepted metadata and selection mapping atomically within its component state boundary.

#### Scenario: Content changes while rendering

- **WHEN** a scroll document is replaced or its revision is invalidated during row rendering or metadata collection
- **THEN** the old publication SHALL NOT replace current metadata, selection mapping, or final geometry

#### Scenario: Content changes while measuring

- **WHEN** measuring a child replaces that child or invalidates its revision
- **THEN** ScrollView SHALL reject the old child's early geometry clamp

#### Scenario: Early clamping remains available

- **WHEN** current content geometry is measured before rendering a bounded row range
- **THEN** ScrollView SHALL clamp the offset before range rendering and retain that early clamp if later invalidation rejects final publication

#### Scenario: Existing provider implementation

- **WHEN** a viewport provider implements only the existing geometry, metadata, and text callbacks
- **THEN** default snapshot and conditional geometry methods SHALL delegate to those callbacks without requiring source changes

### Requirement: Focused lists consume page commands

SelectList and SettingsList SHALL handle default SelectPageUp and SelectPageDown commands by moving at least one and otherwise maxVisible filtered items, clamping selection without falling through to transcript paging.

#### Scenario: Focused list pages
- **WHEN** PageDown reaches a focused list
- **THEN** its selection advances by the configured page size and the primary transcript offset remains unchanged

#### Scenario: Empty or bounded page
- **WHEN** a page command reaches an empty list or a selection already at its bound
- **THEN** the list consumes the command without requesting a render

### Requirement: Explicit bounded wheel normalization

Core SHALL provide opt-in fixed or bounded accelerated wheel normalization using explicit monotonic event time, preserving default one-row and Alt-five-row behavior. Normalization SHALL occur once per original event, not per nested remainder. Already-accelerated sources SHALL support disabling velocity scaling.

#### Scenario: Burst and gesture reset
- **WHEN** accelerated events arrive less than five milliseconds apart, after a pause over 200 milliseconds, with reversed direction or backwards time
- **THEN** the event uses the base one-row step and clears velocity history, retaining the Alt multiplier

#### Scenario: Runtime policy installation
- **WHEN** an application sets TUI.wheelScrollPolicy and receives ordinary terminal wheel input
- **THEN** the runtime normalizes it once using its injected monotonic mouse clock before nested routing
- **AND** replacing the policy or starting a session resets gesture history

#### Scenario: Nested overscroll
- **WHEN** an explicitly normalized wheel event exceeds a child scroll range
- **THEN** Chain returns the exact unused signed delta for ancestor dispatch without further acceleration and Contain returns zero

### Requirement: Optional grapheme word joiners

ScrollView SHALL accept an optional bounded extra-character word selection policy while preserving default classification and document retention bounds. Double-click and word dragging SHALL use identical policy and complete grapheme boundaries.

#### Scenario: Path-friendly selection
- **WHEN** path-friendly policy is enabled and the user double-clicks a word containing dots, backslashes, tildes or plus signs
- **THEN** these joiners participate in the selected word without crossing whitespace or row boundaries

#### Scenario: Default and bounds remain
- **WHEN** no policy is supplied or document retention reaches its bound
- **THEN** existing punctuation behavior and complete-grapheme retention limits remain unchanged
