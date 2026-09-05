package scalatui.core

/**
 * Immutable fullscreen selection in normalized plain-text grapheme offsets.
 *
 * Offsets are half-open and exclude ANSI metadata, typed terminal controls, and display-only soft
 * row boundaries. [[text]] contains only complete grapheme clusters and normalized `\n` separators
 * between selected rendered rows.
 */
final case class ViewportSelection(startOffset: Int, endOffset: Int, text: String) derives CanEqual:
  require(startOffset >= 0, "Selection start must be non-negative")
  require(endOffset >= startOffset, "Selection end must not precede its start")
