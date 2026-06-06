## ADDED Requirements

### Requirement: Public loader APIs
The public core API SHALL expose Scala-idiomatic loader component APIs without third-party runtime dependencies or platform-specific requirements.

#### Scenario: Application creates Loader
- **WHEN** an application creates a loader with options for message, indicator frames, interval metadata, and style functions
- **THEN** it can add the loader to a component tree and render it without JVM-only, Native-only, Node.js, or third-party dependencies

#### Scenario: Application controls loader lifecycle
- **WHEN** application code calls `start()`, `stop()`, `running`, `tick()`, `setMessage(...)`, or `setIndicator(...)`
- **THEN** the loader exposes deterministic state changes suitable for tests and application-owned scheduling

#### Scenario: Loader interval is metadata
- **WHEN** application code configures an interval duration
- **THEN** the public API exposes the value for application or future scheduler use without promising automatic wall-clock animation in this change

### Requirement: Public cancellation APIs
The public core API SHALL expose dependency-free cancellation models for cancellation-enabled loader components.

#### Scenario: Application observes cancellation token
- **WHEN** a cancellable loader is constructed
- **THEN** application code can read a token or state object that reports whether cancellation has occurred

#### Scenario: Application receives cancellation callback
- **WHEN** cancellation occurs through Escape or explicit `cancel()`
- **THEN** application code receives a typed callback at most once

#### Scenario: Cancellation API avoids JavaScript AbortSignal dependency
- **WHEN** applications use cancellable loaders
- **THEN** no JavaScript `AbortSignal`, Node, or external effect-runtime type is required

### Requirement: Loader API documentation
New public loader APIs SHALL include Scaladoc and project documentation covering rendering behavior, lifecycle, tick-driven animation, cancellation, width contracts, and intentional `pi-tui` deviations.

#### Scenario: Scaladoc documents loader lifecycle
- **WHEN** public loader types are added
- **THEN** their Scaladoc explains start/stop/tick behavior, render-request behavior, platform scope, and non-goals

#### Scenario: Documentation explains manual ticking
- **WHEN** project docs mention loader components
- **THEN** they explain that this first loader pass is tick-driven rather than component-owned background timer driven
