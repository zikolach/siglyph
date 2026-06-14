## ADDED Requirements

### Requirement: Backend-independent keybinding manager
The library SHALL expose shared core keybinding models and a keybinding manager that match typed terminal input events to named TUI commands without depending on JVM-only, Native-only, Node.js, or third-party runtime APIs.

#### Scenario: Default manager matches pi-tui command
- **WHEN** an application uses the default keybinding manager and the editor receives `Ctrl+A`
- **THEN** the manager matches the `tui.editor.cursorLineStart` command

#### Scenario: Unknown command is rejected or ignored predictably
- **WHEN** application code supplies a binding for a command id that is not registered
- **THEN** the manager ignores or reports that binding without changing registered command defaults

#### Scenario: Manager is testable without terminal backend
- **WHEN** tests construct typed key events directly
- **THEN** they can assert command matches without starting a JVM, Native, or virtual terminal backend

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
- **THEN** generic input/select commands include Shift+Enter for newline, Enter for submit, Tab for autocomplete, `Ctrl+C` for copy/cancel behavior, Up/Down/PageUp/PageDown for selection, Enter for confirm, and Escape or `Ctrl+C` for cancel

### Requirement: Custom keybinding overrides and conflict reporting
Applications SHALL be able to replace default keys for registered commands and inspect conflicts among user-supplied key claims.

#### Scenario: User binding replaces defaults for command
- **WHEN** an application configures `tui.input.submit` to use `Ctrl+Enter`
- **THEN** the manager resolves `tui.input.submit` to the configured key set instead of the default Enter binding

#### Scenario: Omitted binding keeps defaults
- **WHEN** an application configures one command and omits all others
- **THEN** omitted commands continue to use their default key sets

#### Scenario: Explicit empty binding disables command keys
- **WHEN** an application explicitly configures a command with an empty key list
- **THEN** that command has no matching keys until the configuration changes

#### Scenario: Conflicts are observable
- **WHEN** two or more user-configured commands claim the same key descriptor
- **THEN** the manager exposes conflict information listing the key and conflicting command ids without adding runtime dependencies
