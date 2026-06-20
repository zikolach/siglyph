package scalatui.components

/** Rendering options for [[SelectList]]. */
final case class SelectListOptions(
    maxVisible: Int = 10,
    @deprecated("Use filtering = SelectListFiltering.Containment or Fuzzy instead.", "0.1.0")
    filteringEnabled: Boolean = false,
    selectedPrefix: String = "> ",
    normalPrefix: String = "  ",
    descriptionSeparator: String = " — ",
    noMatchText: String = "No items",
    showDescriptions: Boolean = true,
    showScrollInfo: Boolean = true,
    labelMaxWidth: Option[Int] = None,
    theme: SelectListTheme = SelectListTheme(),
    filtering: SelectListFiltering = SelectListFiltering.Disabled
):
  /**
   * Effective filtering mode. The explicit `filtering` mode takes precedence; the legacy
   * `filteringEnabled = true` value maps to containment only when `filtering` is left disabled.
   */
  def effectiveFiltering: SelectListFiltering =
    filtering match
      case SelectListFiltering.Disabled if filteringEnabled => SelectListFiltering.Containment
      case mode                                             => mode
