package scalatui.dsl

import scalatui.core.{Component, TUI, TUIOptions}
import scalatui.terminal.Terminal

/**
 * Unreleased immutable application composition for JVM and Native. Components and terminals are
 * borrowed, not cloned. Build creates an unstarted TUI; callers retain lifecycle ownership.
 */
final case class App(
    options: TUIOptions = TUIOptions(),
    children: Vector[Component] = Vector.empty,
    focus: Option[Component] = None
):
  /** Append typed components without changing this configuration. */
  def add(components: Component*): App = copy(children = children ++ components)

  /** Select an attached component for initial focus. */
  def focused(component: Component): App = copy(focus = Some(component))

  /** Create a fresh runtime around the supplied backend; never starts terminal I/O. */
  def build(terminal: Terminal): TUI =
    require(
      focus.forall(value => children.exists(_ eq value)),
      "focus must be an application child"
    )
    val tui = TUI(terminal, options)
    children.foreach(tui.addChild)
    focus.foreach(tui.setFocus)
    tui
