package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  CursorPlacement,
  FakeCursorRender,
  Focusable,
  InputResult
}
import scalatui.editing.{KillRing, UndoStack, WordNavigation}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyModifiers,
  KeybindingCommand,
  KeybindingManager,
  TerminalInput,
  TerminalInputChunk,
  TerminalKey,
  TerminalUtf8Decoder
}
import scalatui.unicode.Unicode

/**
 * Single-line text input with pi-tui-style undo/yank commands. Cursor and streamed-paste accounting
 * use Unicode 17.0.0 UAX #29 default extended grapheme clusters on JVM and Scala Native.
 * Segmentation state is bounded and does not limit retained input content. Focused rendering emits
 * structured cursor metadata only when width truncation preserves the complete fake-cursor token;
 * unfocused rendering emits no cursor metadata.
 */
final class Input(
    initialValue: String = "",
    keybindings: KeybindingManager = KeybindingManager()
) extends Component, Focusable:
  var onSubmit: String => Unit = _ => ()
  private var currentValue     = initialValue
  private var cursorCluster    = Unicode.graphemeClusters(initialValue).length
  private var isFocused        = false
  private var pasteSession     = Option.empty[Input.PasteSession]
  private val undoStack        = UndoStack[Input.State]()
  private val killRing         = KillRing()
  private var lastAction       = Option.empty[Input.Action]
  private var yankBaseState    = Option.empty[Input.State]

  def value: String = pasteSession.fold(currentValue)(_.value)

  def setValue(value: String): Unit =
    pasteSession = None
    currentValue = value
    cursorCluster = Unicode.graphemeClusters(value).length
    resetAction()

  /** Undo the most recent editing snapshot, returning whether state changed. */
  def undo(): Boolean =
    finishPaste()
    undoStack.pop() match
      case Some(Input.State(value, cursor)) =>
        currentValue = value
        cursorCluster = math.max(0, math.min(cursor, clusters.length))
        resetAction()
        true
      case None                             => false

  /**
   * Insert the most recent killed text and retain the exact pre-yank state for yank-pop.
   *
   * @return
   *   whether state changed
   */
  def yank(): Boolean =
    finishPaste()
    killRing.peek match
      case Some(text) =>
        val base = Input.State(currentValue, cursorCluster)
        pushUndoSnapshot(base)
        insertRaw(text)
        lastAction = Some(Input.Action.Yank)
        yankBaseState = Some(base)
        true
      case None       => false

  /** Replace the most recent yank from its exact pre-yank state with the next kill-ring entry. */
  def yankPop(): Boolean =
    finishPaste()
    if !lastAction.contains(Input.Action.Yank) || killRing.length <= 1 then false
    else
      yankBaseState match
        case None       => false
        case Some(base) =>
          pushUndo()
          restore(base)
          killRing.rotate()
          insertRaw(killRing.peek.getOrElse(""))
          lastAction = Some(Input.Action.Yank)
          yankBaseState = Some(base)
          true

  override def focused: Boolean                = isFocused
  override def focused_=(value: Boolean): Unit = isFocused = value

  override def handleInput(input: TerminalInput): Unit =
    handleInputResult(input)
    ()

  override def handleInputResult(input: TerminalInput): InputResult =
    input match
      case TerminalInput.PasteStart        =>
        val finalized = finishPaste()
        startPaste()
        if finalized.exposedChanged then InputResult.Render else InputResult.NoRender
      case TerminalInput.PasteChunk(chunk) =>
        pasteSession match
          case Some(session) =>
            val acceptedBefore = session.acceptedCharacterCount
            session.append(chunk)
            if session.acceptedCharacterCount !== acceptedBefore then InputResult.Render
            else InputResult.NoRender
          case None          => InputResult.Ignored
      case TerminalInput.PasteEnd          =>
        if finishPaste().exposedChanged then InputResult.Render else InputResult.NoRender
      case _                               =>
        val finalized = finishPaste()
        val result    = handleNonPasteInput(input)
        if finalized.exposedChanged || result === InputResult.Render then InputResult.Render
        else if finalized.hadSession && result === InputResult.Ignored then InputResult.NoRender
        else result

  override def render(width: Int): ComponentRender =
    val cs            = clusters
    val visibleCursor = pasteSession.fold(cursorCluster)(_.cursorCluster)
    val before        = cs.take(visibleCursor).mkString
    val at            = cs.lift(visibleCursor).getOrElse(" ")
    val after         = cs.drop(visibleCursor + 1).mkString
    if isFocused then
      val rendered = FakeCursorRender.render(before, at, after, width)
      ComponentRender(
        Vector(rendered.line),
        Vector.empty,
        rendered.cursorColumn.map(column => CursorPlacement(0, column)).toVector
      )
    else ComponentRender.text(Ansi.truncateToWidth(Ansi.sanitize(before + at + after), width, ""))

  private def startPaste(): Unit =
    val cs     = clusters
    val prefix = cs.take(cursorCluster).mkString
    val suffix = cs.drop(cursorCluster).mkString
    pasteSession = Some(Input.PasteSession(currentValue, cursorCluster, prefix, suffix))
    currentValue = ""

  private def finishPaste(): Input.PasteFinalization =
    pasteSession match
      case None          => Input.PasteFinalization(hadSession = false, exposedChanged = false)
      case Some(session) =>
        val acceptedBefore = session.acceptedCharacterCount
        pasteSession = None
        session.finish()
        val exposedChanged = session.acceptedCharacterCount !== acceptedBefore
        if session.isEmpty then
          currentValue = session.baseValue
          cursorCluster = session.baseCursor
        else
          undoStack.push(Input.State(session.baseValue, session.baseCursor))
          currentValue = session.value
          cursorCluster = session.cursorCluster
          resetAction()
        Input.PasteFinalization(hadSession = true, exposedChanged = exposedChanged)

  private def handleNonPasteInput(input: TerminalInput): InputResult =
    if keybindings.matches(input, KeybindingCommand.EditorUndo) then
      mutationResult(undo())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteWordBackward) then
      mutationResult(deleteWordBackwards())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineStart) then
      mutationResult(deleteToStart())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineEnd) then
      mutationResult(deleteToEnd())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteCharForward) then
      mutationResult(deleteForwards())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteWordForward) then
      mutationResult(deleteWordForwards())
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteCharBackward) then
      mutationResult(deleteBackwards())
    else if keybindings.matches(input, KeybindingCommand.EditorYank) then
      mutationResult(yank())
    else if keybindings.matches(input, KeybindingCommand.EditorYankPop) then
      mutationResult(yankPop())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorWordLeft) then
      mutationResult(moveWordBackwards())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorWordRight) then
      mutationResult(moveWordForwards())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLeft) then
      mutationResult(moveLeft())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorRight) then
      mutationResult(moveRight())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLineStart) then
      mutationResult(moveToStart())
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLineEnd) then
      mutationResult(moveToEnd())
    else if keybindings.matches(input, KeybindingCommand.InputSubmit) then
      onSubmit(currentValue)
      InputResult.Render
    else if keybindings.matches(input, KeybindingCommand.InputNewLine) then
      mutationResult(insert("\n"))
    else
      input match
        case TerminalInput.Key(TerminalKey.Character(text), modifiers)
            if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
          mutationResult(insert(text))
        case _ => InputResult.Ignored

  private def mutationResult(action: => Unit): InputResult =
    val valueBefore  = currentValue
    val cursorBefore = cursorCluster
    action
    if (currentValue !== valueBefore) || (cursorCluster !== cursorBefore) then InputResult.Render
    else InputResult.NoRender

  private def insert(text: String): Unit =
    if text.nonEmpty then
      if text.exists(_.isWhitespace) || !lastAction.contains(Input.Action.TypeWord) then pushUndo()
      insertRaw(text)
      lastAction = Some(Input.Action.TypeWord)
      yankBaseState = None

  private def insertRaw(text: String): Unit =
    val cs            = clusters
    val prefix        = cs.take(cursorCluster).mkString
    val insertedValue = prefix + text + cs.drop(cursorCluster).mkString
    val finalClusters = Unicode.graphemeClusters(insertedValue)
    currentValue = insertedValue
    cursorCluster = Unicode.graphemeCursorAfterCodeUnit(finalClusters, prefix.length + text.length)

  private def deleteBackwards(): Unit =
    if cursorCluster > 0 then
      pushUndo()
      val cs = clusters
      currentValue = (cs.take(cursorCluster - 1) ++ cs.drop(cursorCluster)).mkString
      cursorCluster -= 1
      resetAction()

  private def deleteForwards(): Unit =
    val cs = clusters
    if cursorCluster < cs.length then
      pushUndo()
      currentValue = (cs.take(cursorCluster) ++ cs.drop(cursorCluster + 1)).mkString
      resetAction()

  private def deleteWordBackwards(): Unit =
    val cs = clusters
    if cursorCluster > 0 then
      val wasKill = lastAction.contains(Input.Action.Kill)
      val start   = WordNavigation.findWordBackward(cs, cursorCluster, _ => false)
      if start !== cursorCluster then
        pushUndo()
        val deleted = cs.slice(start, cursorCluster).mkString
        killRing.push(deleted, prepend = true, accumulate = wasKill)
        currentValue = (cs.take(start) ++ cs.drop(cursorCluster)).mkString
        cursorCluster = start
        lastAction = Some(Input.Action.Kill)
        yankBaseState = None

  private def deleteWordForwards(): Unit =
    val cs = clusters
    if cursorCluster < cs.length then
      val wasKill = lastAction.contains(Input.Action.Kill)
      val end     = WordNavigation.findWordForward(cs, cursorCluster, _ => false)
      if end !== cursorCluster then
        pushUndo()
        val deleted = cs.slice(cursorCluster, end).mkString
        killRing.push(deleted, prepend = false, accumulate = wasKill)
        currentValue = (cs.take(cursorCluster) ++ cs.drop(end)).mkString
        lastAction = Some(Input.Action.Kill)
        yankBaseState = None

  private def deleteToStart(): Unit =
    val cs = clusters
    if cursorCluster > 0 then
      pushUndo()
      val deleted = cs.take(cursorCluster).mkString
      killRing.push(deleted, prepend = true, accumulate = lastAction.contains(Input.Action.Kill))
      currentValue = cs.drop(cursorCluster).mkString
      cursorCluster = 0
      lastAction = Some(Input.Action.Kill)
      yankBaseState = None

  private def deleteToEnd(): Unit =
    val cs = clusters
    if cursorCluster < cs.length then
      pushUndo()
      val deleted = cs.drop(cursorCluster).mkString
      killRing.push(deleted, prepend = false, accumulate = lastAction.contains(Input.Action.Kill))
      currentValue = cs.take(cursorCluster).mkString
      lastAction = Some(Input.Action.Kill)
      yankBaseState = None

  private def moveLeft(): Unit =
    cursorCluster = math.max(0, cursorCluster - 1)
    resetAction()

  private def moveRight(): Unit =
    cursorCluster = math.min(clusters.length, cursorCluster + 1)
    resetAction()

  private def moveWordBackwards(): Unit =
    cursorCluster = WordNavigation.findWordBackward(clusters, cursorCluster, _ => false)
    resetAction()

  private def moveWordForwards(): Unit =
    cursorCluster = WordNavigation.findWordForward(clusters, cursorCluster, _ => false)
    resetAction()

  private def moveToStart(): Unit =
    cursorCluster = 0
    resetAction()

  private def moveToEnd(): Unit =
    cursorCluster = clusters.length
    resetAction()

  private def resetAction(): Unit =
    lastAction = None
    yankBaseState = None

  private def pushUndo(): Unit =
    pushUndoSnapshot(Input.State(currentValue, cursorCluster))

  private def pushUndoSnapshot(state: Input.State): Unit = undoStack.push(state)

  private def restore(state: Input.State): Unit =
    currentValue = state.value
    cursorCluster = state.cursorCluster

  private def clusters: Vector[String] = Unicode.graphemeClusters(value)

object Input:
  private final case class State(value: String, cursorCluster: Int)
  private final case class PasteFinalization(hadSession: Boolean, exposedChanged: Boolean)

  private[scalatui] final class PasteSession(
      val baseValue: String,
      val baseCursor: Int,
      prefix: String,
      suffix: String
  ):
    private val decoder         = TerminalUtf8Decoder()
    private val prefixBuilder   = StringBuilder(prefix)
    private val graphemeCounter = Unicode.IncrementalGraphemeCounter()
    private var acceptedChars   = 0L
    private var appendCalls     = 0L
    private var finalCursor     = Option.empty[Int]

    graphemeCounter.process(prefix)

    def value: String      = prefixBuilder.result() + suffix
    def cursorCluster: Int = finalCursor.getOrElse(graphemeCounter.count.toInt)
    def isEmpty: Boolean   = acceptedChars === 0L

    private[scalatui] def acceptedCharacterCount: Long = acceptedChars
    private[scalatui] def appendCount: Long            = appendCalls

    def append(chunk: TerminalInputChunk): Unit =
      appendDecoded(decoder.process(chunk))

    def finish(): Unit =
      appendDecoded(decoder.flush())
      val finalClusters = Unicode.graphemeClusters(value)
      finalCursor = Some(Unicode.graphemeCursorAfterCodeUnit(finalClusters, prefixBuilder.length))

    private def appendDecoded(decoded: String): Unit =
      val normalized = decoded.replace('\n', ' ').replace('\r', ' ')
      if normalized.nonEmpty then
        prefixBuilder.append(normalized)
        graphemeCounter.process(normalized)
        acceptedChars += normalized.length
        appendCalls += 1

  private enum Action derives CanEqual:
    case Kill, Yank, TypeWord
