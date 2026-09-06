# Publishing

siglyph publishes release jars to GitHub Releases and artifacts to GitHub Packages and Maven
Central. README and Scala CLI examples use the version prepared for the next release, currently
`0.9.0`. The artifacts become available after release tag publishing completes.

## Version source

`build.mill` derives the package version from:

1. `SIGLYPH_VERSION`, when set;
2. tag names like `v0.1.2`, via `GITHUB_REF_NAME`;
3. `0.1.0-SNAPSHOT` for local builds.

The local fallback is a build identifier, not the latest released version. Release preparation sets
an explicit version or tag. Do not change the fallback or published examples as an implicit version
bump.

Before a release repeats a `pi-tui` parity claim, verify and update the pinned commit, review date,
status, and local evidence in `docs/pi-tui-compatibility.md`. Other release documentation should
link to that matrix instead of making an unpinned completeness claim.

## GitHub Packages

The `Publish` workflow publishes to GitHub Packages on `v*` tags and can also
be run manually. It uses GitHub's `GITHUB_TOKEN`.

## Maven Central

The `Publish Maven Central` workflow publishes the publishable modules through
Mill's `SonatypeCentralPublishModule`:

- `io.github.zikolach:siglyph-core_3`
- `io.github.zikolach:siglyph-terminal-jvm_3`
- `io.github.zikolach:siglyph-markdown_3`
- `io.github.zikolach:siglyph-image_3`
- `io.github.zikolach:siglyph-extras_3`
- `io.github.zikolach:siglyph-core_native0.5_3`
- `io.github.zikolach:siglyph-terminal-native_native0.5_3`
- `io.github.zikolach:siglyph-markdown_native0.5_3`
- `io.github.zikolach:siglyph-image_native0.5_3`
- `io.github.zikolach:siglyph-extras_native0.5_3`

Use `siglyph-core`, `siglyph-terminal-native`, `siglyph-markdown`,
`siglyph-image`, and `siglyph-extras` as platform-aware dependencies from
Scala Native modules. Mill resolves them to the `_native0.5_3` artifacts when
used from a `ScalaNativeModule`.

Required GitHub Actions secrets:

- `MILL_SONATYPE_USERNAME` — Sonatype Central user token username
- `MILL_SONATYPE_PASSWORD` — Sonatype Central user token password
- `MILL_PGP_SECRET_BASE64` — base64-encoded ASCII-armored private signing key
- `MILL_PGP_PASSPHRASE` — passphrase for the signing key, if set

The workflow runs on future `v*` tags and can also be run manually with an
explicit version. For non-SNAPSHOT versions it creates an automatic Central
Portal release by default. For snapshots it publishes to Central's snapshot
repository.

Before the first Central release, ensure the `io.github.zikolach` namespace is
verified in Central Portal and the signing public key has been published to a
public keyserver.

## Local dry runs

Snapshot dry run, which does not require a PGP key:

```bash
MILL_TESTS_PUBLISH_DRY_RUN=1 \
MILL_SONATYPE_USERNAME=dummy \
MILL_SONATYPE_PASSWORD=dummy \
SIGLYPH_VERSION=0.2.1-SNAPSHOT \
mill mill.javalib.SonatypeCentralPublishModule/publishAll
```

GitHub Packages dry run:

```bash
MILL_TESTS_PUBLISH_DRY_RUN=1 \
MILL_MAVEN_USERNAME=dummy \
MILL_MAVEN_PASSWORD=dummy \
SIGLYPH_VERSION=0.1.2 \
mill mill.javalib.MavenPublishModule/publishAll \
  --releaseUri https://maven.pkg.github.com/zikolach/siglyph \
  --snapshotUri https://maven.pkg.github.com/zikolach/siglyph
```
