package scalatui.core

import scalatui.terminal.{MouseInputContext, TerminalInput}

/**
 * A renderable terminal UI component.
 *
 * Implementations return ordinary lines, semantic controls, and structured cursor candidates on JVM
 * and Scala Native. Every line and placement must fit within the requested terminal display width,
 * and each placement must use a returned row.
 */
trait Component:
  /**
   * Render this component into one typed frame that fits within `width` visible columns.
   *
   * Text-only implementations should return `ComponentRender.text`. Ordinary line contents do not
   * grant semantic control or cursor authority. Invalid surviving metadata geometry fails before
   * terminal output; the TUI does not move, drop, partially encode, or convert it to text.
   *
   * The TUI serializes rendering with input callbacks and invokes it without holding the runtime
   * lifecycle lock. A render may request or flush another render; that follow-up is coalesced and
   * runs after the current render rather than recursively.
   */
  def render(width: Int): ComponentRender

  /** Render this component and return retained display-cell bounds for coordinate-aware routing. */
  def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    RenderedFrame.leaf(this, width, row, col)

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

/** Component capability for explicit coordinate-routed mouse input handling. */
trait MouseInputHandler:
  /** Handle a routed mouse event with target bounds and local coordinates. */
  def handleMouse(context: MouseInputContext): InputResult

/** Component that can receive focus and expose a hardware cursor position. */
trait Focusable:
  def focused: Boolean
  def focused_=(value: Boolean): Unit
