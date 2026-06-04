package scalatui.core

import scala.collection.mutable.ArrayBuffer

/** Vertical component container that renders children in insertion order. */
final class Container extends Component:
  private val childBuffer = ArrayBuffer.empty[Component]

  def children: Vector[Component] = childBuffer.toVector

  def addChild(component: Component): Unit = childBuffer += component

  def removeChild(component: Component): Boolean =
    val index = childBuffer.indexOf(component)
    if index >= 0 then
      childBuffer.remove(index)
      true
    else false

  def clear(): Unit = childBuffer.clear()

  override def invalidate(): Unit =
    childBuffer.foreach(_.invalidate())

  override def render(width: Int): Vector[String] =
    childBuffer.iterator.flatMap(_.render(width)).toVector
