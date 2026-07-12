## ADDED Requirements

### Requirement: Native baseline image artifact
The baseline image component and dependency-light image helpers SHALL be published for Scala Native when their file, base64, protocol, and dimension-sniffing behavior can be compiled unchanged or behind a small shared portability boundary.

#### Scenario: Native application depends on image module
- **WHEN** a Scala Native application adds the Native `siglyph-image` artifact
- **THEN** it can construct image sources, calculate dimensions, and render the `Image` component through the same public API shape as JVM applications

#### Scenario: Native image uses shared rendering logic
- **WHEN** the Native image module is built
- **THEN** protocol rendering, fallback rendering, and dimension-sniffing logic come from the canonical `image/src` source tree or shared compatibility code rather than a duplicated Native implementation

#### Scenario: Unsupported portability path blocks publication
- **WHEN** the image module cannot compile on Scala Native without duplicated logic, a leaky abstraction, or a new unapproved runtime dependency
- **THEN** the image Native artifact is not published by this change and the blocker is documented before implementation continues

#### Scenario: Native image keeps dependency-light baseline
- **WHEN** the Native image artifact is published
- **THEN** it does not add third-party image parsing, scaling, transcoding, or base64 runtime dependencies to the baseline image module
