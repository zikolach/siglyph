## Why

The approved language DX roadmap needs typed Scala composition and JVM language adapters beyond the published basic facade. All additions are unreleased, not part of 0.12.0.

## What Changes

- Add immutable typed Scala application composition and configuration, shared on JVM and Native.
- Add JVM builders and JDK collection/function adapters for tables, themes, scoped updates and deterministic testing.
- Provide optional Kotlin source extensions with receiver lambdas and explicit nullability, without coroutines or new runtime dependencies.
- Exercise the same table/filter/async workflow in Scala, Java and Kotlin; retain published-artifact compatibility consumers separately from candidate-only APIs.

## Capabilities

### New Capabilities

### Modified Capabilities

- `developer-api`: typed composition and optional JVM language ergonomics.

## Impact

Additive core and terminalJvm APIs, examples, tests and documentation. Existing constructors and runtime semantics remain unchanged; no commits, publication or release are performed.
