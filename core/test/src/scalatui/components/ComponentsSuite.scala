package scalatui.components

import scalatui.ansi.Ansi
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey}

class ComponentsSuite extends munit.FunSuite:
  test("text wraps and pads within width"):
    val text = Text("hello world", paddingX = 1)
    val lines = text.render(8)
    assert(lines.forall(Ansi.visibleWidth(_) <= 8), lines.toString)
    assertEquals(lines.headOption.exists(_.startsWith(" ")), true)

  test("spacer renders empty lines"):
    assertEquals(Spacer(2).render(10), Vector("", ""))

  test("box wraps child with padding"):
    val box = Box(paddingX = 1, paddingY = 1)
    box.addChild(Text("x", paddingX = 0))
    val lines = box.render(5)
    assertEquals(lines.length, 3)
    assert(lines.forall(Ansi.visibleWidth(_) <= 5), lines.toString)

  test("select list navigates and selects"):
    val list = SelectList(Vector(SelectItem("a", "A"), SelectItem("b", "B")), maxVisible = 2)
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

  test("input supports readline-style ctrl shortcuts"):
    val input = Input("hello world")
    input.handleInput(TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("X")))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("e"), KeyModifiers(ctrl = true)))
    input.handleInput(TerminalInput.Key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)))

    assertEquals(input.value, "Xhello ")
