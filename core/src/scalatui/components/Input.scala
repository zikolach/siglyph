package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, CursorMarker, Focusable}
import scalatui.editing.{KillRing, UndoStack, WordNavigation}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyModifiers,
  KeybindingCommand,
  KeybindingManager,
  TerminalInput,
  TerminalKey
}
import scalatui.unicode.Unicode

/** Single-line text input with Unicode-aware editing and pi-tui-style undo/yank commands. */
final class Input(
    initialValue: String = "",
    keybindings: KeybindingManager = KeybindingManager()
) extends Component, Focusable:
  var onSubmit: String => Unit = _ => ()
  private var currentValue     = initialValue
  private var cursorCluster    = Unicode.graphemeClusters(initialValue).length
  private var isFocused        = false
  private val undoStack        = UndoStack[Input.State]()
  private val killRing         = KillRing()
  private var lastAction       = Option.empty[Input.Action]
  private var lastYankClusters = 0

  def value: String = currentValue

  def setValue(value: String): Unit =
    currentValue = value
    cursorCluster = Unicode.graphemeClusters(value).length
    lastAction = None
    lastYankClusters = 0

  /** Undo the most recent editing snapshot, returning whether state changed. */
  def undo(): Boolean =
    undoStack.pop() match
      case Some(Input.State(value, cursor)) =>
        currentValue = value
        cursorCluster = math.max(0, math.min(cursor, clusters.length))
        lastAction = None
        lastYankClusters = 0
        true
      case None                             => false

  /** Insert the most recent killed text at the cursor, returning whether state changed. */
  def yank(): Boolean =
    killRing.peek match
      case Some(text) =>
        pushUndo()
        insertRaw(text)
        lastAction = Some(Input.Action.Yank)
        lastYankClusters = Unicode.graphemeClusters(text).length
        true
      case None       => false

  /** Replace the most recent yank with the next kill-ring entry. */
  def yankPop(): Boolean =
    if !lastAction.contains(Input.Action.Yank) || killRing.length <= 1 || lastYankClusters <= 0 then
      false
    else
      pushUndo()
      val cs          = clusters
      val replaceFrom = math.max(0, cursorCluster - lastYankClusters)
      currentValue = (cs.take(replaceFrom) ++ cs.drop(cursorCluster)).mkString
      cursorCluster = replaceFrom
      killRing.rotate()
      val text        = killRing.peek.getOrElse("")
      insertRaw(text)
      lastAction = Some(Input.Action.Yank)
      lastYankClusters = Unicode.graphemeClusters(text).length
      true

  override def focused: Boolean                = isFocused
  override def focused_=(value: Boolean): Unit = isFocused = value

  override def handleInput(input: TerminalInput): Unit =
    if keybindings.matches(input, KeybindingCommand.EditorUndo) then
      undo()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteWordBackward) then
      deleteWordBackwards()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineStart) then
      deleteToStart()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteToLineEnd) then
      deleteToEnd()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteCharForward) then
      deleteForwards()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteWordForward) then
      deleteWordForwards()
    else if keybindings.matches(input, KeybindingCommand.EditorDeleteCharBackward) then
      deleteBackwards()
    else if keybindings.matches(input, KeybindingCommand.EditorYank) then
      yank()
    else if keybindings.matches(input, KeybindingCommand.EditorYankPop) then
      yankPop()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorWordLeft) then
      moveWordBackwards()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorWordRight) then
      moveWordForwards()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLeft) then
      moveLeft()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorRight) then
      moveRight()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLineStart) then
      moveToStart()
    else if keybindings.matches(input, KeybindingCommand.EditorCursorLineEnd) then
      moveToEnd()
    else if keybindings.matches(input, KeybindingCommand.InputSubmit) then
      onSubmit(currentValue)
    else if keybindings.matches(input, KeybindingCommand.InputNewLine) then
      insert("\n")
    else
      input match
        case TerminalInput.Paste(text) => insert(text.replace('\n', ' ').replace('\r', ' '))
        case TerminalInput.Key(TerminalKey.Character(text), modifiers)
            if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
          insert(text)
        case _                         => ()

  override def render(width: Int): Vector[String] =
    val cs     = clusters
    val before = cs.take(cursorCluster).mkString
    val at     = cs.lift(cursorCluster).getOrElse(" ")
    val after  = cs.drop(cursorCluster + 1).mkString
    val marker = if isFocused then CursorMarker.Sequence else ""
    val cursor = if isFocused then s"\u001b[7m$at\u001b[27m" else at
    Vector(Ansi.truncateToWidth(before + marker + cursor + after, width, ""))

  private def insert(text: String): Unit =
    if text.nonEmpty then
      if text.exists(_.isWhitespace) || !lastAction.contains(Input.Action.TypeWord) then pushUndo()
      insertRaw(text)
      lastAction = Some(Input.Action.TypeWord)
      lastYankClusters = 0

  private def insertRaw(text: String): Unit =
    val cs       = clusters
    val inserted = Unicode.graphemeClusters(text)
    currentValue = (cs.take(cursorCluster) ++ inserted ++ cs.drop(cursorCluster)).mkString
    cursorCluster += inserted.length

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
        lastYankClusters = 0

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
        lastYankClusters = 0

  private def deleteToStart(): Unit =
    val cs = clusters
    if cursorCluster > 0 then
      pushUndo()
      val deleted = cs.take(cursorCluster).mkString
      killRing.push(deleted, prepend = true, accumulate = lastAction.contains(Input.Action.Kill))
      currentValue = cs.drop(cursorCluster).mkString
      cursorCluster = 0
      lastAction = Some(Input.Action.Kill)
      lastYankClusters = 0

  private def deleteToEnd(): Unit =
    val cs = clusters
    if cursorCluster < cs.length then
      pushUndo()
      val deleted = cs.drop(cursorCluster).mkString
      killRing.push(deleted, prepend = false, accumulate = lastAction.contains(Input.Action.Kill))
      currentValue = cs.take(cursorCluster).mkString
      lastAction = Some(Input.Action.Kill)
      lastYankClusters = 0

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
    lastYankClusters = 0

  private def pushUndo(): Unit =
    undoStack.push(Input.State(currentValue, cursorCluster))

  private def clusters: Vector[String] = Unicode.graphemeClusters(currentValue)

object Input:
  private final case class State(value: String, cursorCluster: Int)
  private enum Action derives CanEqual:
    case Kill, Yank, TypeWord
