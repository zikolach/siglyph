## ADDED Requirements

### Requirement: Native modules reuse shared sources through Mill configuration
Scala Native mirror modules SHALL reuse shared JVM/Native implementation sources through Mill source-root configuration rather than symlinked source directories.

#### Scenario: Core Native compiles canonical core sources
- **WHEN** the `coreNative` module is compiled
- **THEN** it compiles the canonical `core/src` shared source tree without requiring a `coreNative/src` symlink

#### Scenario: Interactive demo Native compiles canonical demo sources
- **WHEN** the `interactiveDemoNative` module is compiled
- **THEN** it compiles the canonical `interactiveDemo/src` shared source tree without requiring an `interactiveDemoNative/src` symlink

#### Scenario: Repository layout avoids symlink mirrors
- **WHEN** a contributor inspects the repository source directories
- **THEN** shared JVM/Native implementations appear in one canonical source tree and Native-specific modules do not expose symlink mirrors of that tree

### Requirement: Shared source root cleanup preserves behavior
Replacing source symlinks with Mill shared source roots SHALL preserve module graph, public APIs, and dependency constraints.

#### Scenario: JVM and Native modules still compile
- **WHEN** the source-root cleanup is complete
- **THEN** `mill __.compile` succeeds for JVM and Scala Native modules

#### Scenario: Native interactive demo still links
- **WHEN** the source-root cleanup is complete
- **THEN** `mill interactiveNativeDemo.nativeLink` succeeds using the same shared demo implementation

#### Scenario: No runtime dependency is added
- **WHEN** the Mill build is inspected after this cleanup
- **THEN** no new third-party runtime dependency is present
