package scalatui.components

import scalatui.terminal.{KeybindingCommand, KeybindingManager, TerminalInput}

/** Shared default select commands; movement remains owned by each list's state boundary. */
private[components] object ListPaging:
  private val bindings = KeybindingManager()

  def delta(input: TerminalInput, visibleRows: Int): Option[Int] =
    val page = math.max(1, visibleRows)
    if bindings.matches(input, KeybindingCommand.SelectPageUp) then Some(-page)
    else if bindings.matches(input, KeybindingCommand.SelectPageDown) then Some(page)
    else None
