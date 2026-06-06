package scalatui.core

/**
 * Zero-width terminal cursor marker used to place a hardware cursor for IME workflows.
 *
 * The sequence mirrors `pi-tui`'s APC marker (`ESC _ pi:c BEL`). Renderers treat it as an ANSI-like
 * escape with no visible width, so it can be emitted immediately before a component's fake cursor
 * without changing logical text or layout measurements.
 */
object CursorMarker:
  val Sequence: String = "\u001b_pi:c\u0007"
