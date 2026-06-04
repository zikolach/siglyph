## ADDED Requirements

### Requirement: Minimal input handling result
The public component API SHALL provide a minimal way for input handling to report whether input was handled and whether the runtime should perform standard follow-up actions such as rendering or exit.

#### Scenario: Component reports handled input
- **WHEN** a focused component handles a terminal input event
- **THEN** the runtime can observe that the event was handled without inspecting component internals

#### Scenario: Component requests no redundant render
- **WHEN** a component receives input that does not change visible state
- **THEN** the runtime can avoid scheduling a redundant render for that input event

#### Scenario: Component requests exit
- **WHEN** component input handling requests application exit
- **THEN** the interactive run loop exits and terminal state is restored through the existing shutdown path

### Requirement: Public model APIs remain backend-independent
New editing model APIs SHALL live in the shared core and SHALL NOT depend on JVM-only, Native-only, terminal backend, or rendering implementation details.

#### Scenario: Editor buffer compiles for JVM and Native core
- **WHEN** the editor buffer model is compiled for JVM and Scala Native core modules
- **THEN** it compiles without platform-specific APIs or runtime dependencies

### Requirement: No new runtime dependencies for editor foundation
The editor-buffer and API-refinement change SHALL NOT add third-party runtime dependencies.

#### Scenario: Dependency list remains unchanged
- **WHEN** the Mill build is inspected after this change
- **THEN** no new runtime dependency is present beyond already-approved Scala Native and test-only dependencies
