# developer-api Specification

## Purpose
TBD - created by archiving change scala-tui-port. Update Purpose after archive.
## Requirements
### Requirement: Dependency-light public core
The library SHALL provide a public core module whose component, rendering, ANSI utility, key model, and test-support APIs do not require Node.js or npm packages. Third-party runtime dependencies MUST be added only after explicit confirmation.

#### Scenario: Core compiles without Node.js
- **WHEN** the core module is compiled on a machine without Node.js installed
- **THEN** compilation does not require a JavaScript runtime or npm package resolution

#### Scenario: Runtime dependency requires approval
- **WHEN** a runtime dependency is proposed for any module
- **THEN** the dependency name, purpose, target platforms, and alternatives are documented before it is added

### Requirement: Mill Scala 3 modular architecture
The library SHALL be named `scala-tui`, use latest stable Mill with Scala 3 modules, and SHALL separate pure rendering and component logic from platform terminal backends so backend implementations can vary by target while preserving a stable application API.

#### Scenario: Application chooses backend
- **WHEN** an application creates a TUI
- **THEN** it can pass a terminal backend implementation without changing component code

#### Scenario: Bootstrap package namespace
- **WHEN** the first implementation slice is created
- **THEN** public Scala sources use the `scalatui` package namespace unless a final publishing namespace has been chosen

### Requirement: pi-tui compatibility intent
The library SHALL use current `pi-tui` behavior as the canonical reference for feature semantics while allowing Scala-idiomatic types and APIs.

#### Scenario: Compatibility test uses upstream fixture
- **WHEN** a behavior is ported from `pi-tui`
- **THEN** the Scala test suite includes either a direct scenario equivalent or a documented intentional deviation

### Requirement: TauTUI reference usage
The project SHALL treat TauTUI as a Node-free architectural reference but MUST verify behavior against current `pi-tui` before claiming parity.

#### Scenario: TauTUI differs from current pi-tui
- **WHEN** TauTUI and current `pi-tui` disagree on behavior
- **THEN** the implementation follows current `pi-tui` unless a deviation is documented in the design or spec

### Requirement: Unicode table generation
The library SHALL generate Unicode display-width and grapheme-segmentation tables from the latest Unicode data using a Scala CLI script and commit the generated tables to the repository.

#### Scenario: Generated Unicode version is recorded
- **WHEN** Unicode tables are generated
- **THEN** the generated source records the Unicode data version and source URLs used

#### Scenario: Unicode tables are reproducible
- **WHEN** the Unicode generation script is run with the same Unicode data version
- **THEN** it reproduces the committed table contents

### Requirement: Test harness and regression suite
The library SHALL include tests for rendering, terminal protocol parsing, Unicode width and editing, autocomplete, components, and virtual-terminal integration before a capability is considered complete. Test-only dependencies are allowed.

#### Scenario: Renderer regression test
- **WHEN** the renderer changes
- **THEN** normalized virtual-viewport tests verify semantic output and targeted raw ANSI snapshot tests verify escape-stream compatibility for first render, partial render, full redraw, width change, and overlay composition behavior

#### Scenario: Unicode editing regression test
- **WHEN** input or editor behavior changes
- **THEN** tests verify ASCII, CJK, combining marks, emoji, ANSI-adjacent text, and paste handling

### Requirement: Interactive run API
The public API SHALL provide a simple way to run an interactive TUI until the application requests exit.

#### Scenario: Application runs until exit request
- **WHEN** an application starts the TUI with the interactive run API
- **THEN** the call remains active until the application requests exit or the terminal runtime fails

#### Scenario: Application requests exit
- **WHEN** application code calls the exit-request API from an input handler
- **THEN** the interactive run loop exits and terminal state is restored

### Requirement: Shared demo construction
The project SHALL provide a shared demo UI construction path that can be used by both JVM and Scala Native interactive demo launchers.

#### Scenario: Shared demo UI is used by JVM and Native
- **WHEN** JVM and Native demos are built
- **THEN** they use the same component tree and interaction logic except for backend selection

### Requirement: Interactive demo commands
The Mill build SHALL expose commands or targets for running the JVM interactive demo and building/running the Scala Native interactive demo.

#### Scenario: JVM demo command
- **WHEN** a developer runs the JVM interactive demo target in a TTY
- **THEN** the demo starts without requiring manual terminal setup

#### Scenario: Native demo command
- **WHEN** a developer builds or runs the Native interactive demo target
- **THEN** the target uses the Scala Native terminal backend and documents any required invocation steps

### Requirement: No new runtime dependencies
This change SHALL NOT add new third-party runtime dependencies.

#### Scenario: Dependency list remains unchanged
- **WHEN** the Mill build is inspected after implementation
- **THEN** no new runtime dependency is present beyond the already-approved Scala Native and test-only dependencies

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

### Requirement: Documentation and Scaladoc for public APIs
Changes that add or modify public APIs SHALL include project documentation and Scaladoc describing contract, platform scope, and important non-goals.

#### Scenario: Public API documentation accompanies implementation
- **WHEN** a change adds a public type or public method intended for application use
- **THEN** the implementation includes Scaladoc and the project documentation explains the capability or explicitly records why no documentation update is needed

#### Scenario: Future changes preserve documentation expectations
- **WHEN** a future OpenSpec change proposes public API or user-visible behavior
- **THEN** its tasks include documentation and Scaladoc work before validation is considered complete

### Requirement: Formatting and best-practice lint configuration
The project SHALL include Scalafmt and Scalafix configuration so contributors and automation can check formatting and baseline Scala best-practice rules.

#### Scenario: Formatting configuration exists
- **WHEN** a contributor wants to check source formatting
- **THEN** the repository provides Scalafmt configuration and documented commands for formatting or checking formatting

#### Scenario: Scalafix configuration exists
- **WHEN** a contributor wants to check baseline Scala best-practice rules
- **THEN** the repository provides Scalafix configuration and documented commands for running those checks
