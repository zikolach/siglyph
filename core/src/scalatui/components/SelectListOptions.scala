package scalatui.components

/** Rendering options for [[SelectList]]. */
final case class SelectListOptions(
    maxVisible: Int = 10,
    filteringEnabled: Boolean = false,
    selectedPrefix: String = "> ",
    normalPrefix: String = "  ",
    descriptionSeparator: String = " — ",
    noMatchText: String = "No items",
    showDescriptions: Boolean = true,
    showScrollInfo: Boolean = true,
    labelMaxWidth: Option[Int] = None,
    theme: SelectListTheme = SelectListTheme()
)
