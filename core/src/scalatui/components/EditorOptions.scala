package scalatui.components

import scalatui.autocomplete.AutocompleteProvider
import scalatui.core.{OverlayAnchor, OverlayOptions, OverlaySize}
import scalatui.terminal.KeybindingManager

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
 * @param autocompletePlacement
 *   placement strategy used for autocomplete suggestions; defaults to editor-adjacent placement
 * @param keybindings
 *   command to input mapping resolved through the shared keybinding manager
 */
final case class EditorOptions(
    enterBehavior: EditorEnterBehavior = EditorEnterBehavior.Default,
    onChange: String => Unit = _ => (),
    onSubmit: String => Unit = _ => (),
    autocompleteProvider: Option[AutocompleteProvider] = None,
    autocompleteMaxVisible: Int = 5,
    autocompleteTrigger: EditorAutocompleteTrigger = EditorAutocompleteTrigger.Default,
    autocompletePlacement: EditorAutocompletePlacement = EditorAutocompletePlacement.Default,
    keybindings: KeybindingManager = KeybindingManager()
)

object EditorOptions:
  val FallbackAutocompleteOverlayOptions: OverlayOptions = OverlayOptions(
    width = Some(OverlaySize.Percent(100)),
    anchor = OverlayAnchor.BottomLeft,
    focusCapturing = true
  )
