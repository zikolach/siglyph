package scalatui.markdown

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*
import scalatui.terminal.TerminalCapabilities

/** Converts Markdown source to ANSI-aware, width-safe terminal lines. */
trait MarkdownRenderer:
  def render(markdown: String, width: Int): Vector[String]

/** Optional syntax-highlighting boundary for fenced Markdown code blocks. */
trait MarkdownCodeHighlighter:
  def highlight(language: Option[String], code: String): Option[Vector[String]]

/** Styling hooks for the dependency-free Markdown renderer. */
final case class MarkdownTheme(
    heading: (Int, String) => String = (_, text) => text,
    paragraph: String => String = identity,
    codeSpan: String => String = text => s"`$text`",
    codeBlockFence: String => String = identity,
    codeBlockLine: String => String = identity,
    blockQuotePrefix: String => String = identity,
    blockQuoteText: String => String = identity,
    horizontalRule: String => String = identity,
    listMarker: String => String = identity,
    tableRow: String => String = identity,
    emphasis: String => String = identity,
    strong: String => String = identity,
    link: (String, String) => String = (label, _) => label,
    linkFallback: (String, String) => String = (label, url) => s"$label ($url)"
)

/** Runtime rendering options for Markdown parity helpers. */
final case class MarkdownRenderOptions(
    theme: MarkdownTheme = MarkdownTheme(),
    capabilities: TerminalCapabilities = TerminalCapabilities(
      trueColor = false,
      hyperlinks = false,
      images = None
    ),
    highlighter: Option[MarkdownCodeHighlighter] = None
)

/** Parser boundary for dependency-free or optional dependency-backed Markdown strategies. */
trait MarkdownParser:
  def parse(markdown: String): Either[String, Vector[MarkdownBlock]]

/** Minimal block model used by the dependency-free renderer. */
sealed trait MarkdownBlock derives CanEqual

object MarkdownBlock:
  final case class Heading(level: Int, text: String)                  extends MarkdownBlock
  final case class Paragraph(text: String)                            extends MarkdownBlock
  final case class CodeBlock(language: Option[String], text: String)  extends MarkdownBlock
  final case class ListBlock(ordered: Boolean, items: Vector[String]) extends MarkdownBlock
  final case class BlockQuote(text: String)                           extends MarkdownBlock
  case object HorizontalRule                                          extends MarkdownBlock
  final case class Table(rows: Vector[Vector[String]])                extends MarkdownBlock

/** Dependency-free line-oriented Markdown parser for the documented baseline subset. */
object BasicMarkdownParser extends MarkdownParser:
  override def parse(markdown: String): Either[String, Vector[MarkdownBlock]] =
    try
      Right(parseBlocks(markdown.replace(
        "\r\n",
        "\n"
      ).replace('\r', '\n').split("\n", -1).toVector))
    catch case e: Throwable => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))

  private def parseBlocks(lines: Vector[String]): Vector[MarkdownBlock] =
    val out = Vector.newBuilder[MarkdownBlock]
    var i   = 0
    while i < lines.length do
      val line = lines(i)
      if line.trim.isEmpty then i += 1
      else if line.trim.startsWith("```") then
        val fence = line.trim.drop(3).trim
        val body  = Vector.newBuilder[String]
        i += 1
        while i < lines.length && !lines(i).trim.startsWith("```") do
          body += lines(i)
          i += 1
        if i < lines.length then i += 1
        out += MarkdownBlock.CodeBlock(
          Option.when(fence.nonEmpty)(fence),
          body.result().mkString("\n")
        )
      else if isIndentedCode(line) then
        val body = Vector.newBuilder[String]
        while i < lines.length && (isIndentedCode(lines(i)) || lines(i).trim.isEmpty) do
          body += lines(i).drop(math.min(4, lines(i).prefixLength(_ === ' ')))
          i += 1
        out += MarkdownBlock.CodeBlock(None, body.result().mkString("\n"))
      else if headingLevel(line).nonEmpty then
        val level = headingLevel(line).get
        out += MarkdownBlock.Heading(level, line.drop(level).trim)
        i += 1
      else if isHorizontalRule(line) then
        out += MarkdownBlock.HorizontalRule
        i += 1
      else if isBlockQuote(line) then
        val quoted = Vector.newBuilder[String]
        while i < lines.length && (isBlockQuote(lines(i)) || lines(i).trim.isEmpty) do
          if isBlockQuote(lines(i)) then quoted += lines(i).trim.drop(1).trim
          else quoted += ""
          i += 1
        out += MarkdownBlock.BlockQuote(quoted.result().mkString("\n"))
      else if listMarker(line).nonEmpty then
        val ordered = listMarker(line).exists(_._1)
        val items   = Vector.newBuilder[String]
        while i < lines.length && listMarker(lines(i)).exists(_._1 === ordered) do
          val marker = listMarker(lines(i)).get
          items += lines(i).trim.drop(marker._2).trim
          i += 1
        out += MarkdownBlock.ListBlock(ordered, items.result())
      else if isTableLine(line) then
        val rows = Vector.newBuilder[Vector[String]]
        while i < lines.length && isTableLine(lines(i)) do
          if !isTableSeparator(lines(i)) then rows += tableCells(lines(i))
          i += 1
        out += MarkdownBlock.Table(rows.result())
      else
        val paragraph = Vector.newBuilder[String]
        while i < lines.length && isParagraphLine(lines(i)) do
          paragraph += lines(i).trim
          i += 1
        out += MarkdownBlock.Paragraph(paragraph.result().mkString(" "))
    out.result()

  private def isParagraphLine(line: String): Boolean =
    line.trim.nonEmpty &&
      !line.trim.startsWith("```") &&
      headingLevel(line).isEmpty &&
      !isHorizontalRule(line) &&
      !isBlockQuote(line) &&
      listMarker(line).isEmpty &&
      !isIndentedCode(line) &&
      !isTableLine(line)

  private def headingLevel(line: String): Option[Int] =
    val trimmed = line.trim
    val count   = trimmed.prefixLength(_ === '#')
    Option.when(count > 0 && count <= 6 && trimmed.drop(count).startsWith(" "))(count)

  private def isHorizontalRule(line: String): Boolean =
    val trimmed = line.trim
    trimmed.length >= 3 && trimmed.forall(ch => ch === '-' || ch === '*' || ch === '_')

  private def isBlockQuote(line: String): Boolean = line.trim.startsWith(">")

  private def isIndentedCode(line: String): Boolean =
    line.startsWith("    ") || line.startsWith("\t")

  private def listMarker(line: String): Option[(Boolean, Int)] =
    val trimmed = line.trim
    if trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") then
      Some(false -> 2)
    else
      val dot = trimmed.indexOf('.')
      Option.when(
        dot > 0 && trimmed.take(dot).forall(_.isDigit) && trimmed.drop(dot).startsWith(". ")
      )(true -> (dot + 2))

  private def isTableLine(line: String): Boolean =
    line.trim.startsWith("|") && line.trim.endsWith("|")

  private def isTableSeparator(line: String): Boolean =
    tableCells(line).forall(cell =>
      cell.nonEmpty && cell.forall(ch => ch === '-' || ch === ':' || ch.isWhitespace)
    )

  private def tableCells(line: String): Vector[String] =
    line.trim.stripPrefix("|").stripSuffix("|").split("\\|", -1).toVector.map(_.trim)

/** Dependency-free renderer for a practical baseline Markdown subset. */
final class BasicMarkdownRenderer(
    parser: MarkdownParser = BasicMarkdownParser,
    options: MarkdownRenderOptions = MarkdownRenderOptions()
) extends MarkdownRenderer:
  override def render(markdown: String, width: Int): Vector[String] =
    val safeWidth = math.max(1, width)
    val rendered  = parser.parse(markdown) match
      case Right(blocks) => renderBlocks(blocks, safeWidth)
      case Left(_)       => plainFallback(markdown, safeWidth)
    if rendered.isEmpty && markdown.trim.nonEmpty then plainFallback(markdown, safeWidth)
    else rendered.map(line => Ansi.truncateToWidth(line, safeWidth, ""))

  private def renderBlocks(blocks: Vector[MarkdownBlock], width: Int): Vector[String] =
    blocks.flatMap(renderBlock(_, width)).dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse

  private def renderBlock(block: MarkdownBlock, width: Int): Vector[String] = block match
    case MarkdownBlock.Heading(level, text)      =>
      wrap(
        styled(options.theme.heading(level, s"${"#".repeat(level)} ${renderInline(text)}")),
        width
      ) :+ ""
    case MarkdownBlock.Paragraph(text)           =>
      wrap(styled(options.theme.paragraph(renderInline(text))), width) :+ ""
    case MarkdownBlock.CodeBlock(language, text) =>
      val header = language.fold("```")(lang => s"```$lang")
      val body   = options.highlighter
        .flatMap(_.highlight(language, text))
        .getOrElse(text.split("\n", -1).toVector)
        .map(line => styled(options.theme.codeBlockLine(line)))
      Vector(Ansi.truncateToWidth(styled(options.theme.codeBlockFence(header)), width, "")) ++
        body.map(line => Ansi.truncateToWidth(line, width, "")) ++
        Vector(Ansi.truncateToWidth(styled(options.theme.codeBlockFence("```")), width, ""), "")
    case MarkdownBlock.ListBlock(ordered, items) =>
      items.zipWithIndex.flatMap { (item, index) =>
        val marker = styled(options.theme.listMarker(if ordered then s"${index + 1}. " else "- "))
        wrapWithPrefix(marker, renderInline(item), width)
      } :+ ""
    case MarkdownBlock.BlockQuote(text)          =>
      text.split("\n", -1).toVector.flatMap(line =>
        wrapWithPrefix(
          styled(options.theme.blockQuotePrefix("> ")),
          styled(options.theme.blockQuoteText(renderInline(line))),
          width
        )
      ) :+ ""
    case MarkdownBlock.HorizontalRule            =>
      Vector(
        Ansi.truncateToWidth(styled(options.theme.horizontalRule("─".repeat(width))), width, ""),
        ""
      )
    case MarkdownBlock.Table(rows)               =>
      rows.map(row =>
        Ansi.truncateToWidth(
          styled(options.theme.tableRow(row.map(renderInline).mkString(" | "))),
          width,
          ""
        )
      ) :+ ""

  private def renderInline(value: String): String =
    val builder = StringBuilder()
    var i       = 0
    while i < value.length do
      if value.startsWith("`", i) then
        val end = value.indexOf('`', i + 1)
        if end > i then
          builder.append(styled(options.theme.codeSpan(value.substring(i + 1, end))))
          i = end + 1
        else
          builder.append(value.charAt(i))
          i += 1
      else if value.startsWith("[", i) then
        parseLink(value, i) match
          case Some((label, url, next)) =>
            builder.append(renderLink(renderInline(label), url))
            i = next
          case None                     =>
            builder.append(value.charAt(i))
            i += 1
      else if value.startsWith("**", i) || value.startsWith("__", i) then
        val marker = value.substring(i, i + 2)
        val end    = value.indexOf(marker, i + 2)
        if end > i then
          builder.append(styled(options.theme.strong(renderInline(value.substring(i + 2, end)))))
          i = end + 2
        else
          builder.append(value.charAt(i))
          i += 1
      else if value.startsWith("*", i) || value.startsWith("_", i) then
        val marker = value.substring(i, i + 1)
        val end    = value.indexOf(marker, i + 1)
        if end > i then
          builder.append(styled(options.theme.emphasis(renderInline(value.substring(i + 1, end)))))
          i = end + 1
        else
          builder.append(value.charAt(i))
          i += 1
      else
        builder.append(value.charAt(i))
        i += 1
    builder.result()

  private def parseLink(value: String, offset: Int): Option[(String, String, Int)] =
    val labelEnd = value.indexOf("](", offset + 1)
    if labelEnd < 0 then None
    else
      val urlEnd = value.indexOf(')', labelEnd + 2)
      Option.when(urlEnd >= 0) {
        val label = value.substring(offset + 1, labelEnd)
        val url   = value.substring(labelEnd + 2, urlEnd)
        (label, url, urlEnd + 1)
      }

  private def renderLink(label: String, url: String): String =
    val safeUrl = stripTerminalControls(url)
    if options.capabilities.hyperlinks && safeUrl === url then
      val styledLabel = styled(options.theme.link(label, url))
      s"\u001b]8;;$url\u0007$styledLabel\u001b]8;;\u0007"
    else styled(options.theme.linkFallback(label, safeUrl))

  private def stripTerminalControls(value: String): String =
    value.filterNot(ch => Character.isISOControl(ch))

  private def styled(value: String): String =
    if value.contains("\u001b[") && !value.endsWith(Ansi.Reset) then value + Ansi.Reset else value

  private def wrap(value: String, width: Int): Vector[String] =
    Ansi.wrapTextWithAnsi(value, width).map(Ansi.truncateToWidth(_, width, ""))

  private def wrapWithPrefix(prefix: String, value: String, width: Int): Vector[String] =
    val bodyWidth = math.max(1, width - Ansi.visibleWidth(prefix))
    val wrapped   = wrap(value, bodyWidth)
    wrapped.zipWithIndex.map { (line, index) =>
      val actualPrefix = if index === 0 then prefix else " ".repeat(Ansi.visibleWidth(prefix))
      Ansi.truncateToWidth(actualPrefix + line, width, "")
    }

  private def plainFallback(markdown: String, width: Int): Vector[String] =
    Ansi.wrapTextWithAnsi(markdown, width).map(Ansi.truncateToWidth(_, width, ""))

object BasicMarkdownRenderer:
  val default: BasicMarkdownRenderer = BasicMarkdownRenderer()

/** Component wrapper for rendering Markdown in normal TUI layouts. */
final class Markdown(
    initialText: String,
    renderer: MarkdownRenderer = BasicMarkdownRenderer.default,
    paddingX: Int = 0,
    paddingY: Int = 0
) extends Component:
  private var currentText = initialText

  def text: String                = currentText
  def text_=(value: String): Unit = currentText = value

  override def render(width: Int): Vector[String] =
    val safeWidth    = math.max(0, width)
    val horizontal   = " ".repeat(math.max(0, paddingX))
    val contentWidth = math.max(1, safeWidth - horizontal.length * 2)
    val content      = renderer.render(currentText, contentWidth).map { line =>
      Ansi.truncateToWidth(horizontal + line + horizontal, safeWidth, "")
    }
    val blank        = " ".repeat(safeWidth)
    Vector.fill(math.max(0, paddingY))(blank) ++ content ++ Vector.fill(math.max(
      0,
      paddingY
    ))(blank)
