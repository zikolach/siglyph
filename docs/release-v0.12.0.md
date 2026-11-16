# 0.12.0 release preparation (not published)

Prepared on **2026-11-16** from merged main `ac87a370eb4e5fbfec7640da9227fd045db378a1`.

**0.12.0** is the recommended next version: this is a substantial additive feature release after
0.11.0, not a patch and not a 1.0 stability declaration. No tag, GitHub Release or remote package
publication is authorized by these preparation files.

## Release highlights

- Typed virtualized tables and stable row selection for data-oriented applications.
- Bounded application updates, scheduler adapters, virtual time and scope-owned recurring timers.
- A managed, framework-independent JVM/Native application-test toolkit.
- Immutable semantic palettes and a keyboard reference form with validation/focus restoration.
- Live generated logs and bounded file following with visible I/O recovery.
- Artifact-based compatibility gates and substantially simpler onboarding.

The [prepared changelog](../CHANGELOG.md#unreleased) contains the detailed release notes. Its entries
remain under Unreleased until the actual publication date is approved. Feature scope is merged PRs
#100–#103 and #105–#112, plus the #92 documentation work. The #99 initial roadmap is implemented and
merged; a release remains a separate decision.

## Compatibility and limits

The Scala 3.9.0/JDK 21/Scala Native 0.5.12/Mill 1.3.1/Unicode 17.0.0 baselines remain unchanged.
Artifacts retain the `scalatui` namespace. Existing Input constructor descriptors and plain rendering
defaults are preserved. No new runtime dependency or platform backend was introduced.

The new table, theme, scheduling and testing surfaces remain experimental. The [stability policy](api-stability.md)
deliberately gates a selected JVM surface, not every member, TASTy/inline behavior or Native binary
compatibility. Native consumers are rebuilt. Providers/executors remain explicitly owned; callbacks
must be finite/nonblocking. File polling and emulator reflow limitations remain documented rather
than claimed away. This release does not include a new normal-screen scrollback/reflow fix.

## Verification evidence

- [Merged-main CI](https://github.com/zikolach/siglyph/actions/runs/43901742022) passed both Linux
  test/package and macOS terminal conformance at the exact preparation baseline above.
- Implementation checks included JVM/Native suites, deterministic scheduler/cleanup races and real
  launcher PTY checks for generated/file logs and the themed form. Those are implementation evidence,
  not a substitute for checking the final release commit.
- Fresh local candidate **0.12.0-rcprep-ac87a37** passed selected JVM ABI checks against published
  0.11.0, old Scala/Java/Kotlin binaries on candidate artifacts, and source consumers on Scala JVM,
  Java, Kotlin and Scala Native. These checks ran on macOS arm64; Native consumers were linked and run.
- Local packaging at **0.12.0** produced all ten coordinates and forty POM/binary/source/Javadoc
  payloads. POM coordinates and internal dependency versions, ZIP integrity, JVM classes and Native IR
  were checked; SHA-256 evidence was retained locally. This was not remote publication or signing.
- Formatting/lint, published quickstart/recipe compilation, API inventory validation and sixteen
  offline documentation/compatibility-tool tests passed. Nonfatal existing Scaladoc/Coursier warnings
  remain; no toolchain or runtime code change was made to hide them.
- Eleven completed OpenSpec changes were promoted and archived. All **29 canonical specifications**
  pass strict validation, with no active changes. Promoted requirement bodies match the archived deltas.

## Version and publication policy

`build.mill` continues to derive artifact versions from `SIGLYPH_VERSION` or the tag. Its local
`0.1.0-SNAPSHOT` fallback is not a release number and is not changed. README and Maven-backed examples
stay on the **published 0.11.0** coordinates until 0.12.0 resolves publicly. The compatibility baseline
also remains 0.11.0 so candidate checks continue to exercise old binaries.

Use unique **local-only** candidate coordinates for preparation; do not overwrite published artifacts
or tag a preparation snapshot. Versioned packaging at 0.12.0 is local output only.

## Before tagging

- [ ] Review and merge the release-preparation changes; confirm the final commit and release scope.
- [ ] Require green protected checks on that commit, or document an explicitly approved exact-commit
  local macOS fallback if runner budget/availability requires it. Do not bypass protections.
- [ ] Finalize the actual release date: move the prepared entries into `[0.12.0] - YYYY-MM-DD`, add
  its comparison link, and retain a fresh Unreleased section. Do not claim an unapproved date.
- [ ] Reconfirm the [release-readiness gates](api-stability.md#release-readiness-checklist), packaging
  and applicable terminal smoke evidence for any code changes since this baseline.
- [ ] Obtain explicit authorization to push **one** `v0.12.0` tag; let the two existing publishing
  workflows run. Do not also dispatch a duplicate publication.

## After publication

- Verify all ten Maven Central coordinates and ten GitHub Release JARs (versions, payload integrity,
  Native IR and expected checksums/signatures), and record workflow results.
- Run `python3 scripts/check-compatibility.py --candidate 0.12.0` without a local candidate repository.
- Update README/canonical hello, published dependency examples and feature-guide availability notes
  together, then run documentation/version checks and published consumer smoke again.
- Update this record from preparation to published evidence. Preparing these files does not perform
  any of those shared/publication actions.
