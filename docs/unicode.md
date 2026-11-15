# Unicode and text safety

[Documentation index](README.md)

## Unicode grapheme data

Shared JVM and Scala Native text editing and ANSI geometry use Unicode 17.0.0 UAX #29 default extended grapheme clusters. The generated tables use immutable sources under `https://www.unicode.org/Public/17.0.0/ucd/`, including `auxiliary/GraphemeBreakProperty.txt`, `emoji/emoji-data.txt`, `DerivedCoreProperties.txt`, and `auxiliary/GraphemeBreakTest.txt`. Exact URLs are recorded in the generated files.

Regenerate `core/src/scalatui/unicode/UnicodeTables.scala` and
`core/test/src/scalatui/unicode/UnicodeGraphemeBreakFixtures.scala` with:

```bash
scala-cli run scripts/GenerateUnicodeTables.scala
```

The shared conformance suite executes all 766 official Unicode 17.0.0 GraphemeBreakTest cases on JVM and Scala Native, including incremental and fragmented UTF-8 inputs. This conformance claim applies to UAX #29 segmentation only. Terminal width remains a separate project-specific policy and is unchanged for normal widths.

The one dependency-free segmenter keeps bounded rule state without retaining processed text. Input and EditorBuffer retain exact, unlimited source text and Unicode 17.0.0 source-grapheme cursor positions. Editor scans each complete logical line once with the existing forward ANSI scanner, then wraps sanitized final printable graphemes. Rejected controls can expand to several display units or rows; every unit retains the original half-open source-grapheme owner range and appears exactly once in order. Supported bounded SGR and OSC 8 remain executable atomic metadata. Cursor boundaries inside them map to a safe display boundary, and focused inverse-video styling never divides the sequence.

If a complete final display grapheme is wider than an otherwise empty positive-width editor row, rendering omits the whole unit visually. The row keeps logical ownership and maps its logical cursor to column zero. Rendering emits `CursorPlacement(row, 0)` only when Editor is focused, autocomplete does not own input, and the cursor owns that row. Rendering emits no replacement, partial cluster, or fallback glyph and does not mutate application content. At width zero or below, Editor emits no printable output or cursor placement; translated padded Box frames remain valid.

The implementation does not use ICU, JDK `BreakIterator`, runtime fallback, compatibility paths, or an alternate segmentation engine. Regeneration replaces committed generated output directly; it does not select data at runtime.

ANSI geometry and ordinary `ComponentRender.lines` allow only fully validated atomic SGR and OSC 8 open/close metadata to remain executable. The complete sequence, including introducer and terminator, must be at most `Ansi.MaxRecognizedMetadataBytes` (4096) UTF-8 bytes. Every other CSI, OSC, APC, DCS, ESC form, C0, DEL, and C1 value in ordinary strings renders as visible inert text; controls use uppercase `\uXXXX` and every other code point remains exact. Former cursor APC bytes are ordinary inert text and cannot create or select cursor metadata. Typed `TerminalRenderControl` and `CursorPlacement` values remain separate from ordinary strings. Terminal controls are encoded only through the trusted semantic channel at the final TUI output boundary; cursor placements encode no protocol bytes. Effective SGR state uses fixed modeled fields, and hyperlink state retains at most one bounded OSC 8 opener. This executable-metadata bound is separate from application content: text beyond 4096 bytes remains unlimited and uses the same shared grapheme segmenter. Grapheme segmentation and terminal display width remain separate policies on JVM and Scala Native.
