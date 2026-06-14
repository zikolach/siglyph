package scalatui.components

/** Styling hooks for [[SelectList]] rows and status text. */
final case class SelectListTheme(
    selectedPrefix: String => String = identity,
    normalPrefix: String => String = identity,
    selectedText: String => String = identity,
    normalText: String => String = identity,
    description: String => String = identity,
    noMatchText: String => String = identity,
    scrollInfo: String => String = identity
)
