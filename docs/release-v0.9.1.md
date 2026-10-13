# v0.9.1 release execution (publication pending)

The patch release after `v0.9.0` (2026-09-06) was authorized for 2026-10-13. Its finalized
[0.9.1 changelog section](../CHANGELOG.md#091---2026-10-13) covers merged PRs #63–#66.
Finalizing these notes does not itself create a `v0.9.1` tag, GitHub Release, or package artifacts.
README and Scala CLI dependency examples remain at the latest published version, `0.9.0`, until
`0.9.1` artifacts are available.

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
  `Test and package jars` and `macOS terminal conformance`. Release from the later approved
  commit and recheck its CI status before tagging.

## Publication checklist

1. Merge the release-finalization documentation after its exact head passes Linux and macOS CI.
   Confirm no additional changes entered `main` unnoticed and its exact commit has successful
   required CI before tagging. Resolve any release-blocking regressions before proceeding.
2. The release was authorized on 2026-10-13. The changelog now has a dated `## [0.9.1]` section
   and a fresh `[Unreleased]` section, which the GitHub Release workflow will use for notes.
3. Confirm Maven Central namespace/signing prerequisites and required workflow secret names
   without exposing credentials. Check the final tag target before pushing the tag. A pushed
   `v0.9.1` tag automatically starts **both**
   `Publish` and `Publish Maven Central`; the former publishes GitHub Packages and creates a GitHub
   Release, while the latter automatically releases non-SNAPSHOT artifacts to Central by default.
   Do not use manual workflow dispatch as an extra publication path unless deliberately chosen.
4. After the authorized tag push, verify both workflows, Maven Central and GitHub Packages
   coordinates, and all ten expected GitHub Release jars (below). Do not describe artifacts as
   available based only on a successful build or tag push.
5. Once artifacts are available, update the `0.9.0` dependency examples in README and
   `examples/scala-cli/`, plus their version guidance, to `0.9.1` in a follow-up documentation
   change. Keep the build's `0.1.0-SNAPSHOT` local fallback unchanged; it is not the release
   version source.

Expected GitHub Release jar names, derived from the tag workflow (not yet published):

| JVM | Scala Native 0.5 |
| --- | --- |
| `siglyph-core_3-0.9.1.jar` | `siglyph-core_native0.5_3-0.9.1.jar` |
| `siglyph-terminal-jvm_3-0.9.1.jar` | `siglyph-terminal-native_native0.5_3-0.9.1.jar` |
| `siglyph-markdown_3-0.9.1.jar` | `siglyph-markdown_native0.5_3-0.9.1.jar` |
| `siglyph-image_3-0.9.1.jar` | `siglyph-image_native0.5_3-0.9.1.jar` |
| `siglyph-extras_3-0.9.1.jar` | `siglyph-extras_native0.5_3-0.9.1.jar` |

The workflow publishes the corresponding ten `io.github.zikolach` Maven coordinates; see
[publishing.md](publishing.md) for the complete coordinates and version-source rules. Packaging
and publishing are distinct: no jar list above asserts that a release artifact exists today.
