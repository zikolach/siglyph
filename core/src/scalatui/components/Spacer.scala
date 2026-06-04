package scalatui.components

import scalatui.core.Component

final class Spacer(lines: Int = 1) extends Component:
  override def render(width: Int): Vector[String] = Vector.fill(math.max(0, lines))("")
