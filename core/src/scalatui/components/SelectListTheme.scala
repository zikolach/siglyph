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

object SelectListTheme:
  /** Adapter only: callers may still copy/replace any existing styling hook. */
  def semantic(
      theme: scalatui.theme.SemanticTheme,
      focused: () => Boolean = () => false
  ): SelectListTheme =
    import scalatui.theme.SemanticRole
    SelectListTheme(
      selectedPrefix =
        value => theme.paint(theme.role(focused = focused(), selected = true), value),
      selectedText = value => theme.paint(SemanticRole.Selected, value),
      normalText = value => theme.paint(SemanticRole.Normal, value),
      description = value => theme.paint(SemanticRole.Muted, value),
      noMatchText = value => theme.paint(SemanticRole.Warning, value),
      scrollInfo = value => theme.paint(SemanticRole.Muted, value)
    )
