## 1. Pin and Generate Unicode 17.0.0 Data

- [x] 1.1 Update `scripts/GenerateUnicodeTables.scala` to pin Unicode 17.0.0 and immutable versioned URLs for GraphemeBreakProperty, emoji properties, Indic_Conjunct_Break data, and GraphemeBreakTest; reject source-version mismatches.
- [x] 1.2 Generate deterministic Grapheme_Cluster_Break, Extended_Pictographic, and Indic_Conjunct_Break runtime tables in committed shared-core source, recording Unicode 17.0.0 and every source URL.
- [x] 1.3 Generate and commit platform-neutral official Unicode 17.0.0 GraphemeBreakTest-derived vectors with recorded version and source URL.
- [x] 1.4 Add a generation check that runs twice from identical inputs and verifies every generated runtime and fixture file is byte-for-byte unchanged and matches committed output.

## 2. Implement the Shared Segmenter

- [x] 2.1 Implement one package-private bounded-state engine for Unicode 17.0.0 UAX #29 default extended grapheme clusters, including Hangul, prepend/extend/spacing-mark, Indic conjunct, GB11 extended pictographic, and regional-indicator interactions in rule order.
- [x] 2.2 Route existing whole-string grapheme boundary, count, and partition helpers through the shared engine while preserving public signatures and lossless output.
- [x] 2.3 Route incremental grapheme accounting through the same engine, implement reset, and verify that engine storage remains bounded without retaining processed or application-owned content.
- [x] 2.4 Preserve the existing display-width policy as a separate operation applied only after complete cluster boundaries are known.

## 3. Prove Segmentation Conformance and Equivalence

- [x] 3.1 Add shared tests that run every official Unicode 17.0.0 GraphemeBreakTest vector against whole-string segmentation and verify exact boundaries plus lossless concatenation.
- [x] 3.2 For every official vector, test incremental segmentation at every single code-point split and with one-code-point chunks against whole-string output.
- [x] 3.3 Add reset and long-input tests that verify fresh-state behavior and bounded segmenter storage independent of content length.
- [x] 3.4 Add decoder-to-segmenter tests for official and focused inputs fragmented at every UTF-8 byte boundary.
- [x] 3.5 Add focused tests for Hangul, Indic conjuncts, combining and spacing marks, prepend sequences, GB11 extended pictographic sequences, and regional-indicator runs.

## 4. Integrate Editing and Streamed Input

- [x] 4.1 Route Input cursor movement, deletion, undo/yank cursor restoration, and whole-text insertion accounting through shared Unicode 17.0.0 cluster boundaries without changing public signatures or content limits.
- [x] 4.2 Route Editor and EditorBuffer insertion, deletion, movement, undo/yank, marker expansion, and programmatic insertion boundaries through the shared segmenter while preserving EditorBuffer delegation and paste thresholds.
- [x] 4.3 Carry the shared bounded segmenter state across Input and Editor streamed paste chunks, decoder flush, paste completion, and non-paste interruption: chunks use incremental bounded state, then one final full-value segmentation handles neighbor joins, without rescanning after every chunk.
- [x] 4.4 Add Input, Editor, and EditorBuffer tests for Hangul, Indic, combining, GB11, and regional-indicator clusters at start, middle, and end insertion positions and across every chunk and fragmented UTF-8 boundary.
- [x] 4.5 Verify streamed paste final cursor counts, undo units, callbacks, marker thresholds, normalized content, and unlimited retained content remain unchanged except for corrected cluster boundaries.

## 5. Integrate ANSI Geometry and Editor Layout

- [x] 5.1 Keep ANSI metadata tokenization responsible for SGR and OSC state, but delegate all printable-text boundaries used by measurement, slicing, truncation, wrapping, and padding to the shared segmenter.
- [x] 5.2 Route EditorLayout wrapping, logical-to-visual cursor mapping, page movement, and fake-cursor placement through shared complete cluster boundaries.
- [x] 5.3 Add ANSI slicing, truncation, wrapping, and padding tests with SGR and OSC metadata around Hangul, Indic, combining, GB11, and regional-indicator clusters at both boundaries.
- [x] 5.4 Add editor layout tests that place each focused cluster type at wrap, cursor, narrow-width, and wide-cell boundaries while asserting the existing display-width policy.
- [x] 5.5 Implement whole-cluster omission for impossible editor widths, retain logical ownership and zero-width cursor placement, and add focused ANSI, EditorLayout, and Editor rendering tests.

## 6. Wire JVM and Scala Native Tests

- [x] 6.1 Add the official fixture parser and conformance suites to shared test sources without JVM-only APIs or runtime dependencies.
- [x] 6.2 Update `coreNative.test` source wiring in `build.mill` so Native compiles and runs the same official conformance, incremental, UTF-8 fragmentation, editing, ANSI, and layout fixtures as JVM where applicable.
- [x] 6.3 Run the focused JVM suites and `mill core.test`, confirming every official Unicode 17.0.0 fixture executes.
- [x] 6.4 Run the corresponding focused Native suites and `mill coreNative.test`, confirming every official Unicode 17.0.0 fixture executes on Scala Native.

## 7. Document Contracts

- [x] 7.1 Update affected public Unicode, ANSI, Input, Editor, EditorBuffer, and EditorLayout Scaladoc to state Unicode 17.0.0 UAX #29 default extended grapheme behavior, platform scope, unchanged width policy, and content/state bounds where those contracts are public.
- [x] 7.2 Update README or relevant docs with the pinned Unicode version, immutable generated-data sources, regeneration command, conformance scope, JVM/Native coverage, and the distinction between bounded segmentation state and unlimited application-owned content.
- [x] 7.3 Record explicitly in implementation documentation that ICU, JDK BreakIterator, runtime fallback, compatibility paths, and alternate segmentation engines are not used.

## 8. Full Validation

- [x] 8.1 Run the Unicode generator twice and compare generated runtime and fixture outputs byte-for-byte against each other and the committed files.
- [x] 8.2 Run `mill core.test` and `mill coreNative.test`.
- [x] 8.3 Run `mill __.compile`.
- [x] 8.4 Run `mill scalafmtCheck` and `mill scalafixCheck`.
- [x] 8.5 Run `openspec validate conform-unicode-grapheme-segmentation --strict` and `openspec validate --all --strict`.
- [x] 8.6 Review-repair strict generator parsing: require exact field shapes and complete property syntax, reject unknown GCB and malformed InCB records before writes, run deterministic parser-contract checks, and verify temporary regeneration is byte-for-byte equal to both committed outputs.
- [x] 8.7 Review-repair ANSI processing: use one forward scanner for internal strip and grapheme-unit paths, preserve repeated unterminated OSC/APC text, and pass focused JVM/Native tests for exact SGR/OSC ordering, cluster atomicity, and following-line state non-leakage.
- [x] 8.8 Review-repair documentation: document impossible-width whole-cluster visual omission, logical ownership, retained application content, zero-width cursor placement, no replacement or partial output, unchanged normal-width behavior, and segmentation-only conformance; remove the duplicate README heading.
- [x] 8.9 Replace historical ANSI replay with a one-pass scanner that enforces the complete-sequence 4096 UTF-8 byte bound and emits oversized, unsupported, private, malformed, and unterminated candidates as visible inert text.
- [x] 8.10 Implement fixed-field effective SGR state, atomic compound validation, normalized bounded replay, selective resets, and ordered interaction with OSC 8.
- [x] 8.11 Retain at most one bounded OSC 8 opener and terminator, close/reopen it at output boundaries, and make unknown bounded OSC/APC visible inert text.
- [x] 8.12 Consolidate C0, DEL, and C1 visible escaping in package-scoped `Ansi` code and reuse it from image fallback without changing image output.
- [x] 8.13 Add shared JVM/Native boundary, grammar, reset, ordering, omitted-cluster, unlimited-content, and deterministic linear-output/state tests for ANSI metadata.
- [x] 8.14 Document the public bound, byte definition, grammar, inert behavior, bounded state, platform scope, and segmentation-versus-width distinction in Scaladoc and README.
- [x] 8.15 Run focused ANSI/image JVM and Native suites, full JVM/Native tests, all-module compile, formatting, Scalafix, strict OpenSpec validation, relevant diff review, and byte-identical generator verification.
- [x] 8.16 Review-repair APC terminal compatibility: require ST termination, keep BEL as APC content, and verify exact 4096/4097 UTF-8 boundaries on JVM and Native.
- [x] 8.17 Review-repair exact retained/reset invariants: assert selective-reset replay for every modeled family and inspect actual fixed-field SGR and bounded OSC runtime state.

- [x] 8.18 Review-repair strict terminal output: allow only bounded validated SGR and OSC 8, sanitize every other sequence/control through all ordinary output utilities, and prove scanner progress on JVM and Native.
- [x] 8.19 Review-repair insertion accounting: derive Input, EditorBuffer, typed/programmatic Editor, and streamed-paste cursors from final segmentation across left/right neighbor joins, with immediate-deletion tests on JVM and Native.
- [x] 8.20 Add selective-reset coverage proving SGR 10 clears only the font field, then run all focused and full validation commands.
- [x] 8.21 Integrate with typed terminal output: apply strict sanitization only to ordinary `ComponentRender.lines`, preserve typed controls through frame preparation and final semantic encoding, and cover inert raw image-like strings plus executable typed image controls.
- [x] 8.22 Review-repair terminal string scanning and Input yank-pop: consume rejected ESC-form and C1 string controls atomically through their defined terminator or remaining unterminated input, restore exact pre-yank Input state before rotation insertion, preserve joined neighbors, and pass focused JVM plus existing wired Native coverage.
- [x] 8.23 Replace semantic demo wire-output `Ansi.strip` assertions with `VirtualTerminal.viewportLines`, preserve raw title/progress protocol assertions, and pass `InteractiveDemoSuite`.

## 9. Replace Cursor Sentinels with Structured Metadata

- [x] 9.1 Add the public frame-relative `CursorPlacement` model, require explicit cursor metadata in `ComponentRender`, and add bounded frame validation.
- [x] 9.2 Propagate and translate cursor candidates through frame builders, containers, Box, overlays, clipping, and render-origin handling.
- [x] 9.3 Replace Input and Editor sentinel rendering with structural cursor placement, including truncation, autocomplete, and impossible-width rows.
- [x] 9.4 Prepare and differentially render structured cursor metadata in TUI, including row-major selection, hardware-disabled behavior, cursor-only movement, and typed-control ordering.
- [x] 9.5 Delete the string-sentinel implementation, migrate every constructor and factory, make former APC bytes inert, and update JVM/Native test wiring.
- [x] 9.6 Flush buffered zero-width EditorLayout content before a later over-wide cluster and verify row ownership, order, and cursor mapping.
- [x] 9.7 Update public Scaladoc, README, porting notes, and active OpenSpec artifacts for the direct source break and structured metadata contract.
- [x] 9.8 Run focused JVM and Native suites, compile all modules, check formatting and Scalafix, validate OpenSpec strictly, and review relevant diffs and repository searches.

## 10. Project Sanitized ANSI Geometry into Editor Layout

- [x] 10.1 Refactor the existing forward ANSI scanner to expose one package-private projection with source-grapheme ownership, atomic supported metadata, sanitized printable graphemes, display widths, bounded replay/close state, and deterministic source-boundary mapping.
- [x] 10.2 Route EditorLayout wrapping, cursor mapping, page movement, and Editor focused/unfocused rendering through projected display units without rescanning, source mutation, partial rejected expansion, or executable-metadata splitting.
- [x] 10.3 Suppress Editor cursor placement at non-positive widths while preserving column-zero placement for positive impossible-width owner rows and valid padded Box translation.
- [x] 10.4 Add shared JVM/Native tests for control expansion, complete and unterminated string controls, supported SGR/OSC 8 atomicity and state across wraps, source-boundary cursor mapping, focused/unfocused output, impossible and non-positive widths, Box translation, and unlimited content.
- [x] 10.5 Correct README, public Scaladoc, proposal, design, component-rendering spec, and text-editing spec for direct CursorMarker removal, sanitized Editor geometry, source ownership, metadata behavior, and cursor suppression contracts.
- [x] 10.6 Run focused JVM/Native tests, full `core.test` and `coreNative.test`, all-module compile, formatting, Scalafix, strict OpenSpec validation, and relevant staged/unstaged diff checks without the final full PTY `__.test` target.

## 11. Confine Child-Frame Metadata Before Composition

- [x] 11.1 Specify child-local metadata validation for frame builders, Box, and raw overlays, including explicit post-validation overlay clipping and unchanged final-frame validation.
- [x] 11.2 Validate every child frame against its own rows and requested child width before builder translation, sibling composition, or Box padding translation; add focused cursor and terminal-control tests.
- [x] 11.3 Validate each raw overlay frame against its raw rows and resolved width before max-height clipping, then remove valid metadata with explicitly clipped rows; add focused overlay tests.
- [x] 11.4 Update relevant Scaladoc and prove malformed child metadata fails with bounded redacted diagnostics before synchronized terminal output on JVM and Native where wired.
- [x] 11.5 Run focused JVM/Native suites, full `core.test` and `coreNative.test`, all-module compile, formatting, Scalafix, strict OpenSpec validation, and relevant staged/unstaged diff checks without the final full PTY `__.test` target.

## 12. Confine Direct Overlay Composition Metadata

- [x] 12.1 Specify independent base, overlay-local, and final metadata validation at the public `OverlayRenderer.composite` boundary.
- [x] 12.2 Validate the base against terminal width, every overlay against its resolved width before translation or occlusion, and the final composed frame against terminal width; preserve empty-overlay validation and half-open occlusion.
- [x] 12.3 Add focused shared cursor and terminal-control tests for invalid base geometry, invalid overlay-local width and rows, unchanged valid composition, bounded redacted diagnostics, and explicit final-result validation.
- [x] 12.4 Correct CursorMarker object wording and impossible-width cursor wording, then run focused JVM/Native suites, full core JVM/Native tests, all-module compile, formatting, Scalafix, strict OpenSpec validation, and relevant staged/unstaged diff checks without final PTY `__.test`.
