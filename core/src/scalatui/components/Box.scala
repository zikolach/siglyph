package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender, TerminalControlPlacement}

import scala.collection.mutable.ArrayBuffer

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

  override def render(width: Int): ComponentRender =
    val innerWidth = math.max(0, width - horizontalPadding * 2)
    val horizontal = " ".repeat(horizontalPadding)
    val vertical   = Vector.fill(verticalPadding)(style(" ".repeat(width)))
    val bodyLines  = Vector.newBuilder[String]
    val controls   = Vector.newBuilder[TerminalControlPlacement]
    var bodyRow    = 0
    childrenBuffer.foreach { child =>
      val frame = child.render(innerWidth)
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
      bodyRow += frame.lines.length
    }
    ComponentRender(vertical ++ bodyLines.result() ++ vertical, controls.result())
