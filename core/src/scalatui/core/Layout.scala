package scalatui.core

import scalatui.ansi.Ansi

/** Terminal display-cell bounds for a rendered component. */
final case class LayoutBounds(row: Int, col: Int, width: Int, height: Int) derives CanEqual:
  /** Returns true when the terminal cell is inside these bounds. */
  def contains(rowValue: Int, colValue: Int): Boolean =
    rowValue >= row && rowValue < row + height && colValue >= col && colValue < col + width

/** Retained component layout node for coordinate-aware input routing. */
final case class LayoutNode(
    component: Component,
    bounds: LayoutBounds,
    children: Vector[LayoutNode] = Vector.empty
)

/** Typed component output plus its retained layout tree. */
final case class RenderedFrame(render: ComponentRender, layout: LayoutNode):
  def lines: Vector[String] = render.lines

object RenderedFrame:
  /** Render a component through the existing typed render contract as one leaf node. */
  def leaf(component: Component, width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    val render = component.render(width)
    RenderedFrame(
      render,
      LayoutNode(component, LayoutBounds(row, col, math.max(0, width), render.lines.length))
    )

  private[core] def widthForLines(lines: Vector[String], fallback: Int): Int =
    math.max(0, math.max(fallback, lines.map(Ansi.visibleWidth).maxOption.getOrElse(0)))
