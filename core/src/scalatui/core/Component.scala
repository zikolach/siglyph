package scalatui.core

import scalatui.terminal.TerminalInput

/** A renderable terminal UI component.
  *
  * Implementations must ensure every rendered line is at most the requested
  * terminal display width after ANSI/non-printing escapes are ignored.
  */
trait Component:
  def render(width: Int): Vector[String]

  def handleInput(input: TerminalInput): Unit = ()

  def wantsKeyRelease: Boolean = false

  def invalidate(): Unit = ()

/** Component that can receive focus and expose a hardware cursor position. */
trait Focusable:
  def focused: Boolean
  def focused_=(value: Boolean): Unit
