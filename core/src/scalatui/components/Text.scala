package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender}
import scalatui.syntax.Equality.*

final class Text(
    private var content: String,
    paddingX: Int = 1,
    paddingY: Int = 0,
    style: String => String = identity
) extends Component:
  private var cachedWidth = -1
  private var cachedLines = Vector.empty[String]

  def text: String                = content
  def text_=(value: String): Unit =
    content = value
    invalidate()

  override def invalidate(): Unit =
    cachedWidth = -1
    cachedLines = Vector.empty

  override def render(width: Int): ComponentRender =
    ComponentRender.text(if cachedWidth === width then cachedLines
    else
      val innerWidth = math.max(0, width - paddingX * 2)
      val horizontal = " ".repeat(math.max(0, paddingX))
      val vertical   = Vector.fill(math.max(0, paddingY))(style(" ".repeat(width)))
      val body       = Ansi.wrapLogicalLinesWithAnsi(content, innerWidth).map { line =>
        val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
        style(Ansi.truncateToWidth(padded, width, ""))
      }
      cachedWidth = width
      cachedLines = vertical ++ body ++ vertical
      cachedLines)
