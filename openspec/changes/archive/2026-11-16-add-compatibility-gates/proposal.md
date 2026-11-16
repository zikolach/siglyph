## Why
#98 needs explicit stability boundaries and evidence from release artifacts, not a claim that compiling current sources proves compatibility.

## What Changes
- Classify public surfaces and define a narrow baseline-preservation gate against published 0.11.0.
- Compare selected JVM public descriptors/class shape using JDK tools; exercise baseline-compiled Scala/Java/Kotlin consumers against candidate artifacts.
- Rebuild Native consumers against published/candidate NIR, without claiming JVM-style binary compatibility for Native.
- Add negative compatibility fixtures and local candidate/post-publication readiness instructions.

## Capabilities
### New Capabilities
- `compatibility-gates`: explicit API policy and artifact-based consumer validation.
### Modified Capabilities

## Impact
Local tools, fixtures and documentation. No runtime dependency, remote publication, 1.0 promise or additional hosted CI matrix. Existing Kotlin compiler tooling is reused for consumer checks.
