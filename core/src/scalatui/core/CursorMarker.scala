package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/**
 * Zero-width terminal cursor marker used to place a hardware cursor for IME workflows.
 *
 * The sequence mirrors `pi-tui`'s APC marker (`ESC _ pi:c BEL`). Renderers treat it as an ANSI-like
 * escape with no visible width, so it can be emitted immediately before a component's fake cursor
 * without changing logical text or layout measurements.
 */
object CursorMarker:
  /** APC marker emitted by focused editors immediately before their fake cursor token. */
  val Sequence: String = "\u001b_pi:c\u0007"

  /** Zero-based display-cell position of a cursor marker in a rendered frame. */
  final case class Position(row: Int, column: Int) derives CanEqual

  /** Marker-stripped frame lines plus the first marker position, if any was present. */
  final case class ScanResult(lines: Vector[String], position: Option[Position]) derives CanEqual

  /**
   * Strip all cursor markers from rendered frame lines and locate the first marker in row-major
   * order.
   *
   * ANSI/control sequences, including OSC hyperlinks and other APC sequences, are treated as
   * zero-width terminal metadata. Columns are zero-based display-cell offsets, so wide Unicode and
   * combining-mark grapheme clusters are measured the same way rendered terminal text is measured.
   */
  def stripAndLocate(lines: Vector[String]): ScanResult =
    val cleaned = Vector.newBuilder[String]
    var found   = Option.empty[Position]
    lines.zipWithIndex.foreach { (line, row) =>
      val builder = StringBuilder()
      var column  = 0
      var i       = 0
      while i < line.length do
        if line.startsWith(Sequence, i) then
          if found.isEmpty then found = Some(Position(row, column))
          i += Sequence.length
        else
          Ansi.extractEscape(line, i) match
            case Some(escape) =>
              builder.append(escape.code)
              i += escape.length
            case None         =>
              val nextMarker = line.indexOf(Sequence, i)
              val nextEscape = line.indexOf('\u001b', i)
              val candidates = Vector(nextMarker, nextEscape).filter(_ >= 0)
              val plainEnd   = if candidates.isEmpty then line.length else candidates.min
              val plain      = line.substring(i, plainEnd)
              builder.append(plain)
              column += Unicode.stringWidth(plain)
              i = plainEnd
      cleaned += builder.result()
    }
    ScanResult(cleaned.result(), found)
