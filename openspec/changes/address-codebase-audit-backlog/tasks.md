## 1. Extras final themed width safety

- [x] 1.1 Update affected extras render paths to apply existing theme callbacks before final ANSI-aware truncation to the requested width.
- [x] 1.2 Add focused extras tests for visible and ANSI-styled theme additions at normal, narrow, and zero widths where supported.
- [x] 1.3 Run the focused extras JVM tests and the corresponding portable Native tests if already wired.
- [x] 1.4 Complete the extras workstream after implementation, focused tests, and local validation pass without unrelated changes.

## 2. CI quality gates and Scalafix source coverage

- [x] 2.1 Audit existing CI commands and Scalafix inputs against every canonical current Scala production and test root.
- [x] 2.2 Make `mill scalafmtCheck` and `mill scalafixCheck` mandatory CI steps, and wire every missing canonical Scalafix root without checking duplicate mirror roots.
- [x] 2.3 Add or update focused build/CI assertions or inspection coverage that proves both failing commands fail CI and canonical current roots are included.
- [x] 2.4 Run both local CI-equivalent quality commands and verify the Scalafix root audit result.
- [x] 2.5 Complete the CI workstream after implementation, focused checks, and local validation pass independently of other workstreams.

## 3. Portable Scala Native test expansion

- [x] 3.1 Classify canonical core, extras, and interactive-demo test suites as portable unchanged, portable after a valid shared portability correction, or platform-specific, recording file-level evidence.
- [x] 3.2 Wire classified portable suites into the corresponding Scala Native test modules through Mill canonical source-root reuse; do not copy test files.
- [x] 3.3 Make only portability corrections required for canonical suites to compile unchanged on JVM and Native, stopping if a new abstraction, dependency, or duplicated path would be required.
- [x] 3.4 Run focused JVM and Native tests for each newly shared core, extras, and interactive-demo suite.
- [x] 3.5 Complete the Native-test workstream after classification, canonical reuse, focused tests, and local validation pass.

## 4. Current siglyph product identity

- [x] 4.1 Search active metadata, current documentation, build labels, source comments, and Scaladoc for present-product `scala-tui` references, excluding archives, changelog history, historical notes, package/import identifier `scalatui`, intentional prior-state references, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved. Before archive, classify exactly three promoted `openspec/specs/developer-api/spec.md` occurrences as pending synchronization: the Purpose metadata phrase that identifies the product as `scala-tui`; the `Mill Scala 3 modular architecture` requirement phrase that names the library `scala-tui`, targeted by this change's complete modified requirement; and the `Public markdown/autocomplete contracts are composable` scenario phrase `runtime dependencies to scala-tui`, targeted by this change's complete modified requirement.
- [x] 4.2 Replace in-scope present-product identity with `siglyph` while preserving the `scalatui` package namespace and all explicit exclusions.
- [x] 4.3 Update documentation and Scaladoc only where they state the current product identity or identity contract.
- [x] 4.4 Run focused metadata/documentation checks and repeat the stale-name search with the explicit exclusions, reviewing every remaining match. Fail for every unresolved in-scope current-product occurrence except the three exact promoted `developer-api` occurrences classified as pending synchronization in 4.1.
- [x] 4.5 Complete the identity workstream only after every other in-scope current-product occurrence is resolved and local validation passes without archive, history, package, dependency-version, agent-rule-file, or unrelated changes. Record the archive handoff to apply both modified `developer-api` requirements, `Mill Scala 3 modular architecture` and `Public markdown/autocomplete contracts are composable`, and update the promoted `developer-api` Purpose from `scala-tui` to `siglyph`. `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files remain unchanged unless separately and explicitly approved.

## 5. Basic Markdown parser NonFatal boundary

- [x] 5.1 Replace the dependency-free basic parser's broad `Throwable` recovery with standard `scala.util.control.NonFatal` matching while preserving existing non-fatal fallback behavior.
- [x] 5.2 Add focused parser tests proving non-fatal failures use existing readable recovery and a representative fatal failure is not intercepted.
- [x] 5.3 Run the focused Markdown parser JVM tests and corresponding Native tests where the canonical suite is portable.
- [x] 5.4 Complete the parser-boundary workstream after implementation, focused tests, and local validation pass.

## 6. Typed image dimension and render-geometry safety

- [x] 6.1 Audit PNG, JPEG, GIF, and WebP dimension decoding paths for signedness, narrowing, zero values, negative values, values not representable by `ImageDimensions`, and overflow-prone JPEG marker or segment bounds while preserving exact typed errors and precedence.
- [x] 6.2 Decode through a wide intermediate where required, centralize positive representability validation before constructing `ImageDimensions`, repair JPEG bounds with subtraction-safe checks, and guard supported image geometry above the fixed 10,000-frame-row limit before protocol rendering, frame-row allocation, and image-id allocation or update.
- [x] 6.3 Add focused format, arithmetic, fallback-theme, and render-geometry tests covering valid dimensions; zero, negative where encodable, overflowed, and non-representable dimensions; JPEG near-`Int.MaxValue` bounds without large arrays; exact 10,000-row control output; 10,001-row and `Int.MaxValue` portrait fallback; callback count; visible width; no controls; and no image-id allocation or update.
- [x] 6.4 Run focused image JVM and Native tests, full compile, Scalafmt, Scalafix, strict focused and all OpenSpec validation, `git diff --check`, and source checks for frame allocation order and overflow-prone JPEG additions.
- [x] 6.5 Complete the image workstream only after implementation and every validation in 6.4 pass with the approved fallback extension documented consistently and no unrelated changes.

## 7. Direct Markdown width-zero safety

- [x] 7.1 Update the direct Markdown render entry path so width zero cannot produce visible markers, prefixes, separators, or other positive-width output.
- [x] 7.2 Add focused direct-render tests for headings, lists, quotes, fences, styled content, and ordinary text at width zero, plus positive-width regression cases.
- [x] 7.3 Run the focused Markdown rendering JVM tests and corresponding Native tests where the canonical suite is portable.
- [x] 7.4 Complete the Markdown width workstream after implementation, focused tests, and local validation pass.

## 8. Precise Input and SelectList results

- [x] 8.1 Classify each `Input` command and unsupported-event branch as `Ignored`, `NoRender`, or `Render` from its actual mutation and callback effects.
- [x] 8.2 Implement precise `Input` results without changing callback conditions, callback counts, keybinding behavior, or claiming parent bubbling.
- [x] 8.3 Add focused `Input` tests that assert result values and callback counts for unsupported input, recognized no-op, mutation, paste framing, and submit behavior.
- [x] 8.4 Classify each `SelectList` command and unsupported-event branch as `Ignored`, `NoRender`, or `Render` from its actual selection, filter, and callback effects.
- [x] 8.5 Implement precise `SelectList` results without changing callback conditions, callback counts, filtering behavior, keybindings, or claiming parent bubbling.
- [x] 8.6 Add focused `SelectList` tests that assert result values and callback counts for unsupported input, boundary no-op, selection change, filter change, paste framing, and activation.
- [x] 8.7 Run focused JVM and portable Native `Input` and `SelectList` tests.
- [x] 8.8 Complete the input-result workstream after both components, focused tests, and local validation pass.

## 9. Deterministic concurrency-test synchronization

- [x] 9.1 Inventory fixed sleeps in affected concurrency tests and classify each as readiness/order synchronization or intentional timeout/polling behavior.
- [x] 9.2 Replace readiness/order sleeps with deterministic gates, latches, barriers, or observable conditions using bounded waits and actionable failure messages.
- [x] 9.3 Retain and document sleeps whose elapsed duration is the tested timeout or polling behavior; do not replace them with readiness gates.
- [x] 9.4 Run each changed concurrency test repeatedly and run its containing JVM or Native suite to verify deterministic completion and bounded failure.
- [x] 9.5 Complete the concurrency-test workstream after synchronization changes, repeated focused tests, and local validation pass.

## 10. Final integration validation

- [x] 10.1 Confirm groups 1 through 9 are complete, then inspect `git diff` and `git status` to verify only approved implementation, test, current documentation/Scaladoc, CI, build, and this change's artifact files changed; investigate any unexpected change before continuing.
- [x] 10.2 Run `mill __.compile` for the complete JVM and Scala Native module graph.
- [x] 10.3 Run all focused JVM and Native tests from groups 1 through 9, then run the full test targets, using a PTY-backed invocation for suites whose terminal behavior requires a pseudo-terminal.
- [x] 10.4 Run `mill scalafmtCheck` and `mill scalafixCheck`.
- [x] 10.5 Run `openspec validate --all --strict`.
- [x] 10.6 Audit the final Mill dependency graph and diff to verify no new runtime or test dependency, compatibility layer, fallback path, copied Native suite, or example dependency upgrade was added.
- [x] 10.7 Run and review a final stale `scala-tui` identity search with explicit exclusions for `openspec/changes/archive/**`, changelog history, historical notes, package/import identifier `scalatui`, intentional upstream or prior-state references, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved. Classify exactly three promoted `openspec/specs/developer-api/spec.md` occurrences as pending synchronization: the Purpose metadata phrase that identifies the product as `scala-tui`; the `Mill Scala 3 modular architecture` requirement phrase that names the library `scala-tui`, targeted by this change's complete modified requirement; and the `Public markdown/autocomplete contracts are composable` scenario phrase `runtime dependencies to scala-tui`, targeted by this change's complete modified requirement. Fail for every other in-scope current-product occurrence.
- [x] 10.8 Confirm exactly six capability delta files exist, both current `developer-api` requirements containing present-product identity have complete modified deltas, all nine workstream groups remain independently executable, no rejected audit idea appears, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files remain unchanged, and all change artifacts report done. Application implementation does not directly edit promoted specs; normal archive synchronization applies the deltas and must update the promoted `developer-api` Purpose metadata from `scala-tui` to `siglyph`.
- [x] 10.9 Complete final integration only after every validation command passes and every discovered issue is resolved without broad formatting churn or scope expansion.
