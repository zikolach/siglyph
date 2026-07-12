## ADDED Requirements

### Requirement: Native baseline Markdown artifact
The baseline dependency-free Markdown renderer SHALL be published for Scala Native using the same public renderer and component contracts as the JVM Markdown artifact.

#### Scenario: Native application depends on Markdown module
- **WHEN** a Scala Native application adds the Native `siglyph-markdown` artifact
- **THEN** it can construct and render the baseline `Markdown` component through the same public API as JVM applications

#### Scenario: Native Markdown uses shared sources
- **WHEN** the Native Markdown module is built
- **THEN** it compiles the canonical `markdown/src` source tree through Mill source-root configuration rather than a duplicated Native implementation

#### Scenario: Native Markdown keeps dependency-free baseline
- **WHEN** the Native Markdown artifact is published
- **THEN** it does not add a third-party parser, syntax highlighter, or runtime dependency to the baseline Markdown module
