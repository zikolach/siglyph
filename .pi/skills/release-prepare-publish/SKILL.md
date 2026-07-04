---
name: release-prepare-publish
description: Prepare and publish a siglyph release. Use when the user asks to release a new version, publish a new version, prepare a release PR, or update examples and demos to the latest released version.
license: MIT
compatibility: Requires git, gh, curl, openspec CLI, Mill, and scala-cli.
metadata:
  author: siglyph
  version: "1.0"
---

Prepare and publish a siglyph release with one release-preparation PR when repository state allows it.

## Scope

This skill handles release preparation for this repository:

- Promote `CHANGELOG.md` `Unreleased` entries into a dated release section.
- Add missing user-facing changes from commits since the previous release tag.
- Update install snippets, examples, and demos to the release version.
- Create one PR containing all release-preparation file changes.
- Merge the PR after checks pass.
- Create and push the `vX.Y.Z` tag.
- Verify GitHub Actions publishing, GitHub Release assets, Maven Central availability, and Scala CLI examples.

The release tag and publishing workflows cannot be included inside the PR because publishing is triggered by the tag after the PR is merged. The file changes should still be handled through one PR.

## Required inputs

Accept an explicit version when the user provides one, for example `0.2.8`.

If the user does not provide a version:

1. Read the latest release tag with:
   ```bash
   git describe --tags --abbrev=0
   ```
2. If the latest tag is `vMAJOR.MINOR.PATCH`, propose `MAJOR.MINOR.(PATCH + 1)`.
3. Use the proposed patch version only when the release contains fixes, documentation, examples, demos, or internal changes.
4. Ask the user before using a minor or major version.
5. Ask the user before releasing if commits since the latest tag include a public API addition, public behavior expansion, or breaking change and the requested version does not match that scope.

## Preconditions

Before changing files:

1. Confirm branch and worktree state:
   ```bash
   git status --short --branch
   ```
2. If there are unrelated uncommitted changes, stop and ask whether to include, stash, commit, or leave them untouched.
3. Confirm the current branch is `main` and up to date:
   ```bash
   git fetch origin main --prune
   git switch main
   git pull --ff-only origin main
   ```
4. Confirm the target release tag does not already exist locally, remotely, or as a GitHub release:
   ```bash
   git tag --list 'vX.Y.Z'
   git ls-remote --tags origin 'vX.Y.Z'
   gh release view 'vX.Y.Z' --repo zikolach/siglyph --json tagName,url,isDraft,isPrerelease
   ```
5. Read current release and publishing files:
   ```bash
   files=(
     docs/publishing.md
     .github/workflows/publish.yml
     .github/workflows/publish-central.yml
     .github/workflows/ci.yml
     build.mill
     CHANGELOG.md
   )
   for file in "${files[@]}"; do
     printf '\n--- %s ---\n' "$file"
     sed -n '1,260p' "$file"
   done
   ```

## Prepare the release PR

1. Create a release-preparation branch:
   ```bash
   git switch -c docs/prepare-X.Y.Z-release
   ```
2. Inspect commits since the previous release tag:
   ```bash
   git log --oneline --decorate vPREVIOUS..HEAD
   ```
3. Update `CHANGELOG.md`:
   - Keep `## [Unreleased]` at the top.
   - Move current unreleased entries into `## [X.Y.Z] - YYYY-MM-DD`.
   - Add missing user-facing changes from merge commits since the previous tag.
   - Mention PR numbers when available.
   - Preserve the existing changelog style.
   - Update comparison links:
     ```markdown
     [Unreleased]: https://github.com/zikolach/siglyph/compare/vX.Y.Z...HEAD
     [X.Y.Z]: https://github.com/zikolach/siglyph/compare/vPREVIOUS...vX.Y.Z
     ```
4. Update user-facing dependency examples to `X.Y.Z` in the same release PR before tagging. This includes every tracked file in these paths when it contains siglyph dependency coordinates or a published-version install snippet:
   - `README.md`
   - `examples/`
   - `docs/`
   - `demo/`
   - `asciinemaDemo/`
   - `interactiveDemo/`
   - `interactiveJvmDemo/`
   - `interactiveNativeDemo/`
   - `keyTester/`
5. Treat these files as required release-PR candidates when they exist and contain published siglyph versions:
   - `README.md` SBT and Mill install snippets
   - `examples/scala-cli/*.scala` Scala CLI dependency directives
   - `examples/scala-cli/README.md` published-version instructions
   - Demo documentation or launcher files under `demo/`, `asciinemaDemo/`, `interactiveDemo/`, `interactiveJvmDemo/`, `interactiveNativeDemo/`, and `keyTester/`
6. Do not defer README, example, or demo version updates to a post-release PR. The post-release Scala CLI compile check must validate the versions already committed by the release PR.
7. Do not update historical changelog sections for older releases.
8. Do not update release comparison links for older releases.
9. Do not update dry-run examples that intentionally use dummy, snapshot, or older versions.
10. Do not update archived OpenSpec files unless the user explicitly asks.
11. Do not add `m2Local`, local repository directives, or snapshot versions to published examples.
12. Use tracked-file searches to verify no stale current-release dependency examples remain:
   ```bash
   git grep -nE 'io\.github\.zikolach.*siglyph.*(:|% ")[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?' -- README.md examples docs demo asciinemaDemo interactiveDemo interactiveJvmDemo interactiveNativeDemo keyTester || true
   git grep -nE 'PREVIOUS_VERSION' -- README.md examples docs demo asciinemaDemo interactiveDemo interactiveJvmDemo interactiveNativeDemo keyTester || true
   ```
   Replace `PREVIOUS_VERSION` with the previous released version string, for example `0.2.7`. Every user-facing published siglyph coordinate found by the first search must use `X.Y.Z`, except historical release notes and documented dry-run examples.

## Validate before opening the PR

Run the relevant repository checks:

```bash
git diff --check
mill __.compile
mill core.test
mill scalafmtCheck
mill scalafixCheck
openspec validate --all --strict
```

Do not require `scala-cli compile examples/scala-cli/*.scala` before the tag is published. The examples intentionally point to `X.Y.Z`, which is not available on Maven Central until after publishing completes. State this as an expected pre-release validation gap in the PR body.

## Create and merge the release PR

1. Commit the release-preparation changes:
   ```bash
   git add CHANGELOG.md README.md examples docs demo asciinemaDemo interactiveDemo interactiveJvmDemo interactiveNativeDemo keyTester
   git commit -m "docs(changelog): prepare X.Y.Z release"
   ```
2. Push the branch:
   ```bash
   git push -u origin docs/prepare-X.Y.Z-release
   ```
3. Create one PR containing all release-preparation file changes:
   ```bash
   gh pr create --repo zikolach/siglyph --base main --head docs/prepare-X.Y.Z-release --title "docs(changelog): prepare X.Y.Z release" --body-file PR_BODY.md
   ```
4. The PR body must include:
   - Summary of changelog update.
   - Summary of example, demo, README, and docs version updates.
   - Validation commands and outcomes.
   - Explicit note that Scala CLI published-dependency validation is post-release because `X.Y.Z` is not available until tag publishing completes.
5. Watch PR checks:
   ```bash
   gh pr checks PR_NUMBER --repo zikolach/siglyph --watch --interval 10
   ```
6. If checks pass, merge with the repository's allowed strategy:
   ```bash
   gh pr merge PR_NUMBER --repo zikolach/siglyph --squash --delete-branch
   ```
7. Update local `main`:
   ```bash
   git switch main
   git fetch origin main --prune
   git pull --ff-only origin main
   ```

## Tag and publish

1. Confirm the release section is on `main`:
   ```bash
   grep -nF "## [X.Y.Z]" CHANGELOG.md
   ```
2. Confirm the tag still does not exist:
   ```bash
   git tag --list 'vX.Y.Z'
   git ls-remote --tags origin 'vX.Y.Z'
   ```
3. Create and push the lightweight tag, matching the repository's existing tag style:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
4. Watch the triggered workflows:
   ```bash
   gh run list --repo zikolach/siglyph --limit 10 --json databaseId,workflowName,event,status,conclusion,headBranch,headSha,createdAt
   gh run watch RUN_ID --repo zikolach/siglyph --interval 15 --exit-status
   ```
5. Required successful workflows for the release tag:
   - `Publish`
   - `Publish Maven Central`
6. Required successful workflow for the release commit on `main`:
   - `CI`

## Post-release verification

Verify the GitHub release:

```bash
gh release view vX.Y.Z --repo zikolach/siglyph --json tagName,name,url,isDraft,isPrerelease,createdAt,publishedAt,assets
```

The GitHub release must include these assets:

- `siglyph-core_3-X.Y.Z.jar`
- `siglyph-core_native0.5_3-X.Y.Z.jar`
- `siglyph-image_3-X.Y.Z.jar`
- `siglyph-markdown_3-X.Y.Z.jar`
- `siglyph-terminal-jvm_3-X.Y.Z.jar`
- `siglyph-terminal-native_native0.5_3-X.Y.Z.jar`

Verify Maven Central availability before declaring the release complete:

```bash
for artifact in \
  siglyph-core_3 \
  siglyph-terminal-jvm_3 \
  siglyph-markdown_3 \
  siglyph-image_3 \
  siglyph-core_native0.5_3 \
  siglyph-terminal-native_native0.5_3
do
  url="https://repo1.maven.org/maven2/io/github/zikolach/${artifact}/X.Y.Z/${artifact}-X.Y.Z.pom"
  code=$(curl -s -o /dev/null -w '%{http_code}' "$url")
  echo "$code $url"
done
```

If Maven Central returns `404` immediately after a successful publish workflow, wait and retry. Central propagation lag is expected. Do not mark the release complete until every required POM returns `200`.

After Maven Central is available, compile the Scala CLI examples against the published release:

```bash
scala-cli compile --workspace /tmp/siglyph-scala-cli-X.Y.Z examples/scala-cli/*.scala
```

## Failure handling

- If the release tag already exists, stop and ask the user how to proceed.
- If a workflow fails, stop and report the failed run URL and failed job.
- Do not delete or replace a pushed release tag without explicit user approval.
- Do not create a replacement version to bypass a failed release without explicit user approval.
- Do not silently ignore failed cleanup, failed validation, failed publishing, or failed Maven Central checks.
- If a validation command cannot be run, report the exact command and reason.

## Final report

Report:

- Version released.
- Release PR URL.
- Tag name and commit SHA.
- GitHub Release URL.
- Workflow names and outcomes.
- Maven Central POM availability results.
- Scala CLI example compile result.
- Changed files.
- Local branch and working tree status.
