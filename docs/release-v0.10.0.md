# v0.10.0 release

Publication of **0.10.0** is authorized for **2026-11-04**. This is a minor release rather than a
patch because newly built artifacts raise the Scala compiler/TASTy baseline to 3.9.0 under the
project's pre-1.0 versioning policy.

The [dated 0.10.0 changelog](../CHANGELOG.md#0100---2026-11-04) is the source for GitHub Release
notes. The version comes from the authorized `v0.10.0` tag, not the local `0.1.0-SNAPSHOT` fallback.
Publication is verified: both publishing workflows succeeded, all ten GitHub Release jars were
downloaded and checked against API SHA-256 digests, and all ten Maven Central coordinates were
resolved with POM, binary, sources and Javadoc artifacts. Published dependency examples now use
0.10.0.

## Publication verification

- Tag `v0.10.0` points to reviewed main commit `41d48da0318316d523cca058e88d34e9467b66a2`.
  Finalization is merged in [PR #80](https://github.com/zikolach/siglyph/pull/80).
- [Final main Linux/macOS CI](https://github.com/zikolach/siglyph/actions/runs/41918785557) passed
  both jobs; accepted local macOS verification also passed.
- [Publish](https://github.com/zikolach/siglyph/actions/runs/41920497987) and
  [Publish Maven Central](https://github.com/zikolach/siglyph/actions/runs/41920497876) both passed
  after the single authorized tag push. No extra publication dispatch was used.
- [GitHub Release v0.10.0](https://github.com/zikolach/siglyph/releases/tag/v0.10.0) contains all ten
  binary jars; API SHA-256 digests, sizes, ZIP integrity and Native IR were checked.
- All ten Maven Central coordinates were fetched anonymously, including forty POM/binary/source/
  Javadoc payloads, matching SHA-1 sidecars and present PGP signature files.
- Published JVM and Scala Native consumer programs compiled and ran on Scala 3.9.0 / JDK 21, using
  all five platform-appropriate dependencies from Maven Central.
- GitHub Packages publishing returned all forty artifact deployments successfully. Independent
  package API enumeration requires `read:packages`, which the local token does not have; workflow
  deployment receipts, not an unauthenticated package listing, are the evidence for that provider.

## Compatibility and migration

- New artifacts require **Scala 3.9.0 LTS or a compatible newer compiler** to read their TASTy.
  Scala 3.7.x consumers must upgrade their compiler before adopting this release. Previously
  published `0.9.2` artifacts keep their existing requirements.
- **JDK 21** remains the supported build and JVM runtime baseline. Mill **1.3.1** and managed
  Temurin JDK 21 are pinned in the build header; Mill's default JDK does not raise the runtime floor.
- **Scala Native 0.5.12** and **Unicode 17.0.0** remain unchanged. No new production runtime
  dependency or expanded platform/emulator support is claimed.
- `TUIContext` remains source-compatible and extends the new read-only `RenderServices` interface.
  Existing component implementations do not need to adopt a new attachment API.
- See [README toolchain compatibility](../README.md#toolchain-compatibility) for the current
  compiler baseline and [the pinned compatibility matrix](pi-tui-compatibility.md) for upstream scope.

## Scope

- Dependency/toolchain updates and the five architecture improvements from merged PRs
  [#73](https://github.com/zikolach/siglyph/pull/73),
  [#74](https://github.com/zikolach/siglyph/pull/74),
  [#75](https://github.com/zikolach/siglyph/pull/75),
  [#78](https://github.com/zikolach/siglyph/pull/78),
  [#76](https://github.com/zikolach/siglyph/pull/76), and
  [#77](https://github.com/zikolach/siglyph/pull/77).
- Deterministic input-deadline tests; shared retryable backend protocol sessions; revision-checked
  viewport publication; isolated query bookkeeping; narrower render capabilities. The existing
  serialized runtime/output boundaries and 35 ms/75 ms production input deadlines are preserved.
- Release-blocker fix: cleanup no longer interrupts its own backend callback worker before JVM
  terminal restoration. Other workers are still interrupted; worker-stop signaling does not clear
  existing caller interrupt flags. Genuine process interruption and cleanup failures retain their
  propagation and explicit-retry behavior.
- Strict specification validation is repaired without dropping binding requirements. The five
  completed architecture changes are promoted into canonical specs and archived.

## Local preparation verification

The preparation worktree passed the following on macOS arm64 with managed Temurin JDK 21:

- Formatting/Scalafix and all-module compilation.
- Full JVM/Native test suite: **2,164 tests**, zero failures or ignored tests.
- Dedicated real-PTY lifecycle suite: **10 tests**, including fullscreen typed Ctrl+C.
- Actual fullscreen JVM process smoke: **3/3 clean Ctrl+C exits**, with restored termios, cursor,
  paste and alternate-screen state and no `InterruptedException`.
- Strict OpenSpec validation: **18 specifications pass**; no active changes remain. The five
  architecture changes and the cleanup fix are promoted and archived.
- Local packaging with `SIGLYPH_VERSION=0.10.0`: all ten artifact sets build, all ten POMs carry
  the expected coordinates/version, and all ten binary jars pass ZIP checks. Nonfatal existing
  Scaladoc link/plugin warnings remain; no publication workflow or provider was called.

These preparation checks do not replace review or verification of the final release commit.

## Local macOS validation instead of hosted CI

With maintainer approval, an exact-commit local macOS run may satisfy the macOS release gate when
hosted macOS runners are unavailable or outside the Actions budget. Record the tested commit,
macOS version/architecture, Mill/JDK versions, commands and complete results. Local success does
not change a cancelled GitHub check into a passed check or override required branch protections.
Linux CI and the other release checks remain required. No workflow or repository-rule change is
needed for this fallback.

Run the macOS conformance workflow commands locally on the final candidate:

```bash
mill --no-daemon quality
mill --no-daemon core.compile + terminalJvm.compile + coreNative.compile + terminalNative.compile
script -q -e /dev/null mill --no-daemon \
  core.test + terminalJvm.test + coreNative.test + terminalNative.test
./scripts/test-terminal-pty.sh
```

Retain the full JVM/Native suite, strict specification validation, package checks and fullscreen
Ctrl+C smoke required below. Recheck the final source commit before tagging; earlier worktree
results are not an unqualified substitute for final-candidate evidence.

## Evidence and pre-tag gates

The source baseline is merged main `5970ece66e6cdfba502199bdcb1644177db66dc0`, whose
[Linux/macOS CI](https://github.com/zikolach/siglyph/actions/runs/41826729388) passed. That evidence
predates this preparation's cleanup fix and specification changes; it is not final-candidate CI.

Release execution checklist:

1. Review and merge this preparation, including the cleanup regression fix and promoted specs.
   Require successful exact-head Linux CI and either successful macOS CI or the approved local
   macOS validation above. Recheck the final main commit before tagging.
2. Run formatting/lint, all-module compilation, full JVM/Native tests under a PTY, dedicated PTY
   lifecycle tests, and the fullscreen JVM Ctrl+C process smoke. Require a clean process exit and
   terminal restoration, not merely a visually restored terminal with an exception.
3. Require `openspec validate --all --strict` to pass with no baseline failures. Confirm the five
   architecture changes and verified cleanup fix are archived and canonical specs include their deltas.
4. Review remaining manual emulator checks in [interactive-smoke.md](interactive-smoke.md). Do not
   infer physical Ghostty/multiplexer behavior, Windows support or current upstream parity from CI.
5. Publication is authorized for 2026-11-04. The dated `## [0.10.0]` section, fresh Unreleased
   section and compare links are finalized. Select the reviewed final main commit after successful
   Linux CI and accepted exact-source local macOS verification, then push the `v0.10.0` tag once.
6. Confirm publishing/signing prerequisites without exposing credentials. A single authorized
   `v0.10.0` tag push invokes both **Publish** and **Publish Maven Central**. Do not additionally
   dispatch a publication workflow unless deliberately authorized; see [publishing.md](publishing.md).
7. Verify both successful publication runs, GitHub Packages, all ten Maven Central coordinates and
   all ten release jars. A successful build, tag push or GitHub Release alone is not publication proof.
8. Only after availability is verified, update README and Scala CLI dependency examples from
   `0.9.2` to `0.10.0` and smoke-test published JVM/Native consumers with the new compiler baseline.

## Verified GitHub Release jars

| JVM | Scala Native 0.5 |
| --- | --- |
| `siglyph-core_3-0.10.0.jar` | `siglyph-core_native0.5_3-0.10.0.jar` |
| `siglyph-terminal-jvm_3-0.10.0.jar` | `siglyph-terminal-native_native0.5_3-0.10.0.jar` |
| `siglyph-markdown_3-0.10.0.jar` | `siglyph-markdown_native0.5_3-0.10.0.jar` |
| `siglyph-image_3-0.10.0.jar` | `siglyph-image_native0.5_3-0.10.0.jar` |
| `siglyph-extras_3-0.10.0.jar` | `siglyph-extras_native0.5_3-0.10.0.jar` |

The version comes from the authorized tag (or explicit `SIGLYPH_VERSION` during local packaging).
Keep the build's `0.1.0-SNAPSHOT` fallback and published-version examples unchanged during preparation.
