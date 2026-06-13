package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.CursorMarker
import scalatui.terminal.{KeyDescriptor, KeybindingManager, KeyModifiers, TerminalInput, TerminalKey}

class ComponentsSuite extends munit.FunSuite:
  test("text wraps and pads within width"):
    val text  = Text("hello world", paddingX = 1)
    val lines = text.render(8)
    assert(lines.forall(Ansi.visibleWidth(_) <= 8), lines.toString)
    assertEquals(lines.headOption.exists(_.startsWith(" ")), true)

  test("spacer renders empty lines"):
    assertEquals(Spacer(2).render(10), Vector("", ""))

  test("box wraps child with padding"):
    val box   = Box(paddingX = 1, paddingY = 1)
    box.addChild(Text("x", paddingX = 0))
    val lines = box.render(5)
    assertEquals(lines.length, 3)
    assert(lines.forall(Ansi.visibleWidth(_) <= 5), lines.toString)

  test("select list navigates and selects"):
    val list     = SelectList(Vector(SelectItem("a", "A"), SelectItem("b", "B")), maxVisible = 2)
    var selected = Option.empty[SelectItem]
    list.onSelect = item => selected = Some(item)

    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(selected.map(_.value), Some("b"))
    assertEquals(list.render(20).head.startsWith("  A"), true)
    assertEquals(list.render(20)(1).startsWith("> B"), true)

  test("input edits unicode and submits"):
    val input = Input()
    input.handleInput(TerminalInput.Key(TerminalKey.Character("ä")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("🙂")))
    input.handleInput(TerminalInput.Key(TerminalKey.Left))
    input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    input.handleInput(TerminalInput.Paste("x\ny"))

    var submitted = ""
    input.onSubmit = value => submitted = value
    input.handleInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(input.value, "x y🙂")
    assertEquals(submitted, "x y🙂")

  test("input submit keybinding is configurable"):
    val input = Input(
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
    val input = Input(
      keybindings = KeybindingManager.fromRawBindings(
        Map(
          "tui.editor.cursorLeft" -> Vector(KeyDescriptor(TerminalKey.Character("z"), KeyModifiers(alt = true))),
          "tui.editor.deleteCharBackward" -> Vector(KeyDescriptor(TerminalKey.Character("x"), KeyModifiers(ctrl = true))),
          "tui.input.newLine" -> Vector(KeyDescriptor(TerminalKey.Character("n"), KeyModifiers(ctrl = true))),
          "tui.input.submit" -> Vector(KeyDescriptor(TerminalKey.Character("s"), KeyModifiers(ctrl = true)))
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

  test("input emits cursor marker without changing semantic value"):
    val input    = Input("ab")
    input.focused = true
    val rendered = input.render(10).head
    assert(rendered.contains(CursorMarker.Sequence), rendered)
    assertEquals(input.value, "ab")
    assertEquals(Ansi.strip(rendered), "ab ")

  test("unfocused input does not emit cursor marker"):
    val input    = Input("ab")
    val rendered = input.render(10).head

    assert(!rendered.contains(CursorMarker.Sequence), rendered)

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

  test("input supports pi-tui word movement and forward deletion bindings"):
    val input = Input("hello, 世界 again")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Right, KeyModifiers(alt = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("d"), KeyModifiers(alt = true)))
    assertEquals(input.value, "hello, again")
