package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}

final case class SelectItem(value: String, label: String, description: Option[String] = None)
    derives CanEqual

final class SelectList(items: Vector[SelectItem], maxVisible: Int = 10) extends Component:
  var onSelect: SelectItem => Unit = _ => ()
  var onCancel: () => Unit         = () => ()
  private var selectedIndex        = 0
  private var scrollOffset         = 0

  def selected: Option[SelectItem] = items.lift(selectedIndex)

  override def handleInput(input: TerminalInput): Unit = input match
    case TerminalInput.Key(TerminalKey.Up, _)     => moveSelection(-1)
    case TerminalInput.Key(TerminalKey.Down, _)   => moveSelection(1)
    case TerminalInput.Key(TerminalKey.Enter, _)  => selected.foreach(onSelect)
    case TerminalInput.Key(TerminalKey.Escape, _) => onCancel()
    case _                                        => ()

  /** Move selection up/down by logical items. */
  def moveSelectionBy(delta: Int): Unit = moveSelection(delta)

  /** Move selection by a full page of visible rows, clamped to item bounds. */
  def moveSelectionByPage(pagesize: Int, direction: Int): Unit =
    if items.nonEmpty then moveSelectionBy(pagesize * direction)

  /** Confirm the currently selected entry, if any. */
  def confirmSelection(): Unit =
    selected.foreach(onSelect)

  /** Cancel selection and close the overlay interaction. */
  def cancelSelection(): Unit = onCancel()

  override def render(width: Int): Vector[String] =
    if items.isEmpty then Vector(Ansi.truncateToWidth("No items", width))
    else
      ensureVisible()
      val visible = items.slice(scrollOffset, scrollOffset + math.max(1, maxVisible))
      visible.zipWithIndex.map { case (item, visibleIndex) =>
        val index       = scrollOffset + visibleIndex
        val prefix      = if index === selectedIndex then "> " else "  "
        val description = item.description.fold("")(d => s" — $d")
        Ansi.truncateToWidth(prefix + item.label + description, width)
      }

  private def moveSelection(delta: Int): Unit =
    if items.nonEmpty then
      selectedIndex = math.max(0, math.min(items.length - 1, selectedIndex + delta))
      ensureVisible()

  private def ensureVisible(): Unit =
    if selectedIndex < scrollOffset then scrollOffset = selectedIndex
    val visibleCount = math.max(1, maxVisible)
    if selectedIndex >= scrollOffset + visibleCount then
      scrollOffset = selectedIndex - visibleCount + 1
