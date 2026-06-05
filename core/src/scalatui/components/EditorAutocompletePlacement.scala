package scalatui.components

import scalatui.core.OverlayOptions

/** Placement strategy for editor autocomplete suggestion overlays. */
enum EditorAutocompletePlacement:
  /** Place suggestions immediately after the editor's current rendered visual area. */
  case AdjacentToEditor

  /** Use explicit overlay options supplied by the application. */
  case Custom(options: OverlayOptions)

object EditorAutocompletePlacement:
  val Default: EditorAutocompletePlacement = AdjacentToEditor
