package scalatui.core

import scalatui.terminal.{KeybindingCommand, MouseInputContext, TerminalInput}

/**
 * A renderable terminal UI component.
 *
 * Implementations return ordinary lines, semantic controls, and structured cursor candidates on JVM
 * and Scala Native. Every line and placement must fit within the requested terminal display width,
 * and each placement must use a returned row.
 *
 * Built-in mutable components serialize public mutation, input handling, and render snapshots with
 * one private state boundary on JVM and Scala Native. Application callbacks, providers, styling
 * hooks, cancellation handles, context operations, and render requests run after that boundary is
 * released. Detached effects use one bounded local FIFO. Attached effects share the owning TUI
 * lifecycle's 4096-batch FIFO and single-owner drain. An uncontended call may drain synchronously.
 * A reentrant or concurrent call returns after state commit and enqueue. Full admission or the TUI
 * stopping cutoff rejects an effectful transition before mutation. A callback observes the
 * committed state that triggered it and may reenter component or TUI APIs. Detached descendant
 * failures reach the active outer drain. Attached failures enter TUI cleanup. Asynchronous results
 * commit only while their captured request generation is current.
 */
trait Component:
  /**
   * Render this component into one typed frame that fits within `width` visible columns.
   *
   * Text-only implementations should return `ComponentRender.text`. Ordinary line contents do not
   * grant semantic control or cursor authority. Invalid surviving metadata geometry fails before
   * terminal output; the TUI does not move, drop, partially encode, or convert it to text. This
   * width-only method remains the unbounded fallback for components that do not implement
   * [[ViewportLayoutProvider]]. A viewport measures such a component as an intrinsic-height leaf.
   *
   * The TUI serializes rendering with input callbacks and invokes it without holding the runtime
   * lifecycle lock. A render may request or flush another render; that follow-up is coalesced and
   * runs after the current render rather than recursively.
   */
  def render(width: Int): ComponentRender

  /** Render this component and return retained display-cell bounds for coordinate-aware routing. */
  def renderFrame(width: Int, row: Int = 0, col: Int = 0): RenderedFrame =
    RuntimeCounterScope.recordComponentRender()
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

/**
 * Optional component capability for typed fullscreen commands.
 *
 * A focused component still receives the original [[TerminalInput]] first. The runtime calls this
 * handler only after the focused target ignores that input and before primary
 * [[scalatui.components.ScrollView]] fallback. Search implementations can use this contract without
 * terminal-backend keys or raw escape strings.
 */
trait ViewportCommandHandler:
  self: Component =>

  /** Handle one registered viewport command and report whether routing should continue. */
  def handleViewportCommand(command: KeybindingCommand): InputResult

/** Component capability for explicit coordinate-routed mouse input handling. */
trait MouseInputHandler:
  /** Handle a routed mouse event with target bounds and local coordinates. */
  def handleMouse(context: MouseInputContext): InputResult

/** Component that can receive focus and expose a hardware cursor position. */
trait Focusable:
  def focused: Boolean
  def focused_=(value: Boolean): Unit
