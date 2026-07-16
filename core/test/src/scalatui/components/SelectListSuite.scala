package scalatui.components

import java.nio.charset.StandardCharsets

import scalatui.TestInputStreams

import scalatui.ansi.Ansi
import scalatui.core.InputResult
import scalatui.terminal.{
  KeyEventType,
  KeyModifiers,
  TerminalInput,
  TerminalInputChunk,
  TerminalKey,
  TerminalRawKind
}

class SelectListSuite extends munit.FunSuite:
  private def renderedLabel(line: String): String =
    val stripped = Ansi.strip(line).trim
    if stripped.startsWith("> ") then stripped.drop(2) else stripped

  test("select list preserves public constructor compatibility"):
    val default = new SelectList(Vector(SelectItem("a", "A")))
    val limited = new SelectList(
      Vector(SelectItem("a", "A"), SelectItem("b", "B")),
      1
    )

    val legacyFiltered = SelectList(
      Vector(SelectItem("foo", "fooBar"), SelectItem("fast", "fast-boat")),
      options = SelectListOptions(10, true)
    )
    "fb".foreach(ch =>
      legacyFiltered.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString)))
    )

    assertEquals(default.render(20).lines.map(Ansi.strip), Vector("> A"))
    assertEquals(limited.render(20).lines.length, 1)
    assertEquals(legacyFiltered.selected, None)

  test("select list applies theme hooks to selected rows width-safely"):
    val list = SelectList(
      Vector(
        SelectItem("alpha", "Alpha item", Some("Selected description")),
        SelectItem("beta", "Beta item", Some("Other description"))
      ),
      options = SelectListOptions(
        maxVisible = 5,
        selectedPrefix = "▶ ",
        normalPrefix = "· ",
        theme = SelectListTheme(
          selectedPrefix = text => s"\u001b[33m$text\u001b[0m",
          selectedText = text => s"\u001b[1m$text\u001b[0m",
          description = text => s"\u001b[2m$text\u001b[0m"
        )
      )
    )

    val lines = list.render(28).lines
    assert(lines.head.contains("\u001b[33m"), lines.head)
    assert(lines.head.contains("\u001b[1m"), lines.head)
    assert(lines.head.contains("\u001b[2m"), lines.head)
    assert(lines.forall(Ansi.visibleWidth(_) <= 28), lines.toString)

  test("select list applies unselected prefix and text theme hooks width-safely"):
    val list = SelectList(
      Vector(
        SelectItem("alpha", "Alpha"),
        SelectItem("beta", "Beta")
      ),
      options = SelectListOptions(
        normalPrefix = "· ",
        theme = SelectListTheme(
          normalPrefix = text => s"\u001b[35m$text\u001b[0m",
          normalText = text => s"\u001b[4m$text\u001b[0m"
        )
      )
    )

    val second = list.render(16).lines(1)
    assert(second.contains("\u001b[35m"), second)
    assert(second.contains("\u001b[4m"), second)
    assert(Ansi.strip(second).startsWith("· Beta"), second)
    assert(Ansi.visibleWidth(second) <= 16, second)

  test("select list renders descriptions and themed scroll info width-safely"):
    val list = SelectList(
      Vector(
        SelectItem("alpha", "Alpha", Some("First description")),
        SelectItem("beta", "Beta", Some("Second description")),
        SelectItem("gamma", "Gamma", Some("Third description"))
      ),
      options = SelectListOptions(
        maxVisible = 2,
        showScrollInfo = true,
        theme = SelectListTheme(scrollInfo = text => s"\u001b[36m$text\u001b[0m")
      )
    )

    val rendered = list.render(40).lines
    val stripped = rendered.map(Ansi.strip)
    assertEquals(rendered.length, 2)
    assert(stripped.exists(_.contains("First description")), stripped.toString)
    assert(stripped.exists(_.contains("1-1 of 3")), stripped.toString)
    assert(rendered.exists(_.contains("\u001b[36m")), rendered.toString)
    assert(rendered.forall(Ansi.visibleWidth(_) <= 40), rendered.toString)

  test("select list can hide descriptions"):
    val list = SelectList(
      Vector(SelectItem("alpha", "Alpha", Some("Hidden description"))),
      options = SelectListOptions(showDescriptions = false)
    )

    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(!rendered.contains("Hidden description"), rendered)

  test("select list can hide scroll info"):
    val list = SelectList(
      Vector(
        SelectItem("alpha", "Alpha"),
        SelectItem("beta", "Beta"),
        SelectItem("gamma", "Gamma")
      ),
      options = SelectListOptions(maxVisible = 2, showScrollInfo = false)
    )

    val rendered = list.render(20).lines.map(Ansi.strip)
    assertEquals(rendered.length, 2)
    assert(!rendered.exists(_.contains("of 3")), rendered.toString)

  test("select list truncates labels with labelMaxWidth"):
    val list = SelectList(
      Vector(SelectItem("long", "Very long label that should truncate", Some("desc"))),
      options = SelectListOptions(maxVisible = 1, labelMaxWidth = Some(8))
    )

    val wide     = list.render(80).lines.head
    val stripped = Ansi.strip(wide)
    assert(stripped.contains("Very lon"), stripped)
    assert(!stripped.contains("g label"), stripped)
    assert(stripped.contains("desc"), stripped)
    assert(Ansi.visibleWidth(wide) <= 80, wide)

  test("select list handles narrow and zero render widths"):
    val list = SelectList(
      Vector(SelectItem("long", "Very long label", Some("desc"))),
      options = SelectListOptions(maxVisible = 1)
    )

    val narrow = list.render(1).lines
    assert(narrow.nonEmpty)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)
    assertEquals(list.render(0).lines, Vector(""))

  test("select list filters results by value label or description"):
    val list = SelectList(
      Vector(
        SelectItem("a", "Alpha", Some("first")),
        SelectItem("b", "Beta", Some("second")),
        SelectItem("g", "Gamma", Some("third"))
      ),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    list.handleInput(
      TerminalInput.Key(TerminalKey.Character("g"))
    )

    assertEquals(list.query, "g")
    assertEquals(list.selected.map(_.label), Some("Gamma"))
    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("Gamma"), rendered)
    assert(!rendered.contains("Alpha"), rendered)

  test("select list filters unique value label and description fields"):
    def renderedFor(query: String): String =
      val list = SelectList(
        Vector(
          SelectItem("value-only-token", "Alpha", Some("first")),
          SelectItem("b", "label-only-token", Some("second")),
          SelectItem("g", "Gamma", Some("description-only-token"))
        ),
        options = SelectListOptions(filtering = SelectListFiltering.Containment)
      )
      query.foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))
      Ansi.strip(list.render(80).lines.mkString("\n"))

    assert(renderedFor("value-only").contains("Alpha"))
    assert(renderedFor("label-only").contains("label-only-token"))
    assert(renderedFor("description-only").contains("description-only-token"))

  test("select list fuzzy filtering matches value label and description fields"):
    def renderedFor(query: String): String =
      val list = SelectList(
        Vector(
          SelectItem("vto", "Alpha", Some("first")),
          SelectItem("b", "label-only-token", Some("second")),
          SelectItem("g", "Gamma", Some("description-only-token"))
        ),
        options = SelectListOptions(filtering = SelectListFiltering.Fuzzy)
      )
      query.foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))
      Ansi.strip(list.render(80).lines.mkString("\n"))

    assert(renderedFor("vto").contains("Alpha"))
    assert(renderedFor("lot").contains("label-only-token"))
    assert(renderedFor("dot").contains("description-only-token"))

  test("select list fuzzy filtering omits nonmatching candidates"):
    val list = SelectList(
      Vector(SelectItem("foo", "fooBar"), SelectItem("zeta", "Zeta")),
      options = SelectListOptions(
        filtering = SelectListFiltering.Fuzzy,
        noMatchText = "No fuzzy matches"
      )
    )

    "fb".foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))

    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("fooBar"), rendered)
    assert(!rendered.contains("Zeta"), rendered)

    list.handleInput(TerminalInput.Key(TerminalKey.Character("x")))
    val noMatch = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(noMatch.contains("No fuzzy matches"), noMatch)
    assert(!noMatch.contains("fooBar"), noMatch)

  test("select list filteringEnabled remains containment-compatible"):
    val list = SelectList(
      Vector(SelectItem("foo", "fooBar"), SelectItem("fast", "fast-boat")),
      options = SelectListOptions(filteringEnabled = true)
    )

    "fb".foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))

    assertEquals(list.selected, None)
    assert(Ansi.strip(list.render(40).lines.mkString("\n")).contains("No items"))

  test("select list fuzzy filtering ranks matches by score"):
    val list = SelectList(
      Vector(
        SelectItem("fuzzy boilerplate", "fuzzy boilerplate"),
        SelectItem("fast-boat", "fast-boat"),
        SelectItem("fooBar", "fooBar")
      ),
      options = SelectListOptions(filtering = SelectListFiltering.Fuzzy)
    )

    "fb".foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))

    val labels = list.render(80).lines.map(renderedLabel)
    assertEquals(labels, Vector("fooBar", "fast-boat", "fuzzy boilerplate"))

  test("select list fuzzy filtering preserves stable ordering for equal scores"):
    val list = SelectList(
      Vector(
        SelectItem("same", "One"),
        SelectItem("same", "Two"),
        SelectItem("same", "Three")
      ),
      options = SelectListOptions(filtering = SelectListFiltering.Fuzzy)
    )

    "same".foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))

    val rendered = list.render(80).lines.map(line => Ansi.strip(line).trim)
    assertEquals(rendered, Vector("> One", "Two", "Three"))

  test("select list containment filtering does not apply fuzzy-only matches"):
    val list = SelectList(
      Vector(SelectItem("foo", "fooBar"), SelectItem("fast", "fast-boat")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    "fb".foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))

    assertEquals(list.selected, None)
    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("No items"), rendered)
    assert(!rendered.contains("fooBar"), rendered)

  test("select list ignores printable filter input when filtering disabled"):
    val list = SelectList(Vector(SelectItem("a", "Alpha")))

    list.handleInput(TerminalInput.Key(TerminalKey.Character("z")))

    assertEquals(list.query, "")
    assert(Ansi.strip(list.render(40).lines.mkString("\n")).contains("Alpha"))

  test("select list filtering accepts shift-modified printable input"):
    val list = SelectList(
      Vector(SelectItem("ab", "Alpha Beta"), SelectItem("xy", "Other")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("A"), KeyModifiers(shift = true)))

    assertEquals(list.query, "A")
    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("Alpha Beta"), rendered)
    assert(!rendered.contains("Other"), rendered)

  test("select list filtering paste normalizes newlines"):
    val list = SelectList(
      Vector(SelectItem("ac", "Alpha Cat"), SelectItem("xy", "Other")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    TestInputStreams.paste("Alpha\nCat").foreach(list.handleInput)

    assertEquals(list.query, "Alpha Cat")
    assert(Ansi.strip(list.render(40).lines.mkString("\n")).contains("Alpha Cat"))

  test("select list filtering rejects shortcut-modified printable input"):
    val list = SelectList(
      Vector(SelectItem("alpha", "Alpha"), SelectItem("zeta", "Zeta")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(ctrl = true)))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(alt = true)))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(superKey = true)))

    assertEquals(list.query, "")
    assertEquals(list.selected.map(_.value), Some("alpha"))
    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("Alpha"), rendered)
    assert(rendered.contains("Zeta"), rendered)

  test("select list backspace removes a non-BMP grapheme"):
    val list = SelectList(
      Vector(SelectItem("alpha", "Alpha"), SelectItem("smile", "a🙂 match")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("a")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("🙂")))
    assertEquals(list.query, "a🙂")
    list.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    assertEquals(list.query, "a")
    val rendered = Ansi.strip(list.render(40).lines.mkString("\n"))
    assert(rendered.contains("Alpha"), rendered)
    assert(rendered.contains("a🙂 match"), rendered)

  test("select list selection-change callback covers filter transitions"):
    var changes = Vector.empty[Option[SelectItem]]
    val list    = SelectList(
      Vector(
        SelectItem("alpha", "Alpha"),
        SelectItem("beta", "Beta"),
        SelectItem("gamma", "Gamma")
      ),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    list.onSelectionChange = item => changes :+= item

    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("g")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("z")))

    assertEquals(changes.map(_.map(_.value)), Vector(Some("beta"), Some("gamma"), None))

  test("select list filtering does not notify when selection is preserved"):
    var changes = Vector.empty[Option[SelectItem]]
    val list    = SelectList(
      Vector(SelectItem("alpha", "Alpha"), SelectItem("beta", "Beta")),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    list.onSelectionChange = item => changes :+= item

    list.handleInput(TerminalInput.Key(TerminalKey.Character("a")))

    assertEquals(list.selected.map(_.value), Some("alpha"))
    assertEquals(changes, Vector.empty)

  test("select list renders configurable no-match text for empty filter results"):
    val list = SelectList(
      Vector(SelectItem("a", "Alpha")),
      options = SelectListOptions(
        filtering = SelectListFiltering.Containment,
        noMatchText = "No matching entries",
        theme = SelectListTheme(noMatchText = text => s"\u001b[31m$text\u001b[0m")
      )
    )

    list.handleInput(
      TerminalInput.Key(TerminalKey.Character("z"))
    )

    val line = list.render(20).lines.head
    assert(line.contains("\u001b[31m"), line)
    assert(Ansi.strip(line).contains("No matching"), line)
    assert(Ansi.visibleWidth(line) <= 20, line)

  test("select list invokes selection-change callback with keyboard navigation payloads"):
    var changes = Vector.empty[Option[SelectItem]]
    val list    = SelectList(
      Vector(
        SelectItem("a", "Alpha"),
        SelectItem("b", "Beta"),
        SelectItem("g", "Gamma")
      )
    )
    list.onSelectionChange = item => changes :+= item

    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Up))

    assertEquals(changes.map(_.map(_.value)), Vector(Some("b"), Some("g"), Some("b")))

  test("select list keeps selected item stable when filtering preserves it"):
    val list = SelectList(
      Vector(
        SelectItem("alpha", "Alpha"),
        SelectItem("beta", "Beta"),
        SelectItem("delta", "Delta")
      ),
      options = SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    assertEquals(list.selected.map(_.value), Some("beta"))

    list.handleInput(
      TerminalInput.Key(TerminalKey.Character("e"))
    )

    assertEquals(list.query, "e")
    assertEquals(list.selected.map(_.value), Some("beta"))

  test("select list renders configurable no-match text for empty items width-safely"):
    val list = SelectList(
      Vector.empty,
      options = SelectListOptions(
        noMatchText = "No matching entries",
        theme = SelectListTheme(noMatchText = text => s"\u001b[31m$text\u001b[0m")
      )
    )

    val line = list.render(10).lines.head
    assert(line.contains("\u001b[31m"), line)
    assert(Ansi.strip(line).startsWith("No"), line)
    assert(Ansi.visibleWidth(line) <= 10, line)

  test("select list navigation returns precise results and callback counts"):
    val list    = SelectList(
      Vector(SelectItem("a", "Alpha"), SelectItem("b", "Beta"))
    )
    var changes = 0
    list.onSelectionChange = _ => changes += 1

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Up)),
      InputResult.NoRender
    )
    assertEquals(changes, 0)
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Down)),
      InputResult.Render
    )
    assertEquals(changes, 1)
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Down)),
      InputResult.NoRender
    )
    assertEquals(changes, 1)

    val empty        = SelectList(Vector.empty)
    var emptyChanges = 0
    empty.onSelectionChange = _ => emptyChanges += 1
    assertEquals(
      empty.handleInputResult(TerminalInput.Key(TerminalKey.Up)),
      InputResult.NoRender
    )
    assertEquals(
      empty.handleInputResult(TerminalInput.Key(TerminalKey.Down)),
      InputResult.NoRender
    )
    assertEquals(emptyChanges, 0)

  test("select list duplicate item movement renders without duplicate callback"):
    val duplicate = SelectItem("same", "Same")
    val finalItem = SelectItem("final", "Final")
    val list      = SelectList(Vector(duplicate, duplicate.copy(), finalItem))
    var changes   = Vector.empty[Option[SelectItem]]
    list.onSelectionChange = item => changes :+= item

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Down)),
      InputResult.Render
    )
    assertEquals(changes, Vector.empty)
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Down)),
      InputResult.Render
    )
    assertEquals(list.selected, Some(finalItem))
    assertEquals(changes, Vector(Some(finalItem)))

  test("select list activation and cancel preserve callback cardinality"):
    val item       = SelectItem("a", "Alpha")
    val list       = SelectList(Vector(item))
    var selections = Vector.empty[SelectItem]
    var cancels    = 0
    var changes    = 0
    list.onSelect = selected => selections :+= selected
    list.onCancel = () => cancels += 1
    list.onSelectionChange = _ => changes += 1

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Enter)),
      InputResult.Render
    )
    assertEquals(selections, Vector(item))
    assertEquals(cancels, 0)
    assertEquals(changes, 0)
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Escape)),
      InputResult.Render
    )
    assertEquals(selections, Vector(item))
    assertEquals(cancels, 1)
    assertEquals(changes, 0)

    val empty           = SelectList(Vector.empty)
    var emptySelections = 0
    empty.onSelect = _ => emptySelections += 1
    assertEquals(
      empty.handleInputResult(TerminalInput.Key(TerminalKey.Enter)),
      InputResult.NoRender
    )
    assertEquals(emptySelections, 0)

  test("select list filtering returns precise mutation and no-match callback results"):
    val list    = SelectList(
      Vector(SelectItem("alpha", "Alpha"), SelectItem("beta", "Beta")),
      SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    var changes = Vector.empty[Option[SelectItem]]
    list.onSelectionChange = item => changes :+= item

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Character("a"))),
      InputResult.Render
    )
    assertEquals(changes, Vector.empty)
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Character("z"))),
      InputResult.Render
    )
    assertEquals(changes, Vector(None))
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Backspace)),
      InputResult.Render
    )
    assertEquals(changes.map(_.map(_.value)), Vector(None, Some("alpha")))

    val clean        = SelectList(
      Vector(SelectItem("alpha", "Alpha")),
      SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    var cleanChanges = 0
    clean.onSelectionChange = _ => cleanChanges += 1
    assertEquals(
      clean.handleInputResult(TerminalInput.Key(TerminalKey.Backspace)),
      InputResult.Ignored
    )
    assertEquals(cleanChanges, 0)

  test("select list ignores unsupported input and all paste frames when filtering is disabled"):
    val list       = SelectList(Vector(SelectItem("a", "Alpha")))
    var selections = 0
    var cancels    = 0
    var changes    = 0
    list.onSelect = _ => selections += 1
    list.onCancel = () => cancels += 1
    list.onSelectionChange = _ => changes += 1

    val ignored = Vector(
      TerminalInput.Key(TerminalKey.Tab),
      TerminalInput.Key(TerminalKey.Character("z")),
      TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(ctrl = true)),
      TerminalInput.RawStart(TerminalRawKind.Csi),
      TerminalInput.PasteStart,
      TerminalInput.PasteChunk(TerminalInputChunk("x".getBytes(StandardCharsets.UTF_8))),
      TerminalInput.PasteEnd
    )
    ignored.foreach(input => assertEquals(list.handleInputResult(input), InputResult.Ignored))
    assertEquals(list.query, "")
    assertEquals(selections, 0)
    assertEquals(cancels, 0)
    assertEquals(changes, 0)

  test("select list keeps existing key-event matching for modifiers and releases"):
    val list    = SelectList(Vector(SelectItem("a", "Alpha"), SelectItem("b", "Beta")))
    var changes = 0
    list.onSelectionChange = _ => changes += 1

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Down, KeyModifiers(shift = true))),
      InputResult.Render
    )
    assertEquals(
      list.handleInputResult(TerminalInput.KeyEvent(
        TerminalKey.Up,
        eventType = KeyEventType.Release
      )),
      InputResult.Render
    )
    assertEquals(changes, 2)
