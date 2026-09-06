package scalatui.components

import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  LayoutBounds,
  LayoutNode,
  MouseEvent,
  MouseEventHandler,
  MouseHandlerResult,
  RenderedFrame,
  StackAxis,
  TUIContext,
  ViewportLayoutEngine,
  ViewportLayoutProvider,
  ViewportStackEntry,
  ViewportStackLayout
}

/**
 * One-child semantic mouse region for JVM and Scala Native.
 *
 * The wrapper preserves child text, controls, cursor candidates, document metadata, viewport
 * layout, context, and retained bounds. The application callback runs without component state or
 * runtime locks. It must return all handled, render, capture, and focus intent in one
 * [[MouseHandlerResult]].
 */
final class MouseRegion(
    val child: Component,
    onMouseEvent: MouseEvent => MouseHandlerResult
) extends Component,
      ContextualComponent,
      ViewportLayoutProvider,
      MouseEventHandler:
  private val stateBoundary = ComponentStateBoundary()
  private var context       = Option.empty[TUIContext]

  override def render(width: Int): ComponentRender = child.render(width)

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val childFrame = child.renderFrame(width, row, col)
    RenderedFrame(
      childFrame.render,
      LayoutNode(
        this,
        LayoutBounds(row, col, math.max(0, width), childFrame.render.lines.length),
        Vector(childFrame.layout)
      )
    )

  override def viewportLayout: ViewportStackLayout =
    ViewportStackLayout(StackAxis.Vertical, Vector(ViewportStackEntry(child)))

  override def handleMouseEvent(event: MouseEvent): MouseHandlerResult = onMouseEvent(event)

  override def invalidate(): Unit = child.invalidate()

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if !sameContext(context, value) then
        context = value
        child match
          case contextual: ContextualComponent => effects.add(() => contextual.tuiContext_=(value))
          case _                               => ()
    }

  private def sameContext(left: Option[TUIContext], right: Option[TUIContext]): Boolean =
    (left, right) match
      case (None, None)                      => true
      case (Some(a), Some(b))                => a eq b
      case (None, Some(_)) | (Some(_), None) => false
