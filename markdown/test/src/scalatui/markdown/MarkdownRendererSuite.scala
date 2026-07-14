package scalatui.markdown

import scalatui.ansi.Ansi
import scalatui.terminal.TerminalCapabilities

class MarkdownRendererSuite extends munit.FunSuite:
  test("basic renderer supports documented markdown subset width-safely"):
    val markdown =
      """# Heading
        |
        |Paragraph with **bold**, _italic_, `code`, and [link](https://example.test).
        |
        |- first item
        |- second item
        |
        |> quoted text
        |
        |---
        |
        |```scala
        |val x = 1
        |```
        |
        || Name | Value |
        || ---- | ----- |
        || a    | b     |
        |""".stripMargin

    val lines = BasicMarkdownRenderer.default.render(markdown, 32)

    assert(lines.nonEmpty)
    assert(lines.exists(_.contains("# Heading")), lines.mkString("\n"))
    assert(lines.exists(_.contains("bold")), lines.mkString("\n"))
    assert(lines.exists(_.contains("- first item")), lines.mkString("\n"))
    assert(lines.exists(_.contains("> quoted text")), lines.mkString("\n"))
    assert(lines.exists(_.contains("```scala")), lines.mkString("\n"))
    assert(lines.exists(_.contains("Name | Value")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 32), lines.mkString("\n"))

  test("small widths are respected"):
    val lines = BasicMarkdownRenderer.default.render("## A very long heading", 8)
    assert(lines.nonEmpty)
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 8), lines.mkString("\n"))

  test("parser preserves list marker task marker and loose-list metadata"):
    val parsed = BasicMarkdownParser.parse("+ [ ] todo\n\n7. done")

    val lists = parsed.toOption.get.collect { case block: MarkdownBlock.DetailedListBlock => block }
    assertEquals(lists.length, 2)
    assertEquals(lists.head.items.head.sourceMarker, Some("+"))
    assertEquals(lists.head.items.head.taskMarker, Some("[ ]"))
    assertEquals(lists.head.items.head.text, "todo")
    assertEquals(lists(1).items.head.sourceMarker, Some("7."))

    val loose = BasicMarkdownParser.parse("- first\n\n- second").toOption.get.collect {
      case block: MarkdownBlock.DetailedListBlock => block
    }.head
    assertEquals(loose.items(1).blankLinesBefore, 1)

  test("default list rendering keeps normalized markers"):
    val unordered = BasicMarkdownRenderer.default.render("+ plus\n* star\n- dash", 20)
    val ordered   = BasicMarkdownRenderer.default.render("3. three\n7. seven", 20)

    assertEquals(unordered, Vector("- plus", "- star", "- dash"))
    assertEquals(ordered, Vector("1. three", "2. seven"))

  test("source marker option preserves unordered and ordered markers"):
    val renderer  = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        preserveSourceListMarkers = true
      )
    )
    val unordered = renderer.render("+ plus\n* star\n- dash", 20)
    val ordered   = renderer.render("3. three\n7. seven", 20)

    assertEquals(unordered, Vector("+ plus", "* star", "- dash"))
    assertEquals(ordered, Vector("3. three", "7. seven"))

  test("task markers remain visible and wrapped content aligns after task marker"):
    val lines = BasicMarkdownRenderer.default.render(
      "- [ ] todo item wraps\n- [x] done item wraps\n- [X] caps",
      14
    )

    assert(lines.exists(_.contains("- [ ] todo")), lines.mkString("\n"))
    assert(lines.exists(_.contains("- [x] done")), lines.mkString("\n"))
    assert(lines.exists(_.contains("- [X] caps")), lines.mkString("\n"))
    assert(lines.exists(_.startsWith("      ")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 14), lines.mkString("\n"))

  test("loose lists keep item separation while tight lists stay compact"):
    val loose = BasicMarkdownRenderer.default.render("- first\n\n- second", 20)
    val tight = BasicMarkdownRenderer.default.render("- first\n- second", 20)

    assertEquals(loose, Vector("- first", "", "- second"))
    assertEquals(tight, Vector("- first", "- second"))

  test("wrapped list continuation indentation follows rendered marker width"):
    val renderer  = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        preserveSourceListMarkers = true
      )
    )
    val unordered = renderer.render("* abc def ghi", 12)
    val ordered   = renderer.render("12. abc def ghi", 12)

    assert(unordered.exists(_.startsWith("  ")), unordered.mkString("\n"))
    assert(ordered.exists(_.startsWith("    ")), ordered.mkString("\n"))
    assert((unordered ++ ordered).forall(line => Ansi.visibleWidth(line) <= 12))

  test("parser failures fall back to readable plain text"):
    val renderer = BasicMarkdownRenderer(new MarkdownParser:
      override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] = Left("boom"))

    val lines = renderer.render("# still visible", 20)
    assertEquals(lines, Vector("# still visible"))

  test("theme hooks style headings and code blocks width-safely"):
    val renderer = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(theme =
        MarkdownTheme(
          heading = (_, text) => s"\u001b[1m$text\u001b[0m",
          codeBlockLine = line => s"\u001b[36m$line"
        )
      )
    )

    val lines = renderer.render("# Heading\n\n```\ncode\n```", 12)

    assert(lines.exists(_.contains("\u001b[1m# Heading\u001b[0m")), lines.mkString("\n"))
    assert(lines.exists(_.contains("\u001b[36mcode\u001b[0m")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 12), lines.mkString("\n"))

  test("hyperlink-capable rendering emits OSC 8 links"):
    val renderer = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        capabilities = TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)
      )
    )

    val lines = renderer.render("[label](https://example.test)", 80)

    assert(lines.exists(_.contains("\u001b]8;;https://example.test\u0007label\u001b]8;;\u0007")))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 80), lines.mkString("\n"))

  test("hyperlink-capable rendering falls back for unsafe link URLs"):
    val renderer = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        capabilities = TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)
      )
    )

    val lines = renderer.render("[label](https://example.test\u0007\u001b[31mbad)", 80)
    val raw   = lines.mkString("\n")

    assert(!raw.contains("\u001b]8;;"), raw)
    assert(!raw.exists(Character.isISOControl), raw)
    assert(raw.contains("label (https://example.test[31mbad)"), raw)

  test("non-hyperlink rendering keeps readable link fallback"):
    val lines = BasicMarkdownRenderer.default.render("[label](https://example.test)", 80)

    assert(lines.exists(_.contains("label (https://example.test)")), lines.mkString("\n"))

  test("partial streaming closing fences stay inside code block"):
    val oneTick  = BasicMarkdownRenderer.default.render("```scala\nval x = 1\n`", 80)
    val twoTicks = BasicMarkdownRenderer.default.render("```scala\nval x = 1\n``", 80)

    assertEquals(oneTick.filterNot(_.isEmpty), Vector("```scala", "val x = 1", "`", "```"))
    assertEquals(twoTicks.filterNot(_.isEmpty), Vector("```scala", "val x = 1", "``", "```"))

  test("complete streaming closing fence resumes normal block parsing"):
    val lines = BasicMarkdownRenderer.default.render("```scala\nval x = 1\n```\n\n# Done", 10)

    assert(lines.exists(_.contains("val x = 1")), lines.mkString("\n"))
    assert(lines.exists(_.contains("# Done")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 10), lines.mkString("\n"))

  test("highlighter hook can replace fenced code block lines"):
    val renderer = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        highlighter = Some(new MarkdownCodeHighlighter:
          override def highlight(language: Option[String], code: String): Option[Vector[String]] =
            Option.when(language.contains("scala"))(Vector(s"highlighted: $code")))
      )
    )

    val lines = renderer.render("```scala\nval x = 1\n```", 80)

    assert(lines.exists(_.contains("highlighted: val x = 1")), lines.mkString("\n"))

  test("parser adapter can provide blocks through the renderer contract"):
    val renderer = BasicMarkdownRenderer(new MarkdownParser:
      override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
        Right(Vector(MarkdownBlock.Paragraph(s"adapter: $markdown"))))

    assertEquals(renderer.render("content", 80), Vector("adapter: content"))

  test("Markdown component renders with padding within component width"):
    val component = Markdown("# Hi", paddingX = 1, paddingY = 1)
    val lines     = component.render(10).lines

    assertEquals(lines.head, "          ")
    assert(lines.exists(_.contains("# Hi")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 10), lines.mkString("\n"))
