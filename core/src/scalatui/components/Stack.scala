package scalatui.components

import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  LayoutNode,
  LayoutViewport,
  RenderedFrame,
  StackAlignment,
  StackAxis,
  TUIContext,
  ViewportLayoutEngine,
  ViewportLayoutProvider,
  ViewportStackEntry,
  ViewportStackLayout
}

import scala.collection.mutable.ArrayBuffer

/**
 * Main-axis sizing and responsive visibility for one JVM or Scala Native stack child.
 *
 * Sizes use non-negative terminal display cells. Grow and shrink are weights. The visibility
 * callback receives current terminal viewport dimensions during layout, runs without runtime or
 * terminal-output locks, and should not mutate application state.
 */
final case class StackEntryOptions(
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

/** One component and its immutable stack sizing options. */
final case class StackEntry(component: Component, options: StackEntryOptions = StackEntryOptions())

/**
 * Shared child ownership and direct-render contract for vertical and horizontal stacks.
 *
 * Public child mutations serialize with layout snapshots. Context attachment and detachment happen
 * after the stack state boundary is released. Direct rendering returns the complete width-only
 * document. [[TUI.fullscreen]] owns height allocation and clipping.
 */
abstract class Stack(
    initialEntries: Seq[StackEntry],
    val gap: Int,
    val alignment: StackAlignment
) extends Component,
      ContextualComponent,
      ViewportLayoutProvider:
  require(gap >= 0, "Stack gap must be non-negative")

  protected def axis: StackAxis

  private val stateBoundary = ComponentStateBoundary()
  private val entryBuffer   = ArrayBuffer.from(initialEntries)
  private var context       = Option.empty[TUIContext]

  /** Return the current entries in stable insertion order. */
  def entries: Vector[StackEntry] = stateBoundary(entryBuffer.toVector)

  /** Append one child with default sizing. */
  def addChild(component: Component): Unit = addChild(component, StackEntryOptions())

  /** Append one child with explicit sizing and responsive visibility. */
  def addChild(component: Component, options: StackEntryOptions): Unit =
    stateBoundary.transition { effects =>
      val attach = !entryBuffer.exists(_.component eq component)
      entryBuffer += StackEntry(component, options)
      if attach then
        context.foreach(value => effects.add(() => propagateContext(component, Some(value))))
    }

  /** Remove the first matching child. */
  def removeChild(component: Component): Boolean = stateBoundary.transition { effects =>
    val index = entryBuffer.indexWhere(_.component eq component)
    if index >= 0 then
      entryBuffer.remove(index)
      if context.nonEmpty && !entryBuffer.exists(_.component eq component) then
        effects.add(() => propagateContext(component, None))
      true
    else false
  }

  /** Remove every child and detach each distinct component identity once. */
  def clear(): Unit = stateBoundary.transition { effects =>
    if context.nonEmpty then
      distinctComponents.foreach(component => effects.add(() => propagateContext(component, None)))
    entryBuffer.clear()
  }

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if !sameContext(context, value) then
        context = value
        distinctComponents.foreach(component =>
          effects.add(() => propagateContext(component, value))
        )
    }

  override def invalidate(): Unit = entries.foreach(_.component.invalidate())

  override def viewportLayout: ViewportStackLayout =
    val snapshot = entries.map { entry =>
      val options = entry.options
      ViewportStackEntry(
        entry.component,
        options.basis,
        options.grow,
        options.shrink,
        options.minSize,
        options.maxSize,
        options.visible
      )
    }
    ViewportStackLayout(axis, snapshot, gap, alignment)

  override def render(width: Int): ComponentRender =
    ViewportLayoutEngine.renderUnbounded(this, width).render

  override def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val frame = ViewportLayoutEngine.renderUnbounded(this, width)
    RenderedFrame(frame.render, translate(frame.root.toLayoutNode, row, col))

  private def translate(node: LayoutNode, row: Int, col: Int): LayoutNode =
    node.copy(
      bounds = node.bounds.copy(row = node.bounds.row + row, col = node.bounds.col + col),
      children = node.children.map(child => translate(child, row, col))
    )

  private def distinctComponents: Vector[Component] =
    entryBuffer.indices.flatMap { index =>
      val component = entryBuffer(index).component
      Option.when(
        entryBuffer.take(index).forall(existing => !(existing.component eq component))
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

/**
 * Vertical stack with deterministic height allocation on JVM and Scala Native. Direct rendering is
 * unbounded. Fullscreen rendering applies basis, growth, shrink, bounds, gap, alignment, and
 * responsive visibility.
 */
final class VStack(
    initialEntries: Seq[StackEntry] = Seq.empty,
    gap: Int = 0,
    alignment: StackAlignment = StackAlignment.Stretch
) extends Stack(initialEntries, gap, alignment):
  override protected def axis: StackAxis = StackAxis.Vertical

/**
 * Horizontal stack with ANSI-safe, grapheme-safe width allocation on JVM and Scala Native. Wide
 * graphemes are never split at a child boundary. Direct rendering remains height-unbounded.
 */
final class HStack(
    initialEntries: Seq[StackEntry] = Seq.empty,
    gap: Int = 0,
    alignment: StackAlignment = StackAlignment.Stretch
) extends Stack(initialEntries, gap, alignment):
  override protected def axis: StackAxis = StackAxis.Horizontal
