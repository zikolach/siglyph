package scalatui.extras

/**
 * Opt-in protocol for components or helper objects that can switch between collapsed and expanded
 * presentation states.
 *
 * Implementations should invalidate any cached rendering when the state changes. The protocol does
 * not own keybindings, application state, or render scheduling.
 */
trait Expandable:
  /** Apply the next presentation state. */
  def setExpanded(expanded: Boolean): Unit
