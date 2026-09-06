## ADDED Requirements

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

#### Scenario: Selection crosses scroll boundary
- **WHEN** drag selection reaches a scroll-view edge and content remains beyond that edge
- **THEN** bounded edge scrolling extends selection through newly visible content

#### Scenario: Copy reports unsupported capability
- **WHEN** selection copy is requested and neither terminal nor host clipboard support is configured
- **THEN** the operation reports unsupported status without claiming success or emitting an unsupported escape sequence
