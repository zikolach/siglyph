package scalatui.markdown

import scalatui.ansi.Ansi
import scalatui.core.Component
import scalatui.syntax.Equality.*

/** Converts Markdown source to ANSI-aware, width-safe terminal lines. */
trait MarkdownRenderer:
  def render(markdown: String, width: Int): Vector[String]

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
final class BasicMarkdownRenderer(parser: MarkdownParser = BasicMarkdownParser)
    extends MarkdownRenderer:
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
      wrap(s"${"#".repeat(level)} ${renderInline(text)}", width) :+ ""
    case MarkdownBlock.Paragraph(text)           =>
      wrap(renderInline(text), width) :+ ""
    case MarkdownBlock.CodeBlock(language, text) =>
      val header = language.fold("```")(lang => s"```$lang")
      Vector(Ansi.truncateToWidth(header, width, "")) ++
        text.split("\n", -1).toVector.map(line => Ansi.truncateToWidth(line, width, "")) ++
        Vector(Ansi.truncateToWidth("```", width, ""), "")
    case MarkdownBlock.ListBlock(ordered, items) =>
      items.zipWithIndex.flatMap { (item, index) =>
        val marker = if ordered then s"${index + 1}. " else "- "
        wrapWithPrefix(marker, renderInline(item), width)
      } :+ ""
    case MarkdownBlock.BlockQuote(text)          =>
      text.split("\n", -1).toVector.flatMap(line =>
        wrapWithPrefix("> ", renderInline(line), width)
      ) :+ ""
    case MarkdownBlock.HorizontalRule            =>
      Vector("─".repeat(width), "")
    case MarkdownBlock.Table(rows)               =>
      rows.map(row => Ansi.truncateToWidth(row.map(renderInline).mkString(" | "), width, "")) :+ ""

  private def renderInline(value: String): String =
    renderLinks(stripEmphasis(value)).replaceAll("`([^`]+)`", "`$1`")

  private def stripEmphasis(value: String): String =
    value
      .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
      .replaceAll("__([^_]+)__", "$1")
      .replaceAll("\\*([^*]+)\\*", "$1")
      .replaceAll("_([^_]+)_", "$1")

  private def renderLinks(value: String): String =
    value.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1 ($2)")

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
