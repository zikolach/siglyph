package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}

final case class SelectItem(value: String, label: String, description: Option[String] = None)
    derives CanEqual

final class SelectList private (
    items: Vector[SelectItem],
    options: SelectListOptions
) extends Component:
  def this(items: Vector[SelectItem]) = this(items, SelectListOptions())

  def this(items: Vector[SelectItem], maxVisible: Int) =
    this(items, SelectListOptions(maxVisible = maxVisible))

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
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then Vector("")
    else if items.isEmpty then
      Vector(fit(options.theme.noMatchText(options.noMatchText), safeWidth))
    else
      ensureVisible()
      val totalRows    = math.max(1, options.maxVisible)
      val withScroll   = options.showScrollInfo && items.length > totalRows && totalRows > 1
      val visibleCount = if withScroll then totalRows - 1 else totalRows
      val visible      = items.slice(scrollOffset, scrollOffset + visibleCount)
      val rows         = visible.zipWithIndex.map { case (item, visibleIndex) =>
        renderRow(item, scrollOffset + visibleIndex, safeWidth)
      }
      if withScroll then
        val end = math.min(items.length, scrollOffset + visibleCount)
        rows :+ fit(
          options.theme.scrollInfo(s"${scrollOffset + 1}-$end of ${items.length}"),
          safeWidth
        )
      else rows

  private def moveSelection(delta: Int): Unit =
    if items.nonEmpty then
      selectedIndex = math.max(0, math.min(items.length - 1, selectedIndex + delta))
      ensureVisible()

  private def ensureVisible(): Unit =
    if selectedIndex < scrollOffset then scrollOffset = selectedIndex
    val totalRows    = math.max(1, options.maxVisible)
    val visibleCount =
      if options.showScrollInfo && items.length > totalRows && totalRows > 1 then totalRows - 1
      else totalRows
    if selectedIndex >= scrollOffset + visibleCount then
      scrollOffset = selectedIndex - visibleCount + 1

  private def renderRow(item: SelectItem, index: Int, width: Int): String =
    val selected     = index === selectedIndex
    val prefix       =
      if selected then options.theme.selectedPrefix(options.selectedPrefix)
      else options.theme.normalPrefix(options.normalPrefix)
    val contentWidth = math.max(0, width - Ansi.visibleWidth(prefix))
    val label        = renderLabel(item.label, selected, contentWidth)
    val description  = renderDescription(item)
    fit(prefix + label + description, width)

  private def renderLabel(label: String, selected: Boolean, width: Int): String =
    val boundedWidth = options.labelMaxWidth.map(max => math.min(max, width)).getOrElse(width)
    val truncated    = fit(label, boundedWidth)
    if selected then options.theme.selectedText(truncated) else options.theme.normalText(truncated)

  private def renderDescription(item: SelectItem): String =
    if !options.showDescriptions then ""
    else
      item.description.fold("") { value =>
        val text = options.descriptionSeparator + value
        options.theme.description(text)
      }

  private def fit(value: String, width: Int): String =
    if width <= 0 then "" else Ansi.truncateToWidth(value, width, "")

object SelectList:
  def apply(items: Vector[SelectItem]): SelectList =
    SelectList(items, SelectListOptions())

  def apply(items: Vector[SelectItem], options: SelectListOptions): SelectList =
    new SelectList(items, options)

  def apply(items: Vector[SelectItem], maxVisible: Int): SelectList =
    new SelectList(items, SelectListOptions(maxVisible = maxVisible))

  def apply(
      items: Vector[SelectItem],
      maxVisible: Int,
      options: SelectListOptions
  ): SelectList =
    new SelectList(items, options.copy(maxVisible = maxVisible))
