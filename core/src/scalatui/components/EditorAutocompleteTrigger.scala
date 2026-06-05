package scalatui.components

/** Controls when an [[Editor]] asks its provider for autocomplete suggestions. */
enum EditorAutocompleteTrigger derives CanEqual:
  /** Suggestions are requested only from explicit Tab input. */
  case ExplicitTabOnly

  /** Suggestions are requested by Tab and when typing `/` at the start of the current line. */
  case SlashAndTab

  def triggerSlash: Boolean = this match
    case ExplicitTabOnly => false
    case SlashAndTab     => true

object EditorAutocompleteTrigger:
  val Default: EditorAutocompleteTrigger = SlashAndTab
