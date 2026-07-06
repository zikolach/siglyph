## ADDED Requirements

### Requirement: Public alternate-screen runtime option
The public core API SHALL expose a Scala-idiomatic opt-in option for running a `TUI` in terminal alternate-screen mode without changing existing defaults or component contracts.

#### Scenario: Existing TUI construction remains normal-screen
- **WHEN** existing application code constructs `TUI(terminal)` or `TUI(terminal, TUIOptions())`
- **THEN** the TUI runs in normal-screen mode and existing source remains valid

#### Scenario: Application opts into alternate screen through TUI options
- **WHEN** an application configures alternate-screen mode through public `TUIOptions`
- **THEN** the shared TUI runtime enters alternate screen for that TUI lifecycle without requiring application code to emit raw terminal escape strings

#### Scenario: Component API remains width-only
- **WHEN** alternate-screen mode is enabled
- **THEN** components still render through `Component.render(width): Vector[String]` without requiring a height-aware component API

#### Scenario: API compiles on JVM and Native core
- **WHEN** the alternate-screen option is compiled for JVM core and Scala Native core modules
- **THEN** it compiles without JVM-only, Native-only, or third-party runtime dependencies

### Requirement: Alternate-screen documentation
The project documentation and Scaladoc SHALL describe alternate-screen mode, its opt-in default, cleanup behavior, and non-goals.

#### Scenario: Documentation explains normal-screen default
- **WHEN** a developer reads TUI runtime documentation
- **THEN** it states that normal-screen mode is the default and existing applications do not enter alternate screen unless configured

#### Scenario: Documentation explains scrollback behavior
- **WHEN** a developer reads alternate-screen documentation
- **THEN** it explains that alternate-screen mode prevents TUI frames from being appended to normal shell scrollback while the TUI is running

#### Scenario: Documentation lists non-goals
- **WHEN** a developer reads alternate-screen documentation
- **THEN** it states that temporary modal alternate-screen sessions, a full-screen editor, and height-aware component rendering are not provided by this change
