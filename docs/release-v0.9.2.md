# v0.9.2 release

This patch releases the keyboard-protocol lifecycle fixes from [PR #70](https://github.com/zikolach/siglyph/pull/70).
Publication is authorized for 2026-10-21. The version comes from the `v0.9.2` tag, not the local
`0.1.0-SNAPSHOT` fallback. The [0.9.2 changelog section](../CHANGELOG.md#092---2026-10-21)
is the source for the GitHub Release notes.

## Scope

- TUI automatically negotiates conservative Kitty keyboard disambiguation on supported JVM and
  Scala Native interactive backends. Exact replies stay out of component input, including replies
  admitted before the deadline but handled later. Pending shutdown cancels without a keyboard pop.
- Failed terminal restoration remains available for explicit `TUI.stop()` retry. Backend cleanup
  precedes alternate-screen exit, preserving the correct keyboard stack. Successful teardown and
  callbacks are not repeated. An interrupted idle `run()` performs its initial cleanup attempt.
- Unsupported terminals retain basic parsing. A terminal or multiplexer that discards Shift cannot
  be repaired by Siglyph. Physical Ghostty-through-Herdr forwarding remains a manual check described
  in [interactive-smoke.md](interactive-smoke.md).
- The supported interactive scope remains JVM and Scala Native on Linux/macOS. This patch changes
  no dependencies or publishing workflows and makes no new emulator, Windows, or upstream-parity claim.

## Evidence and publication

Implementation head `85dfd8c25fc91eb47df3c923ed5be3b3fd8eef32` passed
[Linux and macOS CI](https://github.com/zikolach/siglyph/actions/runs/39480870762).
Release-finalization documentation and the final main commit must also satisfy normal required CI
before tagging. Do not bypass protections or use an additional manual publication dispatch.

The normal tag push invokes **Publish** (GitHub Packages and GitHub Release) and
**Publish Maven Central**. Both workflows test before publishing. Verify both successful runs,
the ten GitHub Release jars, and all ten Maven Central coordinates before declaring publication
complete. See [publishing.md](publishing.md) for artifact coordinates and credential prerequisites.

Expected GitHub Release artifacts:

| JVM | Scala Native 0.5 |
| --- | --- |
| `siglyph-core_3-0.9.2.jar` | `siglyph-core_native0.5_3-0.9.2.jar` |
| `siglyph-terminal-jvm_3-0.9.2.jar` | `siglyph-terminal-native_native0.5_3-0.9.2.jar` |
| `siglyph-markdown_3-0.9.2.jar` | `siglyph-markdown_native0.5_3-0.9.2.jar` |
| `siglyph-image_3-0.9.2.jar` | `siglyph-image_native0.5_3-0.9.2.jar` |
| `siglyph-extras_3-0.9.2.jar` | `siglyph-extras_native0.5_3-0.9.2.jar` |

Do not treat this expected list as proof that artifacts already exist. Update dependency examples
only after publication is verified; the older open post-0.9.1 documentation PR is outside this
release's implementation scope.
