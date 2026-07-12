## Context

The shared Unicode code currently has separate whole-string and incremental behavior that does not implement all Unicode grapheme rules. Missing Hangul classes, Indic_Conjunct_Break properties, and interactions among prepend, spacing marks, extended pictographic ZWJ sequences, and regional indicators allow segmentation differences by call path or chunking. These boundaries feed Input, EditorBuffer, Editor, EditorLayout, ANSI geometry, and streamed paste cursor accounting in shared JVM/Scala Native sources.

Unicode data is generated and committed, and shared core must remain dependency-free. The implementation must pin Unicode 17.0.0, use immutable versioned source URLs, preserve existing public signatures and display-width policy, and keep runtime state bounded without limiting application-owned content.

## Goals / Non-Goals

**Goals:**
- Conform to Unicode 17.0.0 UAX #29 default extended grapheme clusters for every official GraphemeBreakTest case.
- Make whole-string and incremental segmentation produce the same lossless partition for arbitrary code-point and UTF-8 fragmentation.
- Provide one package-private segmentation engine shared by all call paths.
- Generate deterministic runtime properties and official test fixtures from pinned immutable Unicode 17.0.0 sources.
- Exercise official fixtures on JVM and Scala Native and cover editing, layout, ANSI, and streamed paste boundaries.

**Non-Goals:**
- Redefining terminal display width or changing how an already segmented cluster receives its width.
- Changing public signatures, paste thresholds, editor-buffer delegation, or application content limits.
- Adding ICU, JDK BreakIterator, another runtime dependency, fallback behavior, compatibility behavior, or a second segmentation engine.
- Tailoring UAX #29 boundaries beyond default extended grapheme clusters.

## Decisions

### Pin generated Unicode inputs and outputs to 17.0.0

The Scala CLI generator will identify Unicode 17.0.0 once and fetch each input from an immutable URL that includes that version. It will generate the Grapheme_Cluster_Break, Extended_Pictographic, and Indic_Conjunct_Break properties needed by UAX #29, record the version and exact source URLs in generated output, and derive committed official test vectors from the version-matched GraphemeBreakTest source. Output ordering, formatting, and line endings will be fixed so repeated runs are byte-for-byte identical.

Using host Unicode APIs was rejected because JVM and Native availability and versions differ. Fetching unversioned `latest` data was rejected because it cannot reproduce a release.

### Use one package-private bounded-state engine

A package-private engine will implement UAX #29 boundary rules in rule order. Whole-string helpers will feed code points into that engine and collect boundaries. Incremental users will retain only the engine state needed to decide the next boundary, including finite property history and counters or flags for regional indicators, extended-pictographic ZWJ context, and Indic conjunct context. Reset will restore the initial state.

The engine may retain bounded rule context but will not retain the segmented string. Callers continue to own content, so transport and segmentation state remain bounded independently of unlimited Input and Editor content.

Maintaining separate batch and streaming algorithms was rejected because equivalence would depend on duplicated rule logic. Retaining the full prefix in the segmenter was rejected because runtime segmentation state would grow with application content.

### Keep ANSI parsing separate from text segmentation

ANSI utilities will continue to parse and preserve SGR, OSC hyperlink, and other non-printing metadata. Whenever they measure, slice, wrap, truncate, or pad printable text, they will obtain boundaries from the shared segmenter and emit a cluster atomically. ANSI state can cross a cluster boundary, but ANSI code will not implement Unicode boundary rules.

Treating ANSI-stripped text with a second local boundary implementation was rejected because it would duplicate segmentation. Treating escape sequences as Unicode text was rejected because metadata must remain zero-width and stateful.

### Preserve display-width policy after segmentation

The existing width calculation remains responsible for assigning terminal columns to each complete cluster. UAX #29 determines only which code points are atomic. This separates Unicode text boundaries from terminal-specific width policy and avoids an unrelated output change.

### Route editing and streamed accounting through shared boundaries

Input, Editor, EditorBuffer, and EditorLayout will use shared cluster boundaries for cursor indexes, deletion, movement, wrapping, and fake-cursor placement. Existing EditorBuffer delegation remains unchanged. Streamed paste sessions will carry the same bounded segmenter state across decoded chunks and finalize it on paste completion or interruption. Fragmented UTF-8 remains the decoder's responsibility; decoded code points enter the shared segmenter without a chunk-local rescan.

### Share official fixtures across JVM and Native tests

Generated GraphemeBreakTest-derived vectors will be committed in a platform-neutral form consumed by shared test logic. Mill Native test source wiring will compile or expose the same fixture tests for `coreNative`, rather than maintaining a separate fixture interpretation. Focused tests will cover Hangul, Indic conjuncts, combining marks, GB11 extended pictographic sequences, regional indicators, reset, every single split, one-code-point chunks, fragmented UTF-8, editing/layout/ANSI boundaries, and streamed paste counts.

## Risks / Trade-offs

- [Generated tables or fixtures accidentally mix Unicode versions] → Validate embedded version and all source URLs, reject mismatches during generation, and test deterministic regeneration.
- [A UAX #29 rule-order or state transition is incomplete] → Run the complete Unicode 17.0.0 GraphemeBreakTest suite in batch and incremental modes, including every split and one-code-point chunks.
- [Incremental state grows with long text] → Store only rule state and counters independent of retained content, and add focused long-sequence tests that inspect or exercise the bounded representation.
- [ANSI metadata causes a text boundary to be split] → Keep metadata tokenization separate and test style and hyperlink state around multi-code-point clusters at slice, wrap, and truncate boundaries.
- [Native test wiring diverges from JVM] → Reuse the same generated fixtures and shared test sources, then run explicit JVM and Native test targets.
- [Boundary corrections alter visible widths unexpectedly] → Preserve the current cluster-width policy and add regression tests that separate segmentation assertions from width assertions.
