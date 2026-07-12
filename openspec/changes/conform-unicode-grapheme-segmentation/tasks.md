## 1. Pin and Generate Unicode 17.0.0 Data

- [ ] 1.1 Update `scripts/GenerateUnicodeTables.scala` to pin Unicode 17.0.0 and immutable versioned URLs for GraphemeBreakProperty, emoji properties, Indic_Conjunct_Break data, and GraphemeBreakTest; reject source-version mismatches.
- [ ] 1.2 Generate deterministic Grapheme_Cluster_Break, Extended_Pictographic, and Indic_Conjunct_Break runtime tables in committed shared-core source, recording Unicode 17.0.0 and every source URL.
- [ ] 1.3 Generate and commit platform-neutral official Unicode 17.0.0 GraphemeBreakTest-derived vectors with recorded version and source URL.
- [ ] 1.4 Add a generation check that runs twice from identical inputs and verifies every generated runtime and fixture file is byte-for-byte unchanged and matches committed output.

## 2. Implement the Shared Segmenter

- [ ] 2.1 Implement one package-private bounded-state engine for Unicode 17.0.0 UAX #29 default extended grapheme clusters, including Hangul, prepend/extend/spacing-mark, Indic conjunct, GB11 extended pictographic, and regional-indicator interactions in rule order.
- [ ] 2.2 Route existing whole-string grapheme boundary, count, and partition helpers through the shared engine while preserving public signatures and lossless output.
- [ ] 2.3 Route incremental grapheme accounting through the same engine, implement reset, and verify that engine storage remains bounded without retaining processed or application-owned content.
- [ ] 2.4 Preserve the existing display-width policy as a separate operation applied only after complete cluster boundaries are known.

## 3. Prove Segmentation Conformance and Equivalence

- [ ] 3.1 Add shared tests that run every official Unicode 17.0.0 GraphemeBreakTest vector against whole-string segmentation and verify exact boundaries plus lossless concatenation.
- [ ] 3.2 For every official vector, test incremental segmentation at every single code-point split and with one-code-point chunks against whole-string output.
- [ ] 3.3 Add reset and long-input tests that verify fresh-state behavior and bounded segmenter storage independent of content length.
- [ ] 3.4 Add decoder-to-segmenter tests for official and focused inputs fragmented at every UTF-8 byte boundary.
- [ ] 3.5 Add focused tests for Hangul, Indic conjuncts, combining and spacing marks, prepend sequences, GB11 extended pictographic sequences, and regional-indicator runs.

## 4. Integrate Editing and Streamed Input

- [ ] 4.1 Route Input cursor movement, deletion, undo/yank cursor restoration, and whole-text insertion accounting through shared Unicode 17.0.0 cluster boundaries without changing public signatures or content limits.
- [ ] 4.2 Route Editor and EditorBuffer insertion, deletion, movement, undo/yank, marker expansion, and programmatic insertion boundaries through the shared segmenter while preserving EditorBuffer delegation and paste thresholds.
- [ ] 4.3 Carry the shared bounded segmenter state across Input and Editor streamed paste chunks, decoder flush, paste completion, and non-paste interruption without rescanning accumulated content.
- [ ] 4.4 Add Input, Editor, and EditorBuffer tests for Hangul, Indic, combining, GB11, and regional-indicator clusters at start, middle, and end insertion positions and across every chunk and fragmented UTF-8 boundary.
- [ ] 4.5 Verify streamed paste final cursor counts, undo units, callbacks, marker thresholds, normalized content, and unlimited retained content remain unchanged except for corrected cluster boundaries.

## 5. Integrate ANSI Geometry and Editor Layout

- [ ] 5.1 Keep ANSI metadata tokenization responsible for SGR and OSC state, but delegate all printable-text boundaries used by measurement, slicing, truncation, wrapping, and padding to the shared segmenter.
- [ ] 5.2 Route EditorLayout wrapping, logical-to-visual cursor mapping, page movement, and fake-cursor placement through shared complete cluster boundaries.
- [ ] 5.3 Add ANSI slicing, truncation, wrapping, and padding tests with SGR and OSC metadata around Hangul, Indic, combining, GB11, and regional-indicator clusters at both boundaries.
- [ ] 5.4 Add editor layout tests that place each focused cluster type at wrap, cursor, narrow-width, and wide-cell boundaries while asserting the existing display-width policy.

## 6. Wire JVM and Scala Native Tests

- [ ] 6.1 Add the official fixture parser and conformance suites to shared test sources without JVM-only APIs or runtime dependencies.
- [ ] 6.2 Update `coreNative.test` source wiring in `build.mill` so Native compiles and runs the same official conformance, incremental, UTF-8 fragmentation, editing, ANSI, and layout fixtures as JVM where applicable.
- [ ] 6.3 Run the focused JVM suites and `mill core.test`, confirming every official Unicode 17.0.0 fixture executes.
- [ ] 6.4 Run the corresponding focused Native suites and `mill coreNative.test`, confirming every official Unicode 17.0.0 fixture executes on Scala Native.

## 7. Document Contracts

- [ ] 7.1 Update affected public Unicode, ANSI, Input, Editor, EditorBuffer, and EditorLayout Scaladoc to state Unicode 17.0.0 UAX #29 default extended grapheme behavior, platform scope, unchanged width policy, and content/state bounds where those contracts are public.
- [ ] 7.2 Update README or relevant docs with the pinned Unicode version, immutable generated-data sources, regeneration command, conformance scope, JVM/Native coverage, and the distinction between bounded segmentation state and unlimited application-owned content.
- [ ] 7.3 Record explicitly in implementation documentation that ICU, JDK BreakIterator, runtime fallback, compatibility paths, and alternate segmentation engines are not used.

## 8. Full Validation

- [ ] 8.1 Run the Unicode generator twice and compare generated runtime and fixture outputs byte-for-byte against each other and the committed files.
- [ ] 8.2 Run `mill core.test` and `mill coreNative.test`.
- [ ] 8.3 Run `mill __.compile`.
- [ ] 8.4 Run `mill scalafmtCheck` and `mill scalafixCheck`.
- [ ] 8.5 Run `openspec validate conform-unicode-grapheme-segmentation --strict` and `openspec validate --all --strict`.
