## ADDED Requirements

### Requirement: SelectList rich configuration
The `SelectList` component SHALL expose options and theme hooks for selected/unselected prefixes, selected text styling, description styling, no-match text, scroll information, and primary-label truncation while preserving width-safe rendering.

#### Scenario: SelectList applies selected theme
- **WHEN** a `SelectList` renders the selected item with a configured selected-text theme
- **THEN** the selected row includes that styled output and remains within the requested visible width

#### Scenario: SelectList reports selection changes
- **WHEN** keyboard navigation changes the selected item
- **THEN** the component invokes a selection-change callback with the newly highlighted item

#### Scenario: SelectList filters items
- **WHEN** a filter query is applied to a `SelectList`
- **THEN** only matching items are rendered and an empty result renders the configured no-match text

### Requirement: Selector fuzzy filtering
Selector-style components SHALL be able to use the shared fuzzy ranking utility to filter and order candidate rows while retaining deterministic behavior for equal scores.

#### Scenario: Fuzzy filter ranks item labels
- **WHEN** a selector filters candidates with a fuzzy query
- **THEN** matching labels are ordered by fuzzy score and stable input order is preserved for equal scores

#### Scenario: Fuzzy filtering remains optional
- **WHEN** a selector is configured for simple containment or no filtering
- **THEN** it does not apply fuzzy ranking to item order

### Requirement: SettingsList submenus
The `SettingsList` component SHALL support settings rows that open application-provided submenu components through the existing component and overlay contracts rather than through a platform-specific runtime.

#### Scenario: Enter opens submenu row
- **WHEN** the selected settings row defines a submenu component and the user activates it with Enter
- **THEN** the settings list requests or exposes that submenu component without cycling a scalar value

#### Scenario: Submenu selection updates setting
- **WHEN** an application-provided submenu returns a selected value for a settings row
- **THEN** the settings list updates that row and invokes the existing change callback with the row id and new value

#### Scenario: Escape cancels submenu
- **WHEN** a settings submenu is visible and the user cancels it
- **THEN** focus returns to the settings list and the setting value remains unchanged

### Requirement: SettingsList fuzzy ranking
The `SettingsList` component SHALL support optional fuzzy filtering across id, label, current value, and description in addition to its existing dependency-free containment filtering.

#### Scenario: Fuzzy query ranks settings rows
- **WHEN** fuzzy filtering is enabled and the user enters a query
- **THEN** rows matching the query are rendered in fuzzy-ranked order while preserving width-safe row rendering

#### Scenario: Existing containment filtering remains available
- **WHEN** fuzzy filtering is disabled and filtering is enabled
- **THEN** settings rows continue using the existing case-insensitive containment behavior
