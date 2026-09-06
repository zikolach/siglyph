# keybinding-management Specification

## Purpose
Defines backend-independent keybinding models, upstream-compatible default command bindings, custom override behavior, conflict reporting, and typed input matching.
## Requirements
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
- **THEN** generic input/select commands include `Shift+Enter` and representable `Ctrl+J` input for newline, Enter for submit, Tab for autocomplete, `Ctrl+C` for copy/cancel behavior, Up/Down/PageUp/PageDown for selection, Enter for confirm, and Escape or `Ctrl+C` for cancel

#### Scenario: Ctrl J newline alias is typed when distinguishable
- **WHEN** the runtime receives a typed Ctrl+J input event that is distinguishable from plain Enter
- **THEN** the default keybinding manager matches it to `tui.input.newLine`

#### Scenario: Bare line feed ambiguity is documented
- **WHEN** a terminal emits a bare line-feed byte that the parser normalizes to plain Enter
- **THEN** the implementation documents that this byte is treated as Enter unless a reliable typed Ctrl+J encoding is available

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

### Requirement: Fullscreen viewport command set
The keybinding model SHALL define backend-independent commands for single-line, half-page, page, document-start, document-end, previous-prompt, next-prompt, search-toggle, search-next, search-previous, search-close, copy-selection, and clear-selection actions.

#### Scenario: Viewport commands use typed keys
- **WHEN** an application configures fullscreen navigation or search bindings
- **THEN** it uses shared command ids and typed key descriptors without terminal-backend imports or raw escape strings

#### Scenario: Commands can remain unbound
- **WHEN** a viewport command has no portable default binding
- **THEN** it remains registered and can be bound by applications without conflicting with editor defaults

### Requirement: Viewport command routing precedence
Fullscreen commands SHALL be offered first to focused overlays and focused components that declare the corresponding action, then to the viewport renderer and primary scroll view. Existing editor movement and history bindings SHALL retain priority while the editor can consume them.

#### Scenario: Editor consumes page command
- **WHEN** the focused editor consumes its configured PageDown action
- **THEN** viewport fallback does not scroll the primary transcript for the same key event

#### Scenario: Primary scroll view receives fallback
- **WHEN** no focused target consumes a registered viewport navigation action
- **THEN** the viewport renderer applies it to the primary scroll view

### Requirement: Search binding context
While fullscreen transcript search is active, text editing and search navigation commands SHALL be resolved by the search input before ordinary component and viewport navigation.

#### Scenario: Enter advances search
- **WHEN** search is active and Enter is bound to next match
- **THEN** search advances without submitting or mutating the underlying editor

#### Scenario: Escape closes search
- **WHEN** search is active and Escape is received
- **THEN** search closes before application exit or ordinary overlay cancellation is considered
