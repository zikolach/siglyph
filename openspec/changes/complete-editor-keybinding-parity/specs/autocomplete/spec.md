## ADDED Requirements

### Requirement: Autocomplete keybinding precedence
When editor autocomplete suggestions are visible, the editor and suggestion overlay SHALL apply selection/autocomplete keybindings before ordinary text-editing commands according to upstream `pi-tui` behavior.

#### Scenario: Cancel binding closes suggestions
- **WHEN** autocomplete suggestions are visible and the configured selection-cancel command is received
- **THEN** the suggestion overlay closes without changing editor text

#### Scenario: Up and Down navigate suggestions
- **WHEN** autocomplete suggestions are visible and the configured select-up or select-down command is received
- **THEN** the selected suggestion changes instead of moving the editor cursor or navigating prompt history

#### Scenario: Tab accepts selected suggestion
- **WHEN** autocomplete suggestions are visible and the configured input-tab command is received
- **THEN** the editor applies the selected suggestion through the autocomplete completion contract instead of inserting a tab character

#### Scenario: Enter completion may fall through to slash submit
- **WHEN** autocomplete suggestions are visible and the configured select-confirm command accepts a slash-command suggestion
- **THEN** the editor applies the completion, closes autocomplete, and follows upstream submit behavior for slash-command completion

### Requirement: Autocomplete uses configurable select keybindings
Autocomplete suggestion navigation SHALL use the configured keybinding manager for selection commands rather than hard-coded key names.

#### Scenario: Custom select-down binding navigates suggestions
- **WHEN** an application configures `tui.select.down` to a custom key and suggestions are visible
- **THEN** that key moves the selected suggestion down

#### Scenario: Default selection bindings remain pi-tui-compatible
- **WHEN** no custom keybindings are configured
- **THEN** Up, Down, PageUp, PageDown, Enter, Escape, and `Ctrl+C` behave according to the default selection command mappings where implemented by the suggestion component
