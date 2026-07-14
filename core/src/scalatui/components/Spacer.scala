package scalatui.components

import scalatui.core.{Component, ComponentRender}

final class Spacer(lines: Int = 1) extends Component:
  override def render(width: Int): ComponentRender =
    ComponentRender.text(Vector.fill(math.max(0, lines))(""))
