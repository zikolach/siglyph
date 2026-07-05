package scalatui.extras

import scalatui.ansi.Ansi
import scalatui.core.Component
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
  private var expandedState = initiallyExpanded
  private var cachedWidth   = -1
  private var cachedState   = !initiallyExpanded
  private var cachedContent = ""
  private var cachedLines   = Vector.empty[String]

  /** Current expansion state. */
  def expanded: Boolean = expandedState

  override def setExpanded(expanded: Boolean): Unit =
    if expandedState !== expanded then
      expandedState = expanded
      invalidate()

  override def invalidate(): Unit =
    cachedWidth = -1
    cachedLines = Vector.empty

  override def render(width: Int): Vector[String] =
    val content = if expandedState then expandedText() else collapsedText()
    if cachedWidth === width && cachedState === expandedState && cachedContent === content then
      cachedLines
    else
      cachedWidth = width
      cachedState = expandedState
      cachedContent = content
      cachedLines = renderText(
        content,
        width,
        paddingX,
        paddingY,
        if expandedState then theme.expanded else theme.collapsed
      )
      cachedLines

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
    val vertical    = Vector.fill(math.max(0, paddingY))(style(" ".repeat(safeWidth)))
    val body        = Ansi.wrapTextWithAnsi(content, innerWidth).map { line =>
      val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
      style(Ansi.truncateToWidth(padded, safeWidth, ""))
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
