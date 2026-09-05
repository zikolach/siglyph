package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  CursorPlacement,
  DocumentMarker,
  DocumentMetadata,
  DocumentPosition,
  LayoutBounds,
  LayoutNode,
  PromptStart,
  RenderedFrame,
  TerminalControlPlacement,
  TUIContext
}

import scala.collection.mutable.ArrayBuffer

/**
 * A padded vertical component container.
 *
 * Each child frame is validated against its own rows and the requested inner width before padding
 * or sibling composition. Invalid control or cursor metadata is rejected rather than made valid by
 * outer padding or rows.
 */
final class Box(paddingX: Int = 1, paddingY: Int = 0, style: String => String = identity)
    extends Component,
      ContextualComponent:
  private val stateBoundary     = ComponentStateBoundary()
  private val childrenBuffer    = ArrayBuffer.empty[Component]
  private val horizontalPadding = math.max(0, paddingX)
  private val verticalPadding   = math.max(0, paddingY)
  private var context           = Option.empty[TUIContext]

  def addChild(component: Component): Unit = stateBoundary.transition { effects =>
    val attach = !childrenBuffer.exists(_ eq component)
    childrenBuffer += component
    if attach then
      context.foreach(value => effects.add(() => propagateContext(component, Some(value))))
  }

  def removeChild(component: Component): Boolean = stateBoundary.transition { effects =>
    val index = childrenBuffer.indexOf(component)
    if index >= 0 then
      childrenBuffer.remove(index)
      if context.nonEmpty && !childrenBuffer.exists(_ eq component) then
        effects.add(() => propagateContext(component, None))
      true
    else false
  }

  /** Remove all children and detach their runtime context once per component identity. */
  def clear(): Unit = stateBoundary.transition { effects =>
    if context.nonEmpty then
      distinctChildren.foreach(component =>
        effects.add(() => propagateContext(component, None))
      )
    childrenBuffer.clear()
  }

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if !sameContext(context, value) then
        context = value
        distinctChildren.foreach(component => effects.add(() => propagateContext(component, value)))
    }

  override def invalidate(): Unit =
    stateBoundary(childrenBuffer.toVector).foreach(_.invalidate())

  override def render(width: Int): ComponentRender = renderFrame(width).render

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val children         = stateBoundary(childrenBuffer.toVector)
    val innerWidth       = math.max(0, width - horizontalPadding * 2)
    val horizontal       = " ".repeat(horizontalPadding)
    val vertical         = Vector.fill(verticalPadding)(style(" ".repeat(width)))
    val bodyLines        = Vector.newBuilder[String]
    val controls         = Vector.newBuilder[TerminalControlPlacement]
    val cursorPlacements = Vector.newBuilder[CursorPlacement]
    val documentMarkers  = Vector.newBuilder[DocumentMarker]
    val childNodes       = Vector.newBuilder[LayoutNode]
    var bodyRow          = 0
    children.foreach { child =>
      val childFrame = child.renderFrame(
        innerWidth,
        row + verticalPadding + bodyRow,
        col + horizontalPadding
      )
      val frame      = childFrame.render.validated(innerWidth)
      childNodes += childFrame.layout
      frame.lines.foreach { line =>
        val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
        bodyLines += style(Ansi.truncateToWidth(padded, width, ""))
      }
      frame.controls.foreach(placement =>
        controls += placement.translated(
          rowOffset = verticalPadding + bodyRow,
          columnOffset = horizontalPadding
        )
      )
      frame.cursorPlacements.foreach(placement =>
        cursorPlacements += placement.translated(
          rowOffset = verticalPadding + bodyRow,
          columnOffset = horizontalPadding
        )
      )
      frame.documentMetadata.markers.foreach {
        case PromptStart(position) =>
          documentMarkers += PromptStart(DocumentPosition(
            position.row + verticalPadding + bodyRow,
            position.column + horizontalPadding
          ))
      }
      bodyRow += frame.lines.length
    }
    val render           = ComponentRender(
      vertical ++ bodyLines.result() ++ vertical,
      controls.result(),
      cursorPlacements.result(),
      DocumentMetadata(documentMarkers.result())
    )
    RenderedFrame(
      render,
      LayoutNode(
        this,
        LayoutBounds(row, col, math.max(0, width), render.lines.length),
        childNodes.result()
      )
    )

  private def distinctChildren: Vector[Component] =
    childrenBuffer.indices.flatMap { index =>
      val component = childrenBuffer(index)
      Option.when(
        childrenBuffer.take(index).forall(existing => !(existing eq component))
      )(component)
    }.toVector

  private def propagateContext(component: Component, value: Option[TUIContext]): Unit =
    component match
      case contextual: ContextualComponent => contextual.tuiContext_=(value)
      case _                               => ()

  private def sameContext(left: Option[TUIContext], right: Option[TUIContext]): Boolean =
    (left, right) match
      case (None, None)                      => true
      case (Some(a), Some(b))                => a eq b
      case (None, Some(_)) | (Some(_), None) => false
