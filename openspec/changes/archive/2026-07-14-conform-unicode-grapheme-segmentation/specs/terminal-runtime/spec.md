## MODIFIED Requirements

### Requirement: Buffered terminal input
The terminal runtime SHALL buffer incomplete raw input framing and emit bounded ordered typed input events without retaining complete paste or raw streams.

#### Scenario: Split escape sequence
- **WHEN** a terminal backend receives an arrow-key escape sequence split across multiple read chunks
- **THEN** the runtime emits one typed arrow-key event after the complete sequence is available

#### Scenario: Split bracketed paste
- **WHEN** bracketed paste start, content, and end markers arrive across multiple chunks
- **THEN** the runtime emits `PasteStart`, zero or more bounded `PasteChunk` events containing every content byte exactly once, and `PasteEnd`

#### Scenario: Incomplete escape timeout
- **WHEN** an escape sequence remains incomplete beyond the configured buffer timeout
- **THEN** the runtime flushes the incomplete data as bounded raw or best-effort input without blocking future input forever

## REMOVED Requirements

### Requirement: Marker-driven hardware cursor positioning
**Reason**: Hardware cursor positioning remains supported, but rendered strings are no longer scanned for cursor markers. Positioning now consumes selected structured frame metadata.
**Migration**: Components SHALL populate `ComponentRender.cursorPlacements`; component composition SHALL propagate and occlude those candidates; `Prepared and differential cursor rendering` SHALL select the surviving placement and apply the public opt-in option. No marker compatibility path exists.

### Requirement: Cursor marker scanning is ANSI and Unicode aware
**Reason**: There are no cursor markers to locate or strip. Cursor columns are supplied directly as display-cell coordinates, and ordinary strings have no cursor authority.
**Migration**: Compute cursor locations as structured `CursorPlacement` values, preserve them through component geometry, and use `Structured component cursor metadata` plus `Prepared and differential cursor rendering` for validation and row-major selection.
