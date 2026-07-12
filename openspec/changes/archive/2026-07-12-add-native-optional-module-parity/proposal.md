## Why

Siglyph already publishes Native artifacts for core, terminal, and extras, but optional Markdown and image helpers are JVM-only artifacts even though their current implementations are mostly shared and dependency-light. Closing this gap makes the documented JVM/Scala Native story more consistent and lets Native applications use the same optional UI helpers where the code is portable.

## What Changes

- Add a Scala Native Markdown module that reuses the existing `markdown/src` implementation and publishes as the Native variant of `siglyph-markdown`.
- Add a Scala Native image module only if a compile spike confirms the current image helper implementation is portable, or after extracting the smallest shared boundary needed for file/base64 compatibility.
- Keep Java/Kotlin interop and `keyTester` JVM-only.
- Update publishing metadata, release docs, README dependency examples, and validation commands for any new Native artifacts.
- Add Native compile/test validation for the optional modules without adding third-party runtime dependencies.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `developer-api`: Document and validate optional module parity across JVM and Scala Native while preserving explicit JVM-only surfaces.
- `markdown-rendering`: Require the baseline dependency-free Markdown renderer to be available as a Scala Native artifact when compiled from shared sources.
- `image-rendering`: Require the baseline image component/helper module to be available as a Scala Native artifact when its file, base64, and byte-sniffing implementation is portable or cleanly factored.

## Impact

- Affected build modules: `build.mill`, publishing metadata, Native mirror modules, and optional module test targets.
- Affected docs: README dependency examples, `docs/publishing.md`, and release verification guidance.
- Affected artifacts: add Native variants for `siglyph-markdown` and, if the portability check passes or is cleanly factored, `siglyph-image`.
- No new third-party runtime dependencies are expected.
- Java/Kotlin JVM interop remains JVM-only and unchanged.
