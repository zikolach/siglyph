# v0.9.1 release execution (published)

The patch release after `v0.9.0` (2026-09-06) was authorized and published on 2026-10-13. Its
finalized [0.9.1 changelog section](../CHANGELOG.md#091---2026-10-13) covers merged PRs #63–#66.
The [`v0.9.1` tag](https://github.com/zikolach/siglyph/tree/v0.9.1) points to
`fee20427e2eb6b7ab5cc6a631466fbb56d948078`; the
[GitHub Release](https://github.com/zikolach/siglyph/releases/tag/v0.9.1) was published at
2026-10-13 07:00:42 UTC with ten jars. README and Scala CLI dependency examples now use the
available Maven Central `0.9.1` artifacts.

## Release scope and evidence

- #63 reduces redundant painting of untouched full-width viewport rows; #64 reuses editor visual
  rows on cursor-only moves. The measurements in [performance-benchmarks.md](performance-benchmarks.md)
  are fixed-workload, same-host comparisons, not general application-speed promises.
- #65 uses parser-idle deadlines for incomplete input framing. The 35 ms lone-Escape and 75 ms
  other non-paste cutoffs are eligibility deadlines, not real-time callback guarantees.
- #66 splits large synchronized frames into bounded physical writes. Their successful
  concatenation preserves prior output, but a `Terminal.write` call no longer necessarily contains
  a complete frame or control. Failed-write repair is best-effort.
- The supported interactive terminal scope is JVM and Scala Native on macOS/Linux. Windows,
  Scala.js/browser, and emulator-specific visual parity are not claimed. The
  [compatibility matrix](pi-tui-compatibility.md) compares only against its pinned `pi-tui` commit
  reviewed on 2026-09-05, not current upstream parity.
- At preparation, merged `main` was `da1cd430bb6e2eb7597e1fafacd81546154f0d0f`. Its
  [CI run](https://github.com/zikolach/siglyph/actions/runs/37917980571) passed both Linux
  `Test and package jars` and `macOS terminal conformance`. The final tag commit
  `fee20427e2eb6b7ab5cc6a631466fbb56d948078` passed those same jobs in its
  [CI run](https://github.com/zikolach/siglyph/actions/runs/38358370456) before tagging.

## Publication checklist — completed 2026-10-13

1. The release-finalization documentation was merged as #68. Final `main` and the peeled
   `v0.9.1` tag both point to `fee20427e2eb6b7ab5cc6a631466fbb56d948078`; its
   [CI run](https://github.com/zikolach/siglyph/actions/runs/38358370456) passed Linux
   `Test and package jars` and `macOS terminal conformance` before tagging.
2. The authorized release used the dated `## [0.9.1]` changelog section and retained a fresh
   `[Unreleased]` section. The GitHub Release notes contain the 0.9.1 change list.
3. The `v0.9.1` tag triggered both publication workflows: [Publish](https://github.com/zikolach/siglyph/actions/runs/38359183129)
   and [Publish Maven Central](https://github.com/zikolach/siglyph/actions/runs/38359183203)
   completed successfully. The latter automatically released non-SNAPSHOT artifacts to Central.
   No extra manual workflow dispatch was used for this release.
4. The [GitHub Release](https://github.com/zikolach/siglyph/releases/tag/v0.9.1) lists all ten
   jars below. All ten corresponding `0.9.1` POMs return HTTP 200 from
   [Maven Central](https://repo.maven.apache.org/maven2/io/github/zikolach/siglyph-core_3/0.9.1/siglyph-core_3-0.9.1.pom).
   The GitHub Packages workflow succeeded; this check did not independently verify package
   listing or unauthenticated coordinate resolution there.
5. README, `examples/scala-cli/`, and [publishing guidance](publishing.md) now point consumers
   to `0.9.1`. The build's `0.1.0-SNAPSHOT` local fallback remains unchanged; it is not the
   release version source.

GitHub Release jar names, verified against the published release:

| JVM | Scala Native 0.5 |
| --- | --- |
| `siglyph-core_3-0.9.1.jar` | `siglyph-core_native0.5_3-0.9.1.jar` |
| `siglyph-terminal-jvm_3-0.9.1.jar` | `siglyph-terminal-native_native0.5_3-0.9.1.jar` |
| `siglyph-markdown_3-0.9.1.jar` | `siglyph-markdown_native0.5_3-0.9.1.jar` |
| `siglyph-image_3-0.9.1.jar` | `siglyph-image_native0.5_3-0.9.1.jar` |
| `siglyph-extras_3-0.9.1.jar` | `siglyph-extras_native0.5_3-0.9.1.jar` |

The corresponding ten `io.github.zikolach` Maven Central coordinates were checked at version
`0.9.1`; see [publishing.md](publishing.md) for the complete coordinate list and version-source
rules. The jar list records release assets, not an independent GitHub Packages listing check.
