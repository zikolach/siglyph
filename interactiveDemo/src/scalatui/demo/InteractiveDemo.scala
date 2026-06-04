package scalatui.demo

import scalatui.components.{Input, SelectItem, SelectList, Text}
import scalatui.core.{Component, TUI}
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}

object InteractiveDemo:
  def install(tui: TUI): Unit =
    tui.exitsOnEscape = true
    tui.handlesControlC = true
    val root = DemoRoot(tui)
    tui.addChild(root)
    tui.setFocus(root)

private final class DemoRoot(tui: TUI) extends Component:
  private enum Focus:
    case Actions, InputLine

  private var focus = Focus.InputLine
  private var messages = Vector.empty[String]
  private val messagesText = Text("Messages: (none)", paddingX = 0)
  private val input = Input()
  private val actions = SelectList(
    Vector(
      SelectItem("add", "Add input as message"),
      SelectItem("clear", "Clear messages"),
      SelectItem("quit", "Quit")
    ),
    maxVisible = 3
  )

  actions.onSelect = item => item.value match
    case "add" => addMessage()
    case "clear" =>
      messages = Vector.empty
      updateMessages()
    case "quit" => tui.requestExit()
    case _ => ()

  input.onSubmit = _ => addMessage()
  updateFocus()

  override def handleInput(event: TerminalInput): Unit = event match
    case TerminalInput.Key(TerminalKey.Tab, _) =>
      focus = if focus == Focus.InputLine then Focus.Actions else Focus.InputLine
      updateFocus()
    case TerminalInput.Key(TerminalKey.Character("l"), modifiers) if modifiers.ctrl =>
      messages = Vector.empty
      updateMessages()
    case _ =>
      focus match
        case Focus.Actions => actions.handleInput(event)
        case Focus.InputLine => input.handleInput(event)

  override def render(width: Int): Vector[String] =
    Vector(
      "scala-tui interactive demo",
      "Tab focus • ↑↓ actions • Enter submit/select • Ctrl+L clear • Esc/Ctrl+C quit",
      "",
      if focus == Focus.Actions then "Actions (focused):" else "Actions:"
    ) ++
      actions.render(width) ++
      Vector("") ++
      messagesText.render(width) ++
      Vector("", if focus == Focus.InputLine then "Input (focused):" else "Input:") ++
      input.render(width)

  private def addMessage(): Unit =
    val value = input.value.trim
    if value.nonEmpty then
      messages :+= value
      input.setValue("")
      updateMessages()

  private def updateMessages(): Unit =
    messagesText.text =
      if messages.isEmpty then "Messages: (none)"
      else messages.zipWithIndex.map((msg, idx) => s"${idx + 1}. $msg").mkString("Messages:\n", "\n", "")

  private def updateFocus(): Unit =
    input.focused = focus == Focus.InputLine
