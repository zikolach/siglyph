## ADDED Requirements

### Requirement: Public hardware cursor positioning option
The public core API SHALL expose a backend-independent option for enabling or disabling marker-driven hardware cursor positioning without requiring application code to emit raw terminal escape sequences.

#### Scenario: Default preserves existing cursor behavior
- **WHEN** an application constructs a TUI without configuring hardware cursor positioning
- **THEN** fake cursor rendering remains visible and no marker-derived hardware cursor movement is performed

#### Scenario: Application opts in through public API
- **WHEN** an application enables hardware cursor positioning through the public TUI options
- **THEN** the shared TUI runtime uses focused editing cursor markers to place the terminal hardware cursor where supported by the backend abstraction

#### Scenario: API compiles on JVM and Native core
- **WHEN** the hardware cursor positioning option is compiled for JVM core and Scala Native core modules
- **THEN** it does not depend on JVM-only, Native-only, Node.js, or third-party runtime APIs

### Requirement: Hardware cursor positioning documentation
The project documentation SHALL describe the hardware cursor positioning option, its opt-in default, fake-cursor preservation, and testing expectations.

#### Scenario: Documentation lists behavior and caveats
- **WHEN** a developer reads TUI runtime or editor documentation
- **THEN** it explains that marker-driven hardware cursor positioning is opt-in, markers are stripped from output, and fake cursor rendering is preserved

#### Scenario: Smoke docs include IME/cursor checks
- **WHEN** a developer performs interactive smoke testing
- **THEN** docs include a manual check that enabling hardware cursor positioning places IME/cursor affordances at the focused editor/input cursor
