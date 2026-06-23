## ADDED Requirements

### Requirement: Insert key matching
The keybinding manager SHALL match the first-class typed Insert key identity where commands or application-defined bindings use Insert.

#### Scenario: Binding matches Insert key
- **WHEN** a keybinding definition includes Insert and the runtime receives `TerminalKey.Insert`
- **THEN** the keybinding manager reports a match for that command

#### Scenario: Binding preserves Insert modifiers
- **WHEN** a keybinding definition includes a modified Insert combination and the runtime receives `TerminalKey.Insert` with matching modifiers
- **THEN** the keybinding manager reports a match for that command

#### Scenario: Insert does not require unknown-key binding
- **WHEN** an application wants to bind the Insert key
- **THEN** it can use the typed Insert key identity instead of matching `TerminalKey.Unknown("insert")`
