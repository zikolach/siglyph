# 0.13.0 release preparation

**Prepared, not published**, on 2026-11-22. Latest verified publication remains **0.12.0**.
The maintainer explicitly authorized commit, PR merge and publication on 2026-11-22.
Publication and post-publication verification are pending; no duplicate workflow dispatch is planned.
Runtime source: `0f1534807b052dbf62e9dce881932a11a8eb8930` (main); preparation branch:
`release/prepare-0.13.0`. Baseline [CI](https://github.com/zikolach/siglyph/actions/runs/44943029245)
passed before documentation/spec/validation-only preparation. [Changelog](../CHANGELOG.md#0130---2026-11-22).

## Scope and migration

- Language DX (#121): experimental shared Scala `App`, single-start `TUI.runWithSetup`, JVM
  `JvmUi` builders, copied Java collections/JDK callbacks, managed tests and optional Kotlin source DSL.
  Kotlin extensions are application source, not an eleventh published coordinate; Java/Kotlin adapters
  are JVM-only. Builders are mutable/single-threaded; borrowed schedulers remain caller-owned.
- Editor (#122): visual-row Up/Down navigation with preferred display columns and exact draft/cursor/
  paste-metadata restoration after deliberate history entry. No public snapshot API was added.
  See [editor interactions](editor-interactions.md).
- Streaming Markdown (#123): experimental bounded responses on JVM/Native, conservative parsed-prefix
  reuse, provisional inline output, completion/reset and local follow-end viewer. Active selection survives
  only unchanged preceding text/cell mappings; plain primary clicks clear fullscreen selection even on blank
  rows. Cached visible-row rendering and viewport-bounded highlighting reduce unchanged-document scroll work.
- Dependency updates: Scala **3.10.1** (#119), jsoniter-scala core/macros **2.43.0** (#117–#118),
  GitHub Actions checkout **v7**, setup-java **v6**, cache **v6** (#120).
  No new library runtime dependency or supported platform.

**Consumer compiler requirement:** new candidate TASTy requires a **Scala 3.10.1-compatible compiler**;
Scala 3.9.0 source compilation against new artifacts is not promised. Published 0.12.0 installation
examples and its Scala 3.9.0 baseline remain unchanged. JDK **21**, Scala Native **0.5.12**, Mill **1.3.1**
and Unicode **17.0.0** are unchanged. Already compiled selected Scala 3.9.0/Java/Kotlin JVM consumers
are tested separately; this is not whole-core binary compatibility. Native consumers must be rebuilt.
The `scalatui` namespace and Scalafix configuration remain unchanged.

## Limits

APIs remain pre-1.0: App/builders/streaming and table/theme/scheduling/testing surfaces are experimental
or evolving, not blanket stable APIs. See [API policy](api-stability.md). Streaming admission defaults to
1 Mi UTF-16 units and rejects overflow rather than truncating. Ambiguous lists/quotes/indented code and
unfinished blocks can reparse growing suffixes with quadratic total work across tiny chunks. Finalization
and custom parsers parse complete source; changed content still needs full layout. No universal O(delta),
frame-rate, full CommonMark, universal selection-under-reflow, screen-reader, emulator-pixel or Native
binary-swap guarantee is made. Transport/providers/retries remain application-owned.
See [streaming limits](streaming-markdown.md) and the pinned [pi-tui matrix](pi-tui-compatibility.md);
this release preparation makes no new completeness/parity claim.

## Local candidate evidence

Candidate: `0.13.0-prep-0f15348-20261122135937`, unique to this HEAD and preparation run.
Repository: `file://$HOME/.m2/repository`; never substitutes for published baseline resolution.
The compatibility policy deliberately remains at published **0.11.0**, with baseline Scala **3.9.0**,
candidate Scala **3.10.1**, Kotlin **2.4.21**, JDK **21** and Native **0.5.12**.

Commands executed from the repository root on macOS arm64 (2026-11-22):

```bash
export SIGLYPH_VERSION=0.13.0-prep-0f15348-20261122135937
mill --no-server -j 1 core.publishM2Local + terminalJvm.publishM2Local + markdown.publishM2Local + image.publishM2Local + extras.publishM2Local + coreNative.publishM2Local + terminalNative.publishM2Local + markdownNative.publishM2Local + imageNative.publishM2Local + extrasNative.publishM2Local
python3 scripts/check-local-release.py --version "$SIGLYPH_VERSION"
python3 scripts/check-compatibility.py --candidate "$SIGLYPH_VERSION" --repository "file://$HOME/.m2/repository"
python3 scripts/check-language-dx.py --version "$SIGLYPH_VERSION" --repository "file://$HOME/.m2/repository" --native
openspec archive add-language-dx --yes
openspec validate --all --strict
python3 scripts/check-docs.py --compile
mill --no-server -j 1 quality
python3 -m unittest discover -s scripts -p 'test_check_*.py'
python3 scripts/check-compatibility.py --inventory-only
```

- Local staging: **passed**, all ten modules, exit 0. Compiler warnings are retained in the local log;
  no warnings were suppressed and no runtime source was changed.
- Artifact integrity: **passed**, ten coordinates/forty POM, binary, source and Javadoc payloads.
- Compatibility: **passed**, selected JVM ABI against 0.11.0; old Scala 3.9.0, Java and Kotlin binaries
  ran against candidate jars. New Scala 3.10.1 JVM/Native, Java and Kotlin source consumers passed;
  baseline and candidate Native consumers were linked and run, without `--skip-native`.
- New language-DX artifact consumers: **passed**, Java/Kotlin JVM and shared Scala 3.10.1 JVM/Native
  compiled and executed from resolved candidate artifacts; Scala interactive launcher compiled.
  Runner reported `published=False; native=True`, exit 0.
- Candidate editor/streaming-Markdown artifact smoke: **passed on JVM and Native**, including visual-row
  navigation, Unicode draft/history restoration, bounded streaming completion, range output and the
  cache-preserving ScrollView refresh overload. The local `.empryo/release-0.13.0/FeatureConsumer.scala`
  ran against staged Markdown/core artifacts (not workspace source roots); Native was linked and run.
- OpenSpec: **30 specs passed**, zero failures. Archive warned about the deliberately deferred 1/10 task.
- Documentation: **58 Markdown files passed**, published version remains 0.12.0; three marked recipes
  recognized. Initial run caught the old active-change link after archiving; roadmap link corrected.
  Published-coordinate JVM/Native quickstarts and marked guide recipes also **compiled successfully**.
- Offline tooling: combined **29 tests passed**, including five new local payload-validator tests.
  Mocked compatibility output emitted by these tests is not artifact-consumer evidence.
- API inventory and fresh `mill --no-server -j 1 quality`: **passed**, exit 0. Full library tests are covered by
  the linked baseline CI; no fresh full-suite run is claimed for this documentation/validation-only preparation.
- Local logs (ignored, not publication artifacts): `.empryo/tmp/tabs/main/stage-013.log`,
  `payloads-013.json`, `compatibility-013.log`, `language-dx-013.log`, `docs-013.log` and
  `quality-013.log` in the same directory.
  The payload report records SHA-256, sizes, POM coordinates/dependency versions, ZIP integrity and Native IR;
  it is not remote checksum, signature or signer-trust verification.

## Real-backend PTY smoke

Parent-run PTY smoke on this macOS arm64 host **passed all four cases**: composer JVM/Native and
streaming JVM/Native, using actual JDK 21/Stty and freshly linked POSIX Native executables.
Build commands were `mill interactiveJvmDemo.compile + fullscreenJvmDemo.compile + interactiveNativeDemo.nativeLink + fullscreenNativeDemo.nativeLink`;
JVM launch classpaths came from `interactiveJvmDemo.runClasspath` and `fullscreenJvmDemo.runClasspath`.
Exact launch arguments and exit results are in local `.empryo/release-0.13.0/{composer-jvm,composer-native,stream-jvm,stream-native}.json`;
matching `.terminal.log` captures and `pty-smoke.py` retain local evidence.

- Composer: pasted Unicode multiline draft, resized 24 to 120 columns, exercised history recall/restore,
  verified submission by local echo and Ctrl+C exit 0.
- Streaming: generated response, wheel-up, drag spanning 600 ms of streaming, new drag/clear click,
  resize 70 to 120 columns and Ctrl+C cancellation exit 0.
- All cases asserted restored ECHO/ICANON, disabled bracketed paste and visible cursor; streaming also
  asserted alternate-screen exit and mouse-reporting disable.

This is PTY input/protocol/lifecycle smoke, **not visual emulator/pixel or selection-geometry proof**.
Manual emulator appearance and cross-emulator behavior remain unverified; virtual tests and PTY output
must not be described as proof of those properties.

## OpenSpec disposition

Completed `add-language-dx` implementation is promoted/archived without changing its requirements.
Its unchecked future-publication task 3.1 is retained as deferred and transferred to the gate below;
archiving implementation does not assert that unavailable published 0.13.0 consumers passed.

## Remaining release gates

- [x] Finalize local artifact/consumer/documentation evidence above.
- [x] Record independently performed real-backend PTY editor and streaming smoke and its limits (above).
- [x] Record coverage limits: release validation uses green CI, deterministic JVM/Native tests and real-backend
  PTY smoke. Manual emulator visual/pixel/selection-geometry validation remains unverified and is not claimed.
- [ ] Review and merge preparation changes; require green CI for the final release tree before tagging.
- [x] Explicit publication authorization received on 2026-11-22; use the CI-green merged release tree.
- [ ] After authorized publication, verify all ten remote coordinates/forty POM/binary/source/Javadoc
  payloads, release asset integrity and signatures with accurate trust boundaries.
- [ ] Deferred language-DX task 3.1: run `python3 scripts/check-language-dx.py --version 0.13.0 --published --native`
  against Maven Central only, plus `python3 scripts/check-compatibility.py --candidate 0.13.0` without a local repository.
- [ ] Only after verified publication, update `publishedVersion`, installation examples and publication record;
  clear `preparedVersion`. Never overwrite historical release evidence or claim local artifacts are published.
