## ADDED Requirements

### Requirement: SelectList mouse wheel scrolling
The `SelectList` component SHALL handle routed wheel-up and wheel-down mouse input when it opts into mouse handling.

#### Scenario: Wheel down advances selection
- **WHEN** a wheel-down event is routed to a `SelectList` with a selectable item below the current selection
- **THEN** the selected item moves down by one row and remains visible

#### Scenario: Wheel up moves selection upward
- **WHEN** a wheel-up event is routed to a `SelectList` with a selectable item above the current selection
- **THEN** the selected item moves up by one row and remains visible

#### Scenario: Wheel at boundary does not request render
- **WHEN** a wheel event is routed to a `SelectList` and selection cannot move in that direction
- **THEN** the component reports that no render is needed

### Requirement: SettingsList mouse wheel scrolling
The `SettingsList` component SHALL handle routed wheel-up and wheel-down mouse input when it opts into mouse handling.

#### Scenario: Wheel down advances settings selection
- **WHEN** a wheel-down event is routed to a `SettingsList` with a row below the current selection
- **THEN** the selected row moves down by one row and remains visible

#### Scenario: Wheel up moves settings selection upward
- **WHEN** a wheel-up event is routed to a `SettingsList` with a row above the current selection
- **THEN** the selected row moves up by one row and remains visible

#### Scenario: Wheel preserves filter query
- **WHEN** a wheel event changes the selected settings row while filtering is active
- **THEN** the filter query remains unchanged

### Requirement: Editor mouse wheel page movement
The `Editor` component SHALL handle routed wheel-up and wheel-down mouse input by using the same visible page movement semantics as PageUp and PageDown.

#### Scenario: Wheel up moves editor cursor by page
- **WHEN** a wheel-up event is routed to an `Editor` and visual lines exist above the cursor
- **THEN** the editor moves the cursor upward by the configured page movement and requests a render

#### Scenario: Wheel down moves editor cursor by page
- **WHEN** a wheel-down event is routed to an `Editor` and visual lines exist below the cursor
- **THEN** the editor moves the cursor downward by the configured page movement and requests a render

#### Scenario: Wheel at editor boundary does not mutate text
- **WHEN** a wheel event is routed to an `Editor` at the first or last reachable visual line
- **THEN** editor text remains unchanged and the component reports that no render is needed

### Requirement: Autocomplete overlay mouse wheel scrolling
Editor autocomplete suggestion overlays SHALL handle routed wheel-up and wheel-down mouse input by moving the selected suggestion.

#### Scenario: Wheel down selects next suggestion
- **WHEN** a wheel-down event is routed to a visible autocomplete suggestion overlay with a suggestion below the current selection
- **THEN** the selected suggestion moves down by one row and the overlay remains visible

#### Scenario: Wheel up selects previous suggestion
- **WHEN** a wheel-up event is routed to a visible autocomplete suggestion overlay with a suggestion above the current selection
- **THEN** the selected suggestion moves up by one row and the overlay remains visible

#### Scenario: Autocomplete wheel keeps editor text unchanged
- **WHEN** a wheel event changes only the selected autocomplete suggestion
- **THEN** the editor text remains unchanged
