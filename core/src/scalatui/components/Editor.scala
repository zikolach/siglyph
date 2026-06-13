package scalatui.components

import scalatui.ansi.Ansi
import scalatui.autocomplete.*
import scalatui.core.*
import scalatui.editing.{EditorBuffer, EditorCursor, KillRing, UndoStack, WordNavigation}
import scalatui.syntax.Containment.*
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeybindingCommand, KeybindingManager, KeyModifiers, TerminalInput, TerminalKey}
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
  private var lastRenderedWidth                                  = 1
  var enterBehavior: EditorEnterBehavior                         = options.enterBehavior
  var onChange: String => Unit                                   = options.onChange
  var onSubmit: String => Unit                                   = options.onSubmit
  var autocompleteMaxVisible: Int                                = math.max(1, options.autocompleteMaxVisible)
  var autocompleteTrigger: EditorAutocompleteTrigger             = options.autocompleteTrigger
  private var autocompletePlacement: EditorAutocompletePlacement = options.autocompletePlacement
  private val keybindings                                        = options.keybindings
  private val undoStack                                          = UndoStack[EditorBuffer.Snapshot]()
  private val killRing                                           = KillRing()
  private var lastAction                                         = Option.empty[Editor.Action]
  private var yankBaseSnapshot                                   = Option.empty[EditorBuffer.Snapshot]
  private var history                                            = Vector.empty[String]
  private var historyIndex                                       = -1
  private val maxHistorySize                                     = 100
  private var jumpMode                                           = Option.empty[Editor.JumpDirection]

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
    historyIndex = -1
  }

  /** Move the editor cursor, clamped to valid logical buffer bounds. */
  def setCursor(cursor: EditorCursor): Unit = synchronized {
    cancelAutocomplete()
    buffer.setCursor(cursor)
    resetEditingAction()
    historyIndex = -1
  }

  /** Add a value to history browsing. */
  def addToHistory(value: String): Unit = synchronized {
    val text = value.trim
    if text.isEmpty then ()
    else if history.headOption.forall(_ !== text) then
      history = text +: history.take(maxHistorySize - 1)
    if history.length > maxHistorySize then history = history.take(maxHistorySize)
  }

  /** Undo the most recent editor mutation, returning whether state changed. */
  def undo(): Boolean = synchronized {
    undoStack.pop() match
      case Some(snapshot) =>
        val before = buffer.text
        buffer.restore(snapshot)
        resetEditingAction()
        historyIndex = -1
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
        case None       => false
        case Some(base) =>
          pushUndoSnapshot(base)
          buffer.restore(base)
          killRing.rotate()
          val text = killRing.peek.getOrElse("")
          buffer.insert(text)
          historyIndex = -1
          resetEditingAction()
          onChange(buffer.text)
          refreshAutocompleteIfActive()
          true
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
    lastRenderedWidth        = width
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
      handleAutocompleteInput(input)
    else
      input match
        case _ if keybindings.matches(input, KeybindingCommand.InputTab) && provider.nonEmpty =>
          requestAutocomplete(force = true)
          InputResult.Render
        case _                                                                             => handleEditingInput(input)

  private def handleAutocompleteInput(input: TerminalInput): InputResult =
    currentAutocomplete match
      case Some(state) if state.suggestions.items.nonEmpty =>
        if keybindings.matches(input, KeybindingCommand.SelectCancel) || keybindings.matches(input, KeybindingCommand.InputCopy) then
          cancelAutocomplete()
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectUp) then
          state.list.moveSelectionBy(-1)
          refreshAutocompleteOverlayPlacement(requestRender = true)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectDown) then
          state.list.moveSelectionBy(1)
          refreshAutocompleteOverlayPlacement(requestRender = true)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectPageUp) then
          state.list.moveSelectionByPage(math.max(1, autocompleteMaxVisible), -1)
          refreshAutocompleteOverlayPlacement(requestRender = true)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectPageDown) then
          state.list.moveSelectionByPage(math.max(1, autocompleteMaxVisible), 1)
          refreshAutocompleteOverlayPlacement(requestRender = true)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectConfirm) then
          handleAutocompleteSelection(submitOnSlash = true)
        else if keybindings.matches(input, KeybindingCommand.InputTab) then
          acceptAutocomplete()
        else
          handleEditingInput(input)
      case Some(_) if keybindings.matches(input, KeybindingCommand.SelectCancel) || keybindings.matches(input, KeybindingCommand.InputCopy) =>
        cancelAutocomplete()
        InputResult.Render
      case Some(_) if keybindings.matches(input, KeybindingCommand.InputTab) =>
        requestAutocomplete(force = true)
        InputResult.Render
      case Some(_) =>
        handleEditingInput(input)
      case None =>
        handleEditingInput(input)

  private def handleEditingInput(input: TerminalInput): InputResult =
    if jumpMode.nonEmpty then
      handleJumpInput(input)
    else input match
      case TerminalInput.Key(TerminalKey.Enter, modifiers) =>
        handleEnter(modifiers)
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorUp) =>
        val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
        if shouldUseHistoryUp(layout) then navigateHistory(-1) else move(_.moveUp())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorDown) =>
        val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
        if shouldUseHistoryDown(layout) then navigateHistory(1) else move(_.moveDown())
      case _ if keybindings.matches(input, KeybindingCommand.EditorPageUp) =>
        pageScroll(-1)
      case _ if keybindings.matches(input, KeybindingCommand.EditorPageDown) =>
        pageScroll(1)
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharForward) =>
        mutate(_.delete())
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordForward) =>
        killDeleteWordForwards()
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordBackward) =>
        killDeleteWordBackwards()
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharBackward) =>
        move(_.backspace())
      case _ if keybindings.matches(input, KeybindingCommand.EditorUndo) =>
        if undo() then InputResult.Render else InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.EditorYank) =>
        if yank() then InputResult.Render else InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.EditorYankPop) =>
        if yankPop() then InputResult.Render else InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordLeft) =>
        move(_.moveWordBackwards())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordRight) =>
        move(_.moveWordForwards())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLeft) =>
        move(_.moveLeft())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorRight) =>
        move(_.moveRight())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineStart) =>
        move(_.moveToLineStart())
      case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineEnd) =>
        move(_.moveToLineEnd())
      case _ if keybindings.matches(input, KeybindingCommand.EditorJumpForward) =>
        jumpMode = Some(Editor.JumpDirection.Forward)
        InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.EditorJumpBackward) =>
        jumpMode = Some(Editor.JumpDirection.Backward)
        InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.InputSubmit) =>
        submit()
      case _ if keybindings.matches(input, KeybindingCommand.InputTab) =>
        if provider.nonEmpty then
          requestAutocomplete(force = true)
          InputResult.Render
        else InputResult.NoRender
      case _ if keybindings.matches(input, KeybindingCommand.InputNewLine) =>
        mutate(_.insertNewline())
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineStart) =>
        killDeleteToStartOfLine()
      case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineEnd) =>
        killDeleteToEndOfLine()
      case TerminalInput.Paste(text) =>
        val result = mutate(_.insertPaste(text), refreshAutocomplete = false)
        refreshAutocompleteIfActive()
        result
      case TerminalInput.Key(TerminalKey.Character(text), modifiers)
          if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
        val result = mutate(_.insert(text), refreshAutocomplete = false)
        maybeTriggerAutocompleteAfterText(text)
        result
      case TerminalInput.Key(TerminalKey.Backspace, modifiers) if modifiers.alt || modifiers.ctrl =>
        killDeleteWordBackwards()
      case TerminalInput.Key(TerminalKey.Backspace, _) =>
        move(_.backspace())
      case TerminalInput.Key(TerminalKey.Delete, modifiers) if modifiers.alt =>
        killDeleteWordForwards()
      case TerminalInput.Key(TerminalKey.Delete, _) =>
        mutate(_.delete())
      case TerminalInput.Key(TerminalKey.Home, _) =>
        move(_.moveToLineStart())
      case TerminalInput.Key(TerminalKey.End, _) =>
        move(_.moveToLineEnd())
      case TerminalInput.Key(TerminalKey.PageUp, _) =>
        pageScroll(-1)
      case TerminalInput.Key(TerminalKey.PageDown, _) =>
        pageScroll(1)
      case TerminalInput.Key(TerminalKey.Left, modifiers) if modifiers.alt || modifiers.ctrl =>
        move(_.moveWordBackwards())
      case TerminalInput.Key(TerminalKey.Right, modifiers) if modifiers.alt || modifiers.ctrl =>
        move(_.moveWordForwards())
      case TerminalInput.Key(TerminalKey.Left, _) =>
        move(_.moveLeft())
      case TerminalInput.Key(TerminalKey.Right, _) =>
        move(_.moveRight())
      case TerminalInput.Key(TerminalKey.Up, _) =>
        move(_.moveUp())
      case TerminalInput.Key(TerminalKey.Down, _) =>
        move(_.moveDown())
      case _ => InputResult.Ignored

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
      historyIndex = -1
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
      historyIndex = -1
      onChange(buffer.text)
    if changed then
      if refreshAutocomplete then refreshAutocompleteIfActive()
      InputResult.Render
    else InputResult.NoRender

  private def shouldUseHistoryUp(layout: EditorLayout): Boolean =
    buffer.text.isEmpty || (historyIndex >= 0 && layout.cursor.row === 0)

  private def shouldUseHistoryDown(layout: EditorLayout): Boolean =
    historyIndex >= 0 && (buffer.lines.length === 1 || layout.cursor.row === layout.lines.length - 1)

  private def navigateHistory(direction: Int): InputResult =
    if history.isEmpty then InputResult.NoRender
    else
      val newIndex = historyIndex - direction
      if newIndex < -1 || newIndex >= history.length then InputResult.NoRender
      else
        if historyIndex === -1 && newIndex >= 0 then pushUndoSnapshot()
        historyIndex = newIndex
        if historyIndex === -1 then setTextInternal("")
        else setTextInternal(history(historyIndex), cursorAtLineStart = true)

  private def setTextInternal(text: String, cursorAtLineStart: Boolean = false): InputResult =
    val before = buffer.text
    buffer = EditorBuffer(text)
    if cursorAtLineStart then buffer.setCursor(EditorCursor(0, 0))
    if before === buffer.text then InputResult.NoRender
    else
      resetEditingAction()
      onChange(buffer.text)
      InputResult.Render

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

  private def handleJumpInput(input: TerminalInput): InputResult =
    if keybindings.matches(input, KeybindingCommand.EditorJumpForward) ||
      keybindings.matches(input, KeybindingCommand.EditorJumpBackward)
    then
      jumpMode = None
      InputResult.NoRender
    else
      printableCharacter(input) match
        case Some(text) =>
          val direction = jumpMode.get
          jumpMode = None
          if jumpToChar(text, direction) then InputResult.Render else InputResult.NoRender
        case None =>
          jumpMode = None
          handleEditingInput(input)

  private def printableCharacter(input: TerminalInput): Option[String] = input match
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey => Some(text)
    case _                                                                => None

  private def jumpToChar(text: String, direction: Editor.JumpDirection): Boolean =
    val lines = buffer.lines
    if lines.isEmpty || text.isEmpty then false
    else
      val isForward  = direction === Editor.JumpDirection.Forward
      val lineRange: Vector[Int] =
        if isForward then (buffer.cursor.line to lines.length - 1).toVector
        else (buffer.cursor.line to 0 by -1).toVector
      var moved      = false
      var foundLine  = buffer.cursor.line
      var foundCol   = buffer.cursor.column

      lineRange.foreach { line =>
        if !moved then
          val lineClusters = Unicode.graphemeClusters(lines(line))
          val start =
            if line === buffer.cursor.line then
              if isForward then buffer.cursor.column + 1 else buffer.cursor.column
            else 0
          val idx =
            if isForward then
              lineClusters.indexOf(text, start)
            else
              lineClusters.lastIndexOf(text, math.max(0, start))
          if idx >= 0 then
            moved = true
            foundLine = line
            foundCol = math.max(0, idx - 1)
      }

      if moved then
        val before = buffer.cursor
        buffer.setCursor(EditorCursor(foundLine, foundCol))
        refreshAutocompleteIfActive()
        if buffer.cursor !== before then
          resetEditingAction()
          true
        else false
      else false

  private def pageScroll(direction: Int): InputResult =
    val layout     = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
    val pageSize   = 5
    val targetRow  = layout.cursor.row + direction * pageSize
    val boundedRow = math.max(0, math.min(layout.lines.length - 1, targetRow))
    if boundedRow === layout.cursor.row then InputResult.NoRender else moveToVisualLine(layout, boundedRow)

  private def moveToVisualLine(layout: EditorLayout, targetRow: Int): InputResult =
    val targetLine     = layout.lines(targetRow)
    val lineClusters   = buffer.clustersForLine(targetLine.logicalLine)
    val lineSlice      = lineClusters.slice(targetLine.startColumn, targetLine.endColumn)
    val targetColIndex = visualColumnToClusterIndex(lineSlice, layout.cursor.column)
    val targetCursor   = EditorCursor(
      targetLine.logicalLine,
      math.min(targetLine.startColumn + targetColIndex, targetLine.endColumn)
    )
    val before = buffer.cursor
    buffer.setCursor(targetCursor)
    if before === targetCursor then InputResult.NoRender
    else
      resetEditingAction()
      refreshAutocompleteIfActive()
      InputResult.Render

  private def visualColumnToClusterIndex(clusters: Vector[String], visualColumn: Int): Int =
    if clusters.isEmpty || visualColumn <= 0 then 0
    else
      var offset  = 0
      var emitted = 0
      while offset < clusters.length && emitted < visualColumn do
        val cluster = clusters(offset)
        val width   = Unicode.graphemeWidth(cluster)
        if emitted + width > visualColumn then return offset
        emitted += width
        offset += 1
      offset

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
    handleAutocompleteSelection(submitOnSlash = false)

  private def handleAutocompleteSelection(submitOnSlash: Boolean = false): InputResult =
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
        if submitOnSlash && state.suggestions.prefix.startsWith("/") then submit()
        else InputResult.Render
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

  private enum JumpDirection derives CanEqual:
    case Forward, Backward

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
      owner.handleAutocompleteInput(input)
    }
