## Context

Published SiglyphJvm exposes basic text/input construction only. DataTable requires Vector and Scala functions; themes require Map/Option; scope submission uses by-name parameters; TUITestDriver uses curried Scala functions. Wrappers should adapt these boundaries, not duplicate their semantics.

## Decisions

- Introduce immutable `App` configuration in shared core with terminal injection, typed TUIOptions, children and optional focus. Building does not start a runtime or own a backend.
- Add separate JVM builder classes and `JvmUi` facade (unreleased). Builders snapshot collections on ingestion and at build; nulls fail at the boundary. Selection is java.util.Optional; callbacks use JDK interfaces.
- Delegate scope submission/scheduling directly to ApplicationScope; preserve admission outcomes and borrowed scheduler ownership. Never schedule blocking work in UI callbacks.
- `TUI.runWithSetup` starts once, admits setup outside runtime locks, then enters the existing wait/cleanup path without calling start again. `run()` retains its descriptor and delegates a no-op hook. This prevents a fast setup stop/failure from being erased by an accidental restart. Setup is skipped if startup already stopped/failed/exited; once admitted it may race external stop.
- Managed testing delegates to TUITestDriver.withSession, retaining its cleanup/suppression guarantees and deterministic clock ownership.
- Kotlin is optional source, compiled by existing test-only tooling. Receiver lambdas operate on JVM builders; selection extensions explicitly return nullable values. No Kotlin dependency enters library artifacts.
- Keep published baseline consumers separate: candidate-only examples must fail against 0.12.0 rather than imply those APIs shipped. A local artifact runner can use an explicit candidate classpath; publication verification requires a future real version.

## Risks and non-goals

Builders are mutable and single-threaded; built applications/components retain their existing threading contracts. No coroutine, effect runtime, independent event loop or ABI promise for all core types is introduced. Virtual terminal results do not establish real-emulator pixel fidelity.
