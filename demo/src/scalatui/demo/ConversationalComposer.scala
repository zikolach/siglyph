package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.components.Editor
import scalatui.editing.EditorCursor
import scalatui.terminal.{TerminalInput, TerminalKey}

/** Core-only, deterministic composer walkthrough; no transport, worker or conversation service. */
object ConversationalComposer:
  def main(args: Array[String]): Unit =
    val editor     = Editor()
    var transcript = Vector.empty[String]
    editor.onSubmit = value =>
      // Sending, busy-state admission and queueing belong here, in application code.
      transcript :+= s"You: $value"
      transcript :+= s"Local echo: $value"
      editor.addToHistory(value)
      editor.setText("")
    editor.focused = true
    val width      = 12

    def press(key: TerminalKey): Unit =
      editor.handleInput(TerminalInput.Key(key))
      editor.render(width)
      ()

    editor.render(width)
    editor.insertAtCursor("Hello 界")
    press(TerminalKey.Enter)
    editor.insertAtCursor("A draft with e\u0301 and 🙂\nsecond line")
    editor.setCursor(EditorCursor(0, 3))
    press(TerminalKey.Down)
    press(TerminalKey.Up)
    assert(editor.cursor === EditorCursor(0, 3))
    editor.setCursor(EditorCursor(0, 0))
    val draft = editor.text
    press(TerminalKey.Up)
    assert(editor.text === "Hello 界")
    press(TerminalKey.Down)
    assert(editor.text === draft)
    assert(editor.cursor === EditorCursor(0, 0))
    transcript.foreach(println)
    println("Restored composer draft:")
    editor.render(width).lines.map(Ansi.strip).foreach(println)
    println("Draft text and cursor restored; visual-row navigation verified.")
