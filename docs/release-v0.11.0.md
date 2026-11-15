# v0.11.0 release

**0.11.0** follows **0.10.0**. This is a feature release, not a patch:
it adds public image-geometry/cropping helpers, runtime wheel/selection policies and negotiated
program-status APIs. No new major-version toolchain migration or 1.0 stability claim is proposed.

The [0.11.0 changelog](../CHANGELOG.md#0110---2026-11-15) is finalized for
**2026-11-15**, with a fresh Unreleased section. The maintainer has explicitly authorized the
release and its tag push. Both publishing workflows and all ten Maven Central coordinates are
verified. Published dependency examples now use **0.11.0**; the local build fallback remains
`0.1.0-SNAPSHOT`.

## Publication verification

- [GitHub Release v0.11.0](https://github.com/zikolach/siglyph/releases/tag/v0.11.0) was published
  on 2026-11-15 from tag commit `351391d65168644bd928cee4bae6143350d4f87d` after merged PRs #89/#90.
- [Final main CI](https://github.com/zikolach/siglyph/actions/runs/43709691200) passed Linux and
  macOS. Its first Linux attempt hit a Scala compiler assertion; the unchanged commit passed a
  failed-job rerun. No compiler/toolchain change or protection bypass was made.
- [Publish](https://github.com/zikolach/siglyph/actions/runs/43710737799) and
  [Publish Maven Central](https://github.com/zikolach/siglyph/actions/runs/43710737732) both passed
  after one authorized tag push. No additional publication dispatch was used.
- All ten GitHub Release JARs were downloaded and checked against API SHA-256 digests, sizes and
  ZIP integrity. Native artifacts contain Native IR.
- All ten Central coordinates resolved anonymously: forty POM/binary/source/Javadoc payloads passed
  coordinate/version, SHA-1 and ZIP checks; PGP signature files are present. This is not independent
  cryptographic signer-trust verification.
- Published JVM and Native consumer programs compiled and ran successfully with all five relevant
  platform dependencies, exercising crop geometry, wheel policy and program-status APIs.
- GitHub Packages returned forty successful deployment receipts. Local independent package API
  enumeration requires read:packages, which the local token does not provide.

## Scope and compatibility

- Merged PRs #82–#86 and #88: bounded delayed-reply framing, fullscreen image lifecycle fixes,
  streaming Markdown cache reuse, list paging/scroll/selection policies, program status/profiles,
  and typed vertical Kitty cropping.
- Scala 3.9.0 (or a compatible newer compiler), supported JDK 21 runtime/build baseline, Mill 1.3.1,
  Scala Native 0.5.12 and Unicode 17.0.0 remain unchanged. No runtime dependency was added.
- Existing full-image helper signatures, control-details extractor arity and base Terminal
  implementations remain supported. Do not infer blanket binary compatibility from these checks.
- Control equality/hash now include optional source/crop geometry. A geometry-aware control need
  not equal a legacy dimensionless control even when their full-image encoded bytes are identical.
- Known Kitty replies remain delimiter-framed until completion or explicit recovery; ordinary
  Escape/CSI idle behavior remains unchanged. See [reply framing](terminal-reply-framing.md).
- Cropping is vertical and requires declared source dimensions. Horizontal partial clips, unknown
  geometry and iTerm2 remain conservative; overlay occlusion still hides whole images.
- Markdown parsing remains whole-document. Cache budgets and work-counter tests are not universal
  throughput or heap-size guarantees.

## Preparation evidence and release procedure

Preparation baseline: merged main `eb5e75347722ca4b7c008358e377eb42c8b16cb9`. The cropping PR
passed Linux/macOS CI; implementation validation recorded 2,290 JVM/Native tests plus 11 dedicated
PTY tests, quality/compilation and 24 strict OpenSpec items. These are historical implementation
results, not fresh checks of a future release-finalization commit.

The maintainer reported that the manual image demo looked good. This is maintainer-observed visual
feedback, not an automated screenshot assertion or a full terminal/emulator compatibility matrix.

All six completed changes have now been promoted into canonical specs and archived. Fresh strict
validation passes **18/18 specifications**, with no active changes. Fresh preparation validation
on macOS arm64 / managed Temurin JDK 21 passed formatting/lint, all-module compilation, **2,290
JVM/Native tests plus 11 dedicated PTY tests**, and local versioned packaging. All ten generated
POMs identify version **0.11.0**; existing nonfatal Scaladoc link/plugin warnings remain. No publishing
provider was invoked. Runtime/build sources are unchanged from the merged implementation.

Release procedure (completed for this release):

1. Review and merge these release-preparation notes and synchronized specifications. Revalidate the
   final canonical specs strictly; confirm no new unreviewed change entered the release scope.
2. Confirm the approved final commit and release date before tagging. The changelog and compare
   links are prepared; adjust the date if publication happens later. Recheck required CI.
3. Require Linux CI and successful macOS verification. The previously approved exact-commit local
   macOS fallback remains acceptable when hosted runners are unavailable or outside budget; record
   platform/toolchain/commands/results and do not override repository protections.
4. Verify local versioned packaging and external-consumer compatibility, plus terminal restoration
   and the image smoke for any implementation changes since the recorded evidence.
5. Push one explicitly authorized v0.11.0 tag to invoke Publish and Publish Maven Central; do not
   additionally dispatch publication. Verify both workflows, all ten Maven coordinates and ten
   GitHub Release jars before declaring availability. See [publishing.md](publishing.md).
6. Update README/Scala CLI dependency examples only after the new artifacts are available.
