## ADDED Requirements

### Requirement: Java and Kotlin JVM interop API
The developer API SHALL provide a JVM-only interop layer that lets Java and Kotlin applications create a basic interactive siglyph TUI without calling Scala-generated default-argument methods.

#### Scenario: Java application creates a basic TUI
- **WHEN** a Java application depends on the JVM siglyph artifacts and uses the interop API to create a terminal-backed TUI with `Text` and `Input` components
- **THEN** the Java source compiles without calling Scala-generated `$lessinit$greater$default$N` methods

#### Scenario: Kotlin application creates a basic TUI
- **WHEN** a Kotlin application depends on the JVM siglyph artifacts and uses the interop API to create a terminal-backed TUI with `Text` and `Input` components
- **THEN** the Kotlin source compiles without calling Scala-generated `$lessinit$greater$default$N` methods

### Requirement: JVM interop boundary uses JDK types for common callbacks
The JVM interop API SHALL expose common callbacks through standard JDK functional interfaces so Java and Kotlin applications do not need to construct Scala function types for basic input submission.

#### Scenario: Java input submit callback uses JDK type
- **WHEN** a Java application registers an input submit callback through the interop API
- **THEN** the callback type is a standard JDK functional interface

#### Scenario: Kotlin input submit callback uses Kotlin lambda syntax
- **WHEN** a Kotlin application registers an input submit callback through the interop API
- **THEN** the callback can be supplied with Kotlin lambda syntax without referencing `scala.Function1`

### Requirement: JVM interop keeps Scala APIs and Native APIs unchanged
The JVM interop layer SHALL be additive and SHALL NOT change existing Scala-first APIs or add Java/Kotlin support promises for Scala Native artifacts.

#### Scenario: Existing Scala construction remains valid
- **WHEN** an existing Scala application constructs `TUI`, `Text`, or `Input` through current public constructors
- **THEN** the existing source continues to compile without using the JVM interop layer

#### Scenario: Native module API surface is not expanded for JVM language interop
- **WHEN** Scala Native modules are compiled after this change
- **THEN** they do not require JVM-only interop sources or Java/Kotlin compiler tooling

### Requirement: JVM interop documentation and validation
The project SHALL document JVM Java and Kotlin usage and validate the documented call shape with compile checks.

#### Scenario: Documentation includes dependency coordinates and examples
- **WHEN** a Java or Kotlin developer reads the project documentation
- **THEN** it lists JVM dependency coordinates and provides minimal Java and Kotlin examples for creating and running a basic TUI

#### Scenario: Java and Kotlin call shapes are validated
- **WHEN** the relevant JVM validation target runs
- **THEN** Java and Kotlin smoke sources that use the documented interop API compile successfully

### Requirement: JVM interop adds no runtime dependencies
The JVM interop change SHALL NOT add third-party runtime dependencies.

#### Scenario: Runtime dependency list remains unchanged
- **WHEN** the Mill build is inspected after the JVM interop implementation
- **THEN** no new runtime dependency is present beyond the existing approved modules and dependencies
