package scalatui.markdown

import scalatui.ansi.Ansi
import scalatui.terminal.TerminalCapabilities
import scala.util.control.ControlThrowable

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

  test("basic parser recovery converts non-fatal failures to readable errors"):
    val withMessage    = BasicMarkdownParser.recoverParse(
      throw new RuntimeException("parse failed")
    )
    val withoutMessage = BasicMarkdownParser.recoverParse(
      throw new RuntimeException(null: String)
    )

    assertEquals(withMessage, Left("parse failed"))
    assertEquals(withoutMessage, Left("RuntimeException"))

  test("basic parser recovery propagates the identical fatal failure"):
    val fatal  = new ControlThrowable {}
    val thrown =
      try
        BasicMarkdownParser.recoverParse(throw fatal)
        throw new AssertionError("expected fatal failure to propagate")
      catch case error: ControlThrowable => error

    assert(thrown eq fatal)

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

  test("direct rendering at non-positive width bypasses all rendering callbacks"):
    var parserCalls                   = 0
    var themeCalls                    = 0
    var highlighterCalls              = 0
    val parser                        = new MarkdownParser:
      override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
        parserCalls += 1
        Right(Vector(MarkdownBlock.CodeBlock(Some("scala"), markdown)))
    def themed(value: String): String =
      themeCalls += 1
      value
    val renderer                      = BasicMarkdownRenderer(
      parser,
      MarkdownRenderOptions(
        theme = MarkdownTheme(
          heading = (_, value) => themed(value),
          paragraph = themed,
          codeSpan = themed,
          codeBlockFence = themed,
          codeBlockLine = themed,
          blockQuotePrefix = themed,
          blockQuoteText = themed,
          horizontalRule = themed,
          listMarker = themed,
          tableRow = themed,
          emphasis = themed,
          strong = themed,
          link = (label, _) => themed(label),
          linkFallback = (label, _) => themed(label)
        ),
        highlighter = Some(new MarkdownCodeHighlighter:
          override def highlight(language: Option[String], code: String): Option[Vector[String]] =
            highlighterCalls += 1
            Some(Vector(code)))
      )
    )

    assertEquals(renderer.render("code", 0), Vector.empty)
    assertEquals(renderer.render("code", -1), Vector.empty)
    assertEquals(parserCalls, 0)
    assertEquals(themeCalls, 0)
    assertEquals(highlighterCalls, 0)

  test("direct width-zero rendering is safe for every supported block category"):
    val markdownByCategory = Vector(
      "heading"            -> "# Heading",
      "unordered list"     -> "- item",
      "ordered list"       -> "1. item",
      "block quote"        -> "> quote",
      "fenced code"        -> "```scala\nval x = 1\n```",
      "inline styled text" -> "**bold** _italic_ `code`",
      "ordinary text"      -> "ordinary text",
      "horizontal rule"    -> "---",
      "table"              -> "| Name | Value |\n| ---- | ----- |\n| a | b |"
    )

    markdownByCategory.foreach { (category, markdown) =>
      val lines = BasicMarkdownRenderer.default.render(markdown, 0)
      assert(
        lines.forall(line => Ansi.visibleWidth(line) <= 0),
        s"$category produced positive-width output: ${lines.mkString("|")}"
      )
    }

  test("positive-width rendering preserves output and callback behavior"):
    var parserCalls      = 0
    var fenceCalls       = 0
    var lineCalls        = 0
    var highlighterCalls = 0
    val renderer         = BasicMarkdownRenderer(
      new MarkdownParser:
        override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
          parserCalls += 1
          Right(Vector(MarkdownBlock.CodeBlock(Some("scala"), markdown)))
      ,
      MarkdownRenderOptions(
        theme = MarkdownTheme(
          codeBlockFence = value =>
            fenceCalls += 1
            value
          ,
          codeBlockLine = value =>
            lineCalls += 1
            value
        ),
        highlighter = Some(new MarkdownCodeHighlighter:
          override def highlight(language: Option[String], code: String): Option[Vector[String]] =
            highlighterCalls += 1
            Some(Vector(s"highlighted: $code")))
      )
    )

    val lines = renderer.render("val x = 1", 40)

    assertEquals(lines, Vector("```scala", "highlighted: val x = 1", "```"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 40), lines.mkString("\n"))
    assertEquals(parserCalls, 1)
    assertEquals(fenceCalls, 2)
    assertEquals(lineCalls, 1)
    assertEquals(highlighterCalls, 1)

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

  test("Markdown component reuses one successful parse for an identical render"):
    var parses    = 0
    val renderer  = BasicMarkdownRenderer(new MarkdownParser:
      override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
        parses += 1
        Right(Vector(MarkdownBlock.Paragraph(markdown))))
    val component = Markdown("cached", renderer)

    val first  = component.render(20)
    val second = component.render(20)

    assert(first eq second)
    assertEquals(parses, 1)

  test("Markdown text width and padding changes invalidate cached output"):
    val renderer  = CountingRenderer()
    val component = Markdown("first", renderer)

    component.render(20)
    component.text = "second"
    assert(component.render(20).lines.exists(_.contains("second")))
    component.render(19)
    component.setPaddingX(1)
    component.render(19)
    component.setPaddingY(1)
    val padded = component.render(19)

    assertEquals(renderer.calls, 5)
    assertEquals(padded.lines.head, " ".repeat(19))

  test("Markdown renderer generation and explicit invalidation prevent stale custom output"):
    val renderer  = StatefulRenderer()
    val component = Markdown("value", renderer)

    assertEquals(component.render(20).lines, Vector("first:value"))
    renderer.prefix = "stale"
    assertEquals(component.render(20).lines, Vector("first:value"))
    renderer.generation += 1
    assertEquals(component.render(20).lines, Vector("stale:value"))
    renderer.prefix = "invalidated"
    component.invalidate()
    assertEquals(component.render(20).lines, Vector("invalidated:value"))
    assertEquals(renderer.calls, 3)

  test("Markdown caches parser fallback but never caches renderer failures"):
    var fallbackParses    = 0
    val fallback          = BasicMarkdownRenderer(new MarkdownParser:
      override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
        fallbackParses += 1
        Left("unsupported"))
    val fallbackComponent = Markdown("plain fallback", fallback)

    fallbackComponent.render(8)
    fallbackComponent.render(8)

    assertEquals(fallbackParses, 1)

    var attempts = 0
    val failing  = Markdown(
      "retry",
      new MarkdownRenderer:
        override def render(markdown: String, width: Int): Vector[String] =
          attempts += 1
          throw RuntimeException("fatal render")
    )
    intercept[RuntimeException](failing.render(10))
    intercept[RuntimeException](failing.render(10))
    assertEquals(attempts, 2)

  test("Markdown cache retains only the most recent geometry"):
    val renderer  = CountingRenderer()
    val component = Markdown("bounded", renderer)

    component.render(10)
    component.render(11)
    component.render(10)

    assertEquals(renderer.calls, 3)

  private final class CountingRenderer extends MarkdownRenderer:
    var calls                                                         = 0
    override def render(markdown: String, width: Int): Vector[String] =
      calls += 1
      Vector(markdown)

  private final class StatefulRenderer extends MarkdownRenderer:
    var generation                                                    = 0L
    var prefix                                                        = "first"
    var calls                                                         = 0
    override def cacheGeneration: Long                                = generation
    override def render(markdown: String, width: Int): Vector[String] =
      calls += 1
      Vector(s"$prefix:$markdown")
