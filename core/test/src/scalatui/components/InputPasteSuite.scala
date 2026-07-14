package scalatui.components

import java.nio.charset.StandardCharsets

import scalatui.ansi.Ansi
import scalatui.core.{CursorMarker, InputResult}
import scalatui.terminal.{TerminalInput, TerminalInputChunk, TerminalKey}

class InputPasteSuite extends munit.FunSuite:
  test("paste session appends each decoded segment once without rebuilding accepted text"):
    val session = Input.PasteSession("left-right", 1, "left", "-right")
    val chunks  = Vector("a", "b", "e", "\u0301", "👩", "\u200d💻", "🇦", "🇹")

    chunks.foreach(text => session.append(chunk(text)))
    session.finish()

    assertEquals(session.appendCount, chunks.length.toLong)
    assertEquals(session.acceptedCharacterCount, chunks.map(_.length.toLong).sum)
    assertEquals(session.value, "leftabe\u0301👩‍💻🇦🇹-right")

  test("active paste exposes value and cursor render without requesting intermediate renders"):
    val input = Input("AB")
    input.focused = true
    input.handleInput(TerminalInput.Key(TerminalKey.Home))
    input.handleInput(TerminalInput.Key(TerminalKey.Right))

    assertEquals(input.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
    assertEquals(
      input.handleInputResult(TerminalInput.PasteChunk(chunk("e"))),
      InputResult.NoRender
    )
    assertEquals(
      input.handleInputResult(TerminalInput.PasteChunk(chunk("\u0301"))),
      InputResult.NoRender
    )
    assertEquals(input.value, "Ae\u0301B")
    val active = input.render(20).lines.head
    assertEquals(Ansi.strip(active), "Ae\u0301B")
    assert(active.indexOf(CursorMarker.Sequence) > active.indexOf("e"), active)

    assertEquals(input.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(input.value, "Ae\u0301B")

  test("paste interruption finalizes once before normal input and remains one undo edit"):
    val input = Input("AB")
    input.handleInput(TerminalInput.Key(TerminalKey.Home))
    input.handleInput(TerminalInput.Key(TerminalKey.Right))
    input.handleInputResult(TerminalInput.PasteStart)
    input.handleInputResult(TerminalInput.PasteChunk(chunk("x\ny")))

    assertEquals(
      input.handleInputResult(TerminalInput.Key(TerminalKey.Character("!"))),
      InputResult.Render
    )
    assertEquals(input.value, "Ax y!B")
    assert(input.undo())
    assertEquals(input.value, "Ax yB")
    assert(input.undo())
    assertEquals(input.value, "AB")

  test("multi-megabyte paste preserves exact text cursor backspace and undo"):
    val input       = Input("<>")
    val block       = "0123456789abcdef" * 256
    val blockBytes  = block.getBytes(StandardCharsets.UTF_8)
    val repetitions = 512
    val expected    = "<" + block * repetitions + ">"

    input.handleInput(TerminalInput.Key(TerminalKey.Home))
    input.handleInput(TerminalInput.Key(TerminalKey.Right))
    assertEquals(input.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
    (0 until repetitions).foreach { _ =>
      assertEquals(
        input.handleInputResult(TerminalInput.PasteChunk(TerminalInputChunk(blockBytes))),
        InputResult.NoRender
      )
    }
    assertEquals(input.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(input.value, expected)

    input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    assertEquals(input.value, expected.dropRight(2) + ">")
    assert(input.undo())
    assertEquals(input.value, expected)
    assert(input.undo())
    assertEquals(input.value, "<>")

  test("chunk-spanning graphemes keep cursor at start middle and end"):
    val graphemes = Vector(("e", "\u0301"), ("👩", "\u200d💻"), ("🇦", "🇹"))

    graphemes.foreach { case (first, rest) =>
      Vector(0, 1, 2).foreach { position =>
        val input = Input("AB")
        input.handleInput(TerminalInput.Key(TerminalKey.Home))
        (0 until position).foreach(_ => input.handleInput(TerminalInput.Key(TerminalKey.Right)))
        input.handleInputResult(TerminalInput.PasteStart)
        input.handleInputResult(TerminalInput.PasteChunk(chunk(first)))
        input.handleInputResult(TerminalInput.PasteChunk(chunk(rest)))
        input.handleInputResult(TerminalInput.PasteEnd)

        val before = "AB".take(position)
        val after  = "AB".drop(position)
        input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
        assertEquals(input.value, before + after)
      }
    }

  private def chunk(value: String): TerminalInputChunk =
    TerminalInputChunk(value.getBytes(StandardCharsets.UTF_8))
