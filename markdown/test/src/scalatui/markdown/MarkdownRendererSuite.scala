package scalatui.markdown

import scalatui.ansi.Ansi

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

  test("Markdown component renders with padding within component width"):
    val component = Markdown("# Hi", paddingX = 1, paddingY = 1)
    val lines     = component.render(10)

    assertEquals(lines.head, "          ")
    assert(lines.exists(_.contains("# Hi")), lines.mkString("\n"))
    assert(lines.forall(line => Ansi.visibleWidth(line) <= 10), lines.mkString("\n"))
