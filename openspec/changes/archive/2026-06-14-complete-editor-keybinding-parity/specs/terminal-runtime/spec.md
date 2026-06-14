## ADDED Requirements

### Requirement: Typed input coverage for keybinding parity
The terminal input model and parser SHALL support the typed key events needed by the default editor, input, and selection keybindings where those events can be recognized portably.

#### Scenario: Page keys are parsed
- **WHEN** a terminal sends common PageUp or PageDown escape sequences
- **THEN** the runtime emits typed key events that can match `tui.editor.pageUp`, `tui.editor.pageDown`, `tui.select.pageUp`, and `tui.select.pageDown`

#### Scenario: Jump-key control events are parsed where distinguishable
- **WHEN** a terminal sends a distinguishable sequence for `Ctrl+]` or `Ctrl+Alt+]`
- **THEN** the runtime emits typed key events that can match jump-forward or jump-backward commands

#### Scenario: Ambiguous key encodings are documented
- **WHEN** a terminal encoding cannot distinguish an upstream default binding from another key or raw control byte
- **THEN** the runtime documents the limitation and tests the closest supported typed event rather than exposing backend-specific raw strings as the primary API

#### Scenario: Parser behavior is shared where possible
- **WHEN** JVM and Scala Native backends receive the same normalized byte sequence for a supported default binding
- **THEN** they deliver the same typed terminal input event to shared component code
