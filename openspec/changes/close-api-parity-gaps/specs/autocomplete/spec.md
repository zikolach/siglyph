## ADDED Requirements

### Requirement: Forced single-completion auto-apply
The editor autocomplete API SHALL support opt-in auto-application of a single unambiguous completion returned by an explicit forced autocomplete request.

#### Scenario: Single forced suggestion is applied when enabled
- **WHEN** forced autocomplete is requested, auto-apply is enabled, and the current provider returns exactly one suggestion
- **THEN** the editor applies that suggestion through the existing completion application contract without opening a suggestion overlay

#### Scenario: Multiple forced suggestions show selection UI
- **WHEN** forced autocomplete is requested, auto-apply is enabled, and the current provider returns more than one suggestion
- **THEN** the editor shows the suggestion selection UI instead of applying a completion automatically

#### Scenario: Empty forced suggestions do not mutate editor
- **WHEN** forced autocomplete is requested and the current provider returns no suggestions
- **THEN** the editor does not mutate text and closes or leaves closed the autocomplete UI according to the existing empty-result behavior

#### Scenario: Auto-apply remains opt-in
- **WHEN** forced autocomplete is requested and auto-apply is not enabled
- **THEN** the editor keeps the current explicit-selection behavior

#### Scenario: Stale single suggestion is ignored
- **WHEN** a forced autocomplete request returns one suggestion after the editor snapshot has changed
- **THEN** the editor ignores the stale result and does not mutate current text
