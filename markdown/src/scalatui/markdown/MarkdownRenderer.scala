package scalatui.markdown

/** Pluggable Markdown rendering hook. Parser implementations are intentionally
  * deferred until JVM/Native dependency choices are approved.
  */
trait MarkdownRenderer:
  def render(markdown: String, width: Int): Vector[String]
