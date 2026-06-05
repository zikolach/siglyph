package scalatui.components

import scalatui.autocomplete.AutocompleteProvider
import scalatui.core.{OverlayAnchor, OverlayOptions, OverlaySize}

/**
 * Public configuration for [[Editor]].
 *
 * @param enterBehavior
 *   how Enter and modified Enter events choose between newline insertion and submit callbacks
 * @param onChange
 *   callback invoked after input changes the editor text
 * @param onSubmit
 *   callback invoked when the configured submit key is received
 * @param autocompleteProvider
 *   optional provider used to populate overlay-backed editor suggestions
 * @param autocompleteMaxVisible
 *   maximum number of suggestion rows shown by the autocomplete overlay
 * @param autocompleteTrigger
 *   automatic trigger policy for autocomplete requests
 * @param autocompleteOverlayOptions
 *   initial overlay placement used for autocomplete suggestions
 */
final case class EditorOptions(
    enterBehavior: EditorEnterBehavior = EditorEnterBehavior.Default,
    onChange: String => Unit = _ => (),
    onSubmit: String => Unit = _ => (),
    autocompleteProvider: Option[AutocompleteProvider] = None,
    autocompleteMaxVisible: Int = 5,
    autocompleteTrigger: EditorAutocompleteTrigger = EditorAutocompleteTrigger.Default,
    autocompleteOverlayOptions: OverlayOptions = EditorOptions.DefaultAutocompleteOverlayOptions
)

object EditorOptions:
  val DefaultAutocompleteOverlayOptions: OverlayOptions = OverlayOptions(
    width = Some(OverlaySize.Percent(100)),
    anchor = OverlayAnchor.BottomLeft,
    focusCapturing = true
  )
