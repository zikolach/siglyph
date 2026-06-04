package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, Focusable, InputResult}
import scalatui.editing.{EditorBuffer, EditorCursor}
import scalatui.syntax.Containment.*
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}
import scalatui.unicode.Unicode

/**
 * Multiline text editor component backed by an [[EditorBuffer]].
 *
 * The editor is a rendered component MVP: it owns focus state, delegates logical text mutations to
 * `EditorBuffer`, wraps text to the requested component width, and renders a fake inverse-video
 * cursor when focused. It intentionally does not implement autocomplete, overlays, undo/kill-ring,
 * large paste markers, IME cursor markers, or hardware terminal cursor positioning.
 *
 * @param initialText
 *   starting logical editor contents
 * @param options
 *   initial callbacks and Enter behavior
 */
final class Editor(initialText: String = "", options: EditorOptions = EditorOptions())
    extends Component,
      Focusable:
  private var buffer           = EditorBuffer(initialText)
  private var isFocused        = false
  var enterBehavior            = options.enterBehavior
  var onChange: String => Unit = options.onChange
  var onSubmit: String => Unit = options.onSubmit

  /** Current editor text joined with `\n` separators. */
  def text: String = buffer.text

  /** Current logical buffer lines. */
  def lines: Vector[String] = buffer.lines

  /** Current logical grapheme-cluster cursor position. */
  def cursor: EditorCursor = buffer.cursor

  /** Replace all editor text and place the cursor at the end. */
  def setText(value: String): Unit =
    buffer = EditorBuffer(value)

  /** Move the editor cursor, clamped to valid logical buffer bounds. */
  def setCursor(cursor: EditorCursor): Unit = buffer.setCursor(cursor)

  override def focused: Boolean = isFocused

  override def focused_=(value: Boolean): Unit = isFocused = value

  override def handleInput(input: TerminalInput): Unit =
    handleInputResult(input)
    ()

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Character("a"), modifiers) if modifiers.ctrl             =>
      move(_.moveToLineStart())
    case TerminalInput.Key(TerminalKey.Character("e"), modifiers) if modifiers.ctrl             =>
      move(_.moveToLineEnd())
    case TerminalInput.Key(TerminalKey.Character("k"), modifiers) if modifiers.ctrl             =>
      mutate(_.deleteToEndOfLine())
    case TerminalInput.Key(TerminalKey.Character("w"), modifiers) if modifiers.ctrl             =>
      mutate(_.deleteWordBackwards())
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if !modifiers.ctrl && !modifiers.superKey =>
      mutate(_.insert(text))
    case TerminalInput.Paste(text)                                                              =>
      mutate(_.insertPaste(text))
    case TerminalInput.Key(TerminalKey.Enter, modifiers)                                        =>
      handleEnter(modifiers)
    case TerminalInput.Key(TerminalKey.Backspace, modifiers) if modifiers.alt || modifiers.ctrl =>
      mutate(_.deleteWordBackwards())
    case TerminalInput.Key(TerminalKey.Backspace, _)                                            =>
      mutate(_.backspace())
    case TerminalInput.Key(TerminalKey.Delete, _)                                               =>
      mutate(_.delete())
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

  override def render(width: Int): Vector[String] =
    val layout = EditorLayout.fromBuffer(buffer, width)
    layout.lines.zipWithIndex.map { (line, index) =>
      val rendered =
        if focused && index === layout.cursor.row then renderCursor(line)
        else line.text
      Ansi.truncateToWidth(rendered, width, "")
    }

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
    onSubmit(buffer.submitText)
    InputResult.NoRender

  private def move(operation: EditorBuffer => Unit): InputResult =
    val before = buffer.cursor
    operation(buffer)
    if buffer.cursor === before then InputResult.NoRender else InputResult.Render

  private def mutate(operation: EditorBuffer => Unit): InputResult =
    val beforeText   = buffer.text
    val beforeCursor = buffer.cursor
    operation(buffer)
    val textChanged  = buffer.text !== beforeText
    val changed      = textChanged || (buffer.cursor !== beforeCursor)
    if textChanged then onChange(buffer.text)
    if changed then InputResult.Render else InputResult.NoRender

  private def renderCursor(line: EditorVisualLine): String =
    val clusters      = Unicode.graphemeClusters(buffer.lines(line.logicalLine))
    val segment       = clusters.slice(line.startColumn, line.endColumn)
    val cursorInLine  = buffer.cursor.column - line.startColumn
    val before        = segment.take(cursorInLine).mkString
    val cursorCluster = segment.lift(cursorInLine).getOrElse(" ")
    val after         = segment.drop(cursorInLine + 1).mkString
    s"$before\u001b[7m$cursorCluster\u001b[27m$after"
