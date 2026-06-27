## 1. JVM Interop API

- [x] 1.1 Add a JVM-only interop package in `terminalJvm` for basic Java and Kotlin app construction.
- [x] 1.2 Add helpers or overloads for creating a default `TUI` with `SttyTerminal` and for creating `Text` and `Input` components without Scala default-argument calls.
- [x] 1.3 Add helpers for adding children, setting focus, running the TUI, requesting exit, and registering `Input` submit callbacks through JDK functional interfaces.
- [x] 1.4 Add Scaladoc or JavaDoc for the interop API covering JVM scope, basic usage, and non-goals.

## 2. Validation

- [x] 2.1 Add a Java smoke source or test that compiles against the documented interop API without Scala-generated default-argument calls.
- [x] 2.2 Add a Kotlin smoke source or test that compiles against the documented interop API without referencing `scala.Function1`.
- [x] 2.3 Verify Scala Native modules do not depend on the JVM interop sources or Kotlin compiler tooling.
- [x] 2.4 Verify the Mill build has no new runtime dependencies.

## 3. Documentation

- [x] 3.1 Update project documentation with JVM dependency coordinates for Java and Kotlin users.
- [x] 3.2 Add minimal Java and Kotlin examples that create a terminal-backed TUI with `Text`, `Input`, and submit handling.
- [x] 3.3 Document that Java/Kotlin support applies to JVM artifacts only and that Scala Native artifacts remain Scala-focused.

## 4. Final Checks

- [x] 4.1 Run `mill terminalJvm.test`.
- [x] 4.2 Run `mill __.compile`.
- [x] 4.3 Run `mill scalafmtCheck` and `mill scalafixCheck`.
- [x] 4.4 Run `openspec validate --all --strict`.
