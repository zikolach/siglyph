# Language-native application examples (unreleased)

These APIs are **not in 0.12.0**. Use a locally staged candidate, not the published installation
examples. Scala composition runs on JVM and Native; Java/Kotlin and interactive launchers are JVM-only.
The optional [Kotlin DSL](kotlin/SiglyphDsl.kt) is source you include in your application, not a published
Kotlin artifact. It adds receiver lambdas and nullable selection; no coroutines or new library runtime dependencies.

## Same application

[Scala](scala/DxApp.scala), [Java](java/DxApp.java) and [Kotlin](kotlin/DxApp.kt) use immutable rows
`a/alpha` and `b/界beta`. Enter submits the filter; stable IDs retain selection, empty results clear it,
and scoped scheduled delivery replaces rows. Default execution runs the deterministic virtual-terminal
scenario and exits. It checks filtering via paste/Enter, empty selection, narrow resize, scheduled
updates, closed-scope rejection and cleanup. Virtual assertions are not real-terminal pixel tests.

Interactive Java/Kotlin use `--interactive`; Scala uses [DxInteractive](scala/DxInteractive.scala).
Tab switches filter/table focus, arrows select, Enter applies the filter, Escape exits. A one-second
fixed-delay producer updates `b` while preserving the current filter. The app owns its executor;
scopes close with the runtime, the adapter closes next, and the executor closes last. No callback does
blocking work. These small tables use complete-document rendering, not fullscreen range virtualization;
large tables should use the existing [ScrollView layout](../../docs/data-table.md).

## Stage and test candidate artifacts

Requires Mill, Coursier, Scala CLI and JDK 21 (plus Native toolchain for `--native`).
`0.13.0-dx-SNAPSHOT` is an arbitrary local test label, not a selected next release. Local staging is not a release:

```bash
SIGLYPH_VERSION=0.13.0-dx-SNAPSHOT mill '{core,terminalJvm,coreNative,terminalNative}.publishM2Local'
python3 scripts/check-language-dx.py --version 0.13.0-dx-SNAPSHOT --repository "file://$HOME/.m2/repository" --native
python3 scripts/check-compatibility.py --candidate 0.13.0-dx-SNAPSHOT --repository "file://$HOME/.m2/repository"
```

The first checker resolves jars/NIR artifacts, never workspace source roots or Mill class directories.
The existing compatibility gate separately checks published baseline descriptors and old binaries.

## Standalone build tools

After staging locally, from repository root:

```bash
mvn -f examples/language-dx/java/pom.xml -Dsiglyph.version=0.13.0-dx-SNAPSHOT compile exec:java
mvn -f examples/language-dx/java/pom.xml -Dsiglyph.version=0.13.0-dx-SNAPSHOT compile exec:java -Dexec.args=--interactive
gradle -p examples/language-dx/kotlin -PsiglyphVersion=0.13.0-dx-SNAPSHOT run
gradle -p examples/language-dx/kotlin -PsiglyphVersion=0.13.0-dx-SNAPSHOT run --args=--interactive
scala-cli run examples/language-dx/scala/DxApp.scala --scala 3.10.1 --jvm 21 --repository "file://$HOME/.m2/repository" --dependency io.github.zikolach::siglyph-terminal-jvm:0.13.0-dx-SNAPSHOT
scala-cli run examples/language-dx/scala/DxInteractive.scala --scala 3.10.1 --jvm 21 --repository "file://$HOME/.m2/repository" --dependency io.github.zikolach::siglyph-terminal-jvm:0.13.0-dx-SNAPSHOT
```

Maven uses JDK 21; Gradle requires a Kotlin-2.4.21-compatible installation and JDK toolchain resolution.
Neither build tool is downloaded by library tests. The artifact checker directly compiles/runs the exact
same Java/Kotlin sources, including the optional DSL, with the pinned compiler versions.

## Contracts and limitations

- `App` is immutable configuration. `AppBuilder`, `TableBuilder` and `ThemeBuilder` are single-threaded.
  Builds snapshot configuration but do not clone components; do not share component instances between concurrent sessions.
- Java row collections are copied and null elements/IDs and duplicate IDs are rejected. Rows themselves must stay immutable.
  Cell callbacks return non-null text. `Optional`/Kotlin nullable selection represents empty data.
- `JvmUi.submit` delegates bounded admission: `Accepted` is not completion, `Full` is backpressure,
  `Closed` is permanent for that scope, even after runtime restart. Never retry in a busy loop.
- `JvmUi.test` owns its supplied virtual clock and restores the terminal on success or failure, retaining
  suppressed cleanup failures. A `JvmTestSession` is valid only during its callback.
- `TUI.runWithSetup` starts once, installs session work, and waits without restarting after a fast
  stop/failure. `JvmUi.run` delegates to it; existing `TUI.run()` remains available. Setup is finite,
  outside runtime locks, and may race an external stop after admission.
- `JvmScheduler` borrows its executor, never shuts it down, and propagates close failures.
  `navigation` is a convenience for exactly two controls (Tab/Escape), not a general focus manager.
- New APIs are experimental/unreleased. Existing `SiglyphJvm` descriptors and published examples are unchanged.

## After a separately authorized release

Set `SIGLYPH_RELEASE` to the actual version containing these APIs, then run:

```bash
python3 scripts/check-language-dx.py --version "$SIGLYPH_RELEASE" --published --native
```

This resolves only Maven Central. No published-success claim is made before that command passes.
