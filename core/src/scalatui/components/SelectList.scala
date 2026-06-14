package scalatui.components

import java.util.Locale

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}
import scalatui.unicode.Unicode

final case class SelectItem(value: String, label: String, description: Option[String] = None)
    derives CanEqual

final class SelectList private (
    items: Vector[SelectItem],
    options: SelectListOptions
) extends Component:
  def this(items: Vector[SelectItem]) = this(items, SelectListOptions())

  def this(items: Vector[SelectItem], maxVisible: Int) =
    this(items, SelectListOptions(maxVisible = maxVisible))

  var onSelect: SelectItem => Unit                  = _ => ()
  var onCancel: () => Unit                          = () => ()
  var onSelectionChange: Option[SelectItem] => Unit = _ => ()
  private var selectedIndex                         = 0
  private var scrollOffset                          = 0
  private var filterQuery                           = ""

  def selected: Option[SelectItem] = filteredItems.lift(selectedIndex)

  def query: String = filterQuery

  override def handleInput(input: TerminalInput): Unit = input match
    case TerminalInput.Key(TerminalKey.Up, _)                                   => moveSelection(-1)
    case TerminalInput.Key(TerminalKey.Down, _)                                 => moveSelection(1)
    case TerminalInput.Key(TerminalKey.Enter, _)                                => selected.foreach(onSelect)
    case TerminalInput.Key(TerminalKey.Escape, _)                               => onCancel()
    case TerminalInput.Key(TerminalKey.Backspace, _)
        if options.filteringEnabled && filterQuery.nonEmpty =>
      updateFilter(dropLastGrapheme(filterQuery))
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if options.filteringEnabled && !modifiers.ctrl && !modifiers.alt && !modifiers.superKey && text.nonEmpty =>
      updateFilter(filterQuery + text)
    case TerminalInput.Paste(text) if options.filteringEnabled && text.nonEmpty =>
      updateFilter(filterQuery + text.replace('\n', ' ').replace('\r', ' '))
    case _                                                                      => ()

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
    else
      val matchingItems = filteredItems
      if matchingItems.isEmpty then
        Vector(fit(options.theme.noMatchText(options.noMatchText), safeWidth))
      else
        renderItems(matchingItems, safeWidth, normalizedScrollOffset(matchingItems.length))

  private def renderItems(
      matchingItems: Vector[SelectItem],
      safeWidth: Int,
      pageOffset: Int
  ): Vector[String] =
    val totalRows    = math.max(1, options.maxVisible)
    val withScroll   = options.showScrollInfo && matchingItems.length > totalRows && totalRows > 1
    val visibleCount = if withScroll then totalRows - 1 else totalRows
    val pageItems    = matchingItems.slice(pageOffset, pageOffset + visibleCount)
    val rows         = pageItems.zipWithIndex.map { case (item, visibleIndex) =>
      renderRow(item, pageOffset + visibleIndex, safeWidth)
    }
    if withScroll then
      val end = math.min(matchingItems.length, pageOffset + visibleCount)
      rows :+ fit(
        options.theme.scrollInfo(s"${pageOffset + 1}-$end of ${matchingItems.length}"),
        safeWidth
      )
    else rows

  private def moveSelection(delta: Int): Unit =
    val currentItems = filteredItems
    if currentItems.nonEmpty then
      val before = selected
      selectedIndex = math.max(0, math.min(currentItems.length - 1, selectedIndex + delta))
      ensureVisible(currentItems.length)
      notifySelectionChange(before)

  private def ensureVisible(length: Int): Unit =
    scrollOffset = normalizedScrollOffset(length)

  private def normalizedScrollOffset(length: Int): Int =
    var offset       = scrollOffset
    if selectedIndex < offset then offset = selectedIndex
    val totalRows    = math.max(1, options.maxVisible)
    val visibleCount =
      if options.showScrollInfo && length > totalRows && totalRows > 1 then totalRows - 1
      else totalRows
    if selectedIndex >= offset + visibleCount then
      offset = selectedIndex - visibleCount + 1
    val maxOffset    = math.max(0, length - visibleCount)
    math.max(0, math.min(offset, maxOffset))

  private def updateFilter(value: String): Unit =
    val selectedBeforeFilter = selected
    filterQuery = value
    val candidates           = filteredItems
    selectedIndex = selectedBeforeFilter.flatMap(item =>
      candidates.indexWhere(_ === item) match
        case -1    => None
        case index => Some(index)
    ).getOrElse(0)
    ensureVisible(candidates.length)
    notifySelectionChange(selectedBeforeFilter)

  private def notifySelectionChange(before: Option[SelectItem]): Unit =
    val after = selected
    if before !== after then onSelectionChange(after)

  private def filteredItems: Vector[SelectItem] =
    if !options.filteringEnabled || filterQuery.isEmpty then items
    else
      val needle = filterQuery.toLowerCase(Locale.ROOT)
      items.filter { item =>
        val haystack =
          (item.value + "\n" + item.label + "\n" + item.description.getOrElse(""))
            .toLowerCase(Locale.ROOT)
        haystack.indexOf(needle) >= 0
      }

  private def dropLastGrapheme(value: String): String =
    Unicode.graphemeClusters(value).dropRight(1).mkString

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
