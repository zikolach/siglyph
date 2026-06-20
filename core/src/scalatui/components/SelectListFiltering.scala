package scalatui.components

/** Filtering behavior for [[SelectList]]. */
enum SelectListFiltering derives CanEqual:
  /** Filtering input is ignored and item order is unchanged. */
  case Disabled

  /** Case-insensitive containment filtering across value, label, and description. */
  case Containment

  /** Dependency-free fuzzy filtering and score ranking across value, label, and description. */
  case Fuzzy

  def enabled: Boolean = this match
    case Disabled => false
    case _        => true
