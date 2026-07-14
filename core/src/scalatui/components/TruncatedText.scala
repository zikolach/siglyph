package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender}
import scalatui.syntax.Equality.*

/**
 * Single-line text component that truncates content to the requested terminal width.
 *
 * `TruncatedText` mirrors the small `pi-tui` status/header widget: rendering uses only the first
 * logical line before a newline, applies horizontal and vertical padding, and truncates by visible
 * terminal columns while preserving ANSI escape sequences. Returned lines are padded to the
 * requested width when possible and are always width-safe for the shared JVM/Native core.
 *
 * @param initialText
 *   text to render; only the first logical line is displayed
 * @param paddingX
 *   number of plain spaces to add on the left and right
 * @param paddingY
 *   number of blank padded lines to add above and below the content line
 */
final class TruncatedText(
    initialText: String,
    paddingX: Int = 0,
    paddingY: Int = 0
) extends Component:
  private var content = initialText

  def text: String = content

  def text_=(value: String): Unit = content = value

  override def render(width: Int): ComponentRender =
    val safeWidth = math.max(0, width)
    val emptyLine = " ".repeat(safeWidth)
    val vertical  = Vector.fill(math.max(0, paddingY))(emptyLine)
    ComponentRender.text(vertical ++ Vector(contentLine(safeWidth)) ++ vertical)

  private def contentLine(width: Int): String =
    if width <= 0 then ""
    else
      val horizontal     = " ".repeat(math.max(0, paddingX))
      val availableWidth = math.max(1, width - math.max(0, paddingX) * 2)
      val firstLine      =
        val newlineIndex = content.indexOf('\n')
        if newlineIndex < 0 then content else content.substring(0, newlineIndex)
      val displayText    = Ansi.truncateToWidth(firstLine, availableWidth, "")
      val padded         = horizontal + displayText + horizontal
      if Ansi.visibleWidth(padded) === width then padded
      else Ansi.padRight(Ansi.truncateToWidth(padded, width, ""), width)
