## MODIFIED Requirements

### Requirement: pi-tui default keybinding set
The default keybinding definitions SHALL mirror current upstream `pi-tui` defaults where the typed terminal input model can represent the key combinations.

#### Scenario: Editor movement defaults are present
- **WHEN** default keybindings are resolved
- **THEN** editor movement commands include Up/Down/Left/Right, `Ctrl+B`, `Ctrl+F`, `Alt+Left`, `Ctrl+Left`, `Alt+B`, `Alt+Right`, `Ctrl+Right`, `Alt+F`, Home, End, `Ctrl+A`, `Ctrl+E`, PageUp, PageDown, `Ctrl+]`, and `Ctrl+Alt+]` where supported by typed input parsing

#### Scenario: Editor deletion and kill-ring defaults are present
- **WHEN** default keybindings are resolved
- **THEN** deletion and kill-ring commands include Backspace, Delete, `Ctrl+D`, `Ctrl+W`, `Alt+Backspace`, `Alt+D`, `Alt+Delete`, `Ctrl+U`, `Ctrl+K`, `Ctrl+Y`, `Alt+Y`, and `Ctrl+-` where supported by typed input parsing

#### Scenario: Input and selection defaults are present
- **WHEN** default keybindings are resolved
- **THEN** generic input/select commands include Shift+Enter and representable `Ctrl+J` input for newline, Enter for submit, Tab for autocomplete, `Ctrl+C` for copy/cancel behavior, Up/Down/PageUp/PageDown for selection, Enter for confirm, and Escape or `Ctrl+C` for cancel

#### Scenario: Ctrl J newline alias is typed when distinguishable
- **WHEN** the runtime receives a typed Ctrl+J input event that is distinguishable from plain Enter
- **THEN** the default keybinding manager matches it to `tui.input.newLine`

#### Scenario: Bare line feed ambiguity is documented
- **WHEN** a terminal emits a bare line-feed byte that the parser normalizes to plain Enter
- **THEN** the implementation documents that this byte is treated as Enter unless a reliable typed Ctrl+J encoding is available
