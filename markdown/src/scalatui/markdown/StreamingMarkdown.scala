package scalatui.markdown

import scalatui.core.{
  Component, ComponentRender, ContextualComponent, TUIContext, ViewportRangeRenderer
}
import scalatui.syntax.Equality.*

/** Deterministic cumulative streaming work, measured in UTF-16 units rather than bytes or time. */
final case class StreamingMarkdownStats(
    parseCalls: Long,
    parsedUnits: Long,
    scannedUnits: Long,
    reusedBlocks: Long,
    fallbackParses: Long
)

/**
 * Opt-in, source-bounded Markdown for JVM and Native live output.
 *
 * Mutations belong on the owning UI/application scope, like other application updates. Append
 * rejects an entire chunk after completion or when the UTF-16 source limit would be exceeded.
 * Finish restores ordinary complete-document semantics; live baseline tails may provisionally close
 * simple inline delimiters and hide unfinished link destinations. Custom parsers always see
 * original whole source.
 *
 * Only proven baseline prefixes are reused. Ambiguous lists, quotes and indented code remain in a
 * reparsed suffix. String copying, normalization and document rendering are not incremental. This
 * is not a CommonMark parser or an unbounded transcript store. Render invalidation preserves
 * checkpoints. Set `provisionalInline = false` for exact baseline output at every prefix.
 * Projection affects only active paragraph/heading text; structural block contents remain literal.
 */
final class StreamingMarkdown(
    maxSourceUnits: Int = 1024 * 1024,
    parser: MarkdownParser = BasicMarkdownParser,
    options: MarkdownRenderOptions = MarkdownRenderOptions(),
    provisionalInline: Boolean = true
) extends Component,
      ContextualComponent,
      ViewportRangeRenderer:
  require(maxSourceUnits >= 0, "maxSourceUnits must be non-negative")
  private val streamingParser = new StreamingMarkdownParser(parser, provisionalInline)
  private val renderer        = new BasicMarkdownRenderer(streamingParser, options)
  private val document        = new Markdown("", renderer)
  private var completed       = false

  /** Original admitted source, including incomplete syntax. */
  def text: String = document.text

  /** Whether finish has sealed this response. */
  def isFinished: Boolean = completed

  /**
   * Cumulative work since construction or reset; rendering the same source adds no parsing work.
   */
  def stats: StreamingMarkdownStats = streamingParser.stats

  /** Admit a whole chunk; false leaves both source and parsing state unchanged. */
  def append(chunk: String): Boolean =
    val source = text
    if completed || chunk.length > maxSourceUnits - source.length then false
    else
      if chunk.nonEmpty then document.text = source + chunk
      true

  /** Seal source and discard live-only projection, without adding invented source delimiters. */
  def finish(): Unit =
    if !completed then
      completed = true
      streamingParser.finish()
      document.invalidate()

  /** Start an empty response, clearing source, checkpoints, counters and render caches. */
  def reset(): Unit =
    completed = false
    streamingParser.reset()
    document.text = ""
    document.invalidate()

  override def tuiContext_=(value: Option[TUIContext]): Unit = document.tuiContext_=(value)
  override def invalidate(): Unit                            = document.invalidate()
  override def render(width: Int): ComponentRender           = document.render(width)

  /** Reuse the current width-constrained document layout without validating offscreen rows. */
  override def contentExtent(width: Int): Int = document.render(width).lines.length

  /** Return only visible rows; source/width changes still render one complete document. */
  override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
    if width <= 0 || rowCount <= 0 then ComponentRender.empty
    else
      val start = math.max(0, startRow)
      ComponentRender.text(document.render(width).lines.drop(start).take(rowCount))

/**
 * Parser state is separate from render caches so ScrollView mapping invalidation preserves reuse.
 */
private final class StreamingMarkdownParser(parser: MarkdownParser, provisionalInline: Boolean)
    extends MarkdownParser:
  private var prefix       = ""
  private var blocks       = Vector.empty[MarkdownBlock]
  private var lastSource   = Option.empty[String]
  private var lastResult   = Either.cond(true, Vector.empty[MarkdownBlock], "")
  private var finished     = false
  private var parseCalls   = 0L
  private var parsedUnits  = 0L
  private var scannedUnits = 0L
  private var reusedBlocks = 0L
  private var fallbacks    = 0L

  def stats: StreamingMarkdownStats = synchronized {
    StreamingMarkdownStats(parseCalls, parsedUnits, scannedUnits, reusedBlocks, fallbacks)
  }

  def finish(): Unit = synchronized {
    finished = true
    lastSource = None
  }

  def reset(): Unit = synchronized {
    prefix = ""
    blocks = Vector.empty
    lastSource = None
    finished = false
    parseCalls = 0L
    parsedUnits = 0L
    scannedUnits = 0L
    reusedBlocks = 0L
    fallbacks = 0L
    lastResult = Right(Vector.empty)
  }

  override def parse(source: String): Either[String, Vector[MarkdownBlock]] =
    if !(parser eq BasicMarkdownParser) then
      synchronized {
        fallbacks += 1
        parseCalls += 1
        parsedUnits += source.length
      }
      // Application parser callbacks must never run under the checkpoint monitor.
      parser.parse(source)
    else parseBaseline(source)

  private def parseBaseline(source: String): Either[String, Vector[MarkdownBlock]] = synchronized {
    if lastSource.contains(source) then lastResult
    else
      val result = if finished then
        fallbacks += 1
        countedParse(source)
      else
        val normalized   = source.replace("\r\n", "\n").replace('\r', '\n')
        // A trailing CR may become CRLF on the next append. Never commit it yet.
        val stableLength = normalized.length - (if source.endsWith("\r") then 1 else 0)
        if !normalized.startsWith(prefix) then
          prefix = ""
          blocks = Vector.empty
        val tail         = normalized.substring(prefix.length)
        val eligible     = math.max(0, stableLength - prefix.length)
        val boundary     = safeBoundary(tail.take(eligible))
        reusedBlocks += blocks.length
        val committed    =
          if boundary > 0 then countedParse(tail.take(boundary)) else Right(Vector.empty)
        committed.flatMap { added =>
          blocks = blocks ++ added
          prefix += tail.take(boundary)
          val active = tail.drop(boundary)
          countedParse(active).map { tailBlocks =>
            blocks ++ (if provisionalInline then tailBlocks.map(projectBlock) else tailBlocks)
          }
        }
      lastSource = Some(source)
      lastResult = result
      result
  }

  private def countedParse(source: String): Either[String, Vector[MarkdownBlock]] =
    parseCalls += 1
    parsedUnits += source.length
    parser.parse(source)

  // List, quote and indented-code blocks can span blank lines. Stop committing at the first such
  // line rather than guessing whether future input extends them. Fence recognition matches baseline.
  private def safeBoundary(source: String): Int =
    var offset    = 0
    var boundary  = 0
    var fenced    = false
    var ambiguous = false
    while offset < source.length && !ambiguous do
      val end = source.indexOf('\n', offset)
      if end < 0 then
        scannedUnits += source.length - offset
        offset = source.length
      else
        val line    = source.substring(offset, end)
        scannedUnits += end + 1 - offset
        val trimmed = line.trim
        if trimmed.startsWith("```") then fenced = !fenced
        else if !fenced then
          val dot     = trimmed.indexOf('.')
          val ordered = dot > 0 && trimmed.take(dot).forall(_.isDigit) &&
            trimmed.drop(dot).startsWith(". ")
          ambiguous = line.startsWith("    ") || line.startsWith("\t") ||
            trimmed.startsWith(">") || trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") || trimmed.startsWith("+ ") || ordered
          if trimmed.isEmpty && !ambiguous then boundary = end + 1
        offset = end + 1
    boundary

  private def projectBlock(block: MarkdownBlock): MarkdownBlock = block match
    case MarkdownBlock.Paragraph(text)      => MarkdownBlock.Paragraph(projectInline(text))
    case MarkdownBlock.Heading(level, text) => MarkdownBlock.Heading(level, projectInline(text))
    // Structural blocks keep literal baseline semantics, including tasks, tables and fence bodies.
    case other                              => other

  // Intentionally the baseline's simple, non-nested delimiter grammar, not CommonMark completion.
  private def projectInline(value: String): String =
    val out = StringBuilder()
    var i   = 0
    while i < value.length do
      if value.charAt(i) === '[' then
        val middle = value.indexOf("](", i + 1)
        val end    = if middle >= 0 then value.indexOf(')', middle + 2) else -1
        if end >= 0 then
          out.append(value.substring(i, end + 1))
          i = end + 1
        else if middle >= 0 then
          out.append(value.substring(i + 1, middle))
          i = value.length
        else
          // Brackets alone are not evidence of a link. Do not eat following ordinary text.
          out.append(value.charAt(i))
          i += 1
      else if value.charAt(i) === '*' || value.charAt(i) === '_' || value.charAt(i) === '`' then
        val marker = if value.startsWith("**", i) || value.startsWith("__", i) then
          value.substring(i, i + 2)
        else value.substring(i, i + 1)
        val end    = value.indexOf(marker, i + marker.length)
        if end >= 0 then
          out.append(value.substring(i, end + marker.length))
          i = end + marker.length
        else
          out.append(value.substring(i)).append(marker)
          i = value.length
      else
        out.append(value.charAt(i))
        i += 1
    out.result()
