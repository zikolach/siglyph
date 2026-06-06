package scalatui.components

import scalatui.ansi.Ansi
import scalatui.autocomplete.*
import scalatui.core.*
import scalatui.editing.{EditorBuffer, EditorCursor, KillRing, UndoStack, WordNavigation}
import scalatui.syntax.Containment.*
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}
import scalatui.unicode.Unicode

/**
 * Multiline text editor component backed by an [[EditorBuffer]].
 *
 * The editor owns focus state, delegates logical text mutations to `EditorBuffer`, wraps text to
 * the requested component width, renders a fake inverse-video cursor with a zero-width cursor
 * marker when focused, and can integrate with autocomplete providers by showing selectable
 * suggestions through a TUI overlay host. Editing behavior includes `pi-tui`-aligned undo-only,
 * kill-ring/yank/yank-pop commands, word movement/deletion, and large-paste marker expansion.
 *
 * @param initialText
 *   starting logical editor contents
 * @param options
 *   initial callbacks, Enter behavior, and autocomplete configuration
 */
final class Editor(initialText: String = "", options: EditorOptions = EditorOptions())
    extends Component,
      Focusable,
      ContextualComponent,
      RenderOriginAware:
  private var buffer                                             = EditorBuffer(initialText)
  private var isFocused                                          = false
  private var context                                            = Option.empty[TUIContext]
  private var provider                                           = options.autocompleteProvider
  private var currentAutocompleteHandle                          = Option.empty[AutocompleteRequestHandle]
  private var currentAutocompleteOverlay                         = Option.empty[OverlayHandle]
  private var currentAutocomplete                                = Option.empty[Editor.AutocompleteState]
  private var autocompleteRequestToken                           = 0L
  private var currentRenderOrigin                                = Option.empty[ComponentRenderOrigin]
  private var lastRenderedVisualHeight                           = 0
  var enterBehavior: EditorEnterBehavior                         = options.enterBehavior
  var onChange: String => Unit                                   = options.onChange
  var onSubmit: String => Unit                                   = options.onSubmit
  var autocompleteMaxVisible: Int                                = math.max(1, options.autocompleteMaxVisible)
  var autocompleteTrigger: EditorAutocompleteTrigger             = options.autocompleteTrigger
  private var autocompletePlacement: EditorAutocompletePlacement = options.autocompletePlacement
  private val undoStack                                          = UndoStack[EditorBuffer.Snapshot]()
  private val killRing                                           = KillRing()
  private var lastAction                                         = Option.empty[Editor.Action]
  private var yankBaseSnapshot                                   = Option.empty[EditorBuffer.Snapshot]

  /** Current editor text joined with `\n` separators. */
  def text: String = synchronized(buffer.text)

  /** Current logical buffer lines. */
  def lines: Vector[String] = synchronized(buffer.lines)

  /** Current logical grapheme-cluster cursor position. */
  def cursor: EditorCursor = synchronized(buffer.cursor)

  /** Current autocomplete provider, if configured. */
  def autocompleteProvider: Option[AutocompleteProvider] = synchronized(provider)

  /** Update autocomplete suggestion placement strategy. */
  def setAutocompletePlacement(value: EditorAutocompletePlacement): Unit = synchronized {
    autocompletePlacement = value
    refreshAutocompleteOverlayPlacement(requestRender = true)
  }

  /** Use explicit placement options for the autocomplete suggestion overlay. */
  def setAutocompleteOverlayOptions(value: OverlayOptions): Unit =
    setAutocompletePlacement(EditorAutocompletePlacement.Custom(value))

  /** Replace the autocomplete provider and close any active autocomplete UI. */
  def setAutocompleteProvider(value: Option[AutocompleteProvider]): Unit = synchronized {
    cancelAutocomplete()
    provider = value
  }

  /** Replace all editor text and place the cursor at the end. */
  def setText(value: String): Unit = synchronized {
    cancelAutocomplete()
    buffer = EditorBuffer(value)
    resetEditingAction()
  }

  /** Move the editor cursor, clamped to valid logical buffer bounds. */
  def setCursor(cursor: EditorCursor): Unit = synchronized {
    cancelAutocomplete()
    buffer.setCursor(cursor)
    resetEditingAction()
  }

  /** Undo the most recent editor mutation, returning whether state changed. */
  def undo(): Boolean = synchronized {
    undoStack.pop() match
      case Some(snapshot) =>
        val before = buffer.text
        buffer.restore(snapshot)
        resetEditingAction()
        if buffer.text !== before then onChange(buffer.text)
        true
      case None           => false
  }

  /** Yank the most recent killed text into the editor. */
  def yank(): Boolean = synchronized {
    killRing.peek match
      case Some(text) =>
        val base    = buffer.snapshot
        val changed = mutate(_.insert(text), refreshAutocomplete = false)
        if changed !== InputResult.NoRender then
          yankBaseSnapshot = Some(base)
          lastAction = Some(Editor.Action.Yank)
          true
        else false
      case None       => false
  }

  /** Replace the previous yank with the next kill-ring candidate. */
  def yankPop(): Boolean = synchronized {
    if !lastAction.contains(Editor.Action.Yank) || killRing.length <= 1 then false
    else
      yankBaseSnapshot match
        case Some(base) =>
          pushUndoSnapshot()
          buffer.restore(base)
          killRing.rotate()
          val text = killRing.peek.getOrElse("")
          buffer.insert(text)
          onChange(buffer.text)
          refreshAutocompleteIfActive()
          lastAction = Some(Editor.Action.Yank)
          true
        case None       => false
  }

  /** Expand all compact large-paste markers into their original logical text. */
  def expandPasteMarkers(): Unit = synchronized {
    val before = buffer.text
    buffer.expandPasteMarkersInBuffer()
    if buffer.text !== before then onChange(buffer.text)
  }

  override def tuiContext_=(value: Option[TUIContext]): Unit = synchronized {
    if value.isEmpty then cancelAutocomplete()
    context = value
  }

  override def renderOrigin_=(value: Option[ComponentRenderOrigin]): Unit = synchronized {
    currentRenderOrigin = value
  }

  override def focused: Boolean = synchronized(isFocused)

  override def focused_=(value: Boolean): Unit = synchronized { isFocused = value }

  override def handleInput(input: TerminalInput): Unit =
    handleInputResult(input)
    ()

  override def handleInputResult(input: TerminalInput): InputResult =
    synchronized(handleInputLocked(input))

  override def render(width: Int): Vector[String] = synchronized {
    val layout = EditorLayout.fromBuffer(buffer, width)
    lastRenderedVisualHeight = layout.lines.length
    val lines  = layout.lines.zipWithIndex.map { (line, index) =>
      val rendered =
        if focused && currentAutocomplete.isEmpty && index === layout.cursor.row then
          renderCursor(line)
        else line.text
      Ansi.truncateToWidth(rendered, width, "")
    }
    refreshAutocompleteOverlayPlacement(requestRender = false)
    lines
  }

  private def handleInputLocked(input: TerminalInput): InputResult =
    if currentAutocomplete.nonEmpty then
      input match
        case TerminalInput.Key(TerminalKey.Tab, _)    => acceptAutocomplete()
        case TerminalInput.Key(TerminalKey.Escape, _) =>
          cancelAutocomplete()
          InputResult.Render
        case _                                        => handleEditingInput(input)
    else
      input match
        case TerminalInput.Key(TerminalKey.Tab, _) if provider.nonEmpty =>
          requestAutocomplete(force = true)
          InputResult.Render
        case _                                                          => handleEditingInput(input)

  private def handleEditingInput(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Character("a"), modifiers) if modifiers.ctrl             =>
      move(_.moveToLineStart())
    case TerminalInput.Key(TerminalKey.Character("e"), modifiers) if modifiers.ctrl             =>
      move(_.moveToLineEnd())
    case TerminalInput.Key(TerminalKey.Character("k"), modifiers) if modifiers.ctrl             =>
      killDeleteToEndOfLine()
    case TerminalInput.Key(TerminalKey.Character("u"), modifiers) if modifiers.ctrl             =>
      killDeleteToStartOfLine()
    case TerminalInput.Key(TerminalKey.Character("w"), modifiers) if modifiers.ctrl             =>
      killDeleteWordBackwards()
    case TerminalInput.Key(TerminalKey.Character("d"), modifiers) if modifiers.alt              =>
      killDeleteWordForwards()
    case TerminalInput.Key(TerminalKey.Character("d"), modifiers) if modifiers.ctrl             =>
      mutate(_.delete())
    case TerminalInput.Key(TerminalKey.Character("b"), modifiers) if modifiers.alt              =>
      move(_.moveWordBackwards())
    case TerminalInput.Key(TerminalKey.Character("f"), modifiers) if modifiers.alt              =>
      move(_.moveWordForwards())
    case TerminalInput.Key(TerminalKey.Character("y"), modifiers) if modifiers.ctrl             =>
      if yank() then InputResult.Render else InputResult.NoRender
    case TerminalInput.Key(TerminalKey.Character("y"), modifiers) if modifiers.alt              =>
      if yankPop() then InputResult.Render else InputResult.NoRender
    case TerminalInput.Key(TerminalKey.Character("-"), modifiers) if modifiers.ctrl             =>
      if undo() then InputResult.Render else InputResult.NoRender
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
      val result = mutate(_.insert(text), refreshAutocomplete = false)
      maybeTriggerAutocompleteAfterText(text)
      result
    case TerminalInput.Paste(text)                                                              =>
      val result = mutate(_.insertPaste(text), refreshAutocomplete = false)
      refreshAutocompleteIfActive()
      result
    case TerminalInput.Key(TerminalKey.Enter, modifiers)                                        =>
      handleEnter(modifiers)
    case TerminalInput.Key(TerminalKey.Backspace, modifiers) if modifiers.alt || modifiers.ctrl =>
      killDeleteWordBackwards()
    case TerminalInput.Key(TerminalKey.Backspace, _)                                            =>
      mutate(_.backspace())
    case TerminalInput.Key(TerminalKey.Delete, modifiers) if modifiers.alt                      =>
      killDeleteWordForwards()
    case TerminalInput.Key(TerminalKey.Delete, _)                                               =>
      mutate(_.delete())
    case TerminalInput.Key(TerminalKey.Left, modifiers) if modifiers.alt || modifiers.ctrl      =>
      move(_.moveWordBackwards())
    case TerminalInput.Key(TerminalKey.Right, modifiers) if modifiers.alt || modifiers.ctrl     =>
      move(_.moveWordForwards())
    case TerminalInput.Key(TerminalKey.Left, _)                                                 =>
      move(_.moveLeft())
    case TerminalInput.Key(TerminalKey.Right, _)                                                =>
      move(_.moveRight())
    case TerminalInput.Key(TerminalKey.Up, _)                                                   =>
      move(_.moveUp())
    case TerminalInput.Key(TerminalKey.Down, _)                                                 =>
      move(_.moveDown())
    case TerminalInput.Key(TerminalKey.Home, _)                                                 =>
      move(_.moveToLineStart())
    case TerminalInput.Key(TerminalKey.End, _)                                                  =>
      move(_.moveToLineEnd())
    case _                                                                                      => InputResult.Ignored

  private def handleEnter(modifiers: KeyModifiers): InputResult = enterBehavior match
    case EditorEnterBehavior.SubmitOnEnter(newlineModifiers) =>
      if newlineModifiers.contains_(modifiers) then mutate(_.insertNewline())
      else if modifiers.isEmpty then submit()
      else InputResult.Ignored
    case EditorEnterBehavior.NewlineOnEnter(submitModifiers) =>
      if submitModifiers.contains_(modifiers) then submit()
      else if modifiers.isEmpty || (modifiers === KeyModifiers(shift = true)) then
        mutate(_.insertNewline())
      else InputResult.Ignored

  private def submit(): InputResult =
    cancelAutocomplete()
    onSubmit(buffer.submitText)
    InputResult.NoRender

  private def move(operation: EditorBuffer => Unit): InputResult =
    val before = buffer.cursor
    operation(buffer)
    if buffer.cursor === before then InputResult.NoRender
    else
      resetEditingAction()
      refreshAutocompleteIfActive()
      InputResult.Render

  private def mutate(
      operation: EditorBuffer => Unit,
      refreshAutocomplete: Boolean = true
  ): InputResult =
    val snapshot     = buffer.snapshot
    val beforeText   = buffer.text
    val beforeCursor = buffer.cursor
    operation(buffer)
    val textChanged  = buffer.text !== beforeText
    val changed      = textChanged || (buffer.cursor !== beforeCursor)
    if textChanged then
      pushUndoSnapshot(snapshot)
      resetEditingAction()
      onChange(buffer.text)
    if changed then
      if refreshAutocomplete then refreshAutocompleteIfActive()
      InputResult.Render
    else InputResult.NoRender

  private def killDeleteWordBackwards(): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted =
      if buffer.cursor.column > 0 then
        val start = WordNavigation.findWordBackward(cs, buffer.cursor.column, buffer.isPasteMarker)
        cs.slice(start, buffer.cursor.column).mkString
      else ""
    performKill(deleted, prepend = true)(_.deleteWordBackwards())

  private def killDeleteWordForwards(): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted =
      if buffer.cursor.column < cs.length then
        val end = WordNavigation.findWordForward(cs, buffer.cursor.column, buffer.isPasteMarker)
        cs.slice(buffer.cursor.column, end).mkString
      else ""
    performKill(deleted, prepend = false)(_.deleteWordForwards())

  private def killDeleteToStartOfLine(): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted = cs.take(buffer.cursor.column).mkString
    performKill(deleted, prepend = true)(_.deleteToStartOfLine())

  private def killDeleteToEndOfLine(): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted = cs.drop(buffer.cursor.column).mkString
    performKill(deleted, prepend = false)(_.deleteToEndOfLine())

  private def performKill(
      deleted: String,
      prepend: Boolean
  )(operation: EditorBuffer => Unit): InputResult =
    val wasKill = lastAction.contains(Editor.Action.Kill)
    val result  = mutate(operation)
    if (result !== InputResult.NoRender) && deleted.nonEmpty then
      killRing.push(deleted, prepend = prepend, accumulate = wasKill)
      lastAction = Some(Editor.Action.Kill)
      yankBaseSnapshot = None
    result

  private def pushUndoSnapshot(snapshot: EditorBuffer.Snapshot = buffer.snapshot): Unit =
    undoStack.push(snapshot)

  private def resetEditingAction(): Unit =
    lastAction = None
    yankBaseSnapshot = None

  private def maybeTriggerAutocompleteAfterText(inserted: String): Unit =
    if provider.nonEmpty && autocompleteTrigger.triggerSlash && (inserted === "/") && currentLineBeforeCursor === "/"
    then
      requestAutocomplete(force = false)
    else refreshAutocompleteIfActive()

  private def refreshAutocompleteIfActive(): Unit =
    if currentAutocomplete.nonEmpty then requestAutocomplete(force = false)
    else if currentAutocompleteHandle.nonEmpty then cancelAutocompleteRequest()

  private def requestAutocomplete(force: Boolean): Unit =
    provider.foreach { autocompleteProvider =>
      cancelAutocompleteRequest()
      autocompleteRequestToken += 1
      val token     = autocompleteRequestToken
      val snapshot  = Editor.AutocompleteSnapshot(buffer.lines, buffer.cursor, buffer.text)
      val request   = AutocompleteRequest(snapshot.lines, snapshot.cursor, force)
      var completed = false
      val callback  = new AutocompleteCallback:
        override def complete(result: Option[AutocompleteSuggestions]): Unit =
          Editor.this.synchronized {
            if isCurrentAutocompleteRequest(token, snapshot) then
              completed = true
              currentAutocompleteHandle = None
              result.filter(_.items.nonEmpty) match
                case Some(suggestions) => showAutocomplete(suggestions, snapshot)
                case None              => closeAutocompleteOverlay()
              context.foreach(_.requestRender())
          }

        override def fail(error: Throwable): Unit = Editor.this.synchronized {
          if isCurrentAutocompleteRequest(token, snapshot) then
            completed = true
            currentAutocompleteHandle = None
            closeAutocompleteOverlay()
            context.foreach(_.requestRender())
        }
      val handle    = autocompleteProvider.requestSuggestions(request, callback)
      if isCurrentAutocompleteRequest(token, snapshot) && !completed then
        currentAutocompleteHandle = Some(handle)
    }

  private def isCurrentAutocompleteRequest(
      token: Long,
      snapshot: Editor.AutocompleteSnapshot
  ): Boolean =
    (token === autocompleteRequestToken) && (buffer.text === snapshot.text) && (buffer.cursor === snapshot.cursor)

  private def showAutocomplete(
      suggestions: AutocompleteSuggestions,
      snapshot: Editor.AutocompleteSnapshot
  ): Unit =
    val list             = SelectList(
      suggestions.items.map(item => SelectItem(item.value, item.label, item.description)),
      autocompleteMaxVisible
    )
    val overlayComponent = Editor.AutocompleteOverlay(this, list)
    val state            = Editor.AutocompleteState(suggestions, snapshot, list, overlayComponent)
    currentAutocomplete = Some(state)
    val options          = currentOverlayOptions
    currentAutocompleteOverlay match
      case Some(handle) => handle.update(overlayComponent, Some(options))
      case None         =>
        currentAutocompleteOverlay = context.map(_.overlays.showOverlay(overlayComponent, options))

  private def acceptAutocomplete(): InputResult =
    (provider, currentAutocomplete.flatMap(_.selectedItem)) match
      case (Some(autocompleteProvider), Some(item))
          if currentAutocomplete.exists(state =>
            isCurrentAutocompleteRequest(autocompleteRequestToken, state.snapshot)
          ) =>
        val beforeText = buffer.text
        val state      = currentAutocomplete.get
        val result     = autocompleteProvider.applyCompletion(CompletionRequest(
          buffer.lines,
          buffer.cursor,
          item,
          state.suggestions.prefix
        ))
        buffer = EditorBuffer.fromLines(result.lines, result.cursor)
        closeAutocompleteOverlay()
        cancelAutocompleteRequest()
        if buffer.text !== beforeText then onChange(buffer.text)
        InputResult.Render
      case _ =>
        cancelAutocomplete()
        InputResult.Render

  private def cancelAutocomplete(): Unit =
    cancelAutocompleteRequest()
    closeAutocompleteOverlay()

  private def cancelAutocompleteRequest(): Unit =
    autocompleteRequestToken += 1
    currentAutocompleteHandle.foreach(_.cancel())
    currentAutocompleteHandle = None

  private def closeAutocompleteOverlay(): Unit =
    currentAutocomplete = None
    currentAutocompleteOverlay.foreach(_.hide())
    currentAutocompleteOverlay = None

  private def currentLineBeforeCursor: String =
    Unicode.graphemeClusters(buffer.lines(buffer.cursor.line)).take(buffer.cursor.column).mkString

  private def refreshAutocompleteOverlayPlacement(requestRender: Boolean): Unit =
    currentAutocomplete.foreach { state =>
      currentAutocompleteOverlay.foreach(_.update(
        state.overlay,
        Some(currentOverlayOptions),
        requestRender = requestRender
      ))
    }

  private def currentOverlayOptions: OverlayOptions =
    val base = autocompletePlacement match
      case EditorAutocompletePlacement.AdjacentToEditor =>
        currentRenderOrigin match
          case Some(origin) =>
            OverlayOptions(
              width = Some(OverlaySize.Percent(100)),
              row = Some(OverlaySize.Absolute(origin.row + lastRenderedVisualHeight)),
              col = Some(OverlaySize.Absolute(origin.col)),
              focusCapturing = true
            )
          case None         => EditorOptions.FallbackAutocompleteOverlayOptions
      case EditorAutocompletePlacement.Custom(options)  => options
    base.copy(maxHeight = base.maxHeight.orElse(Some(OverlaySize.Absolute(autocompleteMaxVisible))))

  private def renderCursor(line: EditorVisualLine): String =
    val clusters      = buffer.clustersForLine(line.logicalLine)
    val segment       = clusters.slice(line.startColumn, line.endColumn)
    val cursorInLine  = buffer.cursor.column - line.startColumn
    val before        = segment.take(cursorInLine).mkString
    val cursorCluster = segment.lift(cursorInLine).getOrElse(" ")
    val after         = segment.drop(cursorInLine + 1).mkString
    s"$before${CursorMarker.Sequence}\u001b[7m$cursorCluster\u001b[27m$after"

object Editor:
  private enum Action derives CanEqual:
    case Kill, Yank

  final case class AutocompleteSnapshot(lines: Vector[String], cursor: EditorCursor, text: String)
      derives CanEqual

  final case class AutocompleteState(
      suggestions: AutocompleteSuggestions,
      snapshot: AutocompleteSnapshot,
      list: SelectList,
      overlay: AutocompleteOverlay
  ):
    def selectedItem: Option[AutocompleteItem] = list.selected.flatMap { item =>
      suggestions.items.find(candidate =>
        candidate.value === item.value && candidate.label === item.label && candidate.description === item.description
      )
    }

  final class AutocompleteOverlay(owner: Editor, list: SelectList) extends Component:
    override def render(width: Int): Vector[String] = list.render(width)

    override def handleInputResult(input: TerminalInput): InputResult = owner.synchronized {
      input match
        case TerminalInput.Key(TerminalKey.Up, _) | TerminalInput.Key(TerminalKey.Down, _)   =>
          list.handleInput(input)
          InputResult.Render
        case TerminalInput.Key(TerminalKey.Enter, _) | TerminalInput.Key(TerminalKey.Tab, _) =>
          owner.acceptAutocomplete()
        case TerminalInput.Key(TerminalKey.Escape, _)                                        =>
          owner.cancelAutocomplete()
          InputResult.Render
        case _                                                                               =>
          owner.handleEditingInput(input)
    }
