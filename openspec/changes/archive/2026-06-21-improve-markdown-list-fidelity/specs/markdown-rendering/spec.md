## ADDED Requirements

### Requirement: Markdown list marker preservation option
The Markdown renderer SHALL preserve current normalized list rendering by default and SHALL provide an option to preserve source list markers where the baseline parser identifies them.

#### Scenario: Default unordered marker remains normalized
- **WHEN** Markdown containing `+ item`, `* item`, and `- item` is rendered with default options
- **THEN** each unordered list row uses the current normalized unordered marker

#### Scenario: Source unordered marker is preserved by option
- **WHEN** Markdown containing `+ item`, `* item`, and `- item` is rendered with source marker preservation enabled
- **THEN** each unordered list row uses the marker from the source line

#### Scenario: Source ordered marker is preserved by option
- **WHEN** Markdown containing ordered list items with source markers such as `3.` and `7.` is rendered with source marker preservation enabled
- **THEN** each rendered ordered list row uses the corresponding source marker text

### Requirement: Markdown task list text preservation
The Markdown renderer SHALL preserve task-list markers as visible text without adding semantic checkbox behavior.

#### Scenario: Incomplete task marker remains visible
- **WHEN** Markdown contains an unordered task item with `[ ]` after the list marker
- **THEN** rendered output includes `[ ]` before the task item text

#### Scenario: Complete task marker remains visible
- **WHEN** Markdown contains an unordered task item with `[x]` or `[X]` after the list marker
- **THEN** rendered output includes the source task marker before the task item text

### Requirement: Markdown loose-list spacing
The Markdown renderer SHALL preserve blank-line spacing inside baseline-supported loose lists while keeping output width-safe.

#### Scenario: Loose list keeps item separation
- **WHEN** Markdown contains blank lines between list items in a baseline-supported list
- **THEN** rendered output includes readable separation between those list items

#### Scenario: Tight list remains compact
- **WHEN** Markdown contains adjacent list items without blank lines
- **THEN** rendered output does not insert extra blank lines between those items beyond current list spacing behavior

### Requirement: Markdown wrapped list continuation indentation
The Markdown renderer SHALL indent wrapped continuation lines so they align with the list item text rather than the marker.

#### Scenario: Unordered list item wraps after marker
- **WHEN** an unordered list item exceeds the render width
- **THEN** continuation lines start at the text column after the unordered marker prefix

#### Scenario: Ordered list item wraps after marker
- **WHEN** an ordered list item exceeds the render width
- **THEN** continuation lines start at the text column after the ordered marker prefix

#### Scenario: Task list item wraps after task marker
- **WHEN** a task list item exceeds the render width
- **THEN** continuation lines align with the task text after the list marker and task marker

### Requirement: Markdown list fidelity remains dependency-free
Baseline Markdown list fidelity improvements SHALL NOT add third-party runtime dependencies.

#### Scenario: Markdown module dependency list remains unchanged
- **WHEN** this change is complete and the Mill build is inspected
- **THEN** the baseline `markdown` module has no new third-party runtime dependency

#### Scenario: Unsupported list construct remains readable
- **WHEN** Markdown contains a list construct outside the baseline parser scope
- **THEN** rendered output remains readable text instead of throwing from component rendering
