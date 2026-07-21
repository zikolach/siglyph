package scalatui.core

import scala.collection.mutable.ArrayBuffer

/** Vertical component container that renders children in insertion order. */
final class Container extends Component, ContextualComponent:
  private val childBuffer = ArrayBuffer.empty[Component]
  private var context     = Option.empty[TUIContext]

  def children: Vector[Component] = childBuffer.toVector

  def addChild(component: Component): Unit =
    val attach = !childBuffer.exists(_ eq component)
    childBuffer += component
    if attach then context.foreach(value => propagateContext(component, Some(value)))

  def removeChild(component: Component): Boolean =
    val index = childBuffer.indexOf(component)
    if index >= 0 then
      childBuffer.remove(index)
      if context.nonEmpty && !childBuffer.exists(_ eq component) then
        propagateContext(component, None)
      true
    else false

  def clear(): Unit =
    if context.nonEmpty then foreachDistinctChild(propagateContext(_, None))
    childBuffer.clear()

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    if !sameContext(context, value) then
      context = value
      foreachDistinctChild(propagateContext(_, value))

  override def invalidate(): Unit =
    childBuffer.foreach(_.invalidate())

  override def render(width: Int): ComponentRender =
    renderFrame(width).render

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val frame = ComponentFrameBuilder(width, row, col)
    childBuffer.foreach(frame.addComponent)
    frame.resultFrame(this)

  private def foreachDistinctChild(action: Component => Unit): Unit =
    childBuffer.indices.foreach { index =>
      val component = childBuffer(index)
      if childBuffer.take(index).forall(existing => !(existing eq component)) then action(component)
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
