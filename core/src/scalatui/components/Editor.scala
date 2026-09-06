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
 * Public mutation methods serialize with input handling and render snapshots on JVM and Scala
 * Native. Each text mutation commits before `onChange` runs. Submit callbacks receive the committed
 * submit text. Change and submit callbacks, autocomplete provider calls, completion application,
 * cancellation handles, overlay operations, and TUI render requests run after the editor state
 * boundary is released. Callbacks may reenter Editor or TUI APIs. Autocomplete completions commit
 * only while their captured request generation, text, and cursor remain current.
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
  private val stateBoundary                                         = ComponentStateBoundary()
  private var buffer                                                = EditorBuffer(initialText)
  private var isFocused                                             = false
  private var context                                               = Option.empty[TUIContext]
  private var provider                                              = options.autocompleteProvider
  private var currentAutocompleteHandle                             = Option.empty[AutocompleteRequestHandle]
  private var autocompleteAwaitingHandle                            = Option.empty[Long]
  private var pendingAutocompleteRefresh                            = Option.empty[AutocompleteRequestHandle]
  private var pendingAutocompleteRefreshToken                       = 0L
  private var currentAutocompleteOverlay                            = Option.empty[OverlayHandle]
  private var currentAutocomplete                                   = Option.empty[Editor.AutocompleteState]
  private var autocompleteRequestToken                              = 0L
  private var currentRenderOrigin                                   = Option.empty[ComponentRenderOrigin]
  private var lastRenderedVisualHeight                              = 0
  private var lastRenderedWidth                                     = 1
  private var currentEnterBehavior: EditorEnterBehavior             = options.enterBehavior
  private var changeCallback: String => Unit                        = options.onChange
  private var submitCallback: String => Unit                        = options.onSubmit
  private var currentAutocompleteMaxVisible: Int                    = math.max(1, options.autocompleteMaxVisible)
  private var currentAutocompleteTrigger: EditorAutocompleteTrigger = options.autocompleteTrigger
  private var autocompletePlacement: EditorAutocompletePlacement    = options.autocompletePlacement
  private val autocompleteDebouncer                                 = options.autocompleteDebouncer
  private val keybindings                                           = options.keybindings
  private val undoStack                                             = UndoStack[EditorBuffer.Snapshot]()
  private val killRing                                              = KillRing()
  private var lastAction                                            = Option.empty[Editor.Action]
  private var yankBaseSnapshot                                      = Option.empty[EditorBuffer.Snapshot]
  private var history                                               = Vector.empty[String]
  private var historyIndex                                          = -1
  private val maxHistorySize                                        = 100
  private var jumpMode                                              = Option.empty[Editor.JumpDirection]
  private var pasteSession                                          = Option.empty[Editor.PasteSession]

  /** Current editor text joined with `\n` separators. */
  def text: String = stateBoundary(buffer.text)

  /** Current logical buffer lines. */
  def lines: Vector[String] = stateBoundary(buffer.lines)

  /** Current logical grapheme-cluster cursor position. */
  def cursor: EditorCursor = stateBoundary(buffer.cursor)

  def enterBehavior: EditorEnterBehavior                            = stateBoundary(currentEnterBehavior)
  def enterBehavior_=(value: EditorEnterBehavior): Unit             = stateBoundary {
    currentEnterBehavior = value
  }
  def onChange: String => Unit                                      = stateBoundary(changeCallback)
  def onChange_=(value: String => Unit): Unit                       = stateBoundary { changeCallback = value }
  def onSubmit: String => Unit                                      = stateBoundary(submitCallback)
  def onSubmit_=(value: String => Unit): Unit                       = stateBoundary { submitCallback = value }
  def autocompleteMaxVisible: Int                                   = stateBoundary(currentAutocompleteMaxVisible)
  def autocompleteMaxVisible_=(value: Int): Unit                    = stateBoundary {
    currentAutocompleteMaxVisible = value
  }
  def autocompleteTrigger: EditorAutocompleteTrigger                = stateBoundary(currentAutocompleteTrigger)
  def autocompleteTrigger_=(value: EditorAutocompleteTrigger): Unit = stateBoundary {
    currentAutocompleteTrigger = value
  }

  /** Current autocomplete provider, if configured. */
  def autocompleteProvider: Option[AutocompleteProvider] = stateBoundary(provider)

  /** Update autocomplete suggestion placement strategy. */
  def setAutocompletePlacement(value: EditorAutocompletePlacement): Unit = transition { effects =>
    autocompletePlacement = value
    deferAutocompleteOverlayPlacement(effects, requestRender = true)
  }

  /** Use explicit placement options for the autocomplete suggestion overlay. */
  def setAutocompleteOverlayOptions(value: OverlayOptions): Unit =
    setAutocompletePlacement(EditorAutocompletePlacement.Custom(value))

  /** Replace the autocomplete provider and close any active autocomplete UI. */
  def setAutocompleteProvider(value: Option[AutocompleteProvider]): Unit = transition { effects =>
    cancelAutocompleteLocked(effects)
    provider = value
  }

  /** Replace all editor text and place the cursor at the end. */
  def setText(value: String): Unit = transition { effects =>
    cancelAutocompleteLocked(effects)
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
  def insertAtCursor(text: String): Unit = transition { effects =>
    val result = mutate(_.insert(text), effects, refreshAutocomplete = false)
    if result !== InputResult.NoRender then
      deferRefreshAutocompleteIfActive(effects)
      deferRenderRequest(effects)
  }

  /** Move the editor cursor, clamped to valid logical buffer bounds. */
  def setCursor(cursor: EditorCursor): Unit = transition { effects =>
    cancelAutocompleteLocked(effects)
    buffer.setCursor(cursor)
    resetEditingAction()
    historyIndex = -1
  }

  /** Add a value to history browsing. */
  def addToHistory(value: String): Unit = stateBoundary {
    val text = value.trim
    if text.isEmpty then ()
    else if history.headOption.forall(_ !== text) then
      history = text +: history.take(maxHistorySize - 1)
    if history.length > maxHistorySize then history = history.take(maxHistorySize)
  }

  /** Undo the most recent editor mutation, returning whether state changed. */
  def undo(): Boolean = transition(undoLocked)

  /** Yank the most recent killed text into the editor. */
  def yank(): Boolean = transition(yankLocked)

  /** Replace the previous yank with the next kill-ring candidate. */
  def yankPop(): Boolean = transition(yankPopLocked)

  /** Expand all compact large-paste markers into their original logical text. */
  def expandPasteMarkers(): Unit = transition { effects =>
    val before = buffer.text
    buffer.expandPasteMarkersInBuffer()
    if buffer.text !== before then deferChange(effects, buffer.text)
  }

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if value.isEmpty then cancelAutocompleteLocked(effects)
      context = value
    }

  override def renderOrigin_=(value: Option[ComponentRenderOrigin]): Unit = stateBoundary {
    currentRenderOrigin = value
  }

  override def focused: Boolean = stateBoundary(isFocused)

  override def focused_=(value: Boolean): Unit = stateBoundary { isFocused = value }

  override def handleInput(input: TerminalInput): Unit =
    handleInputResult(input)
    ()

  override def handleInputResult(input: TerminalInput): InputResult =
    transition(effects => handleInputLocked(input, effects))

  override def handleMouse(context: MouseInputContext): InputResult = transition { effects =>
    context.input.action match
      case MouseAction.Wheel(MouseWheelDirection.Up)   => pageScroll(-1, effects)
      case MouseAction.Wheel(MouseWheelDirection.Down) => pageScroll(1, effects)
      case _                                           => InputResult.Ignored
  }

  override def render(width: Int): ComponentRender =
    val snapshot         = stateBoundary {
      Editor.RenderSnapshot(buffer.snapshot, isFocused, currentAutocomplete.isEmpty)
    }
    val renderBuffer     = EditorBuffer("")
    renderBuffer.restore(snapshot.buffer)
    val plan             = EditorLayout.renderPlan(renderBuffer, width)
    val cursorPlacements = Vector.newBuilder[CursorPlacement]
    val lines            = plan.rows.zipWithIndex.map { (row, index) =>
      if snapshot.focused && snapshot.autocompleteEmpty && index === plan.layout.cursor.row then
        if row.omitted then
          if width > 0 then cursorPlacements += CursorPlacement(index, 0)
          ""
        else
          val (line, column) = row.focusedText(plan.cursorBoundary, width)
          column.foreach(value => cursorPlacements += CursorPlacement(index, value))
          line
      else row.normalText(width)
    }
    val placementEffect  = stateBoundary {
      lastRenderedVisualHeight = plan.layout.lines.length
      lastRenderedWidth = width
      autocompleteOverlayPlacementEffect(requestRender = false)
    }
    placementEffect.foreach(_.apply())
    ComponentRender(lines, Vector.empty, cursorPlacements.result())

  private def transition[A](operation: Editor.Effects => A): A =
    stateBoundary.transition(operation)

  private def deferChange(effects: Editor.Effects, value: String): Unit =
    val callback = changeCallback
    effects.add(() => callback(value))

  private def deferRenderRequest(effects: Editor.Effects): Unit =
    context.foreach(renderContext => effects.add(() => renderContext.requestRender()))

  private def deferRefreshAutocompleteIfActive(effects: Editor.Effects): Unit =
    if currentAutocomplete.nonEmpty || currentAutocompleteHandle.nonEmpty || pendingAutocompleteRefresh.nonEmpty
    then effects.add(() => scheduleAutocompleteRefresh())

  private def deferAutocompleteRequest(effects: Editor.Effects, force: Boolean): Unit =
    effects.add(() => requestAutocomplete(force))

  private def handleInputLocked(input: TerminalInput, effects: Editor.Effects): InputResult =
    val pasteCommit = input match
      case TerminalInput.PasteStart | TerminalInput.PasteChunk(_) | TerminalInput.PasteEnd =>
        InputResult.Ignored
      case _ if pasteSession.nonEmpty                                                      =>
        commitPaste(effects)
      case _                                                                               =>
        InputResult.Ignored
    val result      =
      if currentAutocomplete.nonEmpty then
        handleAutocompleteInput(input, effects)
      else
        input match
          case _ if keybindings.matches(input, KeybindingCommand.InputTab) && provider.nonEmpty =>
            deferAutocompleteRequest(effects, force = true)
            InputResult.Render
          case _                                                                                =>
            handleEditingInput(input, effects)
    combineInputResults(pasteCommit, result)

  private def handleAutocompleteInput(input: TerminalInput, effects: Editor.Effects): InputResult =
    currentAutocomplete match
      case Some(state) if state.suggestions.items.nonEmpty                   =>
        if keybindings.matches(
            input,
            KeybindingCommand.SelectCancel
          ) || keybindings.matches(input, KeybindingCommand.InputCopy)
        then
          cancelAutocompleteLocked(effects)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectUp) then
          deferAutocompleteSelectionMove(effects, state, -1)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectDown) then
          deferAutocompleteSelectionMove(effects, state, 1)
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectPageUp) then
          deferAutocompleteSelectionMove(
            effects,
            state,
            -math.max(1, currentAutocompleteMaxVisible)
          )
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectPageDown) then
          deferAutocompleteSelectionMove(
            effects,
            state,
            math.max(1, currentAutocompleteMaxVisible)
          )
          InputResult.Render
        else if keybindings.matches(input, KeybindingCommand.SelectConfirm) then
          handleAutocompleteSelection(submitOnSlash = true, effects)
        else if keybindings.matches(input, KeybindingCommand.InputTab) then
          acceptAutocomplete(effects)
        else
          handleEditingInput(input, effects)
      case Some(_)
          if keybindings.matches(
            input,
            KeybindingCommand.SelectCancel
          ) || keybindings.matches(input, KeybindingCommand.InputCopy) =>
        cancelAutocompleteLocked(effects)
        InputResult.Render
      case Some(_) if keybindings.matches(input, KeybindingCommand.InputTab) =>
        deferAutocompleteRequest(effects, force = true)
        InputResult.Render
      case Some(_)                                                           =>
        handleEditingInput(input, effects)
      case None                                                              =>
        handleEditingInput(input, effects)

  private def deferAutocompleteSelectionMove(
      effects: Editor.Effects,
      autocompleteState: Editor.AutocompleteState,
      delta: Int
  ): Unit = effects.add(() => {
    autocompleteState.list.moveSelectionBy(delta)
    transition(currentEffects =>
      if currentAutocomplete.exists(_ eq autocompleteState) then
        deferAutocompleteOverlayPlacement(currentEffects, requestRender = true)
    )
  })

  private def handleEditingInput(input: TerminalInput, effects: Editor.Effects): InputResult =
    if jumpMode.nonEmpty then
      handleJumpInput(input, effects)
    else
      input match {
        case TerminalInput.Key(TerminalKey.Enter, modifiers)                                    =>
          handleEnter(modifiers, effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorUp)                  =>
          val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
          if shouldUseHistoryUp(layout) then navigateHistory(-1, effects)
          else move(_.moveUp(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorDown)                =>
          val layout = EditorLayout.fromBuffer(buffer, math.max(1, lastRenderedWidth))
          if shouldUseHistoryDown(layout) then navigateHistory(1, effects)
          else move(_.moveDown(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorPageUp)                    =>
          pageScroll(-1, effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorPageDown)                  =>
          pageScroll(1, effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharForward)         =>
          mutate(_.delete(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordForward)         =>
          killDeleteWordForwards(effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteWordBackward)        =>
          killDeleteWordBackwards(effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteCharBackward)        =>
          move(_.backspace(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorUndo)                      =>
          if undoLocked(effects) then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorYank)                      =>
          if yankLocked(effects) then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorYankPop)                   =>
          if yankPopLocked(effects) then InputResult.Render else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordLeft)            =>
          move(_.moveWordBackwards(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorWordRight)           =>
          move(_.moveWordForwards(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLeft)                =>
          move(_.moveLeft(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorRight)               =>
          move(_.moveRight(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineStart)           =>
          move(_.moveToLineStart(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorCursorLineEnd)             =>
          move(_.moveToLineEnd(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorJumpForward)               =>
          jumpMode = Some(Editor.JumpDirection.Forward)
          InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.EditorJumpBackward)              =>
          jumpMode = Some(Editor.JumpDirection.Backward)
          InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.InputSubmit)                     =>
          submit(effects)
        case _ if keybindings.matches(input, KeybindingCommand.InputTab)                        =>
          if provider.nonEmpty then
            deferAutocompleteRequest(effects, force = true)
            InputResult.Render
          else InputResult.NoRender
        case _ if keybindings.matches(input, KeybindingCommand.InputNewLine)                    =>
          mutate(_.insertNewline(), effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineStart)         =>
          killDeleteToStartOfLine(effects)
        case _ if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineEnd)           =>
          killDeleteToEndOfLine(effects)
        case TerminalInput.PasteStart                                                           =>
          val previous = commitPaste(effects)
          pasteSession = Some(Editor.PasteSession(buffer.snapshot))
          previous
        case TerminalInput.PasteChunk(chunk)                                                    =>
          pasteSession.foreach(_.process(chunk))
          InputResult.NoRender
        case TerminalInput.PasteEnd                                                             =>
          commitPaste(effects)
        case TerminalInput.Key(TerminalKey.Character(text), modifiers)
            if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
          val result = mutate(_.insert(text), effects, refreshAutocomplete = false)
          maybeTriggerAutocompleteAfterText(text, effects)
          result
        case TerminalInput.Key(TerminalKey.Backspace, modifiers)
            if modifiers.alt || modifiers.ctrl =>
          killDeleteWordBackwards(effects)
        case TerminalInput.Key(TerminalKey.Backspace, _)                                        =>
          move(_.backspace(), effects)
        case TerminalInput.Key(TerminalKey.Delete, modifiers) if modifiers.alt                  =>
          killDeleteWordForwards(effects)
        case TerminalInput.Key(TerminalKey.Delete, _)                                           =>
          mutate(_.delete(), effects)
        case TerminalInput.Key(TerminalKey.Home, _)                                             =>
          move(_.moveToLineStart(), effects)
        case TerminalInput.Key(TerminalKey.End, _)                                              =>
          move(_.moveToLineEnd(), effects)
        case TerminalInput.Key(TerminalKey.PageUp, _)                                           =>
          pageScroll(-1, effects)
        case TerminalInput.Key(TerminalKey.PageDown, _)                                         =>
          pageScroll(1, effects)
        case TerminalInput.Key(TerminalKey.Left, modifiers) if modifiers.alt || modifiers.ctrl  =>
          move(_.moveWordBackwards(), effects)
        case TerminalInput.Key(TerminalKey.Right, modifiers) if modifiers.alt || modifiers.ctrl =>
          move(_.moveWordForwards(), effects)
        case TerminalInput.Key(TerminalKey.Left, _)                                             =>
          move(_.moveLeft(), effects)
        case TerminalInput.Key(TerminalKey.Right, _)                                            =>
          move(_.moveRight(), effects)
        case TerminalInput.Key(TerminalKey.Up, _)                                               =>
          move(_.moveUp(), effects)
        case TerminalInput.Key(TerminalKey.Down, _)                                             =>
          move(_.moveDown(), effects)
        case _                                                                                  => InputResult.Ignored
      }

  private def commitPaste(effects: Editor.Effects): InputResult =
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
          deferChange(effects, buffer.text)
          deferRefreshAutocompleteIfActive(effects)
          InputResult.Render

  private def combineInputResults(first: InputResult, second: InputResult): InputResult =
    (first, second) match
      case (InputResult.Exit, _) | (_, InputResult.Exit)                   => InputResult.Exit
      case (InputResult.Handled(true), _) | (_, InputResult.Handled(true)) => InputResult.Render
      case (InputResult.Handled(false), InputResult.Ignored)               => InputResult.NoRender
      case (InputResult.Ignored, InputResult.Handled(false))               => InputResult.NoRender
      case (InputResult.Handled(false), InputResult.Handled(false))        => InputResult.NoRender
      case (InputResult.Ignored, InputResult.Ignored)                      => InputResult.Ignored

  private def handleEnter(modifiers: KeyModifiers, effects: Editor.Effects): InputResult =
    currentEnterBehavior match
      case EditorEnterBehavior.SubmitOnEnter(newlineModifiers) =>
        if newlineModifiers.contains_(modifiers) then mutate(_.insertNewline(), effects)
        else if modifiers.isEmpty then submit(effects)
        else InputResult.Ignored
      case EditorEnterBehavior.NewlineOnEnter(submitModifiers) =>
        if submitModifiers.contains_(modifiers) then submit(effects)
        else if modifiers.isEmpty || (modifiers === KeyModifiers(shift = true)) then
          mutate(_.insertNewline(), effects)
        else InputResult.Ignored

  private def submit(effects: Editor.Effects): InputResult =
    cancelAutocompleteLocked(effects)
    val submitted = buffer.submitText
    val callback  = submitCallback
    effects.add(() => callback(submitted))
    undoStack.clear()
    resetEditingAction()
    InputResult.Render

  private def move(operation: EditorBuffer => Unit, effects: Editor.Effects): InputResult =
    val before = buffer.cursor
    operation(buffer)
    if buffer.cursor === before then InputResult.NoRender
    else
      historyIndex = -1
      resetEditingAction()
      deferRefreshAutocompleteIfActive(effects)
      InputResult.Render

  private def mutate(
      operation: EditorBuffer => Unit,
      effects: Editor.Effects,
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
      deferChange(effects, buffer.text)
    if changed then
      if refreshAutocomplete then deferRefreshAutocompleteIfActive(effects)
      InputResult.Render
    else InputResult.NoRender

  private def undoLocked(effects: Editor.Effects): Boolean =
    undoStack.pop() match
      case Some(snapshot) =>
        val before = buffer.text
        buffer.restore(snapshot)
        resetEditingAction()
        historyIndex = -1
        if buffer.text !== before then deferChange(effects, buffer.text)
        true
      case None           => false

  private def yankLocked(effects: Editor.Effects): Boolean =
    killRing.peek match
      case Some(text) =>
        val base    = buffer.snapshot
        val changed = mutate(_.insert(text), effects, refreshAutocomplete = false)
        if changed !== InputResult.NoRender then
          yankBaseSnapshot = Some(base)
          lastAction = Some(Editor.Action.Yank)
          true
        else false
      case None       => false

  private def yankPopLocked(effects: Editor.Effects): Boolean =
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
          deferChange(effects, buffer.text)
          deferRefreshAutocompleteIfActive(effects)
          true

  private def shouldUseHistoryUp(layout: EditorLayout): Boolean =
    buffer.text.isEmpty || (historyIndex >= 0 && layout.cursor.row === 0)

  private def shouldUseHistoryDown(layout: EditorLayout): Boolean =
    historyIndex >= 0 && (buffer.lines.length === 1 || layout.cursor.row === layout.lines.length - 1)

  private def navigateHistory(direction: Int, effects: Editor.Effects): InputResult =
    if history.isEmpty then InputResult.NoRender
    else
      val newIndex = historyIndex - direction
      if newIndex < -1 || newIndex >= history.length then InputResult.NoRender
      else
        if historyIndex === -1 && newIndex >= 0 then pushUndoSnapshot()
        historyIndex = newIndex
        if historyIndex === -1 then setTextInternal("", effects)
        else setTextInternal(history(historyIndex), effects, cursorAtLineStart = true)

  private def setTextInternal(
      text: String,
      effects: Editor.Effects,
      cursorAtLineStart: Boolean = false
  ): InputResult =
    val before = buffer.text
    buffer = EditorBuffer(text)
    if cursorAtLineStart then buffer.setCursor(EditorCursor(0, 0))
    if before === buffer.text then InputResult.NoRender
    else
      resetEditingAction()
      deferChange(effects, buffer.text)
      InputResult.Render

  private def killDeleteWordBackwards(effects: Editor.Effects): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted =
      if buffer.cursor.column > 0 then
        val start = WordNavigation.findWordBackward(cs, buffer.cursor.column, buffer.isPasteMarker)
        cs.slice(start, buffer.cursor.column).mkString
      else ""
    performKill(deleted, prepend = true, effects)(_.deleteWordBackwards())

  private def killDeleteWordForwards(effects: Editor.Effects): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted =
      if buffer.cursor.column < cs.length then
        val end = WordNavigation.findWordForward(cs, buffer.cursor.column, buffer.isPasteMarker)
        cs.slice(buffer.cursor.column, end).mkString
      else ""
    performKill(deleted, prepend = false, effects)(_.deleteWordForwards())

  private def killDeleteToStartOfLine(effects: Editor.Effects): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted = cs.take(buffer.cursor.column).mkString
    performKill(deleted, prepend = true, effects)(_.deleteToStartOfLine())

  private def killDeleteToEndOfLine(effects: Editor.Effects): InputResult =
    val cs      = buffer.clustersForLine(buffer.cursor.line)
    val deleted = cs.drop(buffer.cursor.column).mkString
    performKill(deleted, prepend = false, effects)(_.deleteToEndOfLine())

  private def performKill(
      deleted: String,
      prepend: Boolean,
      effects: Editor.Effects
  )(operation: EditorBuffer => Unit): InputResult =
    val wasKill = lastAction.contains(Editor.Action.Kill)
    val result  = mutate(operation, effects)
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

  private def handleJumpInput(input: TerminalInput, effects: Editor.Effects): InputResult =
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
          if jumpToChar(text, direction, effects) then InputResult.Render else InputResult.NoRender
        case None       =>
          jumpMode = None
          handleEditingInput(input, effects)

  private def printableCharacter(input: TerminalInput): Option[String] = input match
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey => Some(text)
    case _ => None

  private def jumpToChar(
      text: String,
      direction: Editor.JumpDirection,
      effects: Editor.Effects
  ): Boolean =
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
            if isForward then lineClusters.indexOf(text, start)
            else lineClusters.lastIndexOf(text, math.max(0, start))
          if idx >= 0 then
            moved = true
            foundLine = line
            foundCol = math.max(0, idx - 1)
      }

      if moved then
        val before = buffer.cursor
        buffer.setCursor(EditorCursor(foundLine, foundCol))
        deferRefreshAutocompleteIfActive(effects)
        if buffer.cursor !== before then
          resetEditingAction()
          true
        else false
      else false

  private def pageScroll(direction: Int, effects: Editor.Effects): InputResult =
    val plan       = EditorLayout.renderPlan(buffer, math.max(1, lastRenderedWidth))
    val pageSize   = 5
    val targetRow  = plan.layout.cursor.row + direction * pageSize
    val boundedRow = math.max(0, math.min(plan.layout.lines.length - 1, targetRow))
    if boundedRow === plan.layout.cursor.row then InputResult.NoRender
    else moveToVisualLine(plan, boundedRow, effects)

  private def moveToVisualLine(
      plan: EditorRenderPlan,
      targetRow: Int,
      effects: Editor.Effects
  ): InputResult =
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
      deferRefreshAutocompleteIfActive(effects)
      InputResult.Render

  private def maybeTriggerAutocompleteAfterText(inserted: String, effects: Editor.Effects): Unit =
    if provider.nonEmpty && currentAutocompleteTrigger.triggerSlash && (inserted === "/") && currentLineBeforeCursor === "/"
    then deferAutocompleteRequest(effects, force = false)
    else deferRefreshAutocompleteIfActive(effects)

  private def scheduleAutocompleteRefresh(): Unit =
    val refreshToken = transition { effects =>
      if provider.nonEmpty then
        cancelAutocompleteRequestLocked(effects)
        cancelPendingAutocompleteRefreshLocked(effects)
        pendingAutocompleteRefreshToken += 1
        Some(pendingAutocompleteRefreshToken)
      else None
    }
    refreshToken.foreach { token =>
      val scheduled = autocompleteDebouncer.schedule(() => runScheduledAutocompleteRefresh(token))
      if !scheduled.ranSynchronously then
        val accepted = stateBoundary {
          if token === pendingAutocompleteRefreshToken then
            pendingAutocompleteRefresh = Some(scheduled.handle)
            true
          else false
        }
        if !accepted then scheduled.handle.cancel()
    }

  private def runScheduledAutocompleteRefresh(refreshToken: Long): Unit =
    val start = stateBoundary {
      if refreshToken === pendingAutocompleteRefreshToken then
        pendingAutocompleteRefresh = None
        true
      else false
    }
    if start then requestAutocompleteNow(force = false)

  private def requestAutocomplete(force: Boolean): Unit = transition { effects =>
    cancelPendingAutocompleteRefreshLocked(effects)
    prepareAutocompleteRequestLocked(force, effects).foreach(start =>
      effects.add(() => startAutocompleteRequest(start))
    )
  }

  private def requestAutocompleteNow(force: Boolean): Unit = transition { effects =>
    prepareAutocompleteRequestLocked(force, effects).foreach(start =>
      effects.add(() => startAutocompleteRequest(start))
    )
  }

  private def prepareAutocompleteRequestLocked(
      force: Boolean,
      effects: Editor.Effects
  ): Option[Editor.AutocompleteStart] =
    provider.map { autocompleteProvider =>
      cancelAutocompleteRequestLocked(effects)
      autocompleteRequestToken += 1
      val token    = autocompleteRequestToken
      val snapshot = Editor.AutocompleteSnapshot(buffer.lines, buffer.cursor, buffer.text)
      autocompleteAwaitingHandle = Some(token)
      Editor.AutocompleteStart(
        autocompleteProvider,
        AutocompleteRequest(snapshot.lines, snapshot.cursor, force),
        snapshot,
        token
      )
    }

  private def startAutocompleteRequest(start: Editor.AutocompleteStart): Unit =
    val callback     = new AutocompleteCallback:
      override def complete(result: Option[AutocompleteSuggestions]): Unit =
        completeAutocomplete(start, result)
      override def fail(error: Throwable): Unit                            = failAutocomplete(start)
    val handle       = start.provider.requestSuggestions(start.request, callback)
    val cancelHandle = stateBoundary {
      val current = isCurrentAutocompleteRequest(start.token, start.snapshot)
      if current && autocompleteAwaitingHandle.contains(start.token) then
        autocompleteAwaitingHandle = None
        currentAutocompleteHandle = Some(handle)
      !current
    }
    if cancelHandle then handle.cancel()

  private def completeAutocomplete(
      start: Editor.AutocompleteStart,
      result: Option[AutocompleteSuggestions]
  ): Unit = transition { effects =>
    if isCurrentAutocompleteRequest(start.token, start.snapshot) then
      autocompleteAwaitingHandle = None
      currentAutocompleteHandle = None
      result.filter(_.items.nonEmpty) match
        case Some(suggestions)
            if shouldAutoApplySingleForcedCompletion(start.request, suggestions) =>
          suggestions.items.headOption.foreach { item =>
            effects.add(() =>
              applyAutocompleteCompletion(
                start.provider,
                CompletionRequest(
                  start.snapshot.lines,
                  start.snapshot.cursor,
                  item,
                  suggestions.prefix
                ),
                start.token,
                start.snapshot,
                submitAfter = false
              )
            )
          }
        case Some(suggestions) =>
          prepareShowAutocompleteLocked(suggestions, start.snapshot, start.token, effects)
        case None              => closeAutocompleteOverlayLocked(effects)
      deferRenderRequest(effects)
  }

  private def failAutocomplete(start: Editor.AutocompleteStart): Unit = transition { effects =>
    if isCurrentAutocompleteRequest(start.token, start.snapshot) then
      autocompleteAwaitingHandle = None
      currentAutocompleteHandle = None
      closeAutocompleteOverlayLocked(effects)
      deferRenderRequest(effects)
  }

  private def isCurrentAutocompleteRequest(
      token: Long,
      snapshot: Editor.AutocompleteSnapshot
  ): Boolean =
    (token === autocompleteRequestToken) && (buffer.text === snapshot.text) && (buffer.cursor === snapshot.cursor)

  private def prepareShowAutocompleteLocked(
      suggestions: AutocompleteSuggestions,
      snapshot: Editor.AutocompleteSnapshot,
      token: Long,
      effects: Editor.Effects
  ): Unit =
    val list             = SelectList(
      suggestions.items.map(item => SelectItem(item.value, item.label, item.description)),
      currentAutocompleteMaxVisible
    )
    val overlayComponent = Editor.AutocompleteOverlay(this, list)
    currentAutocomplete =
      Some(Editor.AutocompleteState(suggestions, snapshot, list, overlayComponent))
    val overlayOptions   = currentOverlayOptions
    currentAutocompleteOverlay match
      case Some(handle) =>
        effects.add(() => {
          val current = stateBoundary {
            isCurrentAutocompleteRequest(token, snapshot) &&
            currentAutocomplete.exists(_.overlay eq overlayComponent)
          }
          if current then handle.update(overlayComponent, Some(overlayOptions))
        })
      case None         =>
        val renderContext = context
        effects.add(() => {
          val shown = renderContext.map(_.overlays.showOverlay(overlayComponent, overlayOptions))
          val stale = stateBoundary {
            if isCurrentAutocompleteRequest(token, snapshot) &&
              currentAutocomplete.exists(_.overlay eq overlayComponent)
            then
              currentAutocompleteOverlay = shown
              None
            else shown
          }
          stale.foreach(_.hide())
        })

  private def shouldAutoApplySingleForcedCompletion(
      request: AutocompleteRequest,
      suggestions: AutocompleteSuggestions
  ): Boolean =
    request.force && options.autoApplySingleForcedCompletion && suggestions.items.length === 1

  private def acceptAutocomplete(effects: Editor.Effects): InputResult =
    handleAutocompleteSelection(submitOnSlash = false, effects)

  private def handleAutocompleteSelection(
      submitOnSlash: Boolean = false,
      effects: Editor.Effects
  ): InputResult =
    (provider, currentAutocomplete) match
      case (Some(autocompleteProvider), Some(autocompleteState))
          if isCurrentAutocompleteRequest(autocompleteRequestToken, autocompleteState.snapshot) =>
        effects.add(() =>
          applySelectedAutocomplete(
            autocompleteProvider,
            autocompleteState,
            submitOnSlash
          )
        )
        InputResult.Render
      case _ =>
        cancelAutocompleteLocked(effects)
        InputResult.Render

  private def applySelectedAutocomplete(
      autocompleteProvider: AutocompleteProvider,
      autocompleteState: Editor.AutocompleteState,
      submitOnSlash: Boolean
  ): Unit =
    val selectedItem = autocompleteState.selectedItem
    transition { effects =>
      selectedItem match
        case Some(item)
            if currentAutocomplete.exists(_ eq autocompleteState) &&
              isCurrentAutocompleteRequest(autocompleteRequestToken, autocompleteState.snapshot) =>
          val snapshot    = Editor.AutocompleteSnapshot(buffer.lines, buffer.cursor, buffer.text)
          autocompleteRequestToken += 1
          val token       = autocompleteRequestToken
          val request     = CompletionRequest(
            snapshot.lines,
            snapshot.cursor,
            item,
            autocompleteState.suggestions.prefix
          )
          cancelPendingAutocompleteRefreshLocked(effects)
          cancelAutocompleteRequestHandleLocked(effects)
          closeAutocompleteOverlayLocked(effects)
          val submitAfter = submitOnSlash && autocompleteState.suggestions.prefix.startsWith("/")
          effects.add(() =>
            applyAutocompleteCompletion(
              autocompleteProvider,
              request,
              token,
              snapshot,
              submitAfter
            )
          )
        case _ => cancelAutocompleteLocked(effects)
    }

  private def applyAutocompleteCompletion(
      autocompleteProvider: AutocompleteProvider,
      request: CompletionRequest,
      token: Long,
      snapshot: Editor.AutocompleteSnapshot,
      submitAfter: Boolean
  ): Unit =
    val result = autocompleteProvider.applyCompletion(request)
    transition { effects =>
      if isCurrentAutocompleteRequest(token, snapshot) then
        val beforeText = buffer.text
        buffer = EditorBuffer.fromLines(result.lines, result.cursor)
        autocompleteRequestToken += 1
        if buffer.text !== beforeText then
          resetEditingAction()
          historyIndex = -1
          deferChange(effects, buffer.text)
        if submitAfter then effects.add(() => transition(submit))
        else deferRenderRequest(effects)
    }

  private def cancelAutocompleteLocked(effects: Editor.Effects): Unit =
    cancelPendingAutocompleteRefreshLocked(effects)
    cancelAutocompleteRequestLocked(effects)
    closeAutocompleteOverlayLocked(effects)

  private def cancelPendingAutocompleteRefreshLocked(effects: Editor.Effects): Unit =
    pendingAutocompleteRefreshToken += 1
    pendingAutocompleteRefresh.foreach(handle => effects.add(() => handle.cancel()))
    pendingAutocompleteRefresh = None

  private def cancelAutocompleteRequestLocked(effects: Editor.Effects): Unit =
    autocompleteRequestToken += 1
    autocompleteAwaitingHandle = None
    cancelAutocompleteRequestHandleLocked(effects)

  private def cancelAutocompleteRequestHandleLocked(effects: Editor.Effects): Unit =
    currentAutocompleteHandle.foreach(handle => effects.add(() => handle.cancel()))
    currentAutocompleteHandle = None

  private def closeAutocompleteOverlayLocked(effects: Editor.Effects): Unit =
    currentAutocomplete = None
    currentAutocompleteOverlay.foreach(handle => effects.add(() => handle.hide()))
    currentAutocompleteOverlay = None

  private def currentLineBeforeCursor: String =
    Unicode.graphemeClusters(buffer.lines(buffer.cursor.line)).take(buffer.cursor.column).mkString

  private def deferAutocompleteOverlayPlacement(
      effects: Editor.Effects,
      requestRender: Boolean
  ): Unit = autocompleteOverlayPlacementEffect(requestRender).foreach(effects.add)

  private def autocompleteOverlayPlacementEffect(requestRender: Boolean): Option[() => Unit] =
    for
      autocompleteState <- currentAutocomplete
      handle            <- currentAutocompleteOverlay
      overlayOptions     = currentOverlayOptions
    yield () =>
      handle.update(
        autocompleteState.overlay,
        Some(overlayOptions),
        requestRender = requestRender
      )

  private def renderCurrentAutocompleteOverlay(width: Int): ComponentRender =
    val list = stateBoundary(currentAutocomplete.map(_.list))
    list.map(_.render(width)).getOrElse(ComponentRender.empty)

  private def currentOverlayOptions: OverlayOptions =
    val base = autocompletePlacement match
      case EditorAutocompletePlacement.AdjacentToEditor      =>
        currentRenderOrigin match
          case Some(origin) =>
            OverlayOptions(
              width = Some(OverlaySize.Percent(100)),
              row = Some(OverlaySize.Absolute(origin.row + lastRenderedVisualHeight)),
              col = Some(OverlaySize.Absolute(origin.col)),
              focusCapturing = true
            )
          case None         => EditorOptions.FallbackAutocompleteOverlayOptions
      case EditorAutocompletePlacement.Custom(customOptions) => customOptions
    base.copy(maxHeight =
      base.maxHeight.orElse(Some(OverlaySize.Absolute(currentAutocompleteMaxVisible)))
    )

object Editor:
  private type Effects = ComponentEffects

  private final case class RenderSnapshot(
      buffer: EditorBuffer.Snapshot,
      focused: Boolean,
      autocompleteEmpty: Boolean
  )

  private final case class AutocompleteStart(
      provider: AutocompleteProvider,
      request: AutocompleteRequest,
      snapshot: AutocompleteSnapshot,
      token: Long
  )

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

    override def handleMouse(context: MouseInputContext): InputResult =
      val result = context.input.action match
        case MouseAction.Wheel(MouseWheelDirection.Up)   => list.moveSelectionByResult(-1)
        case MouseAction.Wheel(MouseWheelDirection.Down) => list.moveSelectionByResult(1)
        case _                                           => InputResult.Ignored
      if result === InputResult.Render then
        owner.transition(effects =>
          owner.deferAutocompleteOverlayPlacement(effects, requestRender = true)
        )
      result

    override def handleInputResult(input: TerminalInput): InputResult =
      owner.transition(effects => owner.handleAutocompleteInput(input, effects))
