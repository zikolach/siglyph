## 1. Contract and APIs

- [x] 1.1 Record consumer friction, design and unreleased scope.
- [x] 1.2 Add typed shared Scala application composition and tests on JVM/Native.
- [x] 1.3 Add Java builders, copied collections, JDK callbacks and managed testing adapters.
- [x] 1.4 Add optional Kotlin receiver extensions with explicit nullability.
- [x] 1.5 Add single-start managed setup; prevent fast stop/failure from restarting the runtime.

## 2. Consumers and validation

- [x] 2.1 Add equivalent Scala/Java/Kotlin table/filter/scheduled-update consumers and runnable build instructions.
- [x] 2.2 Execute real Java/Kotlin consumers and shared JVM/Native tests.
- [x] 2.3 Verify staged candidate artifacts and existing published baseline consumers.
- [x] 2.4 Run quality, docs, inventory and strict OpenSpec checks; document gaps.

## 3. Deferred release follow-up

- [ ] 3.1 Verify new APIs against a future published version (blocked until separately authorized publication).

## Evidence and limits

- Full JVM `core.test` passes. Shared `AppSuite` (6 tests on JVM and Native) exercises typed configuration, filtering/selection, bounded Accepted/Full/Closed delivery, cancellation and restart. Startup regressions count terminal starts and preserve fast worker/setup failures. `TUISuite` also passes on both JVM and Native (92 tests each).
- `LanguageDxSuite` actually compiles and runs Java/Kotlin consumers; checks copied collections, nulls, duplicate IDs, immutable themes, borrowed executors, screen diagnostics and suppressed cleanup failures. Existing `SiglyphJvmInteropCompileSuite` remains unchanged and passes.
- Candidate jars/NIR are locally staged with the arbitrary `0.13.0-dx-SNAPSHOT` test label (not a chosen release version). `check-language-dx.py --native` executes Scala JVM/Native, Java and Kotlin from resolved artifacts and compiles the Scala interactive launcher. Candidate APIs are not claimed for 0.12.0.
- Existing compatibility gate passes published 0.11.0 descriptors, baseline Scala/Java/Kotlin binaries on candidate, and Scala Native consumer rebuilds. Published documentation examples compile against their existing released artifacts.
- Actual Maven 3.10.0 Java and Gradle 9.5.0 Kotlin projects execute successfully. Gradle needs `JAVA_HOME` set to JDK21 on this host. Its initial root source directory included build scripts; explicit Kotlin source includes fixed that failure.
- Five offline checker tests cover explicit versions, published-mode repository/prerelease rejection and repository propagation. Twelve documentation checker tests, source inventory, strict OpenSpec validation and Mill quality pass.
- No full all-module test sweep or real-terminal visual smoke was performed. Interactive examples compile; deterministic virtual execution does not prove terminal-emulator pixels. This is a bounded vertical slice, not a comprehensive DSL or full-core ABI promise.
- No new library runtime dependencies or remote publication. The optional Kotlin DSL remains application source rather than a separately published module.
