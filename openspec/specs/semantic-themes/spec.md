# semantic-themes Specification

## Purpose
Defines explicit immutable semantic styles, local override precedence, geometry preservation and keyboard-only reference workflows without a global theme service.

## Requirements

### Requirement: Explicit immutable semantic styling
Themes SHALL provide normal, muted, focused, selected, disabled, warning and error roles without global mutable state. Local overrides SHALL not change parent themes. Plain SHALL preserve existing output by default.

#### Scenario: Nested override
- **WHEN** a child overrides an error style
- **THEN** its parent and sibling styles remain unchanged

### Requirement: Geometry and custom style preservation
Input/table/list/overlay styling SHALL preserve visible geometry and existing customization. Semantic style values SHALL not grant arbitrary terminal-control authority.

#### Scenario: Narrow Unicode input
- **WHEN** a themed input renders wide graphemes at a narrow width
- **THEN** printable width and cursor placement remain within that width

### Requirement: Keyboard reference workflow
A reference form SHALL expose visible focus, keyboard traversal, textual validation and overlay dismissal with focus restoration. Monochrome operation SHALL not depend on color alone.

#### Scenario: Modal dismissal
- **WHEN** a focused overlay is dismissed with Escape
- **THEN** input returns to the underlying field
