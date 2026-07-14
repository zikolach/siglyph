## Context

The shared Unicode code currently has separate whole-string and incremental behavior that does not implement all Unicode grapheme rules. Missing Hangul classes, Indic_Conjunct_Break properties, and interactions among prepend, spacing marks, extended pictographic ZWJ sequences, and regional indicators allow segmentation differences by call path or chunking. These boundaries feed Input, EditorBuffer, Editor, EditorLayout, ANSI geometry, and streamed paste cursor accounting in shared JVM/Scala Native sources.

Unicode data is generated and committed, and shared core must remain dependency-free. The implementation must pin Unicode 17.0.0, use immutable versioned source URLs, preserve the display-width policy, and keep runtime state bounded without limiting application-owned content. The approved cursor migration directly breaks `ComponentRender` construction and adds structured frame-relative cursor metadata without a compatibility path.

## Goals / Non-Goals

**Goals:**
- Conform to Unicode 17.0.0 UAX #29 default extended grapheme clusters for every official GraphemeBreakTest case.
- Make whole-string and incremental segmentation produce the same lossless partition for arbitrary code-point and UTF-8 fragmentation.
- Provide one package-private segmentation engine shared by all call paths.
- Generate deterministic runtime properties and official test fixtures from pinned immutable Unicode 17.0.0 sources.
- Exercise official fixtures on JVM and Scala Native and cover editing, layout, ANSI, and streamed paste boundaries.
- Remove string-sentinel cursor authority and propagate structured cursor candidates through shared
  JVM/Native frame geometry, overlays, and differential rendering.
- Preserve zero-width editor content order before later over-wide clusters.

**Non-Goals:**
- Redefining terminal display width or changing how an already segmented cluster receives its width.
- Changing public editing signatures other than the approved `ComponentRender` source break, paste
  thresholds, editor-buffer delegation, or application content limits.
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

ANSI utilities will parse and preserve only supported SGR and OSC 8 hyperlink metadata. Whenever they measure, slice, wrap, truncate, or pad printable text, they will obtain boundaries from the shared segmenter and emit a cluster atomically. ANSI state can cross a cluster boundary, but ANSI code will not implement Unicode boundary rules.

Treating ANSI-stripped text with a second local boundary implementation was rejected because it would duplicate segmentation. Treating escape sequences as Unicode text was rejected because metadata must remain zero-width and stateful.

### Validate complete Unicode property records before output

The generator parses every non-comment record before either output write. Two-field Unicode sources
require exactly two semicolon fields and a complete identifier-valued property field. The
GraphemeBreakProperty parser rejects every property outside the engine's complete selected GCB set;
other two-field sources validate but ignore well-formed unselected properties. DerivedCoreProperties
accepts its Unicode 17 two-field records and exactly three fields only for `InCB`. Generator-internal
contract checks cover extra fields, whitespace suffixes, unknown GCB properties, and malformed InCB
records without adding a runtime dependency.

### Bound executable ANSI metadata and retain effective state only

One forward scanner classifies input as supported complete bounded SGR/OSC 8 metadata, inert
metadata, or ordinary printable code points. `Ansi.MaxRecognizedMetadataBytes` is 4096. The bound
counts the complete sequence in UTF-8 bytes, including introducer and terminator. A complete candidate
is never partially recognized. An over-limit candidate is scanned through its terminator once and is
then entirely inert. Rejected OSC, DCS, SOS, PM, and APC candidates are consumed atomically through
their defined terminator. Unterminated candidates consume the remaining input once without rescanning
escape-looking suffixes. Unsupported, private, malformed, and out-of-range SGR is also
entirely inert and does not change state. In inert output, C0, DEL, and C1 controls become uppercase
`\uXXXX`; every other code point remains exact. This visible text enters the same
`GraphemeBoundaryEngine` as ordinary text.

SGR persistence includes the modeled attributes and selective resets, 16-color forms, `5;n`, `2;r;g;b`,
approved colon color forms with the empty color-space slot, and values from 0 through 255. Compound
SGR is atomic: every parameter must parse and validate before any update is applied. Empty SGR and
parameter 0 reset all SGR state. Unsupported, private, or malformed SGR is inert. Only valid OSC 8
open and close metadata is executable; OSC accepts BEL or ST termination. Every other CSI, OSC,
APC, DCS, SOS, PM, unsupported ESC form, C0, DEL, and C1 value is inert. ESC-form and
C1-introduced DCS, SOS, PM, and APC use only ST as their terminator, so BEL remains candidate
content. OSC uses BEL or ST. ST may be `ESC \\` or C1 ST. C1-introduced string controls remain
atomic inert candidates. OSC 8 retains at most one bounded opener and its BEL
or ST terminator; replacement and close discard the previous opener. Former cursor APC bytes have no
special treatment and render as visible inert text under the same APC policy.

SGR state consists of a fixed set of effective fields plus fixed field identifiers and sequence
numbers. It never stores source history. Selective resets clear only their fields; full reset clears
all fields and ordering. Replay serializes active fields in update order and preserves relative order
with the active OSC opener. Each emitted slice or wrapped line closes OSC 8 and resets active SGR,
then the next line reopens only state still active in the source. Omitted wide clusters still advance
recognized bounded state.

The scanner and state transitions take `O(N + produced output)` time. Scanner counters, effective SGR
fields, and retained OSC state are bounded independently of input; the sole retained OSC opener is at
most 4096 UTF-8 bytes. Replay size is therefore fixed-field SGR output plus one bounded opener.
Application text has no 4096-byte limit and is never dropped, truncated, or replaced by this metadata
policy. Image fallback metadata uses the same package-scoped visible-control conversion.

This strict policy applies to ordinary `ComponentRender.lines`. `ComponentRender.controls` retains
closed `TerminalRenderControl` values unchanged through frame preparation. The TUI encodes those
typed controls through the separate trusted semantic channel only at final output. Ordinary Kitty
APC and iTerm2 OSC strings remain inert and never gain typed-control authority.

Executing unknown/private metadata, retaining historical SGR strings, storing multiple OSC openers,
partial execution, silent dropping, truncation, typed failures, fallback glyphs, compatibility paths,
and application-content limits were rejected.

### Use structured frame-relative cursor metadata

The public `CursorMarker` object and its `Sequence`, `Position`, `ScanResult`, and `stripAndLocate`
members are removed. Callers migrate directly to `CursorPlacement` values in
`ComponentRender.cursorPlacements`; no compatibility API, adapter, conversion, or deprecation path
exists.

Shared core adds public `CursorPlacement(row: Int, column: Int)`. Coordinates are non-negative,
zero-based, frame-relative display-cell coordinates. Translation returns a new placement and rejects
negative results. Cursor metadata encodes no terminal protocol bytes, is not
`TerminalRenderControl`, and cannot be created or selected by ordinary strings.

`ComponentRender` requires explicit `lines`, `controls`, and `cursorPlacements` fields. Text-only
factories construct explicit empty metadata. This is a direct source break with no default field,
overload, adapter, implicit conversion, or deprecation path. A vector retains multiple candidates
until overlay occlusion. Validation rejects a candidate whose row is outside returned lines or whose
column is at least `max(0, width)`. Cursor diagnostics retain bounded coordinates and frame dimensions
but no application text.

Every `ComponentRender` returned by a child uses child-local geometry. `ComponentFrameBuilder`
validates the frame against its own rows and the builder width before translating metadata or
appending any later sibling. Box validates each child against its own rows and `innerWidth` before
adding body-row or padding offsets. Invalid cursor candidates and terminal-control footprints fail
at that boundary; parent padding and sibling rows cannot make them valid.

TUI validates each raw overlay frame against its raw rows and resolved overlay width before applying
configured max-height clipping. Explicit clipping then removes valid metadata anchored only in rows
removed with the clipped content. Metadata that survives clipping is validated again in the clipped
frame. The final composed-frame validation remains required after overlay translation and occlusion.

`OverlayRenderer.composite` is an independent public pure composition boundary. It validates the
base frame against terminal width even when no overlays are supplied. It validates every overlay
frame against its own rows and resolved width before any translation or occlusion, then validates the
final frame against terminal width after translation and occlusion. Existing TUI raw, clipped, and
final validation remains required because each public boundary enforces its own metadata contract.

Frame translation applies the same row and column offsets to cursor candidates as content geometry
without copying terminal controls. `ComponentFrameBuilder` applies only accumulated local row offsets;
`startRow` and `startCol` remain notifications for `RenderOriginAware`. Box adds body-row and
normalized horizontal-padding offsets. Overlay composition processes lower to higher opaque
half-open rectangles. Each higher rectangle removes covered lower candidates and appends translated
higher candidates; non-overlapping and boundary candidates survive. Height clipping removes cursor
candidates in clipped rows.

Input and Editor sanitize application-owned segments before adding inverse-video fake-cursor styling.
They calculate cursor survival and display-cell columns structurally rather than searching rendered
bytes. Normal rows attach metadata only when the complete fake cursor survives truncation. Focused
Editor impossible-width owner rows attach column-zero metadata despite emitting no printable fake
cursor content. Active autocomplete owns input and suppresses Editor cursor metadata.

TUI sanitizes ordinary lines independently from cursor candidates. It selects the first surviving
row-major candidate and preserves vector order for equal coordinates. A cursor-only differential
change moves the hardware cursor without repainting unchanged lines or typed controls. Disabled
hardware positioning emits no cursor movement. Former cursor APC bytes remain visible inert text and
cannot override a legitimate structured candidate.

String sentinels, trusted-string cursor APIs, typed terminal controls for cursor coordinates, byte
search, compatibility paths, and protocol-prefix inference were rejected.

### Project Editor geometry from one complete-line ANSI scan

Editor joins the exact `EditorBuffer` source graphemes for one logical line and passes that line once
through the existing forward ANSI scanner. The package-private projection records atomic supported
metadata parts, sanitized final printable graphemes, display widths, bounded replay/close state, and
the original half-open source-grapheme owner range for every output unit. Rejected controls may
produce several units or rows with the same owner range. Every visible character is emitted once in
source order. Projection data is retained only by the current layout/render plan and may grow with
rendered application content; parser state remains fixed-field SGR plus one bounded OSC opener.

EditorLayout wraps projected units and maps cursor/page movement through the ownership map. It does
not measure raw source widths or infer ownership from output bytes. A source cursor boundary inside
an executable sequence maps after the complete atomic metadata and before the next printable unit,
or to the nearest complete-grapheme boundary when metadata occurs inside one final grapheme. Focused
inverse-video styling is inserted only between structured parts, never inside executable metadata,
and source ANSI state is replayed after the fake cursor. Row starts and ends use the same bounded
replay/close behavior as ANSI geometry. Editor does not sanitize or truncate projected rows again.

At width zero or below, Editor returns empty rows without cursor placements, so direct and translated
frames validate. At positive widths, an impossible-width cursor-owned unit keeps its blank owner row
and `CursorPlacement(row, 0)`. A default-padded width-one Box receives child width zero and translates
no invalid candidate.

Scanning each source grapheme separately, adding a second parser, mutating retained source, dropping
rejected expansion text, splitting executable metadata, and retaining projection data globally were
rejected.

### Preserve display-width policy after segmentation

The existing width calculation remains responsible for assigning terminal columns to each complete cluster. UAX #29 determines only which code points are atomic. This separates Unicode text boundaries from terminal-specific width policy and avoids an unrelated output change.

### Route editing and streamed accounting through shared boundaries

Input, Editor, EditorBuffer, and EditorLayout use shared cluster boundaries for cursor indexes,
deletion, movement, wrapping, and fake-cursor placement. Existing EditorBuffer delegation remains
unchanged. Streamed paste sessions carry the same bounded segmenter state across decoded chunks and
finalize it on paste completion or interruption. Fragmented UTF-8 remains the decoder's
responsibility; decoded code points enter the shared segmenter without a chunk-local rescan.

Insertion cursor placement uses the final resegmented line or value and the UTF-16 insertion end. If
the insertion end lies inside a final cluster because inserted text joined the left neighbor, right
neighbor, or both, the cursor uses the first final grapheme boundary at or after that insertion end.
Streamed Input paste performs one final segmentation after completion to account for right-neighbor
joins; it does not repeatedly rescan accumulated chunks.

Input yank retains the exact pre-yank `Input.State`. Yank-pop restores that state before inserting
the rotated kill-ring candidate, so segmentation joins involving yanked text cannot remove or
replace original left or right neighbors. Repeated yank-pop retains the same base state. Any other
completed editing action clears the retained yank base.

### Omit whole clusters only at impossible editor widths

When one complete cluster is wider than an otherwise empty requested editor row, layout will emit an empty zero-width visual line that retains the cluster's logical `[column, column + 1)` ownership. Cursor mapping will clamp to the emitted line width, so positions owned by that blank row use visual column zero. Focused rendering attaches `CursorPlacement(row, 0)` without emitting printable fake-cursor content, the cluster, a partial cluster, a replacement glyph, or an over-wide line. The editor buffer and application content remain unchanged.

This omission applies only when no complete cluster can fit atomically. Normal-width layout and fake-cursor rendering remain unchanged. Changing terminal width policy, splitting the cluster, emitting an over-wide line, mutating content, and adding a fallback glyph were rejected.

If buffered zero-width content precedes a later over-wide cluster, layout flushes the buffered content
first even though its display width is zero. The over-wide cluster then receives its own blank owner
row, and later fitting clusters follow it. This preserves visual order, logical ownership, and cursor
mapping.

### Share official fixtures across JVM and Native tests

Generated GraphemeBreakTest-derived vectors will be committed in a platform-neutral form consumed by shared test logic. Mill Native test source wiring will compile or expose the same fixture tests for `coreNative`, rather than maintaining a separate fixture interpretation. Focused tests will cover Hangul, Indic conjuncts, combining marks, GB11 extended pictographic sequences, regional indicators, reset, every single split, one-code-point chunks, fragmented UTF-8, editing/layout/ANSI boundaries, and streamed paste counts.

## Risks / Trade-offs

- [Generated tables or fixtures accidentally mix Unicode versions] → Validate embedded version and all source URLs, reject mismatches during generation, and test deterministic regeneration.
- [A UAX #29 rule-order or state transition is incomplete] → Run the complete Unicode 17.0.0 GraphemeBreakTest suite in batch and incremental modes, including every split and one-code-point chunks.
- [Incremental state grows with long text] → Store only rule state and counters independent of retained content, and add focused long-sequence tests that inspect or exercise the bounded representation.
- [ANSI metadata causes a text boundary to be split] → Keep metadata tokenization separate and test style and hyperlink state around multi-code-point clusters at slice, wrap, and truncate boundaries.
- [Native test wiring diverges from JVM] → Reuse the same generated fixtures and shared test sources, then run explicit JVM and Native test targets.
- [Boundary corrections alter visible widths unexpectedly] → Preserve the current cluster-width policy and add regression tests that separate segmentation assertions from width assertions.
