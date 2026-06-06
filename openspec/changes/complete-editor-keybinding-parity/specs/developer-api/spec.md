## ADDED Requirements

### Requirement: Public configurable keybinding API
The public core API SHALL expose Scala-idiomatic keybinding command ids, key descriptors, defaults, override configuration, and conflict diagnostics without adding runtime dependencies or platform-specific imports.

#### Scenario: Application configures keybindings without backend imports
- **WHEN** an application creates an editor or TUI with custom keybindings
- **THEN** it can use shared `scalatui` core types only, regardless of JVM or Scala Native backend selection

#### Scenario: Existing construction remains source-compatible
- **WHEN** existing applications construct `Input`, `Editor`, or `TUI` without specifying keybindings
- **THEN** they compile and use the default keybindings

#### Scenario: Public API documents command scope
- **WHEN** a developer inspects keybinding command ids
- **THEN** the API distinguishes editor, input, and selection/autocomplete command scopes and documents how custom overrides are resolved

### Requirement: Keybinding parity documentation
Project documentation and Scaladoc SHALL list default editor/input/autocomplete keybindings, customization behavior, and known terminal/parser deviations.

#### Scenario: Docs list default controls
- **WHEN** a developer reads README or interactive smoke documentation
- **THEN** it lists the default controls for editor movement, history, page movement, jump mode, undo, kill-ring/yank, autocomplete navigation, submit, newline, and exit/cancel behavior

#### Scenario: Docs describe unsupported combinations
- **WHEN** a default upstream binding cannot be represented reliably on all supported terminals
- **THEN** docs name the affected binding and describe the closest supported behavior or configuration workaround

#### Scenario: Scaladoc covers public keybinding models
- **WHEN** public keybinding types or methods are added
- **THEN** their Scaladoc explains backend independence, override semantics, conflict diagnostics, and dependency constraints
