package scalatui.core

/**
 * Result returned by component input handling.
 *
 * The model intentionally stays small: richer focus/overlay commands will be introduced only when
 * concrete widgets need them.
 */
enum InputResult derives CanEqual:
  /** The component did not handle the input and no render is needed. */
  case Ignored

  /**
   * The component handled the input.
   *
   * @param requestRender
   *   whether the runtime should schedule/render after handling
   */
  case Handled(requestRender: Boolean = true)

  /** The component handled the input by requesting application shutdown. */
  case Exit

object InputResult:
  /** Convenience result for handled input that should render. */
  val Render: InputResult = Handled(requestRender = true)

  /** Convenience result for handled input that does not need a render. */
  val NoRender: InputResult = Handled(requestRender = false)
