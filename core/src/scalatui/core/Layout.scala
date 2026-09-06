package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

import scala.collection.mutable.ArrayBuffer

/** Terminal display-cell bounds for a rendered component. */
final case class LayoutBounds(row: Int, col: Int, width: Int, height: Int) derives CanEqual:
  /** Returns true when the terminal cell is inside these bounds. */
  def contains(rowValue: Int, colValue: Int): Boolean =
    rowValue >= row && rowValue < row + height && colValue >= col && colValue < col + width

/** Retained component layout node for coordinate-aware input routing. */
final case class LayoutNode(
    component: Component,
    bounds: LayoutBounds,
    children: Vector[LayoutNode] = Vector.empty
)

/** Typed component output plus its retained layout tree. */
final case class RenderedFrame(render: ComponentRender, layout: LayoutNode):
  def lines: Vector[String] = render.lines

object RenderedFrame:
  /** Render a component through the existing typed render contract as one leaf node. */
  def leaf(component: Component, width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val render = component.render(width)
    RenderedFrame(
      render,
      LayoutNode(component, LayoutBounds(row, col, math.max(0, width), render.lines.length))
    )

  private[core] def widthForLines(lines: Vector[String], fallback: Int): Int =
    math.max(0, math.max(fallback, lines.map(Ansi.visibleWidth).maxOption.getOrElse(0)))

/**
 * Immutable component rectangle in zero-based viewport display cells on JVM and Scala Native. Width
 * and height are non-negative. The owning layout frame fixes the terminal bounds.
 */
final case class ViewportRect(row: Int, col: Int, width: Int, height: Int) derives CanEqual:
  require(width >= 0, "Viewport rectangle width must be non-negative")
  require(height >= 0, "Viewport rectangle height must be non-negative")

  /** Return whether this rectangle contains one display cell. */
  def contains(rowValue: Int, colValue: Int): Boolean =
    rowValue >= row && rowValue < row + height && colValue >= col && colValue < col + width

/**
 * Immutable effective clipping rectangle in zero-based viewport display cells.
 *
 * Layout intersects parent, stack, scroll, overlay, and terminal clips before painting text or
 * typed controls. A clip grants no authority to rewrite a partially visible terminal control.
 */
final case class ClipRect(row: Int, col: Int, width: Int, height: Int) derives CanEqual:
  require(width >= 0, "Clip rectangle width must be non-negative")
  require(height >= 0, "Clip rectangle height must be non-negative")

  /** Intersect this clipping rectangle with a component rectangle. */
  def intersect(rect: ViewportRect): ClipRect =
    val nextRow    = math.max(row, rect.row)
    val nextCol    = math.max(col, rect.col)
    val nextBottom = math.min(row + height, rect.row + rect.height)
    val nextRight  = math.min(col + width, rect.col + rect.width)
    ClipRect(
      nextRow,
      nextCol,
      math.max(0, nextRight - nextCol),
      math.max(0, nextBottom - nextRow)
    )

  /** Return whether this clip contains one display cell. */
  def contains(rowValue: Int, colValue: Int): Boolean =
    rowValue >= row && rowValue < row + height && colValue >= col && colValue < col + width

/** Width and height available while evaluating responsive layout visibility. */
final case class LayoutViewport(width: Int, height: Int) derives CanEqual:
  require(width >= 0, "Layout viewport width must be non-negative")
  require(height >= 0, "Layout viewport height must be non-negative")

/** Main-axis direction for a viewport stack. */
enum StackAxis derives CanEqual:
  case Vertical, Horizontal

/** Cross-axis alignment for children in a viewport stack. */
enum StackAlignment derives CanEqual:
  case Stretch, Start, Center, End

/**
 * Immutable stack child intent consumed by the shared viewport layout engine.
 *
 * Basis, minimum, and maximum use main-axis display cells. Grow and shrink are non-negative
 * weights. The visibility callback receives terminal viewport dimensions and runs during layout
 * without a TUI lifecycle or terminal-output lock. Applications must keep it side-effect free.
 */
final case class ViewportStackEntry(
    component: Component,
    basis: Option[Int] = None,
    grow: Int = 0,
    shrink: Int = 1,
    minSize: Int = 0,
    maxSize: Option[Int] = None,
    visible: LayoutViewport => Boolean = _ => true
):
  require(basis.forall(_ >= 0), "Stack basis must be non-negative")
  require(grow >= 0, "Stack grow weight must be non-negative")
  require(shrink >= 0, "Stack shrink weight must be non-negative")
  require(minSize >= 0, "Stack minimum size must be non-negative")
  require(maxSize.forall(_ >= minSize), "Stack maximum size must be at least its minimum")

/** Immutable layout intent returned by a [[ViewportLayoutProvider]]. */
sealed trait ViewportLayout

/** Immutable stack intent exposed by an optional [[ViewportLayoutProvider]]. */
final case class ViewportStackLayout(
    axis: StackAxis,
    entries: Vector[ViewportStackEntry],
    gap: Int = 0,
    alignment: StackAlignment = StackAlignment.Stretch
) extends ViewportLayout:
  require(gap >= 0, "Stack gap must be non-negative")

/** Immutable one-child vertical scroll intent exposed by [[ViewportScrollProvider]]. */
final case class ViewportScrollLayout(child: Component) extends ViewportLayout

/**
 * Optional row-range contract for large width-constrained documents.
 *
 * [[contentExtent]] returns the complete document height. [[renderRange]] returns at most
 * `rowCount` rows beginning at `startRow`, with all typed metadata relative to the returned range.
 * The viewport renderer uses complete [[Component.render]] and clipping for components that do not
 * implement this contract. Fullscreen search and selection may request sequential bounded ranges
 * from row zero and stop once their documented retention limits are reached.
 */
trait ViewportRangeRenderer:
  self: Component =>

  /** Return the complete document height for `width`. */
  def contentExtent(width: Int): Int

  /** Render a bounded row range with range-relative typed metadata. */
  def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender

/** One text decoration painted over a committed scroll viewport. */
final case class ViewportDecoration(row: Int, column: Int, text: String, width: Int):
  require(row >= 0, "Viewport decoration row must be non-negative")
  require(column >= 0, "Viewport decoration column must be non-negative")
  require(width >= 0, "Viewport decoration width must be non-negative")

/** Optional complete metadata contract for a range-rendered document. */
trait ViewportDocumentMetadataProvider:
  self: Component =>

  /** Return complete typed document metadata for the current content width. */
  def viewportDocumentMetadata(width: Int): DocumentMetadata

/** Layout-provider state boundary used by a one-child vertical scroll viewport. */
trait ViewportScrollProvider:
  self: Component =>

  /** Return the child width after reserving any always-visible viewport decoration. */
  private[scalatui] def viewportContentWidth(width: Int): Int = width

  /** Commit measured geometry, clamp scroll state, and return the offset used for this frame. */
  private[scalatui] def commitViewportGeometry(
      contentExtent: Int,
      viewportExtent: Int,
      viewportWidth: Int,
      layoutViewport: LayoutViewport
  ): Int

  /** Capture the content revision before rendering rows for a conditional commit. */
  private[scalatui] def viewportContentRevision: Long = 0L

  /** Commit rendered document rows and their zero-based document start for plain-text mapping. */
  private[scalatui] def commitViewportText(
      rows: Vector[String],
      startRow: Int,
      complete: Boolean,
      child: Component,
      revision: Long
  ): Unit = ()

  /** Commit complete typed metadata for semantic document navigation. */
  private[scalatui] def commitViewportDocumentMetadata(metadata: DocumentMetadata): Unit = ()

  /** Return text decorations against the latest committed local viewport geometry. */
  private[scalatui] def viewportDecorations: Vector[ViewportDecoration] = Vector.empty

/**
 * Optional component contract for height-aware viewport composition on JVM and Scala Native.
 *
 * The component owns children named by its immutable layout snapshot. The fullscreen renderer owns
 * terminal-height allocation, clipping, and retained geometry. Direct [[Component.render]] remains
 * the complete unbounded width-only fallback.
 */
trait ViewportLayoutProvider:
  self: Component =>

  /** Return an immutable snapshot of this component's current viewport layout intent. */
  def viewportLayout: ViewportLayout

  /** Optional identity that owns scrolling for this layout subtree. */
  def viewportScrollOwner: Option[Component] = None

/**
 * One immutable retained component box from a height-aware layout frame.
 *
 * [[rect]] is the allocated rectangle. [[clip]] is the effective visible rectangle. Parent and
 * child identities belong to the committed frame used for mouse routing and are not mutable
 * component ownership handles.
 */
final case class LayoutBox(
    component: Component,
    rect: ViewportRect,
    clip: ClipRect,
    parent: Option[Component],
    children: Vector[LayoutBox],
    scrollOwner: Option[Component]
):
  /** Convert this viewport box to the established retained input-routing tree. */
  def toLayoutNode: LayoutNode = LayoutNode(
    component,
    LayoutBounds(clip.row, clip.col, clip.width, clip.height),
    children.map(_.toLayoutNode)
  )

/**
 * Typed viewport output and immutable geometry for one requested width and height.
 *
 * The renderer paints exactly [[height]] rows. Text, cursor candidates, document metadata, mouse
 * bounds, and complete typed controls use the same committed clipping geometry.
 */
final case class LayoutFrame(
    render: ComponentRender,
    root: LayoutBox,
    width: Int,
    height: Int,
    documentMetadata: DocumentMetadata
):
  /** Painted viewport rows. */
  def lines: Vector[String] = render.lines

/**
 * Shared JVM and Scala Native height-aware layout engine.
 *
 * The engine owns deterministic stack allocation and frame-local render reuse. It does not mutate
 * child content, switch a running renderer, schedule component work, or virtualize an ordinary
 * width-only component. Large documents can opt into [[ViewportRangeRenderer]].
 */
object ViewportLayoutEngine:
  /** Resolve and paint `component` into an immutable viewport frame. */
  def layout(component: Component, width: Int, height: Int): LayoutFrame =
    val safeWidth  = math.max(0, width)
    val safeHeight = math.max(0, height)
    val context    = LayoutContext(
      LayoutViewport(safeWidth, safeHeight),
      includeHidden = false,
      commitViewport = true
    )
    val clip       = ClipRect(0, 0, safeWidth, safeHeight)
    val resolved   = layoutComponent(
      context,
      component,
      row = 0,
      col = 0,
      safeWidth,
      Some(safeHeight),
      clip,
      None,
      None
    )
    paintFrame(resolved, safeWidth, safeHeight)

  /** Render a layout provider as a complete width-constrained document without height clipping. */
  def renderUnbounded(component: Component, width: Int): LayoutFrame =
    val safeWidth = math.max(0, width)
    val context   = LayoutContext(
      LayoutViewport(safeWidth, Int.MaxValue),
      includeHidden = true,
      commitViewport = false
    )
    val rootClip  = ClipRect(0, 0, safeWidth, Int.MaxValue)
    val resolved  = layoutComponent(
      context,
      component,
      row = 0,
      col = 0,
      safeWidth,
      None,
      rootClip,
      None,
      None
    )
    paintFrame(resolved, safeWidth, resolved.box.rect.height)

  /** Render a complete document while applying visibility against the current terminal viewport. */
  private[scalatui] def renderResponsiveUnbounded(
      component: Component,
      width: Int,
      viewport: LayoutViewport
  ): LayoutFrame =
    val safeWidth = math.max(0, width)
    val context   = LayoutContext(viewport, includeHidden = false, commitViewport = false)
    val rootClip  = ClipRect(0, 0, safeWidth, Int.MaxValue)
    val resolved  = layoutComponent(
      context,
      component,
      row = 0,
      col = 0,
      safeWidth,
      None,
      rootClip,
      None,
      None
    )
    paintFrame(resolved, safeWidth, resolved.box.rect.height)

  private final class LayoutContext(
      val viewport: LayoutViewport,
      val includeHidden: Boolean,
      val commitViewport: Boolean
  ):
    private val renders =
      ArrayBuffer.empty[(Component, Int, Option[ComponentRenderOrigin], ComponentRender)]

    def render(
        component: Component,
        width: Int,
        origin: Option[ComponentRenderOrigin] = None
    ): ComponentRender =
      val effectiveOrigin = component match
        case _: RenderOriginAware => origin
        case _                    => None
      renders.collectFirst {
        case (cachedComponent, cachedWidth, cachedOrigin, frame)
            if (cachedComponent eq component) && cachedWidth === width &&
              cachedOrigin === effectiveOrigin => frame
      }.getOrElse {
        component match
          case aware: RenderOriginAware =>
            aware.renderOrigin_=(origin)
          case _                        => ()
        RuntimeCounterScope.recordComponentRender()
        val frame = component.render(width).validated(width)
        renders += ((component, width, effectiveOrigin, frame))
        frame
      }

    def setOrigin(component: Component, origin: ComponentRenderOrigin): Unit = component match
      case aware: RenderOriginAware => aware.renderOrigin_=(Some(origin))
      case _                        => ()

  private object LayoutContext:
    def apply(
        viewport: LayoutViewport,
        includeHidden: Boolean,
        commitViewport: Boolean
    ): LayoutContext =
      new LayoutContext(viewport, includeHidden, commitViewport)

  private final case class ResolvedBox(
      box: LayoutBox,
      source: Option[ComponentRender],
      children: Vector[ResolvedBox] = Vector.empty,
      sourceRowOffset: Int = 0,
      documentRowOffset: Int = 0
  )

  private def layoutComponent(
      context: LayoutContext,
      component: Component,
      row: Int,
      col: Int,
      width: Int,
      height: Option[Int],
      parentClip: ClipRect,
      parent: Option[Component],
      inheritedScrollOwner: Option[Component]
  ): ResolvedBox = component match
    case provider: ViewportLayoutProvider =>
      val scrollOwner = provider.viewportScrollOwner.orElse(inheritedScrollOwner)
      provider.viewportLayout match
        case layout: ViewportStackLayout  =>
          layoutStack(
            context,
            component,
            layout,
            row,
            col,
            width,
            height,
            parentClip,
            parent,
            scrollOwner
          )
        case layout: ViewportScrollLayout =>
          layoutScroll(
            context,
            component,
            provider,
            layout,
            row,
            col,
            width,
            height,
            parentClip,
            parent,
            scrollOwner
          )
    case _                                =>
      val frame           = context.render(component, width, Some(ComponentRenderOrigin(row, col)))
      val allocatedHeight = height.fold(frame.lines.length)(value => math.max(0, value))
      val rect            = ViewportRect(row, col, math.max(0, width), allocatedHeight)
      ResolvedBox(
        LayoutBox(
          component,
          rect,
          parentClip.intersect(rect),
          parent,
          Vector.empty,
          inheritedScrollOwner
        ),
        Some(frame)
      )

  private def layoutScroll(
      context: LayoutContext,
      component: Component,
      provider: ViewportLayoutProvider,
      layout: ViewportScrollLayout,
      row: Int,
      col: Int,
      width: Int,
      height: Option[Int],
      parentClip: ClipRect,
      parent: Option[Component],
      scrollOwner: Option[Component]
  ): ResolvedBox =
    val scrollProvider  = provider match
      case scroll: ViewportScrollProvider => Some(scroll)
      case _                              => None
    val contentWidth    = scrollProvider.fold(width)(_.viewportContentWidth(width))
    val contentHeight   = intrinsicHeight(context, layout.child, contentWidth)
    val viewportHeight  = height.getOrElse(contentHeight)
    val rect            = ViewportRect(row, col, width, math.max(0, viewportHeight))
    val clip            = parentClip.intersect(rect)
    val offset          =
      if !context.commitViewport then 0
      else
        scrollProvider.fold(0)(
          _.commitViewportGeometry(contentHeight, viewportHeight, width, context.viewport)
        )
    val contentRevision = scrollProvider.map(_.viewportContentRevision).getOrElse(0L)
    val child           = layout.child match
      case ranged: ViewportRangeRenderer if context.commitViewport =>
        context.setOrigin(layout.child, ComponentRenderOrigin(row, col))
        RuntimeCounterScope.recordComponentRender()
        val range     = ranged.renderRange(contentWidth, offset, viewportHeight).validated(
          contentWidth
        )
        require(
          range.lines.length <= viewportHeight,
          "Viewport range render returned more rows than requested"
        )
        val childRect = ViewportRect(row, col, contentWidth, contentHeight)
        ResolvedBox(
          LayoutBox(
            layout.child,
            childRect,
            clip.intersect(childRect),
            Some(component),
            Vector.empty,
            scrollOwner
          ),
          Some(range),
          sourceRowOffset = 0,
          documentRowOffset = offset
        )
      case _                                                       =>
        val resolved = layoutComponent(
          context,
          layout.child,
          row - offset,
          col,
          contentWidth,
          Some(contentHeight),
          clip,
          Some(component),
          scrollOwner
        )
        withDocumentRowOffset(resolved, offset)
    if context.commitViewport then
      val metadata = layout.child match
        case source: ViewportDocumentMetadataProvider =>
          source.viewportDocumentMetadata(contentWidth)
        case _                                        =>
          documentMetadata(child).translated(rowOffset = -row, columnOffset = -col)
      require(
        metadata.markers.forall(marker =>
          marker.position.row < contentHeight && marker.position.column <= contentWidth
        ),
        "Viewport document metadata is outside the scroll document"
      )
      scrollProvider.foreach { scroll =>
        val complete = !layout.child.isInstanceOf[ViewportRangeRenderer]
        val startRow = if complete then 0 else offset
        val rowCount =
          if complete then contentHeight
          else math.min(viewportHeight, math.max(0, contentHeight - offset))
        scroll.commitViewportDocumentMetadata(metadata)
        scroll.commitViewportText(
          renderedDocumentRows(child, row, col, contentWidth, startRow, rowCount),
          startRow,
          complete,
          layout.child,
          contentRevision
        )
      }
    ResolvedBox(
      LayoutBox(component, rect, clip, parent, Vector(child.box), scrollOwner),
      None,
      Vector(child)
    )

  private def renderedDocumentRows(
      resolved: ResolvedBox,
      originRow: Int,
      originCol: Int,
      width: Int,
      startRow: Int,
      rowCount: Int
  ): Vector[String] =
    val rows                           = Array.fill(math.max(0, rowCount))("")
    def visit(node: ResolvedBox): Unit =
      node.source.foreach { frame =>
        frame.lines.zipWithIndex.foreach { case (line, sourceRow) =>
          val targetRow =
            sourceRow + node.box.rect.row + node.documentRowOffset - originRow - startRow
          val targetCol = node.box.rect.col - originCol
          if targetRow >= 0 && targetRow < rows.length && targetCol < width then
            val plain = Ansi.selectionText(line)
            val slice = Ansi.sliceByColumns(plain, 0, math.max(0, width - math.max(0, targetCol)))
            if slice.text.nonEmpty then
              rows(targetRow) = OverlayRenderer.compositeLine(
                rows(targetRow),
                slice.text,
                math.max(0, targetCol),
                slice.width,
                width
              )
        }
      }
      node.children.foreach(visit)
    visit(resolved)
    rows.toVector

  private def documentMetadata(resolved: ResolvedBox): DocumentMetadata =
    val own = resolved.source.toVector.flatMap(_.documentMetadata.markers).map {
      case PromptStart(position) =>
        PromptStart(DocumentPosition(
          position.row + resolved.box.rect.row + resolved.documentRowOffset,
          position.column + resolved.box.rect.col
        ))
    }
    DocumentMetadata(own ++ resolved.children.flatMap(documentMetadata(_).markers))

  private def withDocumentRowOffset(resolved: ResolvedBox, offset: Int): ResolvedBox =
    resolved.copy(
      documentRowOffset = resolved.documentRowOffset + offset,
      children = resolved.children.map(child => withDocumentRowOffset(child, offset))
    )

  private def layoutStack(
      context: LayoutContext,
      component: Component,
      layout: ViewportStackLayout,
      row: Int,
      col: Int,
      width: Int,
      height: Option[Int],
      parentClip: ClipRect,
      parent: Option[Component],
      scrollOwner: Option[Component]
  ): ResolvedBox =
    val entries = layout.entries.filter(entry =>
      context.includeHidden || entry.visible(context.viewport)
    )
    layout.axis match
      case StackAxis.Vertical   =>
        val intrinsic = entries.map(entry => intrinsicHeight(context, entry.component, width))
        val sizes     =
          if context.includeHidden then intrinsic
          else allocateStackSizes(entries, intrinsic, height, layout.gap)
        val natural   = sizes.sum + totalGap(entries.length, layout.gap)
        val boxHeight = height.getOrElse(natural)
        val rect      = ViewportRect(row, col, width, math.max(0, boxHeight))
        val clip      = parentClip.intersect(rect)
        var childRow  = row
        val children  = entries.zip(sizes).map { case (entry, childHeight) =>
          val intrinsicWidth = intrinsicDisplayWidth(context, entry.component, width)
          val childWidth     = crossSize(width, intrinsicWidth, layout.alignment)
          val childCol       = col + crossOffset(width, childWidth, layout.alignment)
          val child          = layoutComponent(
            context,
            entry.component,
            childRow,
            childCol,
            childWidth,
            Some(childHeight),
            clip,
            Some(component),
            scrollOwner
          )
          childRow += childHeight + layout.gap
          child
        }
        ResolvedBox(
          LayoutBox(component, rect, clip, parent, children.map(_.box), scrollOwner),
          None,
          children
        )
      case StackAxis.Horizontal =>
        val intrinsic = entries.map(entry =>
          entry.basis.getOrElse(intrinsicDisplayWidth(context, entry.component, width))
        )
        val sizes     = allocateStackSizes(entries, intrinsic, Some(width), layout.gap)
        val heights   = entries.zip(sizes).map { case (entry, childWidth) =>
          intrinsicHeight(context, entry.component, childWidth)
        }
        val natural   = heights.maxOption.getOrElse(0)
        val boxHeight = height.getOrElse(natural)
        val rect      = ViewportRect(row, col, width, math.max(0, boxHeight))
        val clip      = parentClip.intersect(rect)
        var childCol  = col
        val children  = entries.indices.map { index =>
          val childHeight = crossSize(boxHeight, heights(index), layout.alignment)
          val childRow    = row + crossOffset(boxHeight, childHeight, layout.alignment)
          val child       = layoutComponent(
            context,
            entries(index).component,
            childRow,
            childCol,
            sizes(index),
            Some(childHeight),
            clip,
            Some(component),
            scrollOwner
          )
          childCol += sizes(index) + layout.gap
          child
        }.toVector
        ResolvedBox(
          LayoutBox(component, rect, clip, parent, children.map(_.box), scrollOwner),
          None,
          children
        )

  private def intrinsicHeight(context: LayoutContext, component: Component, width: Int): Int =
    component match
      case provider: ViewportLayoutProvider =>
        provider.viewportLayout match
          case layout: ViewportStackLayout  =>
            val entries = layout.entries.filter(entry =>
              context.includeHidden || entry.visible(context.viewport)
            )
            layout.axis match
              case StackAxis.Vertical   =>
                entries.map(entry => intrinsicHeight(context, entry.component, width)).sum +
                  totalGap(entries.length, layout.gap)
              case StackAxis.Horizontal =>
                val widths = allocateStackSizes(
                  entries,
                  entries.map(entry =>
                    entry.basis.getOrElse(intrinsicDisplayWidth(context, entry.component, width))
                  ),
                  Some(width),
                  layout.gap
                )
                entries.zip(widths).map { case (entry, childWidth) =>
                  intrinsicHeight(context, entry.component, childWidth)
                }.maxOption.getOrElse(0)
          case layout: ViewportScrollLayout => intrinsicHeight(context, layout.child, width)
      case ranged: ViewportRangeRenderer    => math.max(0, ranged.contentExtent(width))
      case _                                => context.render(component, width).lines.length

  private def intrinsicDisplayWidth(
      context: LayoutContext,
      component: Component,
      availableWidth: Int
  ): Int = component match
    case provider: ViewportLayoutProvider =>
      provider.viewportLayout match
        case layout: ViewportStackLayout  =>
          val entries = layout.entries.filter(entry =>
            context.includeHidden || entry.visible(context.viewport)
          )
          layout.axis match
            case StackAxis.Vertical   =>
              entries.map(entry => intrinsicDisplayWidth(context, entry.component, availableWidth))
                .maxOption.getOrElse(0)
            case StackAxis.Horizontal =>
              val widths = entries.map { entry =>
                clampSize(
                  entry.basis.getOrElse(intrinsicDisplayWidth(
                    context,
                    entry.component,
                    availableWidth
                  )),
                  entry
                )
              }
              math.min(availableWidth, widths.sum + totalGap(entries.length, layout.gap))
        case layout: ViewportScrollLayout =>
          intrinsicDisplayWidth(context, layout.child, availableWidth)
    case ranged: ViewportRangeRenderer    => availableWidth
    case _                                =>
      context.render(component, availableWidth).lines.map(Ansi.visibleWidth).maxOption.getOrElse(0)

  private def allocateStackSizes(
      entries: Vector[ViewportStackEntry],
      intrinsicSizes: Vector[Int],
      availableSize: Option[Int],
      gap: Int
  ): Vector[Int] =
    val sizes = entries.indices.map(index =>
      clampSize(entries(index).basis.getOrElse(intrinsicSizes(index)), entries(index))
    ).toArray
    availableSize.foreach { available =>
      val content = math.max(0, available - totalGap(entries.length, gap))
      val total   = sizes.sum
      if total < content then distribute(sizes, entries, content - total, grow = true)
      else if total > content then distribute(sizes, entries, total - content, grow = false)
    }
    sizes.toVector

  private def distribute(
      sizes: Array[Int],
      entries: Vector[ViewportStackEntry],
      amount: Int,
      grow: Boolean
  ): Unit =
    var remaining = amount
    while remaining > 0 do
      val candidates = entries.indices.filter { index =>
        val entry = entries(index)
        if grow then entry.grow > 0 && sizes(index) < entry.maxSize.getOrElse(Int.MaxValue)
        else entry.shrink > 0 && sizes(index) > entry.minSize
      }
      if candidates.isEmpty then remaining = 0
      else
        val weights     = candidates.map { index =>
          if grow then entries(index).grow.toLong
          else entries(index).shrink.toLong * math.max(1, sizes(index)).toLong
        }
        val totalWeight = weights.sum
        val roundStart  = remaining
        var changed     = 0
        candidates.zip(weights).foreach { case (index, weight) =>
          val entry    = entries(index)
          val capacity =
            if grow then entry.maxSize.getOrElse(Int.MaxValue) - sizes(index)
            else sizes(index) - entry.minSize
          val share    = math.min(capacity, ((roundStart.toLong * weight) / totalWeight).toInt)
          if share > 0 then
            sizes(index) += (if grow then share else -share)
            remaining -= share
            changed += share
        }
        candidates.foreach { index =>
          if remaining > 0 then
            val entry    = entries(index)
            val capacity =
              if grow then entry.maxSize.getOrElse(Int.MaxValue) - sizes(index)
              else sizes(index) - entry.minSize
            if capacity > 0 then
              sizes(index) += (if grow then 1 else -1)
              remaining -= 1
              changed += 1
        }
        if changed === 0 then remaining = 0

  private def clampSize(value: Int, entry: ViewportStackEntry): Int =
    math.max(entry.minSize, math.min(entry.maxSize.getOrElse(Int.MaxValue), math.max(0, value)))

  private def totalGap(count: Int, gap: Int): Int = math.max(0, count - 1) * gap

  private def crossSize(available: Int, intrinsic: Int, alignment: StackAlignment): Int =
    alignment match
      case StackAlignment.Stretch => math.max(0, available)
      case _                      => math.max(0, math.min(available, intrinsic))

  private def crossOffset(available: Int, size: Int, alignment: StackAlignment): Int =
    alignment match
      case StackAlignment.Center => math.max(0, (available - size) / 2)
      case StackAlignment.End    => math.max(0, available - size)
      case _                     => 0

  private def paintFrame(root: ResolvedBox, width: Int, height: Int): LayoutFrame =
    val lines       = Array.fill(math.max(0, height))("")
    val controls    = Vector.newBuilder[TerminalControlPlacement]
    val cursors     = Vector.newBuilder[CursorPlacement]
    val allMarkers  = Vector.newBuilder[DocumentMarker]
    val shownMarker = Vector.newBuilder[DocumentMarker]

    def visit(resolved: ResolvedBox): Unit =
      resolved.source.foreach { frame =>
        val box          = resolved.box
        val firstRow     = math.max(0, math.max(box.rect.row, box.clip.row))
        val lastRow      =
          math.min(height, math.min(box.rect.row + box.rect.height, box.clip.row + box.clip.height))
        val visibleLeft  = math.max(0, math.max(box.rect.col, box.clip.col))
        val visibleRight = math.min(
          width,
          math.min(box.rect.col + box.rect.width, box.clip.col + box.clip.width)
        )
        var targetRow    = firstRow
        while targetRow < lastRow do
          val sourceRow = targetRow - box.rect.row + resolved.sourceRowOffset
          frame.lines.lift(sourceRow).foreach { line =>
            val sourceStart = math.max(0, visibleLeft - box.rect.col)
            val span        = math.max(0, visibleRight - visibleLeft)
            val prefixWidth = Ansi.sliceByColumns(line, 0, sourceStart).width
            val leadingGap  = math.max(0, sourceStart - prefixWidth)
            val slice       = Ansi.sliceByColumns(line, sourceStart, math.max(0, span - leadingGap))
            if slice.text.nonEmpty then
              lines(targetRow) = OverlayRenderer.compositeLine(
                lines(targetRow),
                slice.text,
                visibleLeft + leadingGap,
                slice.width,
                width
              )
          }
          targetRow += 1

        val paintRowOffset = box.rect.row - resolved.sourceRowOffset
        frame.controls.foreach { placement =>
          val translatedRow = placement.row + paintRowOffset
          if translatedRow >= 0 then
            val translated   = TerminalControlPlacement(
              translatedRow,
              placement.column + box.rect.col,
              placement.control
            )
            val viewportClip = ClipRect(0, 0, width, height).intersect(
              ViewportRect(box.clip.row, box.clip.col, box.clip.width, box.clip.height)
            )
            TypedControlClipping.clipPlacement(translated, viewportClip).foreach(controls += _)
        }
        frame.cursorPlacements.foreach { placement =>
          val translatedRow = placement.row + paintRowOffset
          val translatedCol = placement.column + box.rect.col
          if translatedRow >= 0 && translatedCol >= 0 &&
            pointInside(translatedRow, translatedCol, box.clip, width, height)
          then cursors += CursorPlacement(translatedRow, translatedCol)
        }
        frame.documentMetadata.markers.foreach {
          case PromptStart(position) =>
            val documentPosition = DocumentPosition(
              position.row + box.rect.row + resolved.documentRowOffset,
              position.column + box.rect.col
            )
            allMarkers += PromptStart(documentPosition)
            val paintedRow       = position.row + paintRowOffset
            val paintedCol       = position.column + box.rect.col
            if paintedRow >= 0 && paintedCol >= 0 &&
              markerInside(DocumentPosition(paintedRow, paintedCol), box.clip, width, height)
            then shownMarker += PromptStart(DocumentPosition(paintedRow, paintedCol))
        }
      }
      resolved.children.foreach(visit)
      resolved.box.component match
        case scroll: ViewportScrollProvider =>
          scroll.viewportDecorations.foreach { decoration =>
            val targetRow = resolved.box.rect.row + decoration.row
            val targetCol = resolved.box.rect.col + decoration.column
            if targetRow >= 0 && targetRow < height &&
              resolved.box.clip.contains(targetRow, targetCol)
            then
              lines(targetRow) = OverlayRenderer.compositeLine(
                lines(targetRow),
                decoration.text,
                targetCol,
                decoration.width,
                width
              )
          }
        case _                              => ()

    visit(root)
    val metadata = DocumentMetadata(allMarkers.result())
    val render   = ComponentRender(
      lines.toVector.map(line => Ansi.truncateToWidth(line, width, "")),
      controls.result(),
      cursors.result(),
      DocumentMetadata(shownMarker.result())
    ).validated(width)
    LayoutFrame(render, root.box, width, height, metadata)

  private def pointInside(row: Int, col: Int, clip: ClipRect, width: Int, height: Int): Boolean =
    row >= 0 && row < height && col >= 0 && col < width && clip.contains(row, col)

  private def markerInside(
      position: DocumentPosition,
      clip: ClipRect,
      width: Int,
      height: Int
  ): Boolean =
    position.row >= 0 && position.row < height && position.column >= 0 &&
      position.column <= width && position.row >= clip.row && position.row < clip.row + clip.height &&
      position.column >= clip.col && position.column <= clip.col + clip.width
