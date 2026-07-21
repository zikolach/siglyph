package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  CursorPlacement,
  LayoutBounds,
  LayoutNode,
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
  private val childrenBuffer    = ArrayBuffer.empty[Component]
  private val horizontalPadding = math.max(0, paddingX)
  private val verticalPadding   = math.max(0, paddingY)
  private var context           = Option.empty[TUIContext]

  def addChild(component: Component): Unit       =
    val attach = !childrenBuffer.exists(_ eq component)
    childrenBuffer += component
    if attach then context.foreach(value => propagateContext(component, Some(value)))
  def removeChild(component: Component): Boolean =
    val index = childrenBuffer.indexOf(component)
    if index >= 0 then
      childrenBuffer.remove(index)
      if context.nonEmpty && !childrenBuffer.exists(_ eq component) then
        propagateContext(component, None)
      true
    else false

  /** Remove all children and detach their runtime context once per component identity. */
  def clear(): Unit =
    if context.nonEmpty then foreachDistinctChild(propagateContext(_, None))
    childrenBuffer.clear()

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    if !sameContext(context, value) then
      context = value
      foreachDistinctChild(propagateContext(_, value))

  override def invalidate(): Unit = childrenBuffer.foreach(_.invalidate())

  override def render(width: Int): ComponentRender = renderFrame(width).render

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val innerWidth       = math.max(0, width - horizontalPadding * 2)
    val horizontal       = " ".repeat(horizontalPadding)
    val vertical         = Vector.fill(verticalPadding)(style(" ".repeat(width)))
    val bodyLines        = Vector.newBuilder[String]
    val controls         = Vector.newBuilder[TerminalControlPlacement]
    val cursorPlacements = Vector.newBuilder[CursorPlacement]
    val childNodes       = Vector.newBuilder[LayoutNode]
    var bodyRow          = 0
    childrenBuffer.foreach { child =>
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
      bodyRow += frame.lines.length
    }
    val render           = ComponentRender(
      vertical ++ bodyLines.result() ++ vertical,
      controls.result(),
      cursorPlacements.result()
    )
    RenderedFrame(
      render,
      LayoutNode(
        this,
        LayoutBounds(row, col, math.max(0, width), render.lines.length),
        childNodes.result()
      )
    )

  private def foreachDistinctChild(action: Component => Unit): Unit =
    childrenBuffer.indices.foreach { index =>
      val component = childrenBuffer(index)
      if childrenBuffer.take(index).forall(existing => !(existing eq component)) then
        action(component)
    }

  private def propagateContext(component: Component, value: Option[TUIContext]): Unit =
    component match
      case contextual: ContextualComponent => contextual.tuiContext_=(value)
      case _                               => ()

  private def sameContext(left: Option[TUIContext], right: Option[TUIContext]): Boolean =
    (left, right) match
      case (None, None)                      => true
      case (Some(a), Some(b))                => a eq b
      case (None, Some(_)) | (Some(_), None) => false
