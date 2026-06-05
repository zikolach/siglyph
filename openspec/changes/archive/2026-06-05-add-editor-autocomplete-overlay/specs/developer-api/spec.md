## ADDED Requirements

### Requirement: Public TUI context API
The public core API SHALL expose a backend-independent TUI context or host capability for components that need runtime services such as render requests, exit requests, focus changes, and overlay operations.

#### Scenario: Component requests render through context
- **WHEN** a component receives asynchronous state from a provider after input handling has returned
- **THEN** it can request a render through the TUI context without depending on a concrete terminal backend

#### Scenario: Component requests exit through context
- **WHEN** a component action decides the application should exit
- **THEN** it can request exit through the TUI context and the existing runtime shutdown path restores terminal state

#### Scenario: Context is testable
- **WHEN** a component using runtime services is tested without a real terminal
- **THEN** a fake context can record render, exit, focus, and overlay operations

### Requirement: Public overlay API
The public core API SHALL expose Scala-idiomatic overlay types, overlay options, overlay handles, anchors, margins, and size values without adding third-party runtime dependencies.

#### Scenario: Application creates centered overlay
- **WHEN** an application shows a component overlay without explicit position options
- **THEN** it can use the returned handle to manage a centered overlay through public APIs

#### Scenario: Application creates positioned overlay
- **WHEN** an application supplies width, maximum height, row, column, anchor, offset, margin, visibility, or focus options
- **THEN** those options are expressed using typed Scala values rather than raw terminal escape strings

#### Scenario: Overlay API compiles for JVM and Native core
- **WHEN** the overlay API is compiled by JVM core and Scala Native core modules
- **THEN** it compiles without JVM-only, Native-only, Node.js, or third-party runtime dependencies

### Requirement: Public autocomplete API
The public core API SHALL expose autocomplete provider, request, result, item, suggestion, cancellable handle, and callback types that are usable on both JVM and Scala Native.

#### Scenario: Application supplies provider
- **WHEN** an application configures an editor with an autocomplete provider
- **THEN** it can do so using shared core types without choosing a terminal backend-specific implementation

#### Scenario: Provider can wrap external effect runtime
- **WHEN** an application has file, network, Future, or effect-runtime based autocomplete logic
- **THEN** it can bridge that logic into the callback provider contract without requiring the TUI library to depend on that effect runtime

### Requirement: Overlay and autocomplete documentation
New public overlay, context, and autocomplete APIs SHALL include Scaladoc and project documentation describing behavior, platform scope, keyboard controls, and non-goals.

#### Scenario: Scaladoc documents public contracts
- **WHEN** public overlay, context, or autocomplete types are added
- **THEN** their Scaladoc explains ownership, lifecycle, cancellation, rendering constraints, and dependency expectations

#### Scenario: Project docs describe demo controls
- **WHEN** the interactive demo gains overlay autocomplete behavior
- **THEN** README or smoke documentation lists launch commands and keyboard controls for opening, navigating, accepting, and cancelling suggestions
