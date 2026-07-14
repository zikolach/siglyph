## Why

Current whole-string and incremental grapheme logic omits required Unicode rules and state interactions, so editing, layout, ANSI geometry, and streamed paste accounting can split user-perceived characters or disagree across chunk boundaries. The shared core needs full Unicode 17.0.0 UAX #29 default extended grapheme cluster conformance on JVM and Scala Native.

## What Changes

- Add a dependency-free shared Unicode text capability that conforms to Unicode 17.0.0 UAX #29 default extended grapheme clusters for whole-string and incremental segmentation.
- Use one package-private bounded-state segmentation engine for all whole-string and incremental callers while leaving retained application content unlimited.
- Pin immutable Unicode 17.0.0 data sources and generate deterministic runtime properties and official GraphemeBreakTest-derived fixtures.
- Make Input, Editor, EditorBuffer, EditorLayout, ANSI slicing, wrapping, truncation, and streamed paste cursor counts respect complete extended grapheme clusters.
- Remove the public `CursorMarker` object and its `Sequence`, `Position`, `ScanResult`, and
  `stripAndLocate` APIs. Migrate directly to required frame-relative `CursorPlacement` values in
  `ComponentRender.cursorPlacements`, with no compatibility API. Propagate candidates through
  composition and select the first surviving row-major candidate only after overlay occlusion.
- Flush buffered zero-width editor content before emitting a later over-wide cluster so visual order
  and logical ownership remain aligned.
- Scan each complete Editor logical line once with the existing forward ANSI scanner. Wrap sanitized
  final printable graphemes with exact half-open source-grapheme ownership, atomic supported
  metadata, bounded replay state, and deterministic cursor mapping outside executable metadata.
- Suppress Editor cursor placement at non-positive widths while preserving column-zero ownership on
  positive impossible-width rows and valid translation through a default-padded width-one Box.
- In ordinary `ComponentRender.lines`, allow only fully validated atomic SGR and OSC 8 metadata to execute, bounded to complete sequences of at most 4096 UTF-8 bytes while leaving application text unlimited.
- Render oversized, unsupported, private, and malformed metadata in ordinary strings as visible inert text: C0, DEL, and C1 controls use uppercase `\uXXXX`, and all other text remains exact. Typed `TerminalRenderControl` values use the separate trusted semantic channel.
- At impossible render widths only, omit an entire cluster that is wider than the requested row while retaining its logical ownership, deterministic zero-width cursor placement, and unchanged application content; do not emit a partial cluster or replacement glyph.
- Preserve public editing signatures, display-width policy, paste thresholds, and existing
  editor-buffer delegation. Direct `ComponentRender` construction becomes source-breaking and
  requires explicit `lines`, `controls`, and `cursorPlacements` with no compatibility path.
- Run official conformance and focused integration fixtures on JVM and Scala Native without ICU, JDK BreakIterator, runtime dependencies, fallback paths, compatibility paths, or a second segmentation engine.

## Capabilities

### New Capabilities
- `unicode-text`: Unicode 17.0.0 UAX #29 default extended grapheme cluster conformance, whole-string and incremental equivalence, bounded state, and deterministic versioned data.

### Modified Capabilities
- `developer-api`: Pin Unicode table generation to immutable Unicode 17.0.0 sources, require deterministic runtime properties and official test fixtures, and define the public hardware cursor option and documentation through structured `CursorPlacement` metadata rather than string markers.
- `text-editing`: Require streamed and chunked cursor accounting to preserve Unicode 17.0.0 extended grapheme clusters across chunk and UTF-8 boundaries, and replace focused editing cursor-marker emission with structured cursor placements.
- `component-rendering`: Require ANSI geometry and editor visual layout to treat Unicode 17.0.0 extended grapheme clusters atomically, and replace final string-marker handling with structured cursor validation, propagation, overlay occlusion, selection, and differential rendering.
- `terminal-runtime`: Require bounded bracketed-paste event streaming and remove obsolete requirements that authorize rendered-string cursor-marker scanning and marker-driven positioning.

## Impact

- Affected shared core areas: Unicode tables and segmentation, input and editor mutation, editor layout, ANSI geometry, and streamed paste handling.
- Affected generation and tests: Scala CLI Unicode generation, committed generated runtime data, committed GraphemeBreakTest-derived fixtures, and JVM/Scala Native test wiring.
- `ComponentRender` and every direct constructor are source-breaking. The public
  `CursorPlacement(row, column)` type uses zero-based frame-relative display-cell coordinates.
  Factories may construct explicit empty cursor metadata. No overload, default field, adapter,
  conversion, or deprecation path is provided. Other public editing signatures and the display-width
  policy remain unchanged.
- No runtime dependency is added; shared core remains portable between JVM and Scala Native.
