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

  test("non-hyperlink rendering keeps readable link fallback"):
    val lines = BasicMarkdownRenderer.default.render("[label](https://example.test)", 80)

    assert(lines.exists(_.contains("label (https://example.test)")), lines.mkString("\n"))

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
    val lines     = component.render(10)

    assertEquals(lines.head, "          ")
    assert(lines.exists(_.contains("# Hi")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 10), lines.mkString("\n"))
