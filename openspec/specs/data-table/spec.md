# data-table Specification

## Purpose
Defines typed stable table rows, bounded visible-range formatting, keyboard selection and explicit parent-scroll ownership on JVM and Scala Native.

## Requirements

### Requirement: Typed stable row identity
TableData SHALL validate unique non-null String IDs before publication. DataTable SHALL preserve selection by ID across model replacement, falling back to the previous bounded index when an ID disappears and to no selection for empty data.

#### Scenario: Reordered data
- **WHEN** new data moves the selected ID to a different index
- **THEN** the selection still refers to that ID and its new row value

#### Scenario: Deleted selection
- **WHEN** replacement data omits the selected ID
- **THEN** selection uses the previous index clamped to the replacement or becomes empty

### Requirement: Visible-range table rendering
DataTable SHALL implement ViewportRangeRenderer, format only requested rows and visible columns, and return width-safe typed text output. The header SHALL occupy document row zero. There SHALL be no retained offscreen render cache.

#### Scenario: Large dataset
- **WHEN** a viewport requests ten data rows from a 100,000-row table
- **THEN** only those ten rows are formatted, independently of dataset size

#### Scenario: Empty geometry
- **WHEN** width or requested row count is non-positive
- **THEN** no cell formatter is invoked and no rows are emitted

### Requirement: Ordered selection and input effects
Table selection and focus SHALL use the existing component state/effect boundary. Callbacks and cell formatters SHALL execute outside its lock. Unmodified focused navigation presses/repeats SHALL be handled; releases SHALL NOT mutate selection.

#### Scenario: Reentrant callback
- **WHEN** a selection callback requests another selection
- **THEN** both changes complete in coordinator order without recursive callback dispatch or deadlock

#### Scenario: Navigation at a bound
- **WHEN** a focused table receives Up at the first row
- **THEN** the input is consumed without an additional selection-change callback

### Requirement: Explicit cost and ownership boundaries
Documentation SHALL distinguish O(n) model construction/filtering from viewport formatting, describe the unbounded full render fallback and non-sticky header, and make parent scrolling/invalidation application-owned.

#### Scenario: Log explorer integration
- **WHEN** the log explorer navigates without changing filtered data
- **THEN** it reuses its indexed TableData and updates only selected-row presentation
