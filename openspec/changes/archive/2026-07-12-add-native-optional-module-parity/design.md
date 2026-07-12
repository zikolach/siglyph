## Context

Siglyph currently publishes Native artifacts for core, the POSIX terminal backend, and extras. Markdown and image helper modules are published only as JVM artifacts even though the baseline Markdown renderer is dependency-free and the image module mainly uses shared terminal protocol helpers plus file, base64, and byte-sniffing utilities.

The project already uses Mill source-root mirror modules for Native parity: `coreNative` compiles `core/src`, `interactiveDemoNative` compiles `interactiveDemo/src`, and `extrasNative` compiles `extras/src`. The optional module parity work should follow that pattern instead of duplicating source trees.

Existing constraints remain in force: no new third-party runtime dependencies, Scala 3 only, shared code should stay under the existing public `scalatui` package namespace, and JVM-only APIs such as Java/Kotlin interop should remain JVM-only.

## Goals / Non-Goals

**Goals:**

- Publish a Native Markdown artifact from the existing baseline Markdown implementation.
- Publish a Native image artifact when the existing image implementation compiles on Scala Native or after the smallest needed portability boundary is extracted.
- Reuse existing `markdown/src` and `image/src` sources through Mill source-root configuration.
- Keep JVM and Native optional module APIs aligned where the API is platform-meaningful.
- Update documentation, publishing metadata, and validation for the new Native artifacts.

**Non-Goals:**

- Do not add a third-party Markdown parser, image decoder, scaler, or transcoder.
- Do not add Java/Kotlin interop to Scala Native artifacts.
- Do not make `keyTester` Native as part of this change.
- Do not change the public component render contract.
- Do not add platform-specific duplicate implementations for Markdown or image rendering logic.

## Decisions

### Decision: Mirror optional modules through Mill source roots

Add Native mirror modules that compile the canonical optional source roots, matching the existing `coreNative` and `extrasNative` pattern.

Alternatives considered:

- Duplicate Native source directories. Rejected because it creates parallel implementations and raises drift risk.
- Move optional modules into core. Rejected because optional dependencies and module boundaries are a documented public contract.

### Decision: Treat Markdown Native support as direct shared-source parity

The baseline Markdown module depends on core APIs and contains no JVM terminal backend dependency. The Native module should compile `markdown/src` against `coreNative` and publish as the Native variant of `siglyph-markdown`.

Alternative considered:

- Keep Markdown JVM-only until richer parser adapters exist. Rejected because the existing dependency-free baseline is already useful and should be portable independently of future adapters.

### Decision: Gate image Native support with a compile spike

The image module should first be compiled against Scala Native using a mirror module. If current uses of Java file, path, or base64 APIs do not compile, extract the smallest platform-neutral boundary needed while preserving the public image API and avoiding duplicated protocol or dimension-sniffing logic.

Alternatives considered:

- Drop file-loading helpers from Native. Rejected unless the compile spike proves no clean shared boundary is possible in scope, because it would make the Native artifact materially weaker.
- Add a third-party image or base64 library. Rejected for this change because dependency policy requires explicit approval and the current goal is artifact parity for existing dependency-light helpers.

### Decision: Keep JVM-only surfaces explicit

Java/Kotlin interop and `keyTester` remain JVM-only. Optional module parity means Scala APIs and optional rendering/helper modules that are meaningful on both platforms, not every utility or language interop surface.

Alternative considered:

- Create Native equivalents for every JVM module. Rejected because it expands scope and blurs intentional platform boundaries.

## Risks / Trade-offs

- Image helper APIs may use Java APIs that Scala Native does not support fully. Mitigation: run the image Native compile spike early and factor a small compatibility boundary only if needed.
- Publishing more artifacts increases release verification scope. Mitigation: update release docs and validation loops in the same change.
- Native optional module tests may need shared test-source wiring. Mitigation: reuse existing test behavior where practical and add compile/link validation if full test reuse is not available immediately.
- Documentation could imply Java/Kotlin Native support. Mitigation: state that Java/Kotlin interop remains JVM-only.

## Open Questions

- Does the current image module compile unchanged under Scala Native 0.5.12?
- Can existing Markdown and image tests be reused directly for Native, or is compile/link validation the right first Native guardrail?
