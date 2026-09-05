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
  private val stateBoundary = ComponentStateBoundary()
  private var cachedWidth   = -1
  private var cachedLines   = Vector.empty[String]
  private var revision      = 0L

  def text: String                = stateBoundary(content)
  def text_=(value: String): Unit = stateBoundary {
    content = value
    invalidateLocked()
  }

  override def invalidate(): Unit = stateBoundary(invalidateLocked())

  override def render(width: Int): ComponentRender =
    val (cached, contentSnapshot, token) = stateBoundary {
      (Option.when(cachedWidth === width)(cachedLines), content, revision)
    }
    ComponentRender.text(cached match
      case Some(lines) => lines
      case None        =>
        val innerWidth = math.max(0, width - paddingX * 2)
        val horizontal = " ".repeat(math.max(0, paddingX))
        val vertical   = Vector.fill(math.max(0, paddingY))(style(" ".repeat(width)))
        val body       = Ansi.wrapLogicalLinesWithAnsi(contentSnapshot, innerWidth).map { line =>
          val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
          style(Ansi.truncateToWidth(padded, width, ""))
        }
        val rendered   = vertical ++ body ++ vertical
        stateBoundary {
          if revision === token then
            cachedWidth = width
            cachedLines = rendered
        }
        rendered)

  private def invalidateLocked(): Unit =
    revision += 1
    cachedWidth = -1
    cachedLines = Vector.empty
