package scalatui.theme

import scalatui.ansi.Ansi

/** Application-level meanings; roles describe appearance, not interaction permission. */
enum SemanticRole derives CanEqual:
  case Normal, Muted, Focused, Selected, Disabled, Warning, Error

enum ThemeColor(val sgr: Int) derives CanEqual:
  case Black       extends ThemeColor(30)
  case Red         extends ThemeColor(31)
  case Green       extends ThemeColor(32)
  case Yellow      extends ThemeColor(33)
  case Blue        extends ThemeColor(34)
  case Magenta     extends ThemeColor(35)
  case Cyan        extends ThemeColor(36)
  case White       extends ThemeColor(37)
  case BrightWhite extends ThemeColor(97)

/** Closed SGR vocabulary: styling cannot add cells, cursors or arbitrary control sequences. */
final case class SemanticStyle(
    color: Option[ThemeColor] = None,
    bold: Boolean = false,
    dim: Boolean = false,
    underline: Boolean = false,
    reverse: Boolean = false
):
  def paint(value: String): String =
    val codes = color.map(_.sgr).toVector ++ Option.when(bold)(1) ++ Option.when(dim)(2) ++
      Option.when(underline)(4) ++ Option.when(reverse)(7)
    if codes.isEmpty then value
    else
      val prefix = s"\u001b[${codes.mkString(";")}m"
      prefix + value.replace(Ansi.Reset, Ansi.Reset + prefix) + Ansi.Reset

/** Immutable local theme. Explicit component overrides win; parent/sibling values never mutate. */
final case class SemanticTheme(styles: Map[SemanticRole, SemanticStyle]):
  def style(role: SemanticRole): SemanticStyle                                  = styles.getOrElse(role, SemanticStyle())
  def paint(role: SemanticRole, value: String): String                          =
    if styles.isEmpty then value else style(role).paint(value)
  def withOverrides(overrides: Map[SemanticRole, SemanticStyle]): SemanticTheme =
    SemanticTheme(styles ++ overrides)
  def role(
      focused: Boolean = false,
      selected: Boolean = false,
      disabled: Boolean = false
  ): SemanticRole                                                               =
    if disabled then SemanticRole.Disabled
    else if focused then SemanticRole.Focused
    else if selected then SemanticRole.Selected
    else SemanticRole.Normal

object SemanticTheme:
  val Plain: SemanticTheme                = SemanticTheme(Map.empty)
  val Monochrome: SemanticTheme           = SemanticTheme(Map(
    SemanticRole.Muted    -> SemanticStyle(dim = true),
    SemanticRole.Focused  -> SemanticStyle(bold = true, underline = true),
    SemanticRole.Selected -> SemanticStyle(reverse = true),
    SemanticRole.Disabled -> SemanticStyle(dim = true),
    SemanticRole.Warning  -> SemanticStyle(bold = true),
    SemanticRole.Error    -> SemanticStyle(bold = true, underline = true)
  ))
  val Dark: SemanticTheme                 = Monochrome.withOverrides(Map(
    SemanticRole.Normal  -> SemanticStyle(Some(ThemeColor.White)),
    SemanticRole.Focused -> SemanticStyle(Some(ThemeColor.Cyan), bold = true, underline = true),
    SemanticRole.Warning -> SemanticStyle(Some(ThemeColor.Yellow), bold = true),
    SemanticRole.Error   -> SemanticStyle(Some(ThemeColor.Red), bold = true, underline = true)
  ))
  val Light: SemanticTheme                = Dark.withOverrides(Map(
    SemanticRole.Normal  -> SemanticStyle(Some(ThemeColor.Black)),
    SemanticRole.Focused -> SemanticStyle(Some(ThemeColor.Blue), bold = true, underline = true)
  ))
  val HighContrast: SemanticTheme         = Monochrome.withOverrides(Map(
    SemanticRole.Focused  -> SemanticStyle(bold = true, underline = true, reverse = true),
    SemanticRole.Disabled -> SemanticStyle(underline = true)
  ))
  def named(value: String): SemanticTheme = value match
    case "plain"    => Plain
    case "mono"     => Monochrome
    case "dark"     => Dark
    case "light"    => Light
    case "contrast" => HighContrast
    case _          =>
      throw new IllegalArgumentException("theme must be plain, mono, dark, light or contrast")
