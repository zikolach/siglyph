package scalatui.components

import java.nio.charset.StandardCharsets

import scalatui.ansi.Ansi
import scalatui.core.{CursorPlacement, InputResult}
import scalatui.terminal.{TerminalInput, TerminalInputBuffer, TerminalInputChunk, TerminalKey}

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
    val active = input.render(20)
    assertEquals(Ansi.strip(active.lines.head), "Ae\u0301B")
    assertEquals(active.cursorPlacements, Vector(CursorPlacement(0, 2)))

    assertEquals(input.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(input.value, "Ae\u0301B")

  test("pasted former cursor APC and ESC or C1 string candidates stay inert"):
    val formerApc  = "\u001b_" + "pi" + ":c\u001b\\"
    val candidates = Vector(
      formerApc,
      "\u001b_payload",
      "\u009fpayload\u009c",
      "\u009fpayload"
    )

    candidates.foreach { candidate =>
      val input    = Input()
      input.handleInputResult(TerminalInput.PasteStart)
      input.handleInputResult(TerminalInput.PasteChunk(chunk(candidate + "x")))
      input.handleInputResult(TerminalInput.PasteEnd)
      val expected = Ansi.sanitize(candidate + "x")

      val unfocused = input.render(200)
      assertEquals(Ansi.strip(unfocused.lines.head), expected + " ", candidate)
      assertEquals(unfocused.cursorPlacements, Vector.empty, candidate)

      input.focused = true
      val focused = input.render(200)
      assertEquals(Ansi.strip(focused.lines.head), expected + " ", candidate)
      assertEquals(
        focused.cursorPlacements,
        Vector(CursorPlacement(0, Ansi.visibleWidth(expected))),
        candidate
      )
    }

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
    val graphemes = Vector(
      ("\u1100", "\u1161\u11a8"),
      ("\u0915", "\u094d\u0915"),
      ("e", "\u0301"),
      ("👩", "\u200d💻"),
      ("🇦", "🇹")
    )

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

  test("typed and streamed insertion cursor follows final neighbor joins"):
    val cases = Vector(
      ("AB", 1, "\u0301", "B"),
      ("AB", 1, "\u0600", "A"),
      ("\u1100\u11a8", 1, "\u1161", ""),
      ("\u0915\u0915", 1, "\u094d", ""),
      ("👩💻", 1, "\u200d", ""),
      ("🇹", 0, "🇦", "")
    )

    cases.foreach { case (initial, position, inserted, afterBackspace) =>
      Vector(false, true).foreach { streamed =>
        val input = Input(initial)
        input.handleInput(TerminalInput.Key(TerminalKey.Home))
        (0 until position).foreach(_ => input.handleInput(TerminalInput.Key(TerminalKey.Right)))
        if streamed then
          input.handleInput(TerminalInput.PasteStart)
          input.handleInput(TerminalInput.PasteChunk(chunk(inserted)))
          input.handleInput(TerminalInput.PasteEnd)
        else input.handleInput(TerminalInput.Key(TerminalKey.Character(inserted)))
        input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
        assertEquals(input.value, afterBackspace, s"$inserted streamed=$streamed")
      }
    }

  test("fragmented UTF-8 preserves every focused grapheme as one inserted cursor unit"):
    val graphemes = Vector(
      "\u1100\u1161\u11a8",
      "\u0915\u094d\u0915",
      "e\u0301",
      "👩‍💻",
      "🇦🇹"
    )
    graphemes.foreach { grapheme =>
      val input  = Input("AB")
      val parser = TerminalInputBuffer()
      input.handleInput(TerminalInput.Key(TerminalKey.Home))
      input.handleInput(TerminalInput.Key(TerminalKey.Right))
      val bytes  = ("\u001b[200~" + grapheme + "\u001b[201~").getBytes(StandardCharsets.UTF_8)
      bytes.foreach(byte =>
        parser.process(TerminalInputChunk(Array(byte))).foreach(input.handleInput)
      )
      input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
      assertEquals(input.value, "AB", grapheme)
    }

  private def chunk(value: String): TerminalInputChunk =
    TerminalInputChunk(value.getBytes(StandardCharsets.UTF_8))
