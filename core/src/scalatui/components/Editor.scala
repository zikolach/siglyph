package scalatui.components

import scalatui.autocomplete.*
import scalatui.core.*
import scalatui.editing.{EditorBuffer, EditorCursor, KillRing, UndoStack, WordNavigation}
import scalatui.syntax.Containment.*
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeybindingCommand,
  KeybindingManager,
  KeyModifiers,
  MouseAction,
  MouseInputContext,
  MouseWheelDirection,
  TerminalInput,
  TerminalKey
}
import scalatui.unicode.Unicode

/**
 * Multiline text editor component backed by a [[scalatui.editing.EditorBuffer]].
 *
 * The editor owns focus state, delegates logical text mutations to `EditorBuffer`, wraps text to
 * the requested component width, renders a fake inverse-video cursor with structural cursor
 * metadata when focused, and can integrate with autocomplete providers by showing selectable
 * suggestions through a TUI overlay host. Editing behavior includes `pi-tui`-aligned undo-only,
 * kill-ring/yank/yank-pop commands, word movement/deletion, and large-paste marker expansion.
 * Logical cursor and streamed-paste counts use Unicode 17.0.0 UAX #29 default extended grapheme
 * clusters on JVM and Scala Native. Bounded segmentation state does not limit retained editor
 * content, and terminal display width remains a separate project-specific policy. Layout measures
 * sanitized final printable geometry. Rejected controls may expand to several wrapped display units
 * that retain one source range; supported bounded SGR and OSC 8 remain atomic and executable.
 * Autocomplete ownership suppresses Editor cursor metadata. Non-positive widths suppress printable
 * output and cursor metadata. At a positive impossible width, rendering omits the over-wide cursor
 * unit but retains its logical ownership and application content, places the cursor at visual
 * column zero, and emits no replacement or partial cluster.
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
      RenderOriginAware,
      MouseInputHandler:
  private var buffer                                             = EditorBuffer(initialText)
  private var isFocused                                          = false
  private var context                                            = Option.empty[TUIContext]
  private var provider                                           = options.autocompleteProvider
  private var currentAutocompleteHandle                          = Option.empty[AutocompleteRequestHandle]
  private var pendingAutocompleteRefresh                         = Option.empty[AutocompleteRequestHandle]
  private var pendingAutocompleteRefreshToken                    = 0L
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
  private val autocompleteDebouncer                              = options.autocompleteDebouncer
  private val keybindings                                        = options.keybindings
  private val undoStack                                          = UndoStack[EditorBuffer.Snapshot]()
  private val killRing                                           = KillRing()
  private var lastAction                                         = Option.empty[Editor.Action]
  private var yankBaseSnapshot                                   = Option.empty[EditorBuffer.Snapshot]
  private var history                                            = Vector.empty[String]
  private var historyIndex                                       = -1
  private val maxHistorySize                                     = 100
  private var jumpMode                                           = Option.empty[Editor.JumpDirection]
  private var pasteSession                                       = Option.empty[Editor.PasteSession]

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

  /**
   * Insert application-supplied text at the current cursor.
   *
   * The insertion uses the same editor-buffer path as typed text and paste input. Newline forms are
   * normalized, large values can become compact paste markers, one undo snapshot is created for the
   * call, `onChange` is invoked when text changes, active autocomplete is refreshed, and an
   * attached TUI context is asked to render changed visible state.
   */
  def insertAtCursor(text: String): Unit = synchronized {
    val result = mutate(_.insert(text), refreshAutocomplete = false)
    if result !== InputResult.NoRender then
      refreshAutocompleteIfActive()
      context.foreach(_.requestRender())
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

  override def handleMouse(context: MouseInputContext): InputResult = synchronized {
    context.input.action match
      case MouseAction.Wheel(MouseWheelDirection.Up)   => pageScroll(-1)
      case MouseAction.Wheel(MouseWheelDirection.Down) => pageScroll(1)
      case _                                           => InputResult.Ignored
  }

  override def render(width: Int): ComponentRender = synchronized {
    val plan             = EditorLayout.renderPlan(buffer, width)
    lastRenderedVisualHeight = plan.layout.lines.length
    lastRenderedWidth = width
    val cursorPlacements = Vector.newBuilder[CursorPlacement]
    val lines            = plan.rows.zipWithIndex.map { (row, index) =>
      if focused && currentAutocomplete.isEmpty && index === plan.layout.cursor.row then
        if row.omitted then
          if width > 0 then cursorPlacements += CursorPlacement(index, 0)
          ""
        else
          val (line, column) = row.focusedText(plan.cursorBoundary, width)
          column.foreach(value => cursorPlacements += CursorPlacement(index, value))
          line
      else row.normalText(width)
    }
    refreshAutocompleteOverlayPlacement(requestRender = false)
    ComponentRender(lines, Vector.empty, cursorPlacements.result())
  }

  private def handleInputLocked(input: TerminalInput): InputResult =
    val pasteCommit = input match
      case TerminalInput.PasteStart | TerminalInput.PasteChunk(_) | TerminalInput.PasteEnd =>
        InputResult.Ignored
      case _ if pasteSession.nonEmpty                                                      =>
        commitPaste()
      case _                                                                               =>
        InputResult.Ignored
    val result      =
      if currentAutocomplete.nonEmpty then
        handleAutocompleteInput(input)
      else
        input match
          case _ if keybindings.matches(input, KeybindingCommand.InputTab) && provider.nonEmpty =>
            requestAutocomplete(force = true)
            InputResult.Render
          case _                                                                                =>
            handleEditingInput(input)
    combineInputResults(pasteCommit, result)

  private def handleAutocompleteInput(input: TerminalInput): InputResult =
    currentAutocomplete match
      case Some(state) if state.suggestions.items.nonEmpty                   =>
        if keybindings.matches(
            input,
            KeybindingCommand.SelectCancel
          ) || keybindings.matches(input, KeybindingCommand.InputCopy)
        then
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
      case Some(_)
          if keybindings.matches(
            input,
            KeybindingCommand.SelectCancel
          ) || keybindings.matches(input, KeybindingCommand.InputCopy) =>
        cancelAutocomplete()
        InputResult.Render
      case Some(_) if keybindings.matches(input, KeybindingCommand.InputTab) =>
        requestAutocomplete(force = true)
        InputResult.Render
      case Some(_)                                                           =>
        handleEditingInput(input)
      case None                                                              =>
        handleEditingInput(input)

  private def handleEditingInput(input: TerminalInput): InputResult =
    if jumpMode.nonEmpty then
      handleJumpInput(input)
    else
      input match {
        case TerminalInput.Key(TerminalKey.Enter, modifiers)                                    =>
          handleEnter(modifiers)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorUp)                  =>
          val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
          if shouldUseHistoryUp(layout) then navigateHistory(-1) else move(_.moveUp())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorDown)                =>
          val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
          if shouldUseHistoryDown(layout) then navigateHistory(1) else move(_.moveDown())
        case _ if keybindings.matches(input, KeybindingCommand.EditorPageUp)                    =>
          pageScroll(-1)
        case _ if keybindings.matches(input, KeybindingCommand.EditorPageDown)                  =>
          pageScroll(1)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharForward)         =>
          mutate(_.delete())
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordForward)         =>
          killDeleteWordForwards()
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordBackward)        =>
          killDeleteWordBackwards()
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharBackward)        =>
          move(_.backspace())
        case _ if keybindings.matches(input, KeybindingCommand.EditorUndo)                      =>
          if undo() then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorYank)                      =>
          if yank() then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorYankPop)                   =>
          if yankPop() then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordLeft)            =>
          move(_.moveWordBackwards())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordRight)           =>
          move(_.moveWordForwards())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLeft)                =>
          move(_.moveLeft())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorRight)               =>
          move(_.moveRight())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineStart)           =>
          move(_.moveToLineStart())
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineEnd)             =>
          move(_.moveToLineEnd())
        case _ if keybindings.matches(input, KeybindingCommand.EditorJumpForward)               =>
          jumpMode = Some(Editor.JumpDirection.Forward)
          InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorJumpBackward)              =>
          jumpMode = Some(Editor.JumpDirection.Backward)
          InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.InputSubmit)                     =>
          submit()
        case _ if keybindings.matches(input, KeybindingCommand.InputTab)                        =>
          if provider.nonEmpty then
            requestAutocomplete(force = true)
            InputResult.Render
          else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.InputNewLine)                    =>
          mutate(_.insertNewline())
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineStart)         =>
          killDeleteToStartOfLine()
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineEnd)           =>
          killDeleteToEndOfLine()
        case TerminalInput.PasteStart                                                           =>
          val previous = commitPaste()
          pasteSession = Some(Editor.PasteSession(buffer.snapshot))
          previous
        case TerminalInput.PasteChunk(chunk)                                                    =>
          pasteSession.foreach(_.process(chunk))
          InputResult.NoRender
        case TerminalInput.PasteEnd                                                             =>
          commitPaste()
        case TerminalInput.Key(TerminalKey.Character(text), modifiers)
            if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
          val result = mutate(_.insert(text), refreshAutocomplete = false)
          maybeTriggerAutocompleteAfterText(text)
          result
        case TerminalInput.Key(TerminalKey.Backspace, modifiers)
            if modifiers.alt || modifiers.ctrl =>
          killDeleteWordBackwards()
        case TerminalInput.Key(TerminalKey.Backspace, _)                                        =>
          move(_.backspace())
        case TerminalInput.Key(TerminalKey.Delete, modifiers) if modifiers.alt                  =>
          killDeleteWordForwards()
        case TerminalInput.Key(TerminalKey.Delete, _)                                           =>
          mutate(_.delete())
        case TerminalInput.Key(TerminalKey.Home, _)                                             =>
          move(_.moveToLineStart())
        case TerminalInput.Key(TerminalKey.End, _)                                              =>
          move(_.moveToLineEnd())
        case TerminalInput.Key(TerminalKey.PageUp, _)                                           =>
          pageScroll(-1)
        case TerminalInput.Key(TerminalKey.PageDown, _)                                         =>
          pageScroll(1)
        case TerminalInput.Key(TerminalKey.Left, modifiers) if modifiers.alt || modifiers.ctrl  =>
          move(_.moveWordBackwards())
        case TerminalInput.Key(TerminalKey.Right, modifiers) if modifiers.alt || modifiers.ctrl =>
          move(_.moveWordForwards())
        case TerminalInput.Key(TerminalKey.Left, _)                                             =>
          move(_.moveLeft())
        case TerminalInput.Key(TerminalKey.Right, _)                                            =>
          move(_.moveRight())
        case TerminalInput.Key(TerminalKey.Up, _)                                               =>
          move(_.moveUp())
        case TerminalInput.Key(TerminalKey.Down, _)                                             =>
          move(_.moveDown())
        case _                                                                                  => InputResult.Ignored
      }

  private def commitPaste(): InputResult =
    pasteSession match
      case None          => InputResult.NoRender
      case Some(session) =>
        pasteSession = None
        session.finish()
        if session.isEmpty then InputResult.NoRender
        else
          if session.isLarge then
            buffer.insertLargePaste(
              session.materialize(),
              session.lineCount,
              session.graphemeCount
            )
          else buffer.insertPasteChunks(session.chunks)
          pushUndoSnapshot(session.baseSnapshot)
          resetEditingAction()
          historyIndex = -1
          onChange(buffer.text)
          refreshAutocompleteIfActive()
          InputResult.Render

  private def combineInputResults(first: InputResult, second: InputResult): InputResult =
    (first, second) match
      case (InputResult.Exit, _) | (_, InputResult.Exit)                   => InputResult.Exit
      case (InputResult.Handled(true), _) | (_, InputResult.Handled(true)) => InputResult.Render
      case (InputResult.Handled(false), InputResult.Ignored)               => InputResult.NoRender
      case (InputResult.Ignored, InputResult.Handled(false))               => InputResult.NoRender
      case (InputResult.Handled(false), InputResult.Handled(false))        => InputResult.NoRender
      case (InputResult.Ignored, InputResult.Ignored)                      => InputResult.Ignored

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
    undoStack.clear()
    resetEditingAction()
    InputResult.Render

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
        case None       =>
          jumpMode = None
          handleEditingInput(input)

  private def printableCharacter(input: TerminalInput): Option[String] = input match
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey => Some(text)
    case _ => None

  private def jumpToChar(text: String, direction: Editor.JumpDirection): Boolean =
    val lines = buffer.lines
    if lines.isEmpty || text.isEmpty then false
    else
      val isForward              = direction === Editor.JumpDirection.Forward
      val lineRange: Vector[Int] =
        if isForward then (buffer.cursor.line to lines.length - 1).toVector
        else (buffer.cursor.line to 0 by -1).toVector
      var moved                  = false
      var foundLine              = buffer.cursor.line
      var foundCol               = buffer.cursor.column

      lineRange.foreach { line =>
        if !moved then
          val lineClusters = Unicode.graphemeClusters(lines(line))
          val start        =
            if line === buffer.cursor.line then
              if isForward then buffer.cursor.column + 1 else buffer.cursor.column
            else 0
          val idx          =
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
    val plan       = EditorLayout.renderPlan(buffer, math.max(1, lastRenderedWidth))
    val pageSize   = 5
    val targetRow  = plan.layout.cursor.row + direction * pageSize
    val boundedRow = math.max(0, math.min(plan.layout.lines.length - 1, targetRow))
    if boundedRow === plan.layout.cursor.row then InputResult.NoRender
    else moveToVisualLine(plan, boundedRow)

  private def moveToVisualLine(plan: EditorRenderPlan, targetRow: Int): InputResult =
    val targetLine   = plan.layout.lines(targetRow)
    val targetCursor = EditorCursor(
      targetLine.logicalLine,
      plan.sourceColumnAt(targetRow, plan.layout.cursor.column)
    )
    val before       = buffer.cursor
    buffer.setCursor(targetCursor)
    if before === targetCursor then InputResult.NoRender
    else
      resetEditingAction()
      refreshAutocompleteIfActive()
      InputResult.Render

  private def maybeTriggerAutocompleteAfterText(inserted: String): Unit =
    if provider.nonEmpty && autocompleteTrigger.triggerSlash && (inserted === "/") && currentLineBeforeCursor === "/"
    then
      requestAutocomplete(force = false)
    else refreshAutocompleteIfActive()

  private def refreshAutocompleteIfActive(): Unit =
    if currentAutocomplete.nonEmpty || currentAutocompleteHandle.nonEmpty || pendingAutocompleteRefresh.nonEmpty
    then scheduleAutocompleteRefresh()

  private def scheduleAutocompleteRefresh(): Unit =
    if provider.nonEmpty then
      cancelAutocompleteRequest()
      cancelPendingAutocompleteRefresh()
      pendingAutocompleteRefreshToken += 1
      val refreshToken = pendingAutocompleteRefreshToken
      val scheduled    = autocompleteDebouncer.schedule { () =>
        val start = Editor.this.synchronized {
          if refreshToken === pendingAutocompleteRefreshToken then
            pendingAutocompleteRefresh = None
            prepareAutocompleteRequestLocked(force = false)
          else None
        }
        start.foreach(_.apply())
      }
      if !scheduled.ranSynchronously then pendingAutocompleteRefresh = Some(scheduled.handle)

  private def requestAutocomplete(force: Boolean): Unit =
    cancelPendingAutocompleteRefresh()
    requestAutocompleteNow(force)

  private def requestAutocompleteNow(force: Boolean): Unit =
    prepareAutocompleteRequestLocked(force).foreach(_.apply())

  private def prepareAutocompleteRequestLocked(force: Boolean): Option[() => Unit] =
    provider.map { autocompleteProvider =>
      cancelAutocompleteRequest()
      autocompleteRequestToken += 1
      val token     = autocompleteRequestToken
      val snapshot  = Editor.AutocompleteSnapshot(buffer.lines, buffer.cursor, buffer.text)
      val request   = AutocompleteRequest(snapshot.lines, snapshot.cursor, force)
      var completed = false
      val callback  = new AutocompleteCallback:
        override def complete(result: Option[AutocompleteSuggestions]): Unit =
          val (overlayOperation, renderContext) = Editor.this.synchronized {
            if isCurrentAutocompleteRequest(token, snapshot) then
              completed = true
              currentAutocompleteHandle = None
              val operation = result.filter(_.items.nonEmpty) match
                case Some(suggestions)
                    if shouldAutoApplySingleForcedCompletion(request, suggestions) =>
                  prepareApplyAutocompleteLocked(autocompleteProvider, suggestions, snapshot)
                case Some(suggestions) =>
                  prepareShowAutocompleteLocked(suggestions, snapshot, token)
                case None              => prepareCloseAutocompleteOverlayLocked()
              (operation, context)
            else (None, None)
          }
          overlayOperation.foreach(_.apply())
          renderContext.foreach(_.requestRender())

        override def fail(error: Throwable): Unit =
          val (overlayOperation, renderContext) = Editor.this.synchronized {
            if isCurrentAutocompleteRequest(token, snapshot) then
              completed = true
              currentAutocompleteHandle = None
              (prepareCloseAutocompleteOverlayLocked(), context)
            else (None, None)
          }
          overlayOperation.foreach(_.apply())
          renderContext.foreach(_.requestRender())
      () =>
        val handle = autocompleteProvider.requestSuggestions(request, callback)
        Editor.this.synchronized {
          val current = isCurrentAutocompleteRequest(token, snapshot)
          if current && !completed then
            currentAutocompleteHandle = Some(handle)
          else if !current then handle.cancel()
        }
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
    prepareShowAutocompleteLocked(
      suggestions,
      snapshot,
      autocompleteRequestToken
    ).foreach(_.apply())

  private def prepareShowAutocompleteLocked(
      suggestions: AutocompleteSuggestions,
      snapshot: Editor.AutocompleteSnapshot,
      token: Long
  ): Option[() => Unit] =
    val list             = SelectList(
      suggestions.items.map(item => SelectItem(item.value, item.label, item.description)),
      autocompleteMaxVisible
    )
    val overlayComponent = Editor.AutocompleteOverlay(this, list)
    val state            = Editor.AutocompleteState(suggestions, snapshot, list, overlayComponent)
    currentAutocomplete = Some(state)
    val options          = currentOverlayOptions
    currentAutocompleteOverlay match
      case Some(handle) =>
        Some { () =>
          val current = Editor.this.synchronized {
            isCurrentAutocompleteRequest(
              token,
              snapshot
            ) && currentAutocomplete.exists(_.overlay eq overlayComponent)
          }
          if current then handle.update(overlayComponent, Some(options))
        }
      case None         =>
        val renderContext = context
        Some { () =>
          val shown      = renderContext.map(_.overlays.showOverlay(overlayComponent, options))
          val staleShown = Editor.this.synchronized {
            if isCurrentAutocompleteRequest(
                token,
                snapshot
              ) && currentAutocomplete.exists(_.overlay eq overlayComponent)
            then
              currentAutocompleteOverlay = shown
              None
            else shown
          }
          staleShown.foreach(_.hide())
        }

  private def shouldAutoApplySingleForcedCompletion(
      request: AutocompleteRequest,
      suggestions: AutocompleteSuggestions
  ): Boolean =
    request.force && options.autoApplySingleForcedCompletion && suggestions.items.length === 1

  private def prepareApplyAutocompleteLocked(
      autocompleteProvider: AutocompleteProvider,
      suggestions: AutocompleteSuggestions,
      snapshot: Editor.AutocompleteSnapshot
  ): Option[() => Unit] =
    suggestions.items.headOption match
      case Some(item) =>
        val beforeText = buffer.text
        val result     = autocompleteProvider.applyCompletion(CompletionRequest(
          snapshot.lines,
          snapshot.cursor,
          item,
          suggestions.prefix
        ))
        buffer = EditorBuffer.fromLines(result.lines, result.cursor)
        currentAutocompleteHandle = None
        autocompleteRequestToken += 1
        if buffer.text !== beforeText then
          resetEditingAction()
          historyIndex = -1
          onChange(buffer.text)
        prepareCloseAutocompleteOverlayLocked()
      case None       => prepareCloseAutocompleteOverlayLocked()

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
    cancelPendingAutocompleteRefresh()
    cancelAutocompleteRequest()
    closeAutocompleteOverlay()

  private def cancelPendingAutocompleteRefresh(): Unit =
    pendingAutocompleteRefreshToken += 1
    pendingAutocompleteRefresh.foreach(_.cancel())
    pendingAutocompleteRefresh = None

  private def cancelAutocompleteRequest(): Unit =
    autocompleteRequestToken += 1
    currentAutocompleteHandle.foreach(_.cancel())
    currentAutocompleteHandle = None

  private def closeAutocompleteOverlay(): Unit =
    prepareCloseAutocompleteOverlayLocked().foreach(_.apply())

  private def prepareCloseAutocompleteOverlayLocked(): Option[() => Unit] =
    currentAutocomplete = None
    val handle = currentAutocompleteOverlay
    currentAutocompleteOverlay = None
    handle.map(overlay => () => overlay.hide())

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

  private def renderCurrentAutocompleteOverlay(width: Int): ComponentRender = synchronized {
    currentAutocomplete.map(_.list.render(width)).getOrElse(ComponentRender.empty)
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

object Editor:
  private final class PasteSession(val baseSnapshot: EditorBuffer.Snapshot):
    private val decoder         = scalatui.terminal.TerminalUtf8Decoder()
    private val graphemeCounter = Unicode.IncrementalGraphemeCounter()
    private val blocks          = scala.collection.mutable.ArrayBuffer(StringBuilder())
    private var pendingCr       = false
    private var newlineCount    = 0L
    private var contentLength   = 0L

    def process(chunk: scalatui.terminal.TerminalInputChunk): Unit =
      appendDecoded(decoder.process(chunk))

    def finish(): Unit =
      appendDecoded(decoder.flush())
      if pendingCr then
        pendingCr = false
        appendNormalized("\n")

    def isEmpty: Boolean = contentLength === 0L

    def lineCount: Long = newlineCount + 1L

    def graphemeCount: Long = graphemeCounter.count

    def isLarge: Boolean =
      lineCount > EditorBuffer.LargePasteLineThreshold ||
        graphemeCount > EditorBuffer.LargePasteCharacterThreshold

    def chunks: Vector[String] = blocks.iterator.filter(_.nonEmpty).map(_.result()).toVector

    def materialize(): String =
      val result = StringBuilder()
      blocks.foreach(result.append)
      result.result()

    private def appendDecoded(value: String): Unit =
      if value.nonEmpty then
        val normalized = StringBuilder()
        var index      = 0
        if pendingCr then
          pendingCr = false
          normalized += '\n'
          if value.head === '\n' then index = 1
        while index < value.length do
          value.charAt(index) match
            case '\r' =>
              if index + 1 < value.length then
                normalized += '\n'
                if value.charAt(index + 1) === '\n' then index += 1
              else pendingCr = true
            case char => normalized += char
          index += 1
        appendNormalized(normalized.result())

    private def appendNormalized(value: String): Unit =
      if value.nonEmpty then
        graphemeCounter.process(value)
        newlineCount += value.count(_ === '\n').toLong
        contentLength += value.length
        var index = 0
        while index < value.length do
          val count = Character.charCount(value.codePointAt(index))
          if blocks.last.nonEmpty && blocks.last.length + count > Editor.PasteBlockSize then
            blocks += StringBuilder()
          blocks.last.append(value.substring(index, index + count))
          index += count

  private val PasteBlockSize = 4096

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

  final class AutocompleteOverlay(owner: Editor, list: SelectList) extends Component,
        MouseInputHandler:
    override def render(width: Int): ComponentRender = owner.renderCurrentAutocompleteOverlay(width)

    override def handleMouse(context: MouseInputContext): InputResult = owner.synchronized {
      context.input.action match
        case MouseAction.Wheel(MouseWheelDirection.Up)   =>
          val result = list.moveSelectionByResult(-1)
          if result === InputResult.Render then
            owner.refreshAutocompleteOverlayPlacement(requestRender = true)
          result
        case MouseAction.Wheel(MouseWheelDirection.Down) =>
          val result = list.moveSelectionByResult(1)
          if result === InputResult.Render then
            owner.refreshAutocompleteOverlayPlacement(requestRender = true)
          result
        case _                                           => InputResult.Ignored
    }

    override def handleInputResult(input: TerminalInput): InputResult = owner.synchronized {
      owner.handleAutocompleteInput(input)
    }
