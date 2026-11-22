# API stability and release gates

[Documentation index](README.md) · [Publishing](publishing.md)

Siglyph remains **pre-1.0**. This policy defines a candidate stable subset and repeatable checks; it does not declare the entire core stable or authorize a release.

## Surface inventory

The [source inventory](../compatibility/api-inventory.json) covers publishable source files and top-level declarations. Language visibility is authoritative; nested members are not exhaustively indexed. The [executable policy](../compatibility/policy.json) names the exact classes/members gated against published **0.11.0**.

| Surface | Policy |
| --- | --- |
| Narrow `SiglyphJvm` facade | Preserve selected published public ABI; exercise Java/Kotlin consumers. |
| Selected Input/Text construction/value/render methods, Component contract, render getters/factories and TUI lifecycle/fullscreen entry points | Baseline-preserved candidate surface; the manifest is authoritative, not every method in these files. |
| DataTable, themes, scheduling/scopes and testing helpers | Experimental; evaluate through reference-app usage before graduating. |
| Scala composition, Java builders/Kotlin source DSL, streaming Markdown and editor interaction additions (since 0.13.0) | Experimental; publication does not graduate these APIs to stable. |
| Remaining public Markdown/image/extras/editor/layout/terminal APIs | Evolving; not covered by the selected ABI gate unless explicitly listed. Review changes and migration impact. |
| Private/package-private coordinators, lifecycle machinery and parser implementation | Internal even where JVM bytecode exposes package-restricted Scala members. |

New source files must be classified in the inventory. Explicit themes and auxiliary Input constructors retain the original constructor descriptor rather than relying on Scala default arguments for binary compatibility.

## Compatibility means different things

- **Source:** compatibility baseline 0.11.0 consumers compile with Scala 3.9.0; published 0.13.0 consumers require Scala 3.10.1 or a compatible newer compiler to read its TASTy. Both retain JDK 21. Published 0.12.0 keeps its Scala 3.9.0 baseline. Recompilation alone is not binary proof.
- **JVM binary:** selected published public/protected descriptors/class shape remain, and already compiled Scala/Java/Kotlin consumer binaries run against candidate jars. The gate is conservative and intentionally limited: it does not prove every inheritance, inline/TASTy, generic, serialization or behavioral compatibility case.
- **Behavior:** representative construction, input values, rendering and lifecycle fixtures pass, supplemented by the library regression/PTY suites. Descriptor equality cannot prove behavior.
- **Native:** rebuild consumers using Scala Native 0.5.12 and the supported native toolchain. Statically linked binaries are not validated by swapping jars, so no JVM-style Native ABI promise is made.

## Local candidate gate

Use a unique non-release version; do not overwrite the baseline or publish remotely. Example from a checkout:

```bash
env SIGLYPH_VERSION=0.13.0-compat-local mill --no-server -j 1 core.publishM2Local + terminalJvm.publishM2Local + coreNative.publishM2Local + terminalNative.publishM2Local
python3 scripts/check-compatibility.py --candidate 0.13.0-compat-local --repository "file://$HOME/.m2/repository"
```

Prefer a fresh version suffix per candidate so resolver caches cannot hide changes. The runner uses actual artifacts—not project source roots—and explicitly selects its repositories. Baseline artifacts and compiler tooling always resolve from Maven Central; the candidate repository cannot shadow the baseline. It compiles baseline Scala/Java/Kotlin fixtures, runs those binaries against the candidate, compiles current fixtures against the candidate, and rebuilds Native consumers against both versions. A failure exits nonzero. `--skip-native` is an explicit incomplete check and is reported as such.

Tools: Python 3, Coursier, Scala CLI, JDK 21 tools and the Kotlin compiler already used by repository tests (2.4.21). The checker resolves tool dependencies but adds no runtime dependency, compatibility plugin or hosted CI job.

## Negative checks and drift

```bash
python3 scripts/check-compatibility.py --inventory-only
python3 -m unittest discover -s scripts -p 'test_check_compatibility.py'
```

The existing Linux CI job runs the offline inventory and negative-fixture checks; it adds no new job or matrix. Full artifact candidate checks remain local/release gates.

The regression creates disposable baseline/candidate Java classes, removes a method and demonstrates both descriptor rejection and an actual `NoSuchMethodError` in an old binary. Other tests exercise changed descriptors/staticness and new abstract obligations. No broken production class is shipped.

## Post-publication check

After an explicitly authorized release, invoke the same runner with the **actual published version** and **without** a local repository. For the current release (the compatibility baseline remains 0.11.0):

```bash
python3 scripts/check-compatibility.py --candidate 0.13.0
```

This resolves Maven Central artifacts and is separate from candidate validation. The 0.13.0 Central-only check passed, including already compiled Scala 3.9.0/Java/Kotlin baseline binaries and Native consumer rebuilds; see the [release record](release-v0.13.0.md). No script here creates a tag, release or remote publication.

## Deprecation, migration and graduation

Patch releases must not intentionally break documented behavior. During 0.x, experimental API changes require a minor release and explicit migration notes: old/new call shapes, changed ownership/error semantics and platform constraints. Do not remove a failing manifest member or move the baseline merely to turn a candidate green. Re-scoping baseline-preserved APIs requires a separate reviewed policy/migration decision.

Before 1.0, graduate APIs only after reference-app usage, documented cancellation/resource bounds, cross-platform evidence and a deprecation strategy. Stable APIs should retain forwarding overloads where possible; removals require an announced major-version migration after 1.0. These are policies, not a claim that unfinished experimental APIs are already stable.

## Release-readiness checklist

- [ ] Agree release scope/version and identify intentional source/binary/behavior changes.
- [ ] Review the inventory/manifest and migration notes; no unexplained gate exceptions.
- [ ] Pass source tests, quality, OpenSpec and documentation checks for changed packages.
- [ ] Stage unique candidate artifacts; pass selected ABI, baseline-binary and all language/platform consumer checks.
- [ ] Verify bounded queues/data, cancellation, cleanup retry, restart isolation and error handling for affected runtime features.
- [ ] Record supported toolchains/platforms, real-terminal/PTy coverage and known limitations separately; do not equate virtual output with pixels.
- [ ] Finalize changelog, dependency examples, security/licensing notices and release notes.
- [ ] Obtain explicit release authorization, publish, then run Central-only consumer smoke and artifact integrity checks.

Completing #98 means these gates and policies exist and have evidence; it does not mean every future release checklist is already checked or that a 1.0 release is scheduled.
