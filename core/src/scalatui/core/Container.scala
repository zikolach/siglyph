package scalatui.core

import scalatui.components.ComponentStateBoundary

import scala.collection.mutable.ArrayBuffer

/** Vertical component container that renders children in insertion order. */
final class Container extends Component, ContextualComponent:
  private val stateBoundary = ComponentStateBoundary()
  private val childBuffer   = ArrayBuffer.empty[Component]
  private var context       = Option.empty[TUIContext]

  def children: Vector[Component] = stateBoundary(childBuffer.toVector)

  def addChild(component: Component): Unit = stateBoundary.transition { effects =>
    val attach = !childBuffer.exists(_ eq component)
    childBuffer += component
    if attach then
      context.foreach(value => effects.add(() => propagateContext(component, Some(value))))
  }

  def removeChild(component: Component): Boolean = stateBoundary.transition { effects =>
    val index = childBuffer.indexOf(component)
    if index >= 0 then
      childBuffer.remove(index)
      if context.nonEmpty && !childBuffer.exists(_ eq component) then
        effects.add(() => propagateContext(component, None))
      true
    else false
  }

  def clear(): Unit = stateBoundary.transition { effects =>
    if context.nonEmpty then
      distinctChildren.foreach(component =>
        effects.add(() => propagateContext(component, None))
      )
    childBuffer.clear()
  }

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if !sameContext(context, value) then
        context = value
        distinctChildren.foreach(component => effects.add(() => propagateContext(component, value)))
    }

  override def invalidate(): Unit =
    stateBoundary(childBuffer.toVector).foreach(_.invalidate())

  override def render(width: Int): ComponentRender =
    renderFrame(width).render

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val frame    = ComponentFrameBuilder(width, row, col)
    val children = stateBoundary(childBuffer.toVector)
    children.foreach(frame.addComponent)
    frame.resultFrame(this)

  private def distinctChildren: Vector[Component] =
    childBuffer.indices.flatMap { index =>
      val component = childBuffer(index)
      Option.when(childBuffer.take(index).forall(existing => !(existing eq component)))(component)
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
