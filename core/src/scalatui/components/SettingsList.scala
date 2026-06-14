package scalatui.components

import java.util.Locale

import scalatui.ansi.Ansi
import scalatui.core.{Component, InputResult}
import scalatui.matching.FuzzyMatcher
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}

/**
 * A setting row rendered and edited by [[SettingsList]].
 *
 * @param id
 *   stable application identifier passed to change callbacks
 * @param label
 *   human-readable setting label
 * @param currentValue
 *   currently displayed value
 * @param description
 *   optional help text rendered for the selected row
 * @param values
 *   optional cycle values used by Enter and Space; empty means the row is read-only
 */
final case class SettingItem(
    id: String,
    label: String,
    currentValue: String,
    description: Option[String] = None,
    values: Vector[String] = Vector.empty
) derives CanEqual

/**
 * Styling hooks for [[SettingsList]].
 *
 * Functions may add ANSI styling but must not rely on platform-specific terminal behavior.
 */
final case class SettingsListTheme(
    selected: String => String = identity,
    label: String => String = identity,
    value: String => String = identity,
    description: String => String = identity,
    hint: String => String = identity,
    search: String => String = identity
)

/**
 * Configuration for [[SettingsList]].
 *
 * Filtering is intentionally dependency-free: printable characters append to an internal query.
 * Legacy `filteringEnabled = true` and [[SettingsListFiltering.Containment]] keep the existing
 * case-insensitive containment behavior across id, label, current value, and description.
 * [[SettingsListFiltering.Fuzzy]] uses the shared fuzzy matcher to rank matching rows across the
 * same fields. Space is reserved for cycling the selected value, matching the settings-list
 * activation controls.
 */
final case class SettingsListOptions(
    maxVisible: Int = 10,
    filteringEnabled: Boolean = false,
    showHints: Boolean = true,
    showScrollIndicators: Boolean = true,
    emptyText: String = "No settings",
    noMatchesText: String = "No matching settings",
    searchPrompt: String = "Search: ",
    hintText: String = "↑↓ move • Enter/Space change • Esc cancel",
    selectedPrefix: String = "> ",
    normalPrefix: String = "  ",
    valueSeparator: String = "  ",
    theme: SettingsListTheme = SettingsListTheme(),
    filtering: SettingsListFiltering = SettingsListFiltering.Disabled
):
  /**
   * Effective filtering mode. The explicit `filtering` mode takes precedence; the legacy
   * `filteringEnabled = true` value maps to containment only when `filtering` is left disabled.
   */
  def effectiveFiltering: SettingsListFiltering =
    filtering match
      case SettingsListFiltering.Disabled if filteringEnabled => SettingsListFiltering.Containment
      case mode                                               => mode

/**
 * Interactive settings list component for configuration screens.
 *
 * `SettingsList` renders labels, current values, optional descriptions, scroll indicators, hints,
 * and an optional dependency-free filter query. It handles typed terminal input only: Up/Down move
 * selection, Enter and Space cycle configured values, Backspace edits the filter query when
 * filtering is enabled, printable characters append to the filter query, and Escape invokes the
 * cancel callback. Complex submenus and animated loader behavior are intentionally out of scope for
 * this component batch.
 *
 * The component is shared-core only and has no JVM-only, Scala Native-only, Node.js, or third-party
 * runtime dependencies. Implementations and styles are expected to preserve the component width
 * contract; this component truncates and wraps using ANSI-aware helpers before returning lines.
 */
final class SettingsList(
    initialItems: Vector[SettingItem],
    options: SettingsListOptions = SettingsListOptions()
) extends Component:
  var onChange: (String, String) => Unit = (_, _) => ()
  var onCancel: () => Unit               = () => ()

  private var currentItems  = initialItems
  private var selectedIndex = 0
  private var scrollOffset  = 0
  private var filterQuery   = ""

  def items: Vector[SettingItem] = currentItems

  def items_=(value: Vector[SettingItem]): Unit =
    currentItems = value
    clampSelection()

  def selected: Option[SettingItem] = selectedEntry.map(_.item)

  def query: String = filterQuery

  def clearFilter(): Unit =
    filterQuery = ""
    clampSelection()

  override def handleInput(input: TerminalInput): Unit = handleInputResult(input)

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Up, _)                                             => moveSelection(-1)
    case TerminalInput.Key(TerminalKey.Down, _)                                           => moveSelection(1)
    case TerminalInput.Key(TerminalKey.Enter, _)                                          => cycleSelected()
    case TerminalInput.Key(TerminalKey.Character(" "), _)                                 => cycleSelected()
    case TerminalInput.Key(TerminalKey.Escape, _)                                         =>
      onCancel()
      InputResult.Render
    case TerminalInput.Key(TerminalKey.Backspace, _)
        if options.effectiveFiltering.enabled && filterQuery.nonEmpty =>
      filterQuery = filterQuery.dropRight(1)
      clampSelection()
      InputResult.Render
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if options.effectiveFiltering.enabled && modifiers.isEmpty && text.nonEmpty =>
      filterQuery += text
      clampSelection()
      InputResult.Render
    case TerminalInput.Paste(text) if options.effectiveFiltering.enabled && text.nonEmpty =>
      filterQuery += text.replace('\n', ' ').replace('\r', ' ')
      clampSelection()
      InputResult.Render
    case _                                                                                => InputResult.Ignored

  override def render(width: Int): Vector[String] =
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then Vector("")
    else
      val entries = filteredEntries
      clampSelection(entries)
      val out     = Vector.newBuilder[String]
      if options.effectiveFiltering.enabled then
        out += fit(options.theme.search(options.searchPrompt + filterQuery), safeWidth)
      if currentItems.isEmpty then out += fit(options.emptyText, safeWidth)
      else
        if entries.isEmpty then out += fit(options.noMatchesText, safeWidth)
        else
          ensureVisible(entries.length)
          val visibleCount = math.max(1, options.maxVisible)
          val visible      = entries.slice(scrollOffset, scrollOffset + visibleCount)
          visible.zipWithIndex.foreach { case (entry, visibleIndex) =>
            out += renderRow(entry.item, scrollOffset + visibleIndex, safeWidth)
          }
          if options.showScrollIndicators && entries.length > visibleCount then
            val end = math.min(entries.length, scrollOffset + visibleCount)
            out += fit(s"${scrollOffset + 1}-$end of ${entries.length}", safeWidth)
          entries.lift(selectedIndex).flatMap(_.item.description).foreach { description =>
            Ansi.wrapTextWithAnsi(description, safeWidth).foreach { line =>
              out += fit(options.theme.description(line), safeWidth)
            }
          }
      if options.showHints then out += fit(options.theme.hint(options.hintText), safeWidth)
      out.result()

  private final case class IndexedSetting(originalIndex: Int, item: SettingItem)

  private def filteredEntries: Vector[IndexedSetting] =
    val indexed = currentItems.zipWithIndex.map { case (item, index) =>
      IndexedSetting(index, item)
    }
    options.effectiveFiltering match
      case SettingsListFiltering.Disabled    => indexed
      case _ if filterQuery.isEmpty          => indexed
      case SettingsListFiltering.Containment =>
        val needle = filterQuery.toLowerCase(Locale.ROOT)
        indexed.filter(entry =>
          searchableText(entry.item).toLowerCase(Locale.ROOT).indexOf(needle) >= 0
        )
      case SettingsListFiltering.Fuzzy       =>
        FuzzyMatcher.filter(filterQuery, indexed)(entry => searchableText(entry.item)).map(_.item)

  private def searchableText(item: SettingItem): String =
    item.id + "\n" + item.label + "\n" + item.currentValue + "\n" + item.description.getOrElse("")

  private def selectedEntry: Option[IndexedSetting] = filteredEntries.lift(selectedIndex)

  private def moveSelection(delta: Int): InputResult =
    val entries = filteredEntries
    if entries.isEmpty then InputResult.Ignored
    else
      selectedIndex = math.max(0, math.min(entries.length - 1, selectedIndex + delta))
      ensureVisible(entries.length)
      InputResult.Render

  private def cycleSelected(): InputResult =
    selectedEntry match
      case Some(entry) if entry.item.values.nonEmpty =>
        val values     = entry.item.values
        val valueIndex = values.indexWhere(_ === entry.item.currentValue)
        val nextValue  = values(if valueIndex < 0 then 0 else (valueIndex + 1) % values.length)
        currentItems =
          currentItems.updated(entry.originalIndex, entry.item.copy(currentValue = nextValue))
        onChange(entry.item.id, nextValue)
        InputResult.Render
      case _                                         => InputResult.Ignored

  private def clampSelection(): Unit =
    clampSelection(filteredEntries)

  private def clampSelection(entries: Vector[IndexedSetting]): Unit =
    val length = entries.length
    if length <= 0 then
      selectedIndex = 0
      scrollOffset = 0
    else
      selectedIndex = math.max(0, math.min(length - 1, selectedIndex))
      ensureVisible(length)

  private def ensureVisible(length: Int): Unit =
    val visibleCount = math.max(1, options.maxVisible)
    if selectedIndex < scrollOffset then scrollOffset = selectedIndex
    if selectedIndex >= scrollOffset + visibleCount then
      scrollOffset = selectedIndex - visibleCount + 1
    val maxOffset    = math.max(0, length - visibleCount)
    scrollOffset = math.max(0, math.min(scrollOffset, maxOffset))

  private def renderRow(item: SettingItem, filteredIndex: Int, width: Int): String =
    val selected = filteredIndex === selectedIndex
    val prefix   = if selected then options.selectedPrefix else options.normalPrefix
    val row      = prefix + renderLabelValue(item, math.max(0, width - Ansi.visibleWidth(prefix)))
    val styled   = if selected then options.theme.selected(row) else row
    fit(styled, width)

  private def renderLabelValue(item: SettingItem, width: Int): String =
    if width <= 0 then ""
    else
      val label = options.theme.label(item.label)
      val value = options.theme.value(item.currentValue)
      if item.currentValue.isEmpty then Ansi.truncateToWidth(label, width, "")
      else
        val valueWidth     = Ansi.visibleWidth(value)
        val separatorWidth = Ansi.visibleWidth(options.valueSeparator)
        if valueWidth + separatorWidth < width then
          val labelWidth = math.max(0, width - valueWidth - separatorWidth)
          val labelPart  = Ansi.truncateToWidth(label, labelWidth, "")
          labelPart + options.valueSeparator + value
        else Ansi.truncateToWidth(label + options.valueSeparator + value, width, "")

  private def fit(line: String, width: Int): String =
    if width <= 0 then "" else Ansi.truncateToWidth(line, width, "")
