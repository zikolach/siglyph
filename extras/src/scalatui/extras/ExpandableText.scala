package scalatui.extras

import scalatui.ansi.Ansi
import scalatui.components.ComponentStateBoundary
import scalatui.core.{Component, ComponentRender}
import scalatui.syntax.Equality.*

/** Styling hooks for [[ExpandableText]]. */
final case class ExpandableTextTheme(
    collapsed: String => String = identity,
    expanded: String => String = identity
)

/**
 * Text component that renders a collapsed text provider or an expanded text provider.
 *
 * The component preserves the shared `Component.render(width)` contract. It wraps text with the
 * same ANSI-aware helpers used by core components, applies optional padding, and invalidates cached
 * output when expansion state, width, or provider output changes.
 */
final class ExpandableText(
    collapsedText: () => String,
    expandedText: () => String,
    initiallyExpanded: Boolean = false,
    paddingX: Int = 0,
    paddingY: Int = 0,
    theme: ExpandableTextTheme = ExpandableTextTheme()
) extends Component
    with Expandable:
  private val stateBoundary = ComponentStateBoundary()
  private var expandedState = initiallyExpanded
  private var cachedWidth   = -1
  private var cachedState   = !initiallyExpanded
  private var cachedContent = ""
  private var cachedLines   = Vector.empty[String]
  private var revision      = 0L

  /** Current expansion state. */
  def expanded: Boolean = stateBoundary(expandedState)

  override def setExpanded(expanded: Boolean): Unit = stateBoundary {
    if expandedState !== expanded then
      expandedState = expanded
      invalidateLocked()
  }

  override def invalidate(): Unit = stateBoundary(invalidateLocked())

  override def render(width: Int): ComponentRender =
    val (expandedSnapshot, token) = stateBoundary((expandedState, revision))
    val content                   = if expandedSnapshot then expandedText() else collapsedText()
    val cached                    = stateBoundary {
      Option.when(
        cachedWidth === width && cachedState === expandedSnapshot && cachedContent === content
      )(cachedLines)
    }
    ComponentRender.text(cached.getOrElse {
      val rendered = renderText(
        content,
        width,
        paddingX,
        paddingY,
        if expandedSnapshot then theme.expanded else theme.collapsed
      )
      stateBoundary {
        if revision === token then
          cachedWidth = width
          cachedState = expandedSnapshot
          cachedContent = content
          cachedLines = rendered
      }
      rendered
    })

  private def invalidateLocked(): Unit =
    revision += 1
    cachedWidth = -1
    cachedLines = Vector.empty

  private def renderText(
      content: String,
      width: Int,
      paddingX: Int,
      paddingY: Int,
      style: String => String
  ): Vector[String] =
    val safeWidth   = math.max(0, width)
    val safePadding = math.max(0, paddingX)
    val innerWidth  = math.max(0, safeWidth - safePadding * 2)
    val horizontal  = " ".repeat(safePadding)
    val vertical    = Vector.fill(math.max(0, paddingY))(
      Ansi.truncateToWidth(style(" ".repeat(safeWidth)), safeWidth, "")
    )
    val body        = Ansi.wrapLogicalLinesWithAnsi(content, innerWidth).map { line =>
      val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
      Ansi.truncateToWidth(style(padded), safeWidth, "")
    }
    vertical ++ body ++ vertical

object ExpandableText:
  /** Create an expandable text component from fixed strings. */
  def apply(
      collapsedText: String,
      expandedText: String,
      initiallyExpanded: Boolean = false,
      paddingX: Int = 0,
      paddingY: Int = 0,
      theme: ExpandableTextTheme = ExpandableTextTheme()
  ): ExpandableText =
    new ExpandableText(
      () => collapsedText,
      () => expandedText,
      initiallyExpanded,
      paddingX,
      paddingY,
      theme
    )
