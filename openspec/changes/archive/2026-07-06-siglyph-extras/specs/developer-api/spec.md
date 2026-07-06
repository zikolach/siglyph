## ADDED Requirements

### Requirement: Optional extras artifact
The project SHALL publish an optional `siglyph-extras` artifact for reusable dependency-light TUI helper widgets that depend only on core APIs.

#### Scenario: Extras module compiles without terminal backend
- **WHEN** the extras module is compiled
- **THEN** it compiles without depending on JVM terminal, Native terminal, Markdown, image, demo, or agent-specific modules

#### Scenario: Extras module adds no runtime dependency
- **WHEN** the Mill build is inspected after adding the extras module
- **THEN** no new third-party runtime dependency is present

#### Scenario: Extras artifact is documented
- **WHEN** the extras artifact is added
- **THEN** README or project documentation lists its dependency coordinates, scope, and non-goals

#### Scenario: Release metadata includes extras
- **WHEN** release and publishing documentation lists published artifacts
- **THEN** it includes the extras artifact and any Native variant that the build publishes
