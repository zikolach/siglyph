## Why

Current whole-string and incremental grapheme logic omits required Unicode rules and state interactions, so editing, layout, ANSI geometry, and streamed paste accounting can split user-perceived characters or disagree across chunk boundaries. The shared core needs full Unicode 17.0.0 UAX #29 default extended grapheme cluster conformance on JVM and Scala Native.

## What Changes

- Add a dependency-free shared Unicode text capability that conforms to Unicode 17.0.0 UAX #29 default extended grapheme clusters for whole-string and incremental segmentation.
- Use one package-private bounded-state segmentation engine for all whole-string and incremental callers while leaving retained application content unlimited.
- Pin immutable Unicode 17.0.0 data sources and generate deterministic runtime properties and official GraphemeBreakTest-derived fixtures.
- Make Input, Editor, EditorBuffer, EditorLayout, ANSI slicing, wrapping, truncation, and streamed paste cursor counts respect complete extended grapheme clusters.
- Preserve public signatures, display-width policy, paste thresholds, and existing editor-buffer delegation.
- Run official conformance and focused integration fixtures on JVM and Scala Native without ICU, JDK BreakIterator, runtime dependencies, fallback paths, compatibility paths, or a second segmentation engine.

## Capabilities

### New Capabilities
- `unicode-text`: Unicode 17.0.0 UAX #29 default extended grapheme cluster conformance, whole-string and incremental equivalence, bounded state, and deterministic versioned data.

### Modified Capabilities
- `developer-api`: Pin Unicode table generation to immutable Unicode 17.0.0 sources and require deterministic runtime properties and official test fixtures.
- `text-editing`: Require streamed and chunked cursor accounting to preserve Unicode 17.0.0 extended grapheme clusters across chunk and UTF-8 boundaries.
- `component-rendering`: Require ANSI geometry and editor visual layout to treat Unicode 17.0.0 extended grapheme clusters atomically.

## Impact

- Affected shared core areas: Unicode tables and segmentation, input and editor mutation, editor layout, ANSI geometry, and streamed paste handling.
- Affected generation and tests: Scala CLI Unicode generation, committed generated runtime data, committed GraphemeBreakTest-derived fixtures, and JVM/Scala Native test wiring.
- Public method signatures and the display-width policy remain unchanged.
- No runtime dependency is added; shared core remains portable between JVM and Scala Native.
