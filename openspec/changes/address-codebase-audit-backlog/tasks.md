## 1. Extras final themed width safety

- [ ] 1.1 Update affected extras render paths to apply existing theme callbacks before final ANSI-aware truncation to the requested width.
- [ ] 1.2 Add focused extras tests for visible and ANSI-styled theme additions at normal, narrow, and zero widths where supported.
- [ ] 1.3 Run the focused extras JVM tests and the corresponding portable Native tests if already wired.
- [ ] 1.4 Complete the extras workstream after implementation, focused tests, and local validation pass without unrelated changes.

## 2. CI quality gates and Scalafix source coverage

- [ ] 2.1 Audit existing CI commands and Scalafix inputs against every canonical current Scala production and test root.
- [ ] 2.2 Make Scalafmt checking, Scalafix checking, and `openspec validate --all --strict` mandatory CI steps, and wire every missing canonical Scalafix root without checking duplicate mirror roots.
- [ ] 2.3 Add or update focused build/CI assertions or inspection coverage that proves all three failing commands fail CI and canonical current roots are included.
- [ ] 2.4 Run the local CI-equivalent quality commands and verify the Scalafix root audit result.
- [ ] 2.5 Complete the CI workstream after implementation, focused checks, and local validation pass independently of other workstreams.

## 3. Portable Scala Native test expansion

- [ ] 3.1 Classify canonical core, extras, and interactive-demo test suites as portable unchanged, portable after a valid shared portability correction, or platform-specific, recording file-level evidence.
- [ ] 3.2 Wire classified portable suites into the corresponding Scala Native test modules through Mill canonical source-root reuse; do not copy test files.
- [ ] 3.3 Make only portability corrections required for canonical suites to compile unchanged on JVM and Native, stopping if a new abstraction, dependency, or duplicated path would be required.
- [ ] 3.4 Run focused JVM and Native tests for each newly shared core, extras, and interactive-demo suite.
- [ ] 3.5 Complete the Native-test workstream after classification, canonical reuse, focused tests, and local validation pass.

## 4. Current siglyph product identity

- [ ] 4.1 Search active metadata, current documentation, build labels, source comments, and Scaladoc for present-product `scala-tui` references, excluding archives, changelog history, historical notes, package/import identifier `scalatui`, intentional prior-state references, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved. Before archive, classify only the promoted `openspec/specs/developer-api/spec.md` Purpose product phrase and the `Public markdown/autocomplete contracts are composable` dependency phrase targeted by this change's delta as pending synchronization.
- [ ] 4.2 Replace in-scope present-product identity with `siglyph` while preserving the `scalatui` package namespace and all explicit exclusions.
- [ ] 4.3 Update documentation and Scaladoc only where they state the current product identity or identity contract.
- [ ] 4.4 Run focused metadata/documentation checks and repeat the stale-name search with the explicit exclusions, reviewing every remaining match. Fail for every unresolved in-scope current-product occurrence except the two exact promoted `developer-api` occurrences classified as pending synchronization in 4.1.
- [ ] 4.5 Complete the identity workstream only after every other in-scope current-product occurrence is resolved and local validation passes without archive, history, package, dependency-version, agent-rule-file, or unrelated changes. Record the archive handoff to apply the modified requirement and update the promoted `developer-api` Purpose from `scala-tui` to `siglyph`. `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files remain unchanged unless separately and explicitly approved.

## 5. Basic Markdown parser NonFatal boundary

- [ ] 5.1 Replace the dependency-free basic parser's broad `Throwable` recovery with standard `scala.util.control.NonFatal` matching while preserving existing non-fatal fallback behavior.
- [ ] 5.2 Add focused parser tests proving non-fatal failures use existing readable recovery and a representative fatal failure is not intercepted.
- [ ] 5.3 Run the focused Markdown parser JVM tests and corresponding Native tests where the canonical suite is portable.
- [ ] 5.4 Complete the parser-boundary workstream after implementation, focused tests, and local validation pass.

## 6. Typed image dimension validation

- [ ] 6.1 Audit PNG, JPEG, GIF, and WebP dimension decoding paths for signedness, narrowing, zero values, negative values, and values not representable by `ImageDimensions`.
- [ ] 6.2 Decode through a wide intermediate where required and centralize positive representability validation before constructing `ImageDimensions`, returning existing typed `InvalidImage` for every invalid case.
- [ ] 6.3 Add focused format and boundary tests for valid dimensions plus zero, negative where encodable, overflowed, and non-representable dimensions without clamping or fallback.
- [ ] 6.4 Run focused image sniffer JVM and Native tests.
- [ ] 6.5 Complete the image-dimension workstream after implementation, focused tests, and local validation pass.

## 7. Direct Markdown width-zero safety

- [ ] 7.1 Update the direct Markdown render entry path so width zero cannot produce visible markers, prefixes, separators, or other positive-width output.
- [ ] 7.2 Add focused direct-render tests for headings, lists, quotes, fences, styled content, and ordinary text at width zero, plus positive-width regression cases.
- [ ] 7.3 Run the focused Markdown rendering JVM tests and corresponding Native tests where the canonical suite is portable.
- [ ] 7.4 Complete the Markdown width workstream after implementation, focused tests, and local validation pass.

## 8. Precise Input and SelectList results

- [ ] 8.1 Classify each `Input` command and unsupported-event branch as `Ignored`, `NoRender`, or `Render` from its actual mutation and callback effects.
- [ ] 8.2 Implement precise `Input` results without changing callback conditions, callback counts, keybinding behavior, or claiming parent bubbling.
- [ ] 8.3 Add focused `Input` tests that assert result values and callback counts for unsupported input, recognized no-op, mutation, paste framing, and submit behavior.
- [ ] 8.4 Classify each `SelectList` command and unsupported-event branch as `Ignored`, `NoRender`, or `Render` from its actual selection, filter, and callback effects.
- [ ] 8.5 Implement precise `SelectList` results without changing callback conditions, callback counts, filtering behavior, keybindings, or claiming parent bubbling.
- [ ] 8.6 Add focused `SelectList` tests that assert result values and callback counts for unsupported input, boundary no-op, selection change, filter change, paste framing, and activation.
- [ ] 8.7 Run focused JVM and portable Native `Input` and `SelectList` tests.
- [ ] 8.8 Complete the input-result workstream after both components, focused tests, and local validation pass.

## 9. Deterministic concurrency-test synchronization

- [ ] 9.1 Inventory fixed sleeps in affected concurrency tests and classify each as readiness/order synchronization or intentional timeout/polling behavior.
- [ ] 9.2 Replace readiness/order sleeps with deterministic gates, latches, barriers, or observable conditions using bounded waits and actionable failure messages.
- [ ] 9.3 Retain and document sleeps whose elapsed duration is the tested timeout or polling behavior; do not replace them with readiness gates.
- [ ] 9.4 Run each changed concurrency test repeatedly and run its containing JVM or Native suite to verify deterministic completion and bounded failure.
- [ ] 9.5 Complete the concurrency-test workstream after synchronization changes, repeated focused tests, and local validation pass.

## 10. Final integration validation

- [ ] 10.1 Confirm groups 1 through 9 are complete, then inspect `git diff` and `git status` to verify only approved implementation, test, current documentation/Scaladoc, CI, build, and this change's artifact files changed; investigate any unexpected change before continuing.
- [ ] 10.2 Run `mill __.compile` for the complete JVM and Scala Native module graph.
- [ ] 10.3 Run all focused JVM and Native tests from groups 1 through 9, then run the full test targets, using a PTY-backed invocation for suites whose terminal behavior requires a pseudo-terminal.
- [ ] 10.4 Run `mill scalafmtCheck` and `mill scalafixCheck`.
- [ ] 10.5 Run `openspec validate --all --strict`.
- [ ] 10.6 Audit the final Mill dependency graph and diff to verify no new runtime or test dependency, compatibility layer, fallback path, copied Native suite, or example dependency upgrade was added.
- [ ] 10.7 Run and review a final stale `scala-tui` identity search with explicit exclusions for `openspec/changes/archive/**`, changelog history, historical notes, package/import identifier `scalatui`, intentional upstream or prior-state references, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved. Classify only the promoted `openspec/specs/developer-api/spec.md` Purpose product phrase and the `Public markdown/autocomplete contracts are composable` dependency phrase targeted by this change's delta as pending synchronization; fail for every other in-scope current-product occurrence.
- [ ] 10.8 Confirm exactly six capability delta files exist, both current `developer-api` requirements containing present-product identity have complete modified deltas, all nine workstream groups remain independently executable, no rejected audit idea appears, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files remain unchanged, and all change artifacts report done. Application implementation does not directly edit promoted specs; normal archive synchronization applies the deltas and must update the promoted `developer-api` Purpose metadata from `scala-tui` to `siglyph`.
- [ ] 10.9 Complete final integration only after every validation command passes and every discovered issue is resolved without broad formatting churn or scope expansion.
