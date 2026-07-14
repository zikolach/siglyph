package scalatui.components

import scalatui.TestInputStreams

import scalatui.ansi.Ansi
import scalatui.core.CursorPlacement
import scalatui.terminal.{
  KeyDescriptor,
  KeybindingManager,
  KeyModifiers,
  TerminalInput,
  TerminalKey
}

class ComponentsSuite extends munit.FunSuite:
  test("text wraps and pads within width"):
    val text  = Text("hello world", paddingX = 1)
    val lines = text.render(8).lines
    assert(lines.forall(Ansi.visibleWidth(_) <= 8), lines.toString)
    assertEquals(lines.headOption.exists(_.startsWith(" ")), true)

  test("spacer renders empty lines"):
    assertEquals(Spacer(2).render(10).lines, Vector("", ""))

  test("box wraps child with padding"):
    val box   = Box(paddingX = 1, paddingY = 1)
    box.addChild(Text("x", paddingX = 0))
    val lines = box.render(5).lines
    assertEquals(lines.length, 3)
    assert(lines.forall(Ansi.visibleWidth(_) <= 5), lines.toString)

  test("select list navigates and selects"):
    val list     = SelectList(Vector(SelectItem("a", "A"), SelectItem("b", "B")), maxVisible = 2)
    var selected = Option.empty[SelectItem]
    list.onSelect = item => selected = Some(item)

    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(selected.map(_.value), Some("b"))
    assertEquals(list.render(20).lines.head.startsWith("  A"), true)
    assertEquals(list.render(20).lines(1).startsWith("> B"), true)

  test("input edits unicode and submits"):
    val input = Input()
    input.handleInput(TerminalInput.Key(TerminalKey.Character("ä")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("🙂")))
    input.handleInput(TerminalInput.Key(TerminalKey.Left))
    input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    TestInputStreams.paste("x\ny").foreach(input.handleInput)

    var submitted = ""
    input.onSubmit = value => submitted = value
    input.handleInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(input.value, "x y🙂")
    assertEquals(submitted, "x y🙂")

  test("input consumes streaming paste chunks incrementally across UTF-8 boundaries"):
    val input = Input()
    val bytes = "界🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    input.handleInput(TerminalInput.PasteStart)
    bytes.foreach(byte =>
      input.handleInput(TerminalInput.PasteChunk(scalatui.terminal.TerminalInputChunk(Array(byte))))
    )
    input.handleInput(TerminalInput.PasteEnd)

    assertEquals(input.value, "界🙂")

  test("input keeps combining grapheme cursor across multiple paste chunks at varied positions"):
    assertPasteBoundaryGrapheme("e", "\u0301\u0327")

  test("input keeps ZWJ grapheme cursor across multiple paste chunks at varied positions"):
    assertPasteBoundaryGrapheme("👩", "\u200d💻")

  test("input keeps regional-indicator grapheme cursor across multiple chunks at varied positions"):
    assertPasteBoundaryGrapheme("🇦", "🇹")

  test("input submit keybinding is configurable"):
    val input     = Input(
      keybindings = KeybindingManager.fromRawBindings(
        Map(
          "tui.input.submit" -> Vector(
            KeyDescriptor(TerminalKey.Character("s"), KeyModifiers(ctrl = true))
          )
        )
      )
    )
    var submitted = ""
    input.onSubmit = value => submitted = value

    input.handleInput(TerminalInput.Key(TerminalKey.Character("a")))
    input.handleInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(submitted, "")

    input.handleInput(TerminalInput.Key(TerminalKey.Character("s"), KeyModifiers(ctrl = true)))
    assertEquals(submitted, "a")

  test("input supports custom movement, deletion, newline, and submit bindings"):
    val input     = Input(
      keybindings = KeybindingManager.fromRawBindings(
        Map(
          "tui.editor.cursorLeft"         -> Vector(KeyDescriptor(
            TerminalKey.Character("z"),
            KeyModifiers(alt = true)
          )),
          "tui.editor.deleteCharBackward" -> Vector(KeyDescriptor(
            TerminalKey.Character("x"),
            KeyModifiers(ctrl = true)
          )),
          "tui.input.newLine"             -> Vector(KeyDescriptor(
            TerminalKey.Character("n"),
            KeyModifiers(ctrl = true)
          )),
          "tui.input.submit"              -> Vector(KeyDescriptor(
            TerminalKey.Character("s"),
            KeyModifiers(ctrl = true)
          ))
        )
      )
    )
    var submitted = ""
    input.onSubmit = value => submitted = value

    input.handleInput(TerminalInput.Key(TerminalKey.Character("a")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("b")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(alt = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("x"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("n"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("s"), KeyModifiers(ctrl = true)))

    assertEquals(input.value, "\nb")
    assertEquals(submitted, "\nb")

  test("focused input emits structural cursor metadata without changing semantic value"):
    val input    = Input("ab")
    input.focused = true
    val rendered = input.render(10)
    assertEquals(input.value, "ab")
    assertEquals(Ansi.strip(rendered.lines.head), "ab ")
    assertEquals(rendered.cursorPlacements, Vector(CursorPlacement(0, 2)))

  test("unfocused input does not emit cursor metadata"):
    val input    = Input("ab")
    val rendered = input.render(10)

    assertEquals(rendered.cursorPlacements, Vector.empty)

  test("input cursor metadata follows fake-cursor truncation"):
    val truncated = Input("abcd")
    truncated.focused = true
    val omitted   = truncated.render(2)
    assertEquals(Ansi.strip(omitted.lines.head), "ab")
    assertEquals(omitted.cursorPlacements, Vector.empty)

    val visible   = Input("abcd")
    visible.focused = true
    visible.handleInput(TerminalInput.Key(TerminalKey.Home))
    visible.handleInput(TerminalInput.Key(TerminalKey.Right))
    val surviving = visible.render(2)
    assertEquals(Ansi.strip(surviving.lines.head), "ab")
    assertEquals(surviving.cursorPlacements, Vector(CursorPlacement(0, 1)))

    val overWide       = Input("界")
    overWide.focused = true
    overWide.handleInput(TerminalInput.Key(TerminalKey.Home))
    val overWideRender = overWide.render(1)
    assertEquals(overWideRender.lines.map(Ansi.strip), Vector(""))
    assertEquals(overWideRender.cursorPlacements, Vector.empty)

  test("input supports readline-style ctrl shortcuts"):
    val input = Input("hello world")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("X")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("e"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "Xhello ")

    input.handleInput(TerminalInput.Key(TerminalKey.Character("k"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "Xhello ")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "")

  test("input supports pi-tui undo-only and kill-ring commands"):
    val input = Input()
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("b")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("-"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "")

    input.setValue("hello world again")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "hello ")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "hello world again")

  test("input yank-pop cycles previous kills"):
    val input = Input("one two")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("k"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
    assertEquals(input.value, "one ")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(alt = true)))
    assertEquals(input.value, "two")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(alt = true)))
    assertEquals(input.value, "one ")
    assert(input.undo())
    assertEquals(input.value, "two")

  test("input yank-pop preserves neighbors joined by Unicode grapheme segmentation"):
    val cases = Vector(
      ("combining", "A", "\u0301", false),
      ("prepend", "B", "\u0600", true),
      ("Hangul", "\u1100", "\u1161", false),
      ("Indic", "\u0915\u094d", "\u0915", false),
      ("GB11", "👩\u200d", "💻", false),
      ("regional indicators", "🇦", "🇹", false)
    )

    cases.foreach { case (label, original, yanked, insertAtStart) =>
      val input = Input()
      Vector("x", yanked).foreach { candidate =>
        input.setValue(candidate)
        input.handleInput(TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)))
      }
      input.setValue(original)
      if insertAtStart then
        input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))

      input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)))
      val joined = if insertAtStart then yanked + original else original + yanked
      assertEquals(input.value, joined, label)

      input.handleInput(TerminalInput.Key(TerminalKey.Character("y"), KeyModifiers(alt = true)))
      val replaced = if insertAtStart then "x" + original else original + "x"
      assertEquals(input.value, replaced, label)

      input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
      assertEquals(input.value, original, label)
    }

  test("input clears the yank-pop base after another editing action"):
    val input = Input()
    Vector("first", "second").foreach { candidate =>
      input.setValue(candidate)
      input.handleInput(TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)))
    }
    input.setValue("base")
    assert(input.yank())
    input.handleInput(TerminalInput.Key(TerminalKey.Character("x")))

    assert(!input.yankPop())
    assertEquals(input.value, "basesecondx")

  test("input supports pi-tui word movement and forward deletion bindings"):
    val input = Input("hello, 世界 again")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("d"), KeyModifiers(alt = true)))
    assertEquals(input.value, "hello, again")

  private def assertPasteBoundaryGrapheme(firstPart: String, remaining: String): Unit =
    val charset        = java.nio.charset.StandardCharsets.UTF_8
    val firstBytes     = firstPart.getBytes(charset)
    val remainingBytes = remaining.getBytes(charset)
    val fillerBytes    = Array.fill[Byte](4096 - firstBytes.length)('x'.toByte)
    val chunks         = Vector(
      fillerBytes ++ firstBytes,
      remainingBytes.take(1),
      remainingBytes.slice(1, remainingBytes.length - 1),
      remainingBytes.takeRight(1)
    ).filter(_.nonEmpty)
    val pasted         = "x" * fillerBytes.length + firstPart + remaining

    Vector(0, 1, 2).foreach { insertionPosition =>
      val input = Input("AB")
      input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
      (0 until insertionPosition).foreach(_ =>
        input.handleInput(TerminalInput.Key(TerminalKey.Right))
      )
      input.handleInput(TerminalInput.PasteStart)
      chunks.foreach(bytes =>
        input.handleInput(TerminalInput.PasteChunk(scalatui.terminal.TerminalInputChunk(bytes)))
      )
      input.handleInput(TerminalInput.PasteEnd)

      val before = "AB".take(insertionPosition)
      val after  = "AB".drop(insertionPosition)
      assertEquals(input.value, before + pasted + after)

      input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
      assertEquals(input.value, before + "x" * fillerBytes.length + after)
      assert(input.undo())
      assertEquals(input.value, before + pasted + after)
    }
