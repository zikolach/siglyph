package scalatui.components

/** Filtering behavior for [[SettingsList]]. */
enum SettingsListFiltering derives CanEqual:
  /** Filtering input is ignored and row order is unchanged. */
  case Disabled

  /** Case-insensitive containment filtering across id, label, current value, and description. */
  case Containment

  /**
   * Dependency-free fuzzy filtering and score ranking across id, label, current value, and
   * description.
   */
  case Fuzzy

  def enabled: Boolean = this match
    case Disabled => false
    case _        => true
