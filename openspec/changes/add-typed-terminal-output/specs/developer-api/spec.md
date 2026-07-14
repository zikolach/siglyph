## ADDED Requirements

### Requirement: Typed component render API
The public shared component API SHALL expose `ComponentRender`, `TerminalControlPlacement`, and read-only `TerminalRenderControl`, and **BREAKING** SHALL require `Component.render(width)` and `ComponentFrameBuilder.result()` to return the typed frame result instead of `Vector[String]`.

#### Scenario: Application implements text component
- **WHEN** application code implements a component containing ordinary text only
- **THEN** it returns a text-only `ComponentRender` with no terminal-control values

#### Scenario: Application requests supported semantic control
- **WHEN** application code intentionally requests a supported terminal protocol through its typed helper
- **THEN** it can place the returned semantic control without handling raw escape strings

#### Scenario: Legacy render signature is absent
- **WHEN** application code still implements `render(width): Vector[String]`
- **THEN** compilation fails with no compatibility overload, implicit conversion, adapter, or deprecated parallel path

### Requirement: Typed render API is documented
Public render, placement, and control types SHALL include Scaladoc and project documentation covering source migration, JVM/Native scope, trust boundaries, geometry, validation failure, and explicit non-goals.

#### Scenario: Migration documentation is complete
- **WHEN** a component author reads the migration documentation
- **THEN** it shows the direct text-only migration and explains that ordinary strings cannot request trusted terminal protocols

#### Scenario: Non-goals are explicit
- **WHEN** a developer reads terminal-control documentation
- **THEN** it states that arbitrary trusted strings, protocol-prefix inference, compatibility rendering, and backend direct-write sanitization are not provided
