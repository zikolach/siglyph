package scalatui.terminal

/** Terminal cursor-position query and response helpers. */
object TerminalCursorProtocol:
  /** Standard DSR cursor-position query, reported by terminals as `CSI row ; col R`. */
  val CursorPositionQuery: String = "\u001b[6n"

  final case class CursorPosition(row: Int, col: Int) derives CanEqual

  private val CursorPositionReport = "\u001b\\[(\\d+);(\\d+)R".r

  /** Parse a DSR cursor-position report into zero-based terminal cell coordinates. */
  def parseCursorPositionReport(data: String): Option[CursorPosition] = data match
    case CursorPositionReport(rowText, colText) =>
      for
        row <- parsePositive(rowText)
        col <- parsePositive(colText)
      yield CursorPosition(row - 1, col - 1)
    case _                                      => None

  def isCursorPositionReport(data: String): Boolean = parseCursorPositionReport(data).nonEmpty

  private def parsePositive(value: String): Option[Int] =
    scala.util.Try(value.toInt).toOption.filter(_ > 0)
