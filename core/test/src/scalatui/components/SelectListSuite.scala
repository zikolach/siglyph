package scalatui.components

import scalatui.ansi.Ansi

class SelectListSuite extends munit.FunSuite:
  test("select list preserves public constructor compatibility"):
    val default = new SelectList(Vector(SelectItem("a", "A")))
    val limited = new SelectList(
      Vector(SelectItem("a", "A"), SelectItem("b", "B")),
      1
    )

    assertEquals(default.render(20).map(Ansi.strip), Vector("> A"))
    assertEquals(limited.render(20).length, 1)

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

    val lines = list.render(28)
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

    val second = list.render(16)(1)
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

    val rendered = list.render(40)
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

    val rendered = Ansi.strip(list.render(40).mkString("\n"))
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

    val rendered = list.render(20).map(Ansi.strip)
    assertEquals(rendered.length, 2)
    assert(!rendered.exists(_.contains("of 3")), rendered.toString)

  test("select list truncates labels with labelMaxWidth"):
    val list = SelectList(
      Vector(SelectItem("long", "Very long label that should truncate", Some("desc"))),
      options = SelectListOptions(maxVisible = 1, labelMaxWidth = Some(8))
    )

    val wide     = list.render(80).head
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

    val narrow = list.render(1)
    assert(narrow.nonEmpty)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)
    assertEquals(list.render(0), Vector(""))

  test("select list renders configurable no-match text width-safely"):
    val list = SelectList(
      Vector.empty,
      options = SelectListOptions(
        noMatchText = "No matching entries",
        theme = SelectListTheme(noMatchText = text => s"\u001b[31m$text\u001b[0m")
      )
    )

    val line = list.render(10).head
    assert(line.contains("\u001b[31m"), line)
    assert(Ansi.strip(line).startsWith("No"), line)
    assert(Ansi.visibleWidth(line) <= 10, line)
