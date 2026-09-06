## ADDED Requirements

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
