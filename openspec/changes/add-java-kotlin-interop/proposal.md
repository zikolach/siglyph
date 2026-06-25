## Why

siglyph JVM artifacts can be called from Java and Kotlin today, but the public API is Scala-first and exposes Scala-specific constructors, callbacks, options, and collections at JVM language call sites. A small JVM interop surface would make basic TUI construction usable from Java and Kotlin without requiring users to understand Scala 3 bytecode conventions.

## What Changes

- Add Java/Kotlin-friendly JVM API entry points for constructing a terminal-backed TUI, adding common components, setting focus, and registering submit callbacks.
- Add Java/Kotlin-friendly adapters for common callback and option use cases without changing the Scala-first core API.
- Add documentation with Gradle/Maven coordinates and minimal Java and Kotlin examples.
- Add JVM interop compile tests that exercise Java and Kotlin call sites where tooling is available in the build.
- Do not add runtime dependencies.
- Do not change Scala Native APIs or promise Java/Kotlin support for Scala Native artifacts.

## Capabilities

### New Capabilities

### Modified Capabilities
- `developer-api`: add JVM language interop requirements for Java/Kotlin-friendly APIs, documentation, and validation.

## Impact

- Affected modules: `core`, `terminalJvm`, documentation, and JVM-focused tests.
- Public API: additive JVM interop helpers or facade types under the existing `scalatui` package namespace.
- Dependencies: no new runtime dependencies; test-only tooling may be used only if scoped to tests.
- Platform scope: JVM only for Java/Kotlin call sites; Scala Native remains unchanged.
