## 1. Native Module Setup

- [x] 1.1 Add a `markdownNative` publish module that depends on `coreNative` and compiles canonical `markdown/src` sources.
- [x] 1.2 Add an `imageNative` compile spike module that depends on `coreNative` and compiles canonical `image/src` sources.
- [x] 1.3 If `image/src` does not compile on Native, identify the exact unsupported APIs and stop unless a small shared compatibility boundary can preserve the public image API without duplicated rendering logic.
- [x] 1.4 If the image portability path is clean, keep `imageNative` as a publish module for the Native `siglyph-image` artifact.

## 2. Tests and Validation Targets

- [x] 2.1 Add or wire Native validation for baseline Markdown behavior, using shared tests where practical or Native compile/link validation where test reuse is not practical.
- [x] 2.2 Add or wire Native validation for baseline image behavior, using shared tests where practical or Native compile/link validation where test reuse is not practical.
- [x] 2.3 Ensure JVM Markdown and image tests continue to run unchanged.
- [x] 2.4 Ensure Native optional module validation does not add third-party runtime dependencies.

## 3. Documentation and Publishing Metadata

- [x] 3.1 Update README dependency guidance to list Native optional Markdown and image artifacts where available.
- [x] 3.2 Update `docs/publishing.md` and release verification guidance to include new Native optional artifacts.
- [x] 3.3 Document that Java/Kotlin interop and `keyTester` remain JVM-only.
- [x] 3.4 Update promoted OpenSpec specs after implementation is complete.

## 4. Validation

- [x] 4.1 Run `mill markdownNative.compile`.
- [x] 4.2 Run `mill imageNative.compile` if the image Native artifact remains in scope.
- [x] 4.3 Run relevant Native optional module tests or compile/link validation added by this change.
- [x] 4.4 Run `mill markdown.test`.
- [x] 4.5 Run `mill image.test`.
- [x] 4.6 Run `mill __.compile`.
- [x] 4.7 Run `mill scalafmtCheck`.
- [x] 4.8 Run `mill scalafixCheck`.
- [x] 4.9 Run `openspec validate --all --strict`.
- [x] 4.10 Run `git diff --check`.
