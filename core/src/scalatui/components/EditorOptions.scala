package scalatui.components

/**
 * Public configuration for [[Editor]].
 *
 * @param enterBehavior
 *   how Enter and modified Enter events choose between newline insertion and submit callbacks
 * @param onChange
 *   callback invoked after input changes the editor text
 * @param onSubmit
 *   callback invoked when the configured submit key is received
 */
final case class EditorOptions(
    enterBehavior: EditorEnterBehavior = EditorEnterBehavior.Default,
    onChange: String => Unit = _ => (),
    onSubmit: String => Unit = _ => ()
)
