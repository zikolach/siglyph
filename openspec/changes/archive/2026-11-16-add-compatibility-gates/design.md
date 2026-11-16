## Classification
Preserve the explicitly selected legacy core/interop surface against 0.11.0 within the documented Scala/JDK baseline. This is a candidate stability policy, not a declaration that every public package is frozen. New table/theme/scheduling/testing APIs remain experimental until usage and migration review. Package-private runtime/coordinator helpers are implementation details even if JVM bytecode exposes them.

## Gates
The checked-in policy names classes and public members to compare. Use javap public/protected descriptors and class headers, detecting removals, descriptor/static/finality changes and new abstract interface obligations. This conservative JVM check is not a replacement for complete MiMa/TASTy analysis: inheritance resolution, inline behavior, serialization and unlisted members are outside its claim.

Compile small Scala/Java/Kotlin consumers against published baseline artifacts. Run those same binaries with candidate artifacts, then compile/run current source fixtures against the candidate. Build Native consumers against each artifact set; Native is statically linked and requires a rebuild rather than a JVM binary-swap claim.

A local unique candidate version is published only to Maven local/staging. Resolve with an explicit file repository plus Maven Central for dependencies. Post-publication smoke is a separate invocation using the actual released version and no candidate repository. Baseline fixtures are read-only inputs; never use current source roots as consumer dependencies.

Negative tests build disposable Java APIs, remove a method or alter a descriptor, and prove the gate detects the incompatibility. No intentionally broken production class is shipped.

## Dependencies and limitations
Use Python standard library, JDK 21 javap/javac/java, existing Coursier/Scala CLI and the already-used Kotlin compiler version. No compatibility plugin or runtime dependency is added. Run locally first. The existing Linux CI job also runs offline inventory/negative-fixture checks, without a new job or matrix. Matrix limitations and skipped Native checks must be explicit, not reported as full success.
