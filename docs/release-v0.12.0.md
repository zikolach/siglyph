# 0.12.0 release

**0.12.0** was published on **2026-11-16** from tag commit
`2bd87e73fba528890ce2254d1e9625ea7cad179a`, following merged preparation #114 and finalization #115.
The maintainer explicitly authorized publication. No duplicate publication dispatch was used.

[GitHub Release](https://github.com/zikolach/siglyph/releases/tag/v0.12.0) ·
[Dated changelog](../CHANGELOG.md#0120---2026-11-16)

## Highlights

- Typed virtualized tables and stable row selection.
- Bounded application scopes, cancellable schedulers, owned recurring timers and virtual time.
- Managed JVM/Native application tests and bounded diagnostics.
- Immutable semantic themes, keyboard validation and modal focus restoration.
- Live generated logs and bounded file following with visible I/O recovery.
- Artifact-based compatibility gates and simpler onboarding.

The new table/theme/scheduling/testing APIs remain experimental. This is a feature release, not a
1.0 stability declaration. Scala 3.9.0-compatible compiler, JDK 21, Scala Native 0.5.12, Mill 1.3.1 and
Unicode 17.0.0 remain the baseline. No runtime dependency or terminal platform was added.

## Publication verification

- Finalization [CI](https://github.com/zikolach/siglyph/actions/runs/43916570485) passed Linux and
  macOS before the single authorized tag push. The final tag tree matches the finalization PR.
- [Publish](https://github.com/zikolach/siglyph/actions/runs/43920407459) and
  [Publish Maven Central](https://github.com/zikolach/siglyph/actions/runs/43920407732) both succeeded.
- All ten Maven Central coordinates resolved anonymously: forty POM/binary/source/Javadoc payloads
  passed coordinate/version, SHA-1 and ZIP checks. PGP signature files are present; this is not
  independent cryptographic signer-trust verification.
- All ten GitHub Release JARs passed API SHA-256 digest, size and ZIP checks; Native artifacts contain
  Native IR. GitHub Packages publication succeeded in its workflow; independent authenticated package
  downloads are not claimed.
- Central-only compatibility verification passed selected JVM ABI checks and baseline-compiled
  Scala/Java/Kotlin binaries against 0.12.0. Source consumers passed on Scala JVM, Java, Kotlin and
  Scala Native; Native consumers were linked and run.
- Published-coordinate smoke programs for application testing, update scopes, virtual time,
  executor scheduling and owned timers ran on both JVM and Native.
- Central initially returned 404s during propagation; verification was retried after availability.
  No tag was moved and no packages were republished to work around propagation.

## Preparation evidence and boundaries

Runtime baseline `ac87a370eb4e5fbfec7640da9227fd045db378a1` had green Linux/macOS CI. Release
finalization changed documentation/version tracking only, not runtime code. Fresh local preparation
also verified ten package coordinates/forty payloads, selected ABI and candidate consumers, quality,
quickstart/recipe compilation, API inventory and offline tooling tests. Eleven completed changes were
promoted/archived with unchanged requirements; all 29 canonical specs passed strict validation.

Published examples now use **0.12.0**. `docs/release-status.json` tracks verified availability separately
from candidate changelogs; the compatibility baseline remains **0.11.0** to keep testing older binaries.
The local build fallback stays `0.1.0-SNAPSHOT` and is not a release identifier.

Scope ownership does not include a borrowed executor. File following remains a bounded polling viewer,
not audit-grade exactly-once ingestion. No screen-reader, universal emulator-pixel, whole-core ABI or
Native binary-swap guarantee is implied. No new normal-screen scrollback/reflow fix is claimed.

Future releases should follow the [readiness checklist](api-stability.md#release-readiness-checklist)
and [publishing procedure](publishing.md), including fresh candidate and post-publication checks.
