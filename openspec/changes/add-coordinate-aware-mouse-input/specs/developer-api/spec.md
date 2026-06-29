## ADDED Requirements

### Requirement: Public mouse input API
The public core API SHALL expose backend-independent mouse input types for terminal coordinates, mouse actions, mouse buttons, wheel directions, and mouse handling context without adding runtime dependencies.

#### Scenario: Application matches mouse input
- **WHEN** application code receives `TerminalInput.Mouse(...)`
- **THEN** it can pattern match the mouse action, row, column, modifiers, button, or wheel direction using shared core types

#### Scenario: API compiles for JVM and Native core
- **WHEN** mouse input types are compiled by JVM core and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, Node.js, or third-party runtime APIs

### Requirement: Public opt-in mouse configuration
The public TUI API SHALL expose mouse input configuration that is disabled by default and can be enabled without raw terminal escape strings.

#### Scenario: Default TUI construction keeps mouse disabled
- **WHEN** an application constructs a TUI with default options
- **THEN** mouse reporting remains disabled

#### Scenario: Application enables mouse through options
- **WHEN** an application enables mouse input through public TUI options
- **THEN** the runtime enables supported mouse terminal protocols during interactive start

### Requirement: Public mouse handler contract
The public component API SHALL expose a backend-independent way for components to opt into mouse handling and receive routed mouse context.

#### Scenario: Component opts into mouse handling
- **WHEN** a component implements the mouse handling contract
- **THEN** the runtime can deliver coordinate-routed mouse input to that component

#### Scenario: Component without mouse handler is not forced to handle mouse
- **WHEN** a component does not implement the mouse handling contract
- **THEN** it is not treated as a mouse input target during coordinate routing

### Requirement: Mouse input documentation
Mouse input public APIs SHALL include Scaladoc and project documentation covering opt-in behavior, coordinate base, terminal support scope, text-selection caveats, and wheel scrollback capture.

#### Scenario: Scaladoc documents coordinate base
- **WHEN** a developer reads Scaladoc for mouse input types
- **THEN** it states that parsed coordinates are zero-based terminal cell coordinates

#### Scenario: Documentation explains opt-in caveat
- **WHEN** a developer reads project documentation for mouse input
- **THEN** it explains that mouse reporting is disabled by default because it can affect normal terminal text selection and wheel scrollback

### Requirement: Mouse input adds no runtime dependencies
The coordinate-aware mouse input change SHALL NOT add new third-party runtime dependencies.

#### Scenario: Dependency list remains unchanged
- **WHEN** the Mill build is inspected after implementation
- **THEN** no new runtime dependency is present beyond already-approved modules and dependencies
