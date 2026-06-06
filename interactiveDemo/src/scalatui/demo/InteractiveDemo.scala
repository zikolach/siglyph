package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.autocomplete.{
  CombinedAutocompleteProvider,
  PathCompletion,
  PathCompletionProvider,
  SlashCommand
}
import scalatui.components.{
  CancellableLoader,
  Editor,
  EditorOptions,
  Loader,
  LoaderIndicatorOptions,
  LoaderOptions,
  SelectItem,
  SelectList,
  Text
}
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
      autocompleteProvider = Some(CombinedAutocompleteProvider(
        commands = Vector(
          SlashCommand("help", Some("Show demo help")),
          SlashCommand("clear", Some("Clear submitted messages")),
          SlashCommand("quit", Some("Exit the demo"))
        ),
        pathProvider = Some(PathCompletionProvider.sync(request =>
          val prefix = request.prefix.rawPrefix
          DemoRoot.PathSuggestions.filter(item => prefix.isEmpty || item.path.startsWith(prefix))
        ))
      ))
    )
  )
  private val loader       = Loader(LoaderOptions(
    message = "Tick me from Actions",
    indicator = LoaderIndicatorOptions(frames = Vector("◐", "◓", "◑", "◒")),
    leadingBlankLine = false
  ))
  private val cancellable  = CancellableLoader(LoaderOptions(
    message = "Cancel me from Actions",
    indicator = LoaderIndicatorOptions(frames = Vector("!")),
    leadingBlankLine = false
  ))
  private val actions      = SelectList(
    Vector(
      SelectItem("submit", "Submit editor text"),
      SelectItem("clear", "Clear submitted messages"),
      SelectItem("large-paste", "Insert large paste marker"),
      SelectItem("expand-paste", "Expand paste markers"),
      SelectItem("tick-loader", "Tick loader"),
      SelectItem("cancel-loader", "Cancel loader"),
      SelectItem("quit", "Quit")
    ),
    maxVisible = 5
  )

  actions.onSelect = item =>
    item.value match
      case "submit"        => addMessage(editor.text)
      case "clear"         =>
        messages = Vector.empty
        updateMessages()
      case "large-paste"   =>
        editor.handleInput(
          TerminalInput.Paste((1 to 12).map(i => s"pasted line $i").mkString("\n"))
        )
        focus = Focus.EditorPane
        updateFocus()
      case "expand-paste"  =>
        editor.expandPasteMarkers()
        focus = Focus.EditorPane
        updateFocus()
      case "tick-loader"   => loader.tick()
      case "cancel-loader" => cancellable.cancel()
      case "quit"          => tui.requestExit()
      case _               => ()

  cancellable.onCancel = () => cancellable.setMessage("Cancelled")
  loader.start()
  updateFocus()
  editor.tuiContext_=(Some(tui))
  loader.tuiContext_=(Some(tui))
  cancellable.tuiContext_=(Some(tui))

  override def handleInput(event: TerminalInput): Unit = event match
    case TerminalInput.Key(TerminalKey.Character("t"), modifiers) if modifiers.ctrl =>
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
      "Ctrl+T focus • ↑↓ actions • Enter submit/select • Shift+Enter newline • Tab autocompletes in editor (type /, ./, or @ first) • Ctrl+- undo • Ctrl+W kill word • Ctrl+Y yank • Alt+Y yank-pop • large-paste actions demo markers • Ctrl+L clear • Esc/Ctrl+C quit",
      renderWidth
    ))
    frame.addLines(Vector(
      "",
      fit(if focus === Focus.Actions then "Actions (focused):" else "Actions:", renderWidth)
    ))
    frame.addComponent(actions)
    frame.addLine(fit(
      s"Loader: ${plainLoaderLine(loader, renderWidth)} | ${plainLoaderLine(cancellable, renderWidth)}",
      renderWidth
    ))
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

  private def plainLoaderLine(component: Component, width: Int): String =
    component.render(width).headOption.map(Ansi.strip).getOrElse("").trim

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

private object DemoRoot:
  val PathSuggestions: Vector[PathCompletion] = Vector(
    PathCompletion("./README.md", "README.md"),
    PathCompletion("./docs/interactive-smoke.md", "interactive-smoke.md"),
    PathCompletion("./core/src/scalatui/components/Editor.scala", "Editor.scala"),
    PathCompletion("screenshots/demo image.png", "demo image.png")
  )
