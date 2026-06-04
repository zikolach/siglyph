package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, Focusable}
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}
import scalatui.unicode.Unicode

final class Input(initialValue: String = "") extends Component, Focusable:
  var onSubmit: String => Unit = _ => ()
  private var currentValue = initialValue
  private var cursorCluster = Unicode.graphemeClusters(initialValue).length
  private var isFocused = false

  def value: String = currentValue
  def setValue(value: String): Unit =
    currentValue = value
    cursorCluster = Unicode.graphemeClusters(value).length

  override def focused: Boolean = isFocused
  override def focused_=(value: Boolean): Unit = isFocused = value

  override def handleInput(input: TerminalInput): Unit = input match
    case TerminalInput.Key(TerminalKey.Character("a"), modifiers) if modifiers.ctrl => cursorCluster = 0
    case TerminalInput.Key(TerminalKey.Character("e"), modifiers) if modifiers.ctrl => cursorCluster = clusters.length
    case TerminalInput.Key(TerminalKey.Character("w"), modifiers) if modifiers.ctrl => deleteWordBackwards()
    case TerminalInput.Key(TerminalKey.Character("u"), modifiers) if modifiers.ctrl => deleteToStart()
    case TerminalInput.Key(TerminalKey.Character("k"), modifiers) if modifiers.ctrl => deleteToEnd()
    case TerminalInput.Key(TerminalKey.Character(text), _) => insert(text)
    case TerminalInput.Paste(text) => insert(text.replace('\n', ' ').replace('\r', ' '))
    case TerminalInput.Key(TerminalKey.Enter, _) => onSubmit(currentValue)
    case TerminalInput.Key(TerminalKey.Backspace, modifiers) if modifiers.alt || modifiers.ctrl => deleteWordBackwards()
    case TerminalInput.Key(TerminalKey.Backspace, _) => deleteBackwards()
    case TerminalInput.Key(TerminalKey.Delete, _) => deleteForwards()
    case TerminalInput.Key(TerminalKey.Left, _) => cursorCluster = math.max(0, cursorCluster - 1)
    case TerminalInput.Key(TerminalKey.Right, _) => cursorCluster = math.min(clusters.length, cursorCluster + 1)
    case TerminalInput.Key(TerminalKey.Home, _) => cursorCluster = 0
    case TerminalInput.Key(TerminalKey.End, _) => cursorCluster = clusters.length
    case _ => ()

  override def render(width: Int): Vector[String] =
    val cs = clusters
    val before = cs.take(cursorCluster).mkString
    val at = cs.lift(cursorCluster).getOrElse(" ")
    val after = cs.drop(cursorCluster + 1).mkString
    val cursor = if isFocused then s"\u001b[7m$at\u001b[27m" else at
    Vector(Ansi.truncateToWidth(before + cursor + after, width, ""))

  private def insert(text: String): Unit =
    val cs = clusters
    currentValue = (cs.take(cursorCluster) ++ Unicode.graphemeClusters(text) ++ cs.drop(cursorCluster)).mkString
    cursorCluster += Unicode.graphemeClusters(text).length

  private def deleteBackwards(): Unit =
    if cursorCluster > 0 then
      val cs = clusters
      currentValue = (cs.take(cursorCluster - 1) ++ cs.drop(cursorCluster)).mkString
      cursorCluster -= 1

  private def deleteForwards(): Unit =
    val cs = clusters
    if cursorCluster < cs.length then currentValue = (cs.take(cursorCluster) ++ cs.drop(cursorCluster + 1)).mkString

  private def deleteWordBackwards(): Unit =
    val cs = clusters
    var i = cursorCluster
    while i > 0 && cs(i - 1).forall(_.isWhitespace) do i -= 1
    while i > 0 && !cs(i - 1).forall(_.isWhitespace) do i -= 1
    currentValue = (cs.take(i) ++ cs.drop(cursorCluster)).mkString
    cursorCluster = i

  private def deleteToStart(): Unit =
    val cs = clusters
    currentValue = cs.drop(cursorCluster).mkString
    cursorCluster = 0

  private def deleteToEnd(): Unit =
    val cs = clusters
    currentValue = cs.take(cursorCluster).mkString

  private def clusters: Vector[String] = Unicode.graphemeClusters(currentValue)
