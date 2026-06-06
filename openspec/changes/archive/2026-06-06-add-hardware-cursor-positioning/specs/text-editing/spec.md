## MODIFIED Requirements

### Requirement: Hardware cursor marker support
When IME and terminal marker workflows are enabled, focused editing components SHALL emit a zero-width cursor marker in front of the visual cursor position while preserving fake-cursor rendering, and the marker SHALL be suitable for runtime hardware cursor positioning.

#### Scenario: Cursor marker is emitted on focused input
- **WHEN** an `Input` or `Editor` is focused and renders a fake cursor
- **THEN** it includes a terminal cursor marker sequence (or equivalent marker abstraction) immediately before the fake cursor token

#### Scenario: Marker does not alter semantic text
- **WHEN** callbacks observe the submitted editor value
- **THEN** no marker sequence appears in logical text values returned by `onSubmit` or `text` getters

#### Scenario: Unfocused editors do not claim hardware cursor
- **WHEN** an `Input` or `Editor` is not focused
- **THEN** it does not emit a cursor marker for hardware cursor positioning

#### Scenario: Autocomplete ownership suppresses editor marker when appropriate
- **WHEN** editor autocomplete has a focus-capturing suggestion overlay that owns keyboard input
- **THEN** the editor does not emit a stale hardware cursor marker that would compete with the active overlay focus target
