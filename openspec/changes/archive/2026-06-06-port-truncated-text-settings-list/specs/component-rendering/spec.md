## ADDED Requirements

### Requirement: TruncatedText component
The library SHALL provide a `TruncatedText` component that renders one logical text line, truncates it by visible terminal width, applies optional padding, and returns width-safe output.

#### Scenario: TruncatedText uses first logical line
- **WHEN** `TruncatedText` is constructed with text containing newline characters
- **THEN** it renders only the text before the first newline as its content line

#### Scenario: TruncatedText truncates by visible width
- **WHEN** `TruncatedText` renders content wider than the available inner width
- **THEN** it truncates using ANSI-aware visible width and preserves terminal escape correctness

#### Scenario: TruncatedText pads to requested width
- **WHEN** `TruncatedText` renders with horizontal or vertical padding
- **THEN** each returned line has visible width less than or equal to the requested width and padding is included without overflowing

#### Scenario: TruncatedText handles narrow width
- **WHEN** `TruncatedText` renders at width 1 or another narrow positive width
- **THEN** it returns valid width-safe output instead of throwing

### Requirement: SettingsList component rendering
The library SHALL provide a `SettingsList` component that renders configurable setting rows with labels, current values, optional descriptions, selection cursor, scroll indicators, and hint text within the requested width.

#### Scenario: SettingsList renders setting labels and values
- **WHEN** a settings list has items with labels and current values
- **THEN** it renders visible rows containing those labels and values within the requested width

#### Scenario: SettingsList highlights selected row
- **WHEN** a settings row is selected
- **THEN** the rendered output includes a cursor or selected styling for that row

#### Scenario: SettingsList renders selected description
- **WHEN** the selected settings item has a description
- **THEN** the description is wrapped or truncated to fit the requested width

#### Scenario: SettingsList renders empty state
- **WHEN** a settings list has no settings items
- **THEN** it renders a width-safe hint indicating that no settings are available

#### Scenario: SettingsList scrolls visible rows
- **WHEN** the settings item count exceeds `maxVisible`
- **THEN** the component renders a bounded visible slice and a width-safe scroll indicator

#### Scenario: SettingsList handles narrow width
- **WHEN** `SettingsList` renders at narrow positive widths including 1
- **THEN** every returned line has visible width less than or equal to the requested width

### Requirement: SettingsList typed input behavior
The `SettingsList` component SHALL handle typed terminal input for navigation, activation, cancellation, and optional dependency-free filtering.

#### Scenario: Arrow keys move selection
- **WHEN** the settings list receives Up or Down key input
- **THEN** it updates the selected item and keeps it visible within the rendered slice

#### Scenario: Enter cycles value
- **WHEN** the selected settings item has configured cycle values and Enter is received
- **THEN** the item current value advances to the next configured value and the change callback is invoked

#### Scenario: Space cycles value
- **WHEN** the selected settings item has configured cycle values and Space is received
- **THEN** the item current value advances to the next configured value and the change callback is invoked

#### Scenario: Escape cancels settings list
- **WHEN** the settings list receives Escape
- **THEN** it invokes the cancel callback without changing the selected item value

#### Scenario: Filtering narrows displayed settings
- **WHEN** optional search/filtering is enabled and printable input is received
- **THEN** the displayed setting rows are filtered using a deterministic dependency-free matcher and selection is clamped to the filtered items
