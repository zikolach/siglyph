## 1. Shared Typed Output Model

- [x] 1.1 Add public shared-core `ComponentRender` and `TerminalControlPlacement` types with validated non-negative placement, text-only construction, direct Scaladoc, and no legacy conversion.
- [x] 1.2 Add sealed read-only `TerminalRenderControl` semantic variants and package-owned exhaustive encoding without an arbitrary raw-string constructor.
- [x] 1.3 Add control footprint and placement validation that rejects negative, out-of-frame, over-width, and partially surviving controls before terminal output.
- [x] 1.4 Add shared JVM/Native tests proving closed construction, semantic equality, exact encoding, placement validation, and no payload copies during rebasing.

## 2. Breaking Component API Migration

- [x] 2.1 Change `Component.render(width)` and `ComponentFrameBuilder.result()` to return `ComponentRender` directly, with no compatibility overload, implicit conversion, adapter, or deprecated path.
- [x] 2.2 Update `ComponentFrameBuilder` and `Container` to concatenate ordinary lines and rebase child controls by locally accumulated rows while keeping builder `startRow` and `startCol` limited to render-origin notification.
- [x] 2.3 Migrate every built-in core component, nested component, test fake, and shared demo component to the typed render result.
- [x] 2.4 Migrate Markdown, extras, image, demos, examples, JVM interop fixtures, and all direct render assertions across JVM and Native modules.
- [x] 2.5 Search tracked sources and documentation to prove no `Component.render(width): Vector[String]` implementation or consumer remains.

## 3. Typed Image Protocol Integration

- [x] 3.1 Replace `ImageRenderResult.sequence` with typed control and explicit width/row geometry while preserving validated payload, sizing, filename, and image-id semantics.
- [x] 3.2 Change Kitty/iTerm2 render helpers to construct semantic controls and keep raw encoding package-owned at the final output boundary.
- [x] 3.3 Change Kitty cleanup helpers and `Image.cleanupSequence` to typed cleanup controls with no raw-string compatibility path.
- [x] 3.4 Change `Image.render` to return reserved blank lines plus one positioned typed image control, while unsupported capability returns ordinary fallback text only.
- [x] 3.5 Update shared JVM/Native protocol and image tests for exact typed fields, encoded bytes, row reservation, cleanup, invalid payload rejection, and fallback behavior.

## 4. Frame and Overlay Composition

- [x] 4.1 Extend prepared frame state to retain sanitized ordinary lines, cursor-marker position, and validated typed controls without merging channels.
- [x] 4.2 Rebase visible overlay controls into final coordinates and suppress lower controls whose footprints intersect higher overlay rectangles.
- [x] 4.3 Reject partially clipped or otherwise invalid surviving overlay controls before output without moving, dropping, stringifying, or partially encoding them.
- [x] 4.4 Add deterministic tests for nested vertical composition, frame-local builder coordinates, non-zero render-origin notification, overlay relocation, overlay suppression, clipping rejection, and text-only frame parity.

## 5. Differential Runtime Output

- [x] 5.1 Include semantic control additions, removals, moves, and field changes in first-changed-row calculation and prepared-frame equality.
- [x] 5.2 Encode valid typed controls only while building full and partial synchronized terminal buffers at their declared row and column anchors.
- [x] 5.3 Preserve resize-generation rejection, autowrap restoration, terminal-write ownership, hardware cursor positioning, and failure cleanup for frames with controls.
- [x] 5.4 Add VirtualTerminal tests for first render, unchanged frame, partial update, move, replacement, removal, resize redraw, write failure, and content below reserved image rows.
- [x] 5.5 Prove ordinary strings matching Kitty/iTerm2 prefixes receive no typed-control identity or image-specific frame behavior.

## 6. Public Documentation

- [x] 6.1 Add Scaladoc for all typed render/control APIs covering JVM/Native scope, authority, geometry, validation, failure behavior, and non-goals.
- [x] 6.2 Update README and porting notes with the source-breaking text-only migration and the intentional security deviation from pi-tui string-prefix image detection.
- [x] 6.3 Update image and component examples to use `ComponentRender` and typed controls without raw protocol strings.
- [x] 6.4 Verify documentation contains no arbitrary trusted string, legacy render overload, adapter, fallback protocol, or deprecation path.

## 7. Validation and Review

- [x] 7.1 Run focused core, overlay, TUI, terminal image protocol, and image suites on JVM and Scala Native.
- [x] 7.2 Run `mill __.compile` and PTY-backed `mill --no-daemon __.test`.
- [x] 7.3 Run `mill scalafmtCheck`, `mill scalafixCheck`, and `git diff --check`.
- [x] 7.4 Run `openspec validate add-typed-terminal-output --strict` and `openspec validate --all --strict`.
- [x] 7.5 Inspect the resolved dependency graph and diff to confirm no runtime dependency, compatibility path, duplicated protocol parser, or unrelated change was added.
- [x] 7.6 Complete security, JVM/Native test, API documentation, and broad implementation review; repair every material finding and rerun the full validation set.

## 8. Final Review Repairs

- [x] 8.1 Reject non-positive image geometry and Kitty IDs during typed construction and final encoding; bound allocator state and test the `Int.MaxValue` exhaustion boundary on JVM and Native.
- [x] 8.2 Return empty text-only output from `Image.render` at non-positive width and test direct, boxed width-one, and resize composition on JVM and Native.
- [x] 8.3 Validate final-frame Kitty image ID uniqueness from semantic integers without reconstructing payload strings, with deterministic rejection and unique-ID cleanup tests on JVM and Native.
- [x] 8.4 Wire `TUITypedControlSuite` and `OverlayRendererSuite` into `coreNative` and prove selected Native suites execute their cases.
- [x] 8.5 Run the complete focused, Native, compile, test, formatting, Scalafix, strict OpenSpec, dependency, and diff validation set before marking repair tasks complete.

## 9. Latest Production Review Repairs

- [x] 9.1 Enforce typed final-frame rejection for repeated active semantic Kitty image IDs, excluding cleanup controls from active placement uniqueness.
- [x] 9.2 Make ordered control-vector differences trigger redraw from the earliest affected row while preserving unique-ID cleanup order for moves, replacements, and removals.
- [x] 9.3 Normalize `Box` padding once and add direct and nested-image negative-padding coverage.
- [x] 9.4 Remove duplicate design headings and update proposal, design, specs, and tests to remove duplicate-placement survival behavior.
- [x] 9.5 Run focused JVM/Native tests, full core/image tests, compile, PTY full tests, formatting, Scalafix, strict OpenSpec validation, dependency inspection, and diff checks.

## 10. Final Kitty Lifecycle and Diagnostic Confidentiality Repairs

- [x] 10.1 Encode targeted Kitty cleanup as uppercase `d=I` while preserving delete-all encoding.
- [x] 10.2 Clean each previous-frame Kitty ID exactly once before retransmission or removal across partial, reorder, forced, resize, move, replacement, and removal paths, without cleaning new or out-of-range unaffected IDs.
- [x] 10.3 Replace retained validation placements and controls with bounded semantic diagnostics, and redact terminal-control string output.
- [x] 10.4 Add shared JVM/Native protocol, lifecycle, validation-confidentiality, overlay, and control-string tests.
- [x] 10.5 Update public documentation and OpenSpec lifecycle and bounded-diagnostic contracts.
- [x] 10.6 Run focused JVM/Native tests, full compile and PTY tests, formatting, Scalafix, strict OpenSpec validation, dependency inspection, and diff checks before marking these tasks complete.

## 11. Repair Archive Spec Semantics

- [x] 11.1 Correct the promoted width-only component signature, bounded bracketed-paste protocol wording, and ordinary-versus-typed image output boundary in source deltas without changing implementation behavior.
- [x] 11.2 Run strict change and repository OpenSpec validation plus scoped diff and whitespace checks before archive.
