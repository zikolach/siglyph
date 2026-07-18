## MODIFIED Requirements

### Requirement: Mill Scala 3 modular architecture
The library SHALL be named `siglyph`, use latest stable Mill with Scala 3 modules, and SHALL separate pure rendering and component logic from platform terminal backends so backend implementations can vary by target while preserving a stable application API. Public Scala sources SHALL retain the `scalatui` package namespace.

#### Scenario: Application chooses backend
- **WHEN** an application creates a TUI
- **THEN** it can pass a terminal backend implementation without changing component code

#### Scenario: Public package namespace remains stable
- **WHEN** current product identity is normalized to `siglyph`
- **THEN** public Scala sources, packages, and imports continue to use the `scalatui` package namespace

#### Scenario: Current product surfaces use siglyph identity
- **WHEN** active build metadata, current documentation, source comments, or Scaladoc identify the present product
- **THEN** they use `siglyph` rather than `scala-tui`

#### Scenario: Historical identity remains unchanged
- **WHEN** archives, changelog history, historical notes, or intentional prior-state references identify the project as `scala-tui`
- **THEN** those historical records remain unchanged

### Requirement: Public markdown/autocomplete contracts are composable
The markdown and autocomplete contracts SHALL remain composable with existing `Component`, `TUI`, and effect runtimes.

#### Scenario: Provider composition without effect runtime dependencies
- **WHEN** an application bridges a future/callback/file-system source into autocomplete
- **THEN** it can do so through the documented provider contract without adding runtime dependencies to `siglyph`

#### Scenario: Markdown usage stays within component contract
- **WHEN** an application uses markdown rendering
- **THEN** it does so through the shared component-style output contract and standard width constraints

### Requirement: Formatting and best-practice lint configuration
The project SHALL include Scalafmt and Scalafix configuration so contributors and automation can check formatting and baseline Scala best-practice rules. Continuous integration SHALL run Scalafmt checking and Scalafix checking as mandatory quality gates. Scalafix SHALL cover every canonical current Scala production and test source root.

#### Scenario: Formatting configuration exists
- **WHEN** a contributor wants to check source formatting
- **THEN** the repository provides Scalafmt configuration and documented commands for formatting or checking formatting

#### Scenario: Scalafix configuration exists
- **WHEN** a contributor wants to check baseline Scala best-practice rules
- **THEN** the repository provides Scalafix configuration and documented commands for running those checks

#### Scenario: CI runs mandatory quality gates
- **WHEN** continuous integration validates a change
- **THEN** it runs `mill scalafmtCheck` and `mill scalafixCheck`

#### Scenario: Quality-gate failure fails CI
- **WHEN** Scalafmt or Scalafix exits unsuccessfully
- **THEN** the continuous-integration workflow fails

#### Scenario: Scalafix checks canonical current roots
- **WHEN** the Scalafix quality gate runs
- **THEN** it checks all canonical current Scala production and test source roots without relying on copied or generated mirror roots
