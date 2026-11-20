package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.components.{Editor, EditorLayout, Text}
import scalatui.core.Component
import scalatui.editing.{EditorBuffer, EditorCursor}
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}

/** Real editor input and controlled component-width reflow, captured as immutable frames. */
private[demo] object EditorInteractionDemo:
  def frames(): Vector[Component] =
    val editor = Editor()
    editor.focused = true
    editor.addToHistory("Summarize last week's changes.")
    var width  = 52
    val result = Vector.newBuilder[Component]
    val cyan   = "\u001b[36m"
    val dim    = "\u001b[2m"
    val reset  = Ansi.Reset

    def capture(action: String, explanation: String): Unit =
      val rendered = editor.render(width)
      val layout   = EditorLayout.fromBuffer(EditorBuffer.fromText(editor.text, editor.cursor), width)
      val border   = "─".repeat(width + 2)
      val lines    = Vector(
        "\u001b[1m" + cyan + "SIGLYPH  /  A draft worth keeping" + reset,
        "Unreleased editor interaction preview · source checkout",
        "",
        cyan + action + reset,
        explanation,
        "",
        s"╭$border╮"
      ) ++ rendered.lines.map(line => "│ " + Ansi.padRight(line, width) + " │") ++ Vector(
        s"╰$border╯",
        "",
        s"Visual row ${layout.cursor.row + 1} / ${layout.lines.length}" +
          s"    Display column ${layout.cursor.column + 1}    Editor width $width",
        dim + "Real Editor + typed keys; controlled width changes, not a live terminal resize." +
          reset
      )
      result += Text(lines.mkString("\n"), paddingX = 0)

    def key(value: TerminalKey): Unit =
      editor.handleInput(TerminalInput.Key(value))

    capture("01  Compose", "Unicode, multiple lines, one retained draft.")
    Vector(
      "Review 界, café and 🙂",
      " with visual navigation across this wrapped draft.",
      "\nKeep this second line exactly as written."
    ).foreach { chunk =>
      key(TerminalKey.Character(chunk))
      capture("01  Compose", "Typing into the real editor; the inverse cell is its cursor.")
    }
    val draft       = editor.text
    editor.setCursor(EditorCursor(0, 12))
    val original    = editor.cursor
    capture("02  Choose a starting position", "Cursor positioned on the first visual row.")
    key(TerminalKey.Down)
    capture("03  Down", "Move one wrapped visual row, not one logical line.")
    key(TerminalKey.Down)
    capture("04  Down again", "Carry the preferred display column into the next logical line.")
    key(TerminalKey.Up)
    capture("05  Up", "Return through the wrapped rows.")
    key(TerminalKey.Up)
    require(editor.cursor === original, "vertical round trip must restore the cursor")
    capture("06  Up again", "Original cursor position restored; draft text unchanged.")
    width = 34
    capture(
      "07  Narrow the editor: 52 → 34 columns",
      "Reflow the same draft; preserve its logical cursor."
    )
    require(
      (editor.text === draft) && (editor.cursor === original),
      "reflow must preserve the draft"
    )
    key(TerminalKey.Down)
    capture("08  Down after reflow", "Navigation follows the newly wrapped rows.")
    key(TerminalKey.Up)
    require(editor.cursor === original, "narrow vertical round trip must restore cursor")
    key(TerminalKey.Home)
    val draftCursor = editor.cursor
    capture("09  Home", "At the first row's start, Up can deliberately enter history.")
    key(TerminalKey.Up)
    require(editor.text === "Summarize last week's changes.", "Up must recall history")
    capture("10  Up: recall history", "The draft is retained, not replaced permanently.")
    key(TerminalKey.Down)
    require(
      (editor.text === draft) && (editor.cursor === draftCursor),
      "Down must restore text and cursor"
    )
    capture(
      "11  Down: restore the draft",
      "Exact text and logical cursor restored, including Unicode."
    )
    width = 52
    capture(
      "12  Widen the editor: 34 → 52 columns",
      "Your draft is back. Keep typing where you left it."
    )
    require((editor.text === draft) && (editor.cursor === draftCursor), "final draft verification")
    capture(
      "READY TO CONTINUE",
      "Verified: visual navigation, width reflow, history, exact draft restoration."
    )
    result.result()
