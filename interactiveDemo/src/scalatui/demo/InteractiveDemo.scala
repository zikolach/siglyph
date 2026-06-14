package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.autocomplete.{
  AutocompleteFuzzyRanking,
  AutocompleteItem,
  CombinedAutocompleteProvider,
  FileSystemPathCompletionOptions,
  FileSystemPathCompletionProvider,
  SlashCommand,
  TriggerCompletionSource
}
import scalatui.components.{
  CancellableLoader,
  Editor,
  EditorOptions,
  Input,
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

import java.io.File
import scala.io.Source
import scala.util.Using

object InteractiveDemo:
  def install(tui: TUI): Unit =
    tui.exitsOnEscape = true
    tui.handlesControlC = true
    DemoRoot.tagTriggerSource match
      case Left(error)      =>
        tui.addChild(Text(s"Invalid autocomplete trigger configuration: ${error.message}"))
      case Right(tagSource) =>
        val root = DemoRoot(tui, tagSource)
        tui.addChild(root)
        tui.setFocus(root)

private final class DemoRoot(tui: TUI, tagTriggerSource: TriggerCompletionSource) extends Component:
  private enum DemoMode derives CanEqual:
    case EditorMode, FileManagerMode

  private enum Focus derives CanEqual:
    case Actions, EditorPane

  private enum FileManagerFocus derives CanEqual:
    case PathInput, FileList

  private final case class FileManagerItem(
      path: File,
      isDirectory: Boolean,
      displayName: String,
      detail: String
  )

  private enum FileModeAction derives CanEqual:
    case Preview, OpenInEditor

  private var mode  = DemoMode.EditorMode
  private var focus = Focus.EditorPane

  // ---------- Shared editor showcase ----------
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
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = File("."),
          maxResults = 20,
          includeHidden = false
        ))),
        triggerSources = Vector(tagTriggerSource),
        fuzzyRanking = AutocompleteFuzzyRanking.Enabled
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

  // ---------- File manager / viewer showcase ----------
  private var fileDirectory           = File(System.getProperty("user.dir")).getAbsoluteFile
  private val fileManagerPathInput    = Input(fileDirectory.getAbsolutePath)
  private var fileManagerItems        = Vector.empty[FileManagerItem]
  private var fileManagerSelection    = 0
  private var fileManagerScrollOffset = 0
  private var fileManagerFileFocus    = FileManagerFocus.PathInput
  private val fileManagerPreview      = Text("No file selected", paddingX = 0)
  private val fileManagerStatus       =
    Text("Tip: Ctrl+P to edit path, Enter opens entries", paddingX = 0)
  private val fileManagerLoader       = Loader(LoaderOptions(
    message = "Reading directory",
    indicator = LoaderIndicatorOptions(frames = Vector("◐", "◓", "◑", "◒")),
    leadingBlankLine = false
  ))
  private var fileManagerBusy         = false

  private val fileManagerMaxVisible = 12
  private val filePreviewMaxLines   = 18
  private var fileManagerFocusMode  = FileManagerFocus.FileList

  private val fileListRowPrefix = "  "

  // Shared behavior wiring
  actions.onSelect = handleAction
  fileManagerPathInput.onSubmit = text => navigateToPathFromInput(text)

  cancellable.onCancel = () => cancellable.setMessage("Cancelled")
  loader.start()
  refreshFileManagerDirectory()
  updateFocusStates()
  editor.tuiContext_=(Some(tui))
  loader.tuiContext_=(Some(tui))
  cancellable.tuiContext_=(Some(tui))

  private def handleAction(item: SelectItem): Unit =
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
        updateEditorFocus()
      case "expand-paste"  =>
        editor.expandPasteMarkers()
        focus = Focus.EditorPane
        updateEditorFocus()
      case "tick-loader"   => loader.tick()
      case "cancel-loader" => cancellable.cancel()
      case "quit"          => tui.requestExit()
      case _               => ()

  override def handleInput(event: TerminalInput): Unit =
    event match
      case TerminalInput.Key(TerminalKey.Character("m"), modifiers) if modifiers.ctrl =>
        mode =
          if mode === DemoMode.EditorMode then DemoMode.FileManagerMode else DemoMode.EditorMode
        if mode === DemoMode.EditorMode then
          fileManagerFocusMode = FileManagerFocus.FileList
          updateFocusStates()
        else
          fileManagerFocusMode = FileManagerFocus.FileList
          updateFocusStates()
          fileManagerStatus.text = "Press Ctrl+P for quick path jump, Enter to open"
        fileManagerStatus.text = statusLine
      case TerminalInput.Key(TerminalKey.Character("t"), modifiers)
          if modifiers.ctrl && mode === DemoMode.EditorMode =>
        focus = if focus === Focus.EditorPane then Focus.Actions else Focus.EditorPane
        updateEditorFocus()
      case TerminalInput.Key(TerminalKey.Character("l"), modifiers)
          if modifiers.ctrl && mode === DemoMode.EditorMode =>
        messages = Vector.empty
        updateMessages()
      case _ if mode === DemoMode.FileManagerMode                                     =>
        handleFileManagerInput(event)
      case _                                                                          =>
        focus match
          case Focus.Actions    => actions.handleInput(event)
          case Focus.EditorPane => editor.handleInput(event)

  private def handleFileManagerInput(event: TerminalInput): Unit =
    event match
      case TerminalInput.Key(TerminalKey.Character("p"), modifiers)
          if modifiers.ctrl && fileManagerFocusMode === FileManagerFocus.FileList =>
        fileManagerFocusMode = FileManagerFocus.PathInput
        updateFileManagerFocus()
      case TerminalInput.Key(TerminalKey.Escape, _)
          if fileManagerFocusMode === FileManagerFocus.PathInput =>
        fileManagerFocusMode = FileManagerFocus.FileList
        updateFileManagerFocus()
      case TerminalInput.Key(TerminalKey.Escape, _)                                   =>
        fileManagerFocusMode = FileManagerFocus.PathInput
        updateFileManagerFocus()
      case TerminalInput.Key(TerminalKey.Character("t"), modifiers) if modifiers.ctrl =>
        // Return to editor mode by design from the file manager.
        mode = DemoMode.EditorMode
        focus = Focus.EditorPane
        updateEditorFocus()
      case TerminalInput.Key(TerminalKey.Character("r"), modifiers) if modifiers.ctrl =>
        refreshFileManagerDirectory()
      case TerminalInput.Key(TerminalKey.Character("o"), modifiers) if modifiers.ctrl =>
        openSelection(FileModeAction.OpenInEditor)
      case TerminalInput.Key(TerminalKey.Backspace, _)                                =>
        if fileManagerFocusMode === FileManagerFocus.PathInput then
          fileManagerPathInput.handleInput(event)
        else navigateToParent()
      case TerminalInput.Key(TerminalKey.Enter, _)
          if fileManagerFocusMode === FileManagerFocus.PathInput =>
        fileManagerPathInput.handleInput(event)
      case TerminalInput.Key(TerminalKey.Up, _)
          if fileManagerFocusMode === FileManagerFocus.FileList =>
        moveFileSelection(-1)
      case TerminalInput.Key(TerminalKey.Down, _)
          if fileManagerFocusMode === FileManagerFocus.FileList =>
        moveFileSelection(1)
      case TerminalInput.Key(TerminalKey.PageUp, _)
          if fileManagerFocusMode === FileManagerFocus.FileList =>
        moveFileSelectionByPage(-1)
      case TerminalInput.Key(TerminalKey.PageDown, _)
          if fileManagerFocusMode === FileManagerFocus.FileList =>
        moveFileSelectionByPage(1)
      case TerminalInput.Key(TerminalKey.Enter, _)
          if fileManagerFocusMode === FileManagerFocus.FileList =>
        openSelection(FileModeAction.Preview)
      case _ if fileManagerFocusMode === FileManagerFocus.PathInput                   =>
        fileManagerPathInput.handleInput(event)
        fileManagerPathInputOnChange(fileManagerPathInput.value)
      case _                                                                          => ()

  override def render(width: Int): Vector[String] =
    val renderWidth = math.max(1, width)
    val frame       = ComponentFrameBuilder(renderWidth)
    frame.addLines(Vector(fit("siglyph showcase demo", renderWidth)))
    frame.addLines(Ansi.wrapTextWithAnsi(
      "Ctrl+T focus • Ctrl+M switch editor/file-manager modes • Esc/Ctrl+C quit",
      renderWidth
    ))
    frame.addLine("")

    if mode === DemoMode.EditorMode then renderEditorMode(renderWidth, frame)
    else renderFileManagerMode(renderWidth, frame)

    frame.result()

  private def renderEditorMode(width: Int, frame: ComponentFrameBuilder): Unit =
    frame.addLines(Ansi.wrapTextWithAnsi(
      "Editor mode: Ctrl+T focus • ↑↓ actions • Enter submit/select • Shift+Enter newline • Tab autocomplete: / commands, ./ paths, @ attachments, # tags • fuzzy ranking on • Ctrl+- undo • Ctrl+W kill word • Ctrl+Y yank • Alt+Y yank-pop • Ctrl+L clear",
      width
    ))
    frame.addLines(Vector(
      "",
      fit(if focus === Focus.Actions then "Actions (focused):" else "Actions:", width)
    ))
    frame.addComponent(actions)
    frame.addLine(fit(
      s"Loader: ${plainLoaderLine(loader, width)} | ${plainLoaderLine(cancellable, width)}",
      width
    ))
    frame.addLine("")
    frame.addComponent(messagesText)
    frame.addLines(Vector(
      "",
      fit(if focus === Focus.EditorPane then "Editor (focused):" else "Editor:", width)
    ))
    frame.addComponent(editor)

  private def renderFileManagerMode(width: Int, frame: ComponentFrameBuilder): Unit =
    frame.addLines(Ansi.wrapTextWithAnsi(
      "File manager mode: Enter opens, Backspace goes parent, Ctrl+P edit path, Ctrl+O open file in editor, Ctrl+R refresh, Ctrl+T returns to editor mode",
      width
    ))
    frame.addLines(Vector(
      "",
      fit(
        if fileManagerFocusMode === FileManagerFocus.PathInput then
          "Path input (focused):"
        else
          "Path input:"
        ,
        width
      )
    ))
    frame.addComponent(fileManagerPathInput)
    frame.addLines(Vector(
      "",
      fit(s"Current directory: ${fileDirectory.getAbsolutePath}", width),
      fit(
        s"${fileManagerItems.length} entries | selection ${fileManagerSelection + 1} of ${math.max(1, fileManagerItems.length)}",
        width
      ),
      ""
    ))
    fileManagerListLines(width).foreach(frame.addLine)
    frame.addLine("")
    frame.addLines(Ansi.wrapTextWithAnsi(
      s"Loader: ${plainLoaderLine(fileManagerLoader, width)}",
      width
    ))
    frame.addComponent(fileManagerStatus)
    frame.addLines(Vector(
      "",
      "Preview:",
      ""
    ))
    frame.addComponent(fileManagerPreview)

  private def fileManagerListLines(width: Int): Vector[String] =
    if fileManagerItems.isEmpty then Vector(fit("(empty directory)", width))
    else
      ensureFileSelectionVisible()
      val max     = fileManagerMaxVisible
      val visible = fileManagerItems.slice(fileManagerScrollOffset, fileManagerScrollOffset + max)
      visible.zipWithIndex.map { case (item, visibleIndex) =>
        val index  = fileManagerScrollOffset + visibleIndex
        val marker = if index === fileManagerSelection then "> " else fileListRowPrefix
        val prefix = if item.isDirectory then "📁 " else "📄 "
        val detail = s"${item.detail}"
        val text   = s"${marker}${prefix}${item.displayName}   ${detail}"
        fit(text, width)
      }

  private def fileManagerPathInputOnChange(value: String): Unit =
    fileManagerStatus.text = s"Path input: ${value.trim}"

  private def updateFileManagerFocus(): Unit =
    fileManagerPathInput.focused = fileManagerFocusMode === FileManagerFocus.PathInput

  private def updateEditorFocus(): Unit =
    editor.focused = mode === DemoMode.EditorMode && focus === Focus.EditorPane

  private def updateFocusStates(): Unit =
    updateEditorFocus()
    updateFileManagerFocus()

  private def handleActionModeToggle(): Unit =
    mode =
      if mode === DemoMode.EditorMode then DemoMode.FileManagerMode else DemoMode.EditorMode
    fileManagerFocusMode = FileManagerFocus.FileList
    updateFocusStates()

  private def statusLine: String =
    if mode === DemoMode.EditorMode then
      "Mode: Editor + slash/path/attachment/# autocomplete"
    else
      "Mode: File manager + live preview"

  private def fileManagerPathFor(path: File): String =
    path.getAbsolutePath

  private def refreshFileManagerDirectory(): Unit =
    fileManagerBusy = true
    fileManagerLoader.start()
    val directoryName = if fileDirectory.getName.isEmpty then "/" else fileDirectory.getName
    fileManagerLoader.setMessage(s"Reading: ${directoryName}")

    val (items, readError) =
      try
        Option(fileDirectory.listFiles()) match
          case Some(entries) =>
            val parent     = Option(fileDirectory.getParentFile)
            val sorted     =
              entries.sortBy(file => (if file.isDirectory then 0 else 1, file.getName.toLowerCase))
            val converted  = sorted.map(file =>
              FileManagerItem(
                file,
                file.isDirectory,
                if file.isDirectory then s"${file.getName}/" else file.getName,
                if file.isDirectory then "<dir>" else formatSize(file.length())
              )
            )
            val withParent =
              parent
                .filter(_ => fileDirectory.getParentFile !== null)
                .toVector
                .map(parentDir =>
                  FileManagerItem(
                    parentDir,
                    isDirectory = true,
                    displayName = "..",
                    detail = "Parent directory"
                  )
                )
            (withParent ++ converted, None)
          case None          =>
            (Vector.empty, Some(s"Cannot read directory: ${fileDirectory.getAbsolutePath}"))
      catch
        case e: SecurityException => (
            Vector.empty,
            Some(s"Cannot read directory: ${fileManagerPathFor(fileDirectory)} (${e.getMessage})")
          )

    fileManagerItems = items
    fileManagerSelection = if fileManagerItems.nonEmpty then 0 else -1
    fileManagerScrollOffset = 0
    previewCurrentSelection()
    fileManagerStatus.text = readError.getOrElse(statusLine)
    fileManagerLoader.stop()
    fileManagerBusy = false

  private def navigateToParent(): Unit =
    Option(fileDirectory.getParentFile).foreach { parent =>
      fileDirectory = parent
      fileManagerPathInput.setValue(fileManagerPathFor(fileDirectory))
      refreshFileManagerDirectory()
    }

  private def navigateToPathFromInput(raw: String): Unit =
    val trimmed = raw.trim
    val target  =
      if trimmed.isEmpty then fileDirectory
      else
        val candidate = File(trimmed)
        if candidate.isAbsolute then candidate else File(fileDirectory, trimmed)
    if !target.exists then
      fileManagerStatus.text = s"Path not found: ${target.getPath}"
      return

    if target.isDirectory then
      fileDirectory = target.getAbsoluteFile
      fileManagerPathInput.setValue(fileManagerPathFor(fileDirectory))
      refreshFileManagerDirectory()
      fileManagerStatus.text = s"Browsing ${fileManagerPathFor(fileDirectory)}"
      fileManagerFocusMode = FileManagerFocus.FileList
      updateFileManagerFocus()
    else if target.isFile then
      fileManagerPreview.text = readFilePreview(target)
      fileManagerStatus.text = s"Previewing file: ${target.getName}"
      fileManagerFocusMode = FileManagerFocus.FileList
      updateFileManagerFocus()

  private def openSelection(action: FileModeAction): Unit =
    if fileManagerItems.isEmpty then
      fileManagerStatus.text = "No item selected"
    else
      fileManagerItems.lift(fileManagerSelection).foreach { item =>
        if item.isDirectory then
          if action === FileModeAction.Preview then
            fileDirectory = item.path.getAbsoluteFile
            fileManagerPathInput.setValue(fileManagerPathFor(fileDirectory))
            refreshFileManagerDirectory()
            fileManagerStatus.text = s"Entered ${fileManagerPathFor(fileDirectory)}"
          else
            fileManagerStatus.text = "Cannot open a directory in editor"
        else
          action match
            case FileModeAction.Preview      => fileManagerPreview.text = readFilePreview(item.path)
            case FileModeAction.OpenInEditor =>
              fileDirectory = Option(item.path.getParentFile).getOrElse(fileDirectory)
              editor.setText(readFileContent(item.path))
              mode = DemoMode.EditorMode
              focus = Focus.EditorPane
              updateEditorFocus()
              fileManagerStatus.text = s"Loaded ${item.displayName} into editor"
      }

  private def ensureFileSelectionVisible(): Unit =
    if fileManagerItems.isEmpty then
      fileManagerSelection = -1
      fileManagerScrollOffset = 0
    else
      fileManagerSelection =
        math.max(0, math.min(fileManagerItems.length - 1, fileManagerSelection))
      val visible   = math.max(1, fileManagerMaxVisible)
      if fileManagerSelection < fileManagerScrollOffset then
        fileManagerScrollOffset = fileManagerSelection
      if fileManagerSelection >= fileManagerScrollOffset + visible then
        fileManagerScrollOffset = fileManagerSelection - visible + 1
      val maxOffset = math.max(0, fileManagerItems.length - visible)
      fileManagerScrollOffset = math.max(0, math.min(fileManagerScrollOffset, maxOffset))

  private def moveFileSelection(delta: Int): Unit =
    if fileManagerItems.nonEmpty then
      fileManagerSelection =
        math.max(0, math.min(fileManagerItems.length - 1, fileManagerSelection + delta))
      ensureFileSelectionVisible()
      previewCurrentSelection()

  private def moveFileSelectionByPage(direction: Int): Unit =
    if fileManagerItems.nonEmpty then
      fileManagerSelection =
        math.max(
          0,
          math.min(
            fileManagerItems.length - 1,
            fileManagerSelection + fileManagerMaxVisible * direction
          )
        )
      ensureFileSelectionVisible()
      previewCurrentSelection()

  private def previewCurrentSelection(): Unit =
    fileManagerItems.lift(fileManagerSelection) match
      case Some(item) if item.isDirectory =>
        val itemCount = fileDirectoryCount(item.path)
        fileManagerPreview.text = s"[dir] ${item.displayName}\nContains ${itemCount} entries"
      case Some(item)                     =>
        fileManagerPreview.text = readFilePreview(item.path)
      case None                           =>
        fileManagerPreview.text = "(no selection)"

  private def fileDirectoryCount(directory: File): Int =
    Option(directory.listFiles()).map(_.length).getOrElse(0)

  private def readFilePreview(file: File): String =
    if !file.exists || !file.canRead || file.isDirectory then
      "(not readable file)"
    else
      try
        Using.resource(Source.fromFile(file)) { source =>
          val lines   = source.getLines().toVector
          val shown   = lines.take(filePreviewMaxLines)
          val hidden  = lines.length - filePreviewMaxLines
          val tail    = if hidden > 0 then s"\n... (+${hidden} lines hidden)" else ""
          val snippet = shown.zipWithIndex.map((line, idx) =>
            s"${idx + 1.toString.padTo(2, '0')}: $line"
          ).mkString("\n")
          s"$snippet$tail"
        }
      catch
        case _: Throwable => "(unable to read file)"

  private def readFileContent(file: File): String =
    if !file.exists || !file.canRead || file.isDirectory then ""
    else
      try
        Using.resource(Source.fromFile(file)) { source =>
          source.getLines().mkString("\n")
        }
      catch
        case _: Throwable => ""

  private def formatSize(bytes: Long): String =
    if bytes < 1024 then s"${bytes} B"
    else if bytes < 1024 * 1024 then s"${bytes / 1024} KB"
    else if bytes < 1024 * 1024 * 1024 then s"${bytes / (1024 * 1024)} MB"
    else s"${bytes / (1024 * 1024 * 1024)} GB"

  private def addMessage(value: String): Unit =
    val trimmed = value.trim
    if trimmed.nonEmpty then
      if trimmed === "/clear" then
        messages = Vector.empty
        editor.setText("")
        updateMessages()
      else if trimmed === "/quit" then tui.requestExit()
      else if trimmed === "/help" then
        messages :+= "Autocomplete examples: /help, ./ filesystem paths, @ attachments, #bug/#docs/#demo/#release-notes tags"
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

  private def plainLoaderLine(component: Component, width: Int): String =
    component.render(width).headOption.map(Ansi.strip).getOrElse("").trim

  private def fit(value: String, width: Int): String =
    Ansi.truncateToWidth(value, width, "")

private object DemoRoot:
  private val Tags = Vector("#bug", "#docs", "#demo", "#release-notes")

  def tagTriggerSource: Either[scalatui.autocomplete.TriggerPrefixError, TriggerCompletionSource] =
    TriggerCompletionSource.fromPrefix(
      "#",
      _ =>
        Some(Tags.map(tag =>
          AutocompleteItem(tag.drop(1), tag, Some("application-owned demo tag"))
        ))
    )
