package scalatui.core

import scalatui.terminal.TerminalInput

/**
 * A renderable terminal UI component.
 *
 * Implementations must ensure every rendered line is at most the requested terminal display width
 * after ANSI/non-printing escapes are ignored.
 */
trait Component:
  /**
   * Render this component into terminal lines that fit within `width` visible columns.
   *
   * The TUI serializes rendering with input callbacks and invokes it without holding the runtime
   * lifecycle lock. A render may request or flush another render; that follow-up is coalesced and
   * runs after the current render rather than recursively.
   */
  def render(width: Int): Vector[String]

  /** Legacy/simple input hook for components that do not need result control. */
  def handleInput(input: TerminalInput): Unit = ()

  /**
   * Handle typed terminal input and report runtime follow-up behavior.
   *
   * The default delegates to [[handleInput]] and requests a render, preserving the previous simple
   * component contract. The callback runs without the TUI lifecycle lock. Reentrant render requests
   * are queued for the current work-drain owner.
   */
  def handleInputResult(input: TerminalInput): InputResult =
    handleInput(input)
    InputResult.Render

  def wantsKeyRelease: Boolean = false

  def invalidate(): Unit = ()

/** Component that can receive focus and expose a hardware cursor position. */
trait Focusable:
  def focused: Boolean
  def focused_=(value: Boolean): Unit
