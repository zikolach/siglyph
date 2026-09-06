package scalatui.extras

import scalatui.ansi.Ansi
import scalatui.components.ComponentStateBoundary
import scalatui.core.{Component, ComponentRender}
import scalatui.syntax.Equality.*

/** Controls when an [[ExpandableSection]] hint line is rendered. */
enum ExpansionHintVisibility derives CanEqual:
  case Always, CollapsedOnly, ExpandedOnly

/** Styling hooks for [[ExpandableSection]]. */
final case class ExpandableSectionTheme(
    title: String => String = identity,
    collapsedBody: String => String = identity,
    expandedBody: String => String = identity,
    hint: String => String = identity
)

/**
 * Section component that renders a title plus collapsed or expanded body text.
 *
 * The section is a small reusable helper above core primitives. It owns only local expansion state,
 * exposes no keybindings, and keeps output width-safe through ANSI-aware wrapping and truncation.
 */
final class ExpandableSection(
    title: () => String,
    collapsedBody: () => String,
    expandedBody: () => String,
    hintText: Option[() => String] = None,
    hintVisibility: ExpansionHintVisibility = ExpansionHintVisibility.CollapsedOnly,
    initiallyExpanded: Boolean = false,
    paddingX: Int = 0,
    paddingY: Int = 0,
    theme: ExpandableSectionTheme = ExpandableSectionTheme()
) extends Component
    with Expandable:
  private val stateBoundary = ComponentStateBoundary()
  private var expandedState = initiallyExpanded
  private var cachedWidth   = -1
  private var cachedState   = !initiallyExpanded
  private var cachedContent = SectionContent("", "", None)
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
    val content                   = SectionContent(
      title(),
      if expandedSnapshot then expandedBody() else collapsedBody(),
      visibleHint(expandedSnapshot).map(provider => provider())
    )
    val cached                    = stateBoundary {
      Option.when(
        cachedWidth === width && cachedState === expandedSnapshot && cachedContent === content
      )(cachedLines)
    }
    ComponentRender.text(cached.getOrElse {
      val rendered = renderSection(content, expandedSnapshot, width)
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

  private def visibleHint(expandedSnapshot: Boolean): Option[() => String] =
    hintText.filter(_ =>
      hintVisibility match
        case ExpansionHintVisibility.Always        => true
        case ExpansionHintVisibility.CollapsedOnly => !expandedSnapshot
        case ExpansionHintVisibility.ExpandedOnly  => expandedSnapshot
    )

  private def renderSection(
      content: SectionContent,
      expandedSnapshot: Boolean,
      width: Int
  ): Vector[String] =
    val safeWidth   = math.max(0, width)
    val safePadding = math.max(0, paddingX)
    val innerWidth  = math.max(0, safeWidth - safePadding * 2)
    val vertical    = Vector.fill(math.max(0, paddingY))(" ".repeat(safeWidth))
    val titleLines  = renderBlock(content.title, innerWidth, safePadding, safeWidth, theme.title)
    val bodyStyle   = if expandedSnapshot then theme.expandedBody else theme.collapsedBody
    val bodyLines   = renderBlock(content.body, innerWidth, safePadding, safeWidth, bodyStyle)
    val hintLines   =
      content.hint.toVector.flatMap(renderBlock(_, innerWidth, safePadding, safeWidth, theme.hint))
    vertical ++ titleLines ++ bodyLines ++ hintLines ++ vertical

  private def renderBlock(
      content: String,
      innerWidth: Int,
      paddingX: Int,
      width: Int,
      style: String => String
  ): Vector[String] =
    val horizontal = " ".repeat(paddingX)
    Ansi.wrapLogicalLinesWithAnsi(content, innerWidth).map { line =>
      val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
      Ansi.truncateToWidth(style(padded), width, "")
    }

private final case class SectionContent(title: String, body: String, hint: Option[String])

object ExpandableSection:
  /** Create an expandable section from fixed strings. */
  def apply(
      title: String,
      collapsedBody: String,
      expandedBody: String,
      hintText: Option[String] = None,
      hintVisibility: ExpansionHintVisibility = ExpansionHintVisibility.CollapsedOnly,
      initiallyExpanded: Boolean = false,
      paddingX: Int = 0,
      paddingY: Int = 0,
      theme: ExpandableSectionTheme = ExpandableSectionTheme()
  ): ExpandableSection =
    new ExpandableSection(
      () => title,
      () => collapsedBody,
      () => expandedBody,
      hintText.map(text => () => text),
      hintVisibility,
      initiallyExpanded,
      paddingX,
      paddingY,
      theme
    )
