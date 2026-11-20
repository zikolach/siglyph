package scalatui.demo

import scalatui.components.{Editor, Text}
import scalatui.core.TUI

/** Shared local-echo composer. Submission and any future queue policy belong to the application. */
object ConversationalComposerDemo:
  def install(tui: TUI): Unit =
    tui.exitsOnEscape = true
    tui.handlesControlC = true
    val transcript = Text("Local echo: type a message and press Enter.", paddingX = 0)
    val editor     = Editor()
    editor.onSubmit = value =>
      // No networking, work queue or busy-state policy is hidden in the editor.
      transcript.text = s"You: $value\nLocal echo: $value"
      editor.addToHistory(value)
      editor.setText("")
    tui.addChild(Text(
      "Composer: Enter sends; Shift+Enter inserts newline; Escape/Ctrl+C exits.\n" +
        "Resize to wrap. Up/Down move visual rows. Home then Up on the first row recalls history;\n" +
        "Down past its last row restores your draft. Try 界, é and 🙂.",
      paddingX = 0
    ))
    tui.addChild(transcript)
    tui.addChild(editor)
    tui.setFocus(editor)
