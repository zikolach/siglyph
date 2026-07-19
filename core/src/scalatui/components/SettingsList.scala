package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  InputResult,
  MouseInputHandler,
  OverlayHandle,
  OverlayUnfocusOptions,
  OverlayOptions,
  TUIContext
}
import scalatui.matching.FuzzyMatcher
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  MouseAction,
  MouseInputContext,
  MouseWheelDirection,
  TerminalInput,
  TerminalKey
}
import scalatui.unicode.{TextCase, Unicode}

/** Controller passed to application-provided settings submenu components. */
trait SettingsSubmenuController:
  def commitValue(value: String): Unit
  def cancel(): Unit

/**
 * Application-provided submenu descriptor for a settings row.
 *
 * The component is shown through the existing overlay host. `onOpen` receives a controller that the
 * application submenu can call when a value is selected or the submenu is cancelled.
 */
final case class SettingsSubmenu(
    component: Component,
    options: OverlayOptions = OverlayOptions(),
    onOpen: SettingsSubmenuController => Unit = _ => ()
)

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
 * @param submenu
 *   optional application-provided submenu opened instead of scalar value cycling
 */
final case class SettingItem(
    id: String,
    label: String,
    currentValue: String,
    description: Option[String] = None,
    values: Vector[String] = Vector.empty,
    submenu: Option[SettingsSubmenu] = None
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
 * same fields. Space is reserved for activating the selected setting, cycling scalar values or
 * opening submenus.
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
 * selection, Enter and Space cycle configured values or open application-provided submenu overlays,
 * Backspace edits the filter query when filtering is enabled, printable characters append to the
 * filter query, and Escape invokes the cancel callback.
 *
 * The component is shared-core only and has no JVM-only, Scala Native-only, Node.js, or third-party
 * runtime dependencies. Implementations and styles are expected to preserve the component width
 * contract; this component truncates and wraps using ANSI-aware helpers before returning lines.
 */
final class SettingsList(
    initialItems: Vector[SettingItem],
    options: SettingsListOptions = SettingsListOptions()
) extends Component,
      ContextualComponent,
      MouseInputHandler:
  var onChange: (String, String) => Unit = (_, _) => ()
  var onCancel: () => Unit               = () => ()

  private var currentItems  = initialItems
  private var selectedIndex = 0
  private var scrollOffset  = 0
  private var filterQuery   = ""
  private var pasteSession  = Option.empty[FilterPasteSession]
  private var context       = Option.empty[TUIContext]
  private var activeSubmenu = Option.empty[OverlayHandle]

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    if value.isEmpty then
      activeSubmenu.foreach { handle =>
        handle.unfocus(Some(OverlayUnfocusOptions(target = null)))
        handle.hide()
      }
      activeSubmenu = None
    context = value

  def items: Vector[SettingItem] = currentItems

  def items_=(value: Vector[SettingItem]): Unit =
    commitPaste()
    currentItems = value
    clampSelection()

  def selected: Option[SettingItem] = selectedEntry.map(_.item)

  def query: String = pasteSession.fold(filterQuery)(_.query)

  def clearFilter(): Unit =
    pasteSession = None
    filterQuery = ""
    clampSelection()

  override def handleInput(input: TerminalInput): Unit = handleInputResult(input)

  override def handleMouse(context: MouseInputContext): InputResult = context.input.action match
    case MouseAction.Wheel(MouseWheelDirection.Up)   => moveSelection(-1)
    case MouseAction.Wheel(MouseWheelDirection.Down) => moveSelection(1)
    case _                                           => InputResult.Ignored

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.PasteStart if options.effectiveFiltering.enabled        =>
      val changed = commitPaste()
      pasteSession = Some(FilterPasteSession(filterQuery))
      if changed then InputResult.Render else InputResult.NoRender
    case TerminalInput.PasteChunk(chunk) if options.effectiveFiltering.enabled =>
      pasteSession match
        case Some(session) =>
          session.append(chunk)
          InputResult.NoRender
        case None          => InputResult.Ignored
    case TerminalInput.PasteEnd if options.effectiveFiltering.enabled          =>
      if commitPaste() then InputResult.Render else InputResult.NoRender
    case _                                                                     =>
      val pasteChanged = commitPaste()
      val result       = handleNonPasteInput(input)
      if pasteChanged && result === InputResult.Ignored then InputResult.Render else result

  private def handleNonPasteInput(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Up, _)             => moveSelection(-1)
    case TerminalInput.Key(TerminalKey.Down, _)           => moveSelection(1)
    case TerminalInput.Key(TerminalKey.Enter, _)          => activateSelected()
    case TerminalInput.Key(TerminalKey.Character(" "), _) => activateSelected()
    case TerminalInput.Key(TerminalKey.Escape, _)         =>
      onCancel()
      InputResult.Render
    case TerminalInput.Key(TerminalKey.Backspace, _)
        if options.effectiveFiltering.enabled && filterQuery.nonEmpty =>
      filterQuery = dropLastGrapheme(filterQuery)
      clampSelection()
      InputResult.Render
    case TerminalInput.Key(TerminalKey.Character(text), modifiers)
        if options.effectiveFiltering.enabled && modifiers.isEmpty && text.nonEmpty =>
      filterQuery += text
      clampSelection()
      InputResult.Render
    case _                                                => InputResult.Ignored

  override def render(width: Int): ComponentRender = ComponentRender.text {
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then Vector("")
    else
      val activeQuery = query
      val entries     = filteredEntries(filterQuery)
      if pasteSession.isEmpty then clampSelection(entries)
      val out         = Vector.newBuilder[String]
      if options.effectiveFiltering.enabled then
        out += fit(options.theme.search(options.searchPrompt + activeQuery), safeWidth)
      if currentItems.isEmpty then out += fit(options.emptyText, safeWidth)
      else
        if entries.isEmpty then out += fit(options.noMatchesText, safeWidth)
        else
          if pasteSession.isEmpty then ensureVisible(entries.length)
          val visibleCount = math.max(1, options.maxVisible)
          val pageOffset   = normalizedScrollOffset(entries.length, visibleCount)
          val visible      = entries.slice(pageOffset, pageOffset + visibleCount)
          visible.zipWithIndex.foreach { case (entry, visibleIndex) =>
            out += renderRow(entry.item, pageOffset + visibleIndex, safeWidth)
          }
          if options.showScrollIndicators && entries.length > visibleCount then
            val end = math.min(entries.length, pageOffset + visibleCount)
            out += fit(s"${pageOffset + 1}-$end of ${entries.length}", safeWidth)
          entries.lift(selectedIndex).flatMap(_.item.description).foreach { description =>
            Ansi.wrapLogicalLinesWithAnsi(description, safeWidth).foreach { line =>
              out += fit(options.theme.description(line), safeWidth)
            }
          }
      if options.showHints then out += fit(options.theme.hint(options.hintText), safeWidth)
      out.result()
  }

  private final case class IndexedSetting(originalIndex: Int, item: SettingItem)

  private def filteredEntries(queryValue: String): Vector[IndexedSetting] =
    val indexed = currentItems.zipWithIndex.map { case (item, index) =>
      IndexedSetting(index, item)
    }
    options.effectiveFiltering match
      case SettingsListFiltering.Disabled    => indexed
      case _ if queryValue.isEmpty           => indexed
      case SettingsListFiltering.Containment =>
        val needle = TextCase.lowercase(queryValue)
        indexed.filter(entry => TextCase.lowercase(searchableText(entry.item)).indexOf(needle) >= 0)
      case SettingsListFiltering.Fuzzy       =>
        FuzzyMatcher.filter(queryValue, indexed)(entry => searchableText(entry.item)).map(_.item)

  private def searchableText(item: SettingItem): String =
    item.id + "\n" + item.label + "\n" + item.currentValue + "\n" + item.description.getOrElse("")

  private def dropLastGrapheme(value: String): String =
    Unicode.graphemeClusters(value).dropRight(1).mkString

  private def selectedEntry: Option[IndexedSetting] =
    filteredEntries(filterQuery).lift(selectedIndex)

  private def moveSelection(delta: Int): InputResult =
    val entries = filteredEntries(filterQuery)
    if entries.isEmpty then InputResult.Ignored
    else
      val before = selectedIndex
      selectedIndex = math.max(0, math.min(entries.length - 1, selectedIndex + delta))
      ensureVisible(entries.length)
      if selectedIndex === before then InputResult.NoRender else InputResult.Render

  private def activateSelected(): InputResult =
    selectedEntry match
      case Some(entry) =>
        entry.item.submenu match
          case Some(submenu) => openSubmenu(entry, submenu)
          case None          => cycleSelected()
      case None        => InputResult.Ignored

  private def openSubmenu(entry: IndexedSetting, submenu: SettingsSubmenu): InputResult =
    activeSubmenu.foreach(_.hide())
    context match
      case Some(contextValue) =>
        val handle = contextValue.overlays.showOverlay(submenu.component, submenu.options)
        activeSubmenu = Some(handle)
        handle.focus()
        submenu.onOpen(new SettingsSubmenuController:
          override def commitValue(value: String): Unit =
            completeSubmenu(entry.item.id, handle, value)
          override def cancel(): Unit                   = cancelSubmenu(handle))
        InputResult.Render
      case None               => InputResult.Ignored

  private def completeSubmenu(rowId: String, handle: OverlayHandle, value: String): Unit =
    if activeSubmenu.exists(_.id === handle.id) then
      currentItems.indexWhere(_.id === rowId) match
        case -1    => ()
        case index =>
          val item = currentItems(index)
          currentItems = currentItems.updated(index, item.copy(currentValue = value))
          onChange(rowId, value)
      closeSubmenu()

  private def cancelSubmenu(handle: OverlayHandle): Unit =
    if activeSubmenu.exists(_.id === handle.id) then
      closeSubmenu()

  private def closeSubmenu(): Unit =
    activeSubmenu.foreach(_.hide())
    activeSubmenu = None
    context.foreach(_.setFocus(this))

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
    clampSelection(filteredEntries(filterQuery))

  private def clampSelection(entries: Vector[IndexedSetting]): Unit =
    val length = entries.length
    if length <= 0 then
      selectedIndex = 0
      scrollOffset = 0
    else
      selectedIndex = math.max(0, math.min(length - 1, selectedIndex))
      ensureVisible(length)

  private def normalizedScrollOffset(length: Int, visibleCount: Int): Int =
    var offset    = scrollOffset
    if selectedIndex < offset then offset = selectedIndex
    if selectedIndex >= offset + visibleCount then offset = selectedIndex - visibleCount + 1
    val maxOffset = math.max(0, length - visibleCount)
    math.max(0, math.min(offset, maxOffset))

  private def commitPaste(): Boolean =
    pasteSession match
      case None          => false
      case Some(session) =>
        pasteSession = None
        session.finish()
        if session.changed then
          filterQuery = session.query
          clampSelection()
          true
        else false

  private def ensureVisible(length: Int): Unit =
    val visibleCount = math.max(1, options.maxVisible)
    scrollOffset = normalizedScrollOffset(length, visibleCount)

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
