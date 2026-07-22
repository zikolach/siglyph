package scalatui.markdown.consumer

import scalatui.markdown.Markdown

final class MarkdownPublicApiSuite extends munit.FunSuite:
  test("Markdown supports constructor application outside its package"):
    val component = Markdown("# Public API")

    assertEquals(component.text, "# Public API")
