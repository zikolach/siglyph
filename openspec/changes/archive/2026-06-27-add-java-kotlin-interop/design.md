## Context

siglyph publishes Scala 3 JVM artifacts and a JVM terminal backend. The current public API is Scala-first: common constructors rely on Scala default arguments, callbacks use Scala function types, optional values use `Option`, and rendered children use Scala collections. Java can call the bytecode, but simple examples require generated default-argument methods and Scala runtime types.

The goal is not to replace the Scala API. The goal is to add a narrow JVM interop layer for Java and Kotlin users who want to build a basic interactive TUI with the JVM backend.

## Goals / Non-Goals

**Goals:**
- Provide a JVM-only interop layer for common Java and Kotlin call sites.
- Keep the existing Scala-first API and component model unchanged.
- Use standard JDK types at the interop boundary, including `java.util.function` callback types.
- Document Gradle, Maven, Java, and Kotlin usage.
- Validate the interop layer with compile checks for Java and Kotlin call sites.

**Non-Goals:**
- Do not make every Scala API type Java-idiomatic in this change.
- Do not add Scala Native Java or Kotlin support.
- Do not add runtime dependencies.
- Do not change existing component semantics, terminal behavior, keybindings, or rendering behavior.

## Decisions

### Add a JVM-only facade in `terminalJvm`

Add the interop API to the JVM backend module rather than shared `core`. Most Java and Kotlin users need a terminal-backed app entry point, and keeping the facade in `terminalJvm` avoids changing the Scala Native public surface.

Alternative considered: add facade types to `core`. This would make component helpers available without the JVM backend, but it would also expose Java-focused APIs from the shared Native-compiling source tree. That does not match the JVM-only scope of this change.

### Keep the facade narrow

Expose helpers for the first working app path: create a `TUI` with `SttyTerminal`, create `Text` and `Input`, register an input submit callback, add children, set focus, run, and request exit. Return existing siglyph types where possible so advanced users can still drop into the Scala-first API.

Alternative considered: wrap every component and option type. That would create a second API surface before real Java and Kotlin usage identifies which APIs need wrappers.

### Use JDK callback and collection types at the boundary

Use standard JDK types such as `java.util.function.Consumer` for callbacks. Avoid requiring Java and Kotlin users to call Scala-generated default-argument methods or construct Scala `Function1`, `Option`, or `Vector` for basic examples.

Alternative considered: document direct calls to Scala bytecode. This is possible today, but the generated names are brittle for users and make examples hard to read.

### Prefer source-level compile checks

Add smoke sources or tests that compile Java and Kotlin examples against the facade. A test-only Kotlin compiler dependency is acceptable if the build does not already provide a reliable Kotlin compiler. No runtime Kotlin dependency is added to siglyph artifacts.

Alternative considered: documentation-only Kotlin validation. That would not prove the Kotlin call shape stays valid.

## Risks / Trade-offs

- Facade grows into a parallel API → Keep the first version limited to basic app construction and document that advanced Scala APIs remain the primary API.
- Kotlin compile validation adds test build cost → Keep any Kotlin dependency test-scoped and use a minimal smoke source.
- Java-friendly helpers hide Scala options → Provide overloads for common defaults and leave advanced configuration available through existing Scala APIs.
- Package naming becomes hard to change after release → Use a clear JVM-specific package and document it as public once released.

## Migration Plan

This change is additive. Existing Scala, Java, and Kotlin code that calls current APIs continues to compile. Rollback removes only the new facade, tests, and documentation before a release.

## Open Questions

None.
