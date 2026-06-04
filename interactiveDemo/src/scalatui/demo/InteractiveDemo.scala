package scalatui.demo

import scalatui.components.{Editor, EditorOptions, SelectItem, SelectList, Text}
import scalatui.core.{Component, TUI}
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}

object InteractiveDemo:
  def install(tui: TUI): Unit =
    tui.exitsOnEscape = true
    tui.handlesControlC = true
    val root = DemoRoot(tui)
    tui.addChild(root)
    tui.setFocus(root)

private final class DemoRoot(tui: TUI) extends Component:
  private enum Focus derives CanEqual:
    case Actions, EditorPane

  private var focus        = Focus.EditorPane
  private var messages     = Vector.empty[String]
  private val messagesText = Text("Submitted: (none)", paddingX = 0)
  private val editor       = Editor(options = EditorOptions(onSubmit = addMessage))
  private val actions      = SelectList(
    Vector(
      SelectItem("submit", "Submit editor text"),
      SelectItem("clear", "Clear submitted messages"),
      SelectItem("quit", "Quit")
    ),
    maxVisible = 3
  )

  actions.onSelect = item =>
    item.value match
      case "submit" => addMessage(editor.text)
      case "clear"  =>
        messages = Vector.empty
        updateMessages()
      case "quit"   => tui.requestExit()
      case _        => ()

  updateFocus()

  override def handleInput(event: TerminalInput): Unit = event match
    case TerminalInput.Key(TerminalKey.Tab, _)                                      =>
      focus = if focus === Focus.EditorPane then Focus.Actions else Focus.EditorPane
      updateFocus()
    case TerminalInput.Key(TerminalKey.Character("l"), modifiers) if modifiers.ctrl =>
      messages = Vector.empty
      updateMessages()
    case _                                                                          =>
      focus match
        case Focus.Actions    => actions.handleInput(event)
        case Focus.EditorPane => editor.handleInput(event)

  override def render(width: Int): Vector[String] =
    Vector(
      "scala-tui multiline editor demo",
      "Tab focus • ↑↓ actions • Enter submit • Shift+Enter newline • Ctrl+L clear • Esc/Ctrl+C quit",
      "",
      if focus === Focus.Actions then "Actions (focused):" else "Actions:"
    ) ++
      actions.render(width) ++
      Vector("") ++
      messagesText.render(width) ++
      Vector("", if focus === Focus.EditorPane then "Editor (focused):" else "Editor:") ++
      editor.render(width)

  private def addMessage(value: String): Unit =
    val trimmed = value.trim
    if trimmed.nonEmpty then
      messages :+= value
      editor.setText("")
      updateMessages()

  private def updateMessages(): Unit =
    messagesText.text =
      if messages.isEmpty then "Submitted: (none)"
      else
        messages.zipWithIndex.map((msg, idx) =>
          s"${idx + 1}. ${msg.replace("\n", " ⏎ ")}"
        ).mkString(
          "Submitted:\n",
          "\n",
          ""
        )

  private def updateFocus(): Unit =
    editor.focused = focus === Focus.EditorPane
