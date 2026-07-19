package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  CursorPlacement,
  LayoutBounds,
  LayoutNode,
  RenderedFrame,
  TerminalControlPlacement
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
    extends Component:
  private val childrenBuffer    = ArrayBuffer.empty[Component]
  private val horizontalPadding = math.max(0, paddingX)
  private val verticalPadding   = math.max(0, paddingY)

  def addChild(component: Component): Unit       = childrenBuffer += component
  def removeChild(component: Component): Boolean =
    val index = childrenBuffer.indexOf(component)
    if index >= 0 then
      childrenBuffer.remove(index)
      true
    else false

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
