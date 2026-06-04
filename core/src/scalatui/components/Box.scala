package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.Component

import scala.collection.mutable.ArrayBuffer

final class Box(paddingX: Int = 1, paddingY: Int = 0, style: String => String = identity) extends Component:
  private val childrenBuffer = ArrayBuffer.empty[Component]

  def addChild(component: Component): Unit = childrenBuffer += component
  def removeChild(component: Component): Boolean =
    val index = childrenBuffer.indexOf(component)
    if index >= 0 then
      childrenBuffer.remove(index)
      true
    else false

  override def invalidate(): Unit = childrenBuffer.foreach(_.invalidate())

  override def render(width: Int): Vector[String] =
    val innerWidth = math.max(0, width - paddingX * 2)
    val horizontal = " ".repeat(math.max(0, paddingX))
    val vertical = Vector.fill(math.max(0, paddingY))(style(" ".repeat(width)))
    val body = childrenBuffer.iterator.flatMap(_.render(innerWidth)).map { line =>
      val padded = horizontal + Ansi.padRight(line, innerWidth) + horizontal
      style(Ansi.truncateToWidth(padded, width, ""))
    }.toVector
    vertical ++ body ++ vertical
