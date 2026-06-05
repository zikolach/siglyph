## ADDED Requirements

### Requirement: Editor autocomplete placement API
The public editor API SHALL expose a Scala-idiomatic way to use built-in editor-adjacent autocomplete placement while preserving explicit overlay placement overrides.

#### Scenario: Default placement requires no row arithmetic
- **WHEN** an application creates an editor with an autocomplete provider and default placement settings
- **THEN** it does not need to compute terminal row or column values to place suggestions next to the editor

#### Scenario: Application can override placement
- **WHEN** an application needs fixed, terminal-relative, or custom overlay positioning for editor autocomplete
- **THEN** it can configure explicit overlay placement options using public core types

#### Scenario: Placement API is backend-independent
- **WHEN** editor autocomplete placement APIs compile for JVM and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, Node.js, or third-party runtime dependencies

#### Scenario: Placement API is documented
- **WHEN** public editor autocomplete placement types or methods are added or changed
- **THEN** their Scaladoc documents default adjacent placement, custom override behavior, and important non-goals
