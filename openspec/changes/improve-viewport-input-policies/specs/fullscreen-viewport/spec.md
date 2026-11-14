## ADDED Requirements

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
