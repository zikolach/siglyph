## ADDED Requirements

### Requirement: Public editor API
The public core API SHALL expose a Scala-idiomatic multiline editor component and options without adding runtime dependencies.

#### Scenario: Application creates editor
- **WHEN** an application creates an editor with initial text and options
- **THEN** it can add the editor to a TUI component tree without platform-specific code

#### Scenario: Editor API compiles for JVM and Native core
- **WHEN** the editor component and options are compiled for JVM and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, or third-party runtime dependencies

### Requirement: Editor Enter behavior API
The public API SHALL expose a typed configuration for editor Enter behavior.

#### Scenario: Submit on Enter configuration
- **WHEN** an application configures the editor for submit-on-Enter behavior
- **THEN** it can configure Shift+Enter as a newline input without raw escape strings

#### Scenario: Newline on Enter configuration
- **WHEN** an application configures the editor for newline-on-Enter behavior
- **THEN** it can configure Cmd/Super+Enter as a submit input without raw escape strings

### Requirement: Editor API documentation
The editor public APIs SHALL include Scaladoc and project documentation covering behavior, keyboard controls, and non-goals.

#### Scenario: Scaladoc documents editor contracts
- **WHEN** public editor types or options are added
- **THEN** their Scaladoc explains mutation delegation, Enter behavior, callbacks, and platform scope

#### Scenario: Demo documentation lists controls
- **WHEN** the editor demo is added
- **THEN** README or project docs list its launch commands and key controls

### Requirement: Editor validation includes quality checks
The editor demo change SHALL include formatting, lint, test, and Native build validation.

#### Scenario: Mill quality validation
- **WHEN** the change is complete
- **THEN** `mill quality`, `mill __.compile`, `mill core.test`, and Native editor demo build validation pass
