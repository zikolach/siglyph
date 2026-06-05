package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.autocomplete.{SlashCommand, SlashCommandAutocompleteProvider}
import scalatui.components.{Editor, EditorOptions, SelectItem, SelectList, Text}
import scalatui.core.{Component, ComponentFrameBuilder, TUI}
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
  private val editor       = Editor(options =
    EditorOptions(
      onSubmit = addMessage,
      autocompleteProvider = Some(SlashCommandAutocompleteProvider(Vector(
        SlashCommand("help", Some("Show demo help")),
        SlashCommand("clear", Some("Clear submitted messages")),
        SlashCommand("quit", Some("Exit the demo"))
      )))
    )
  )
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
  editor.tuiContext_=(Some(tui))

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
    val renderWidth = math.max(1, width)
    val frame       = ComponentFrameBuilder(renderWidth)
    frame.addLines(Vector(fit("scala-tui multiline editor demo", renderWidth)))
    frame.addLines(Ansi.wrapTextWithAnsi(
      "Tab focus • ↑↓ actions • Enter submit • Shift+Enter newline • type / for commands • Ctrl+L clear • Esc/Ctrl+C quit",
      renderWidth
    ))
    frame.addLines(Vector(
      "",
      fit(if focus === Focus.Actions then "Actions (focused):" else "Actions:", renderWidth)
    ))
    frame.addComponent(actions)
    frame.addLine("")
    frame.addComponent(messagesText)
    frame.addLines(Vector(
      "",
      fit(if focus === Focus.EditorPane then "Editor (focused):" else "Editor:", renderWidth)
    ))
    frame.addComponent(editor)
    frame.result()

  private def fit(value: String, width: Int): String =
    Ansi.truncateToWidth(value, width, "")

  private def addMessage(value: String): Unit =
    val trimmed = value.trim
    if trimmed.nonEmpty then
      if trimmed === "/clear" then
        messages = Vector.empty
        editor.setText("")
        updateMessages()
      else if trimmed === "/quit" then tui.requestExit()
      else if trimmed === "/help" then
        messages :+= "Slash commands: /help, /clear, /quit"
        editor.setText("")
        updateMessages()
      else
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
