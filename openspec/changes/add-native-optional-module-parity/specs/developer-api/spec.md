## ADDED Requirements

### Requirement: Optional module Native parity
The project SHALL publish Scala Native variants for optional modules whose public Scala APIs and implementations are portable without adding third-party runtime dependencies.

#### Scenario: Portable optional module has Native artifact
- **WHEN** an optional module implementation compiles against shared core APIs and has no JVM-only runtime dependency
- **THEN** the project publishes a Scala Native artifact for that module using the same public Scala API shape

#### Scenario: JVM-only surfaces remain explicit
- **WHEN** a module or facade depends on JVM language interop, JVM terminal implementation details, or JVM-only tooling
- **THEN** documentation identifies it as JVM-only rather than promising Scala Native support

#### Scenario: Native optional artifacts are documented
- **WHEN** a Native optional artifact is added
- **THEN** README and publishing documentation list its dependency coordinates, artifact name, and validation expectations

#### Scenario: Optional module parity adds no runtime dependency
- **WHEN** Native optional module parity is implemented
- **THEN** the Mill build does not add third-party runtime dependencies beyond already approved Scala Native and test-only dependencies
