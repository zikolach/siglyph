package scalatui.ansi

import scalatui.syntax.Equality.*
import scalatui.unicode.{GraphemeBoundaryEngine, Unicode}

/**
 * ANSI-aware terminal text helpers shared by JVM and Scala Native.
 *
 * Geometry uses Unicode 17.0.0 UAX #29 default extended grapheme clusters while terminal width
 * remains a separate policy. Complete executable SGR and OSC 8 metadata is limited to
 * [[MaxRecognizedMetadataBytes]] UTF-8 bytes, including its introducer and terminator. Unsupported,
 * private, malformed, unterminated, and oversized candidates are inert visible text: C0, DEL, and
 * C1 controls use uppercase `\\uXXXX`, while other characters remain exact. Rejected ESC-form and
 * C1-introduced string controls are consumed atomically through their defined terminator or the
 * remaining unterminated input. SGR persistence uses fixed effective fields, and OSC persistence
 * retains at most one bounded OSC 8 opener. The metadata bound does not limit application text. No
 * alternate segmenter or runtime fallback is used.
 */
object Ansi:
  final case class Escape(code: String, length: Int) derives CanEqual
  final case class Slice(text: String, width: Int) derives CanEqual

  private[scalatui] final case class SourceRange(start: Int, end: Int) derives CanEqual:
    require(start >= 0 && end >= start, "Invalid ANSI projection source range")

  private[scalatui] sealed trait ProjectedPart:
    def text: String
    def source: SourceRange

  private[scalatui] final case class ProjectedMetadata(text: String, source: SourceRange)
      extends ProjectedPart

  private[scalatui] final case class ProjectedPrintable(text: String, source: SourceRange)
      extends ProjectedPart

  private[scalatui] final case class ProjectedUnit(
      parts: Vector[ProjectedPart],
      printable: String,
      width: Int,
      source: SourceRange,
      replayBefore: String,
      replayAfter: String,
      closeAfter: String
  ):
    def raw: String = parts.iterator.map(_.text).mkString

    def printableSource: SourceRange =
      val ranges = parts.collect { case part: ProjectedPrintable => part.source }
      SourceRange(ranges.iterator.map(_.start).min, ranges.iterator.map(_.end).max)

  private[scalatui] final case class LayoutProjection(
      units: Vector[ProjectedUnit],
      metadataOnly: Vector[ProjectedMetadata],
      sourceGraphemeCount: Int,
      metadataReplayBefore: String,
      finalReplay: String,
      finalClose: String
  ):
    /** Map a source-grapheme boundary to a display-unit boundary without splitting one unit. */
    def displayBoundary(sourceBoundary: Int): Int =
      val boundary = math.max(0, math.min(sourceGraphemeCount, sourceBoundary))
      units.indexWhere(_.printableSource.end > boundary) match
        case -1    => units.length
        case index => index

    def metadataOnlyRaw: String = metadataOnly.iterator.map(_.text).mkString

    def metadataOnlyText: String =
      if metadataOnly.isEmpty then ""
      else metadataReplayBefore + metadataOnlyRaw + finalClose

  /**
   * Project one complete logical editor line from exact source graphemes to sanitized display
   * units. The joined source is scanned once. Supported metadata remains atomic; every final
   * printable grapheme retains the half-open source range that produced it.
   */
  private[scalatui] def projectLayout(sourceGraphemes: Vector[String]): LayoutProjection =
    val source        = sourceGraphemes.mkString
    val boundaries    = new Array[Int](sourceGraphemes.length + 1)
    var boundaryIndex = 0
    while boundaryIndex < sourceGraphemes.length do
      boundaries(boundaryIndex + 1) =
        boundaries(boundaryIndex) + sourceGraphemes(boundaryIndex).length
      boundaryIndex += 1

    var ownerIndex                                    = 0
    def ownerRange(start: Int, end: Int): SourceRange =
      while ownerIndex < sourceGraphemes.length && boundaries(ownerIndex + 1) <= start do
        ownerIndex += 1
      val first = ownerIndex
      var last  = first
      while last < sourceGraphemes.length && boundaries(last) < end do last += 1
      SourceRange(first, math.max(first, last))

    val engine         = GraphemeBoundaryEngine()
    val state          = AnsiState()
    val units          = Vector.newBuilder[ProjectedUnit]
    val parts          = Vector.newBuilder[ProjectedPart]
    val printable      = new StringBuilder
    var unitStart      = Int.MaxValue
    var unitEnd        = 0
    var replayBefore   = ""
    var pending        = Vector.empty[ProjectedMetadata]
    var pendingReplay  = ""
    var metadataReplay = ""

    def include(range: SourceRange): Unit =
      unitStart = math.min(unitStart, range.start)
      unitEnd = math.max(unitEnd, range.end)

    def flush(): Unit =
      if printable.nonEmpty then
        val text = printable.result()
        units += ProjectedUnit(
          parts.result(),
          text,
          Unicode.graphemeWidth(text),
          SourceRange(unitStart, unitEnd),
          replayBefore,
          state.replay,
          state.closeBoundary
        )
        printable.clear()
        parts.clear()
        unitStart = Int.MaxValue
        unitEnd = 0

    def acceptPrintable(text: String, sourceRange: SourceRange): Unit =
      var index = 0
      while index < text.length do
        val codePoint = text.codePointAt(index)
        if engine.accept(codePoint) && printable.nonEmpty then flush()
        if printable.isEmpty then
          replayBefore = if pending.nonEmpty then pendingReplay else state.replay
          pending.foreach { metadata =>
            parts += metadata
            include(metadata.source)
          }
          pending = Vector.empty
        val value     = new String(Character.toChars(codePoint))
        parts += ProjectedPrintable(value, sourceRange)
        printable.append(value)
        include(sourceRange)
        index += Character.charCount(codePoint)

    foreachScannedPart(source) {
      case ScannedMetadata(start, end, text, update) =>
        val metadata = ProjectedMetadata(text, ownerRange(start, end))
        if printable.isEmpty then
          if pending.isEmpty then pendingReplay = state.replay
          pending :+= metadata
        else
          parts += metadata
          include(metadata.source)
        state.applyUpdate(update)
      case ScannedPrintable(start, end, text)        =>
        acceptPrintable(text, ownerRange(start, end))
    }
    flush()
    if pending.nonEmpty then metadataReplay = pendingReplay
    LayoutProjection(
      units.result(),
      pending,
      sourceGraphemes.length,
      metadataReplay,
      state.replay,
      state.closeBoundary
    )

  val Reset: String = "\u001b[0m"

  /** Maximum complete SGR or OSC 8 sequence size recognized for execution, in UTF-8 bytes. */
  val MaxRecognizedMetadataBytes: Int = 4096

  /**
   * Extract a complete supported escape at `offset` when its complete UTF-8 encoding, including the
   * introducer and terminator, is at most [[MaxRecognizedMetadataBytes]]. Unsupported, private,
   * malformed, unterminated, and oversized candidates return `None` and are not partially
   * recognized.
   */
  def extractEscape(value: String, offset: Int): Option[Escape] =
    candidateAt(value, offset).filter(_.update.nonEmpty).map(candidate =>
      Escape(value.substring(offset, candidate.end), candidate.end - offset)
    )

  def strip(value: String): String =
    val builder = new StringBuilder
    scan(value)(_ => (), builder.append)
    builder.result()

  /** Preserve supported executable metadata and make every other terminal control visible. */
  private[scalatui] def sanitize(value: String): String =
    val builder = new StringBuilder
    scan(value)(builder.append, builder.append)
    builder.result()

  def visibleWidth(value: String): Int = Unicode.stringWidth(strip(value))

  def truncateToWidth(value: String, maxWidth: Int, ellipsis: String = "..."): String =
    if maxWidth <= 0 then ""
    else if visibleWidth(value) <= maxWidth then sanitize(value)
    else
      val ellipsisWidth = visibleWidth(ellipsis)
      if ellipsisWidth === 0 then withReset(sliceByColumns(value, 0, maxWidth).text)
      else if ellipsisWidth >= maxWidth then withReset(sliceByColumns(ellipsis, 0, maxWidth).text)
      else
        val prefix = sliceByColumns(value, 0, maxWidth - ellipsisWidth).text
        withReset(prefix) + sanitize(ellipsis) + Reset

  def padRight(value: String, width: Int): String =
    sanitize(value) + " ".repeat(math.max(0, width - visibleWidth(value)))

  def sliceByColumns(value: String, startColumn: Int, maxWidth: Int): Slice =
    if maxWidth <= 0 then Slice("", 0)
    else
      val safeStart    = math.max(0, startColumn)
      val builder      = new StringBuilder
      var visible      = 0
      var emittedWidth = 0
      var emitted      = false
      var close        = ""
      foreachRawGraphemeUnit(value) { unit =>
        if emittedWidth < maxWidth then
          val clusterWidth = Unicode.graphemeWidth(unit.printable)
          val clusterStart = visible
          val clusterEnd   = visible + clusterWidth
          if clusterStart >= safeStart && clusterEnd <= safeStart + maxWidth &&
            emittedWidth + clusterWidth <= maxWidth
          then
            if !emitted then builder.append(unit.replayBefore)
            builder.append(unit.raw)
            emittedWidth += clusterWidth
            emitted = true
            close = unit.closeAfter
          visible = clusterEnd
      }
      if emitted then builder.append(close)
      Slice(builder.result(), emittedWidth)

  def wrapTextWithAnsi(value: String, width: Int): Vector[String] =
    if width <= 0 then Vector("")
    else if value.isEmpty then Vector("")
    else
      val result        = Vector.newBuilder[String]
      val current       = new StringBuilder
      var currentWidth  = 0
      var close         = ""
      def flush(): Unit =
        if current.nonEmpty then
          current.append(close)
          result += current.result()
          current.clear()
          currentWidth = 0
      foreachRawGraphemeUnit(value) { unit =>
        val unitWidth = Unicode.graphemeWidth(unit.printable)
        if currentWidth > 0 && currentWidth + unitWidth > width then flush()
        if unitWidth <= width then
          if current.isEmpty then current.append(unit.replayBefore)
          current.append(unit.raw)
          currentWidth += unitWidth
          close = unit.closeAfter
      }
      flush()
      result.result()

  /** Shared visible conversion for untrusted terminal metadata. */
  private[scalatui] def visibleControlText(value: String): String =
    val builder = new StringBuilder
    var index   = 0
    while index < value.length do
      val codePoint = value.codePointAt(index)
      if codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f) then
        builder.append(f"\\u$codePoint%04X")
      else builder.appendAll(Character.toChars(codePoint))
      index += Character.charCount(codePoint)
    builder.result()

  private enum Field:
    case Bold, Faint, Italic, Underline, Blink, Inverse, Conceal, Crossed, Font, Proportional,
      Frame,
      Overline, Ideogram, Script, Foreground, Background, UnderlineColor

  private final case class Active(order: Long, code: String)

  private sealed trait Update
  private final case class SgrUpdate(operations: Vector[(Option[Field], String)]) extends Update
  private final case class Osc8Update(opener: Option[String], closer: String)     extends Update

  private final class AnsiState:
    private val fields          = Array.fill[Option[Active]](Field.values.length)(None)
    private var nextOrder       = 0L
    private var hyperlink       = Option.empty[Active]
    private var hyperlinkCloser = ""

    def applyUpdate(update: Update): Unit = update match
      case SgrUpdate(operations)            =>
        operations.foreach {
          case (None, _)           =>
            var index = 0
            while index < fields.length do
              fields(index) = None
              index += 1
            nextOrder = hyperlink.fold(0L)(_.order + 1L)
          case (Some(field), "")   => fields(field.ordinal) = None
          case (Some(field), code) =>
            fields(field.ordinal) = Some(Active(next(), code))
        }
      case Osc8Update(None, _)              =>
        hyperlink = None
        hyperlinkCloser = ""
      case Osc8Update(Some(opener), closer) =>
        hyperlink = Some(Active(next(), opener))
        hyperlinkCloser = closer

    private def next(): Long =
      val result = nextOrder
      nextOrder += 1
      result

    def retainedState: RetainedState =
      RetainedState(
        fields.count(_.nonEmpty),
        hyperlink.fold(0)(active => utf8Bytes(active.code))
      )

    def replay: String =
      val active = fields.iterator.flatten.toVector ++ hyperlink.toVector
      active.sortBy(_.order).map(_.code).mkString

    def closeBoundary: String =
      hyperlink.fold("")(_ => hyperlinkCloser) + (if fields.exists(_.nonEmpty) then Reset else "")

  private object AnsiState:
    def apply(): AnsiState = new AnsiState

  /** Test-only snapshot of the effective runtime ANSI state. */
  private[ansi] final case class RetainedState(sgrSlots: Int, oscOpenerUtf8Bytes: Int)

  /** Test support that inspects the same fixed-field state used by ANSI geometry. */
  private[ansi] def retainedStateAfter(value: String): RetainedState =
    val state = AnsiState()
    foreachScannedPart(value) {
      case ScannedMetadata(_, _, _, update) => state.applyUpdate(update)
      case _: ScannedPrintable              => ()
    }
    state.retainedState

  private final case class Candidate(end: Int, update: Option[Update])
  private final case class RawGraphemeUnit(
      raw: String,
      printable: String,
      replayBefore: String,
      closeAfter: String
  )

  private sealed trait ScannedPart
  private final case class ScannedMetadata(start: Int, end: Int, text: String, update: Update)
      extends ScannedPart
  private final case class ScannedPrintable(start: Int, end: Int, text: String) extends ScannedPart

  private def scan(value: String)(onMetadata: String => Unit, onPrintable: String => Unit): Unit =
    foreachScannedPart(value) {
      case ScannedMetadata(_, _, text, _) => onMetadata(text)
      case ScannedPrintable(_, _, text)   => onPrintable(text)
    }

  private def foreachScannedPart(value: String)(consume: ScannedPart => Unit): Unit =
    var index = 0
    while index < value.length do
      candidateAt(value, index) match
        case Some(candidate)                                   =>
          val raw = value.substring(index, candidate.end)
          candidate.update match
            case Some(update) => consume(ScannedMetadata(index, candidate.end, raw, update))
            case None         =>
              consume(ScannedPrintable(index, candidate.end, visibleControlText(raw)))
          index = candidate.end
        case None if startsUnterminatedCandidate(value, index) =>
          consume(ScannedPrintable(index, value.length, visibleControlText(value.substring(index))))
          index = value.length
        case None                                              =>
          val codePoint = value.codePointAt(index)
          val end       = index + Character.charCount(codePoint)
          consume(ScannedPrintable(
            index,
            end,
            visibleControlText(new String(Character.toChars(codePoint)))
          ))
          index = end

  private def candidateAt(value: String, offset: Int): Option[Candidate] =
    if offset < 0 || offset >= value.length then None
    else if value.charAt(offset) === '\u001b' && offset + 1 < value.length then
      value.charAt(offset + 1) match
        case '['                   => csiCandidate(value, offset)
        case ']'                   => stringCandidate(value, offset, offset + 2, isOsc = true, true)
        case 'P' | 'X' | '^' | '_' =>
          stringCandidate(value, offset, offset + 2, isOsc = false, false)
        case _                     => None
    else
      value.charAt(offset) match
        case '\u0090' | '\u0098' | '\u009e' | '\u009f' =>
          stringCandidate(value, offset, offset + 1, isOsc = false, false)
        case '\u009d'                                  =>
          stringCandidate(value, offset, offset + 1, isOsc = true, false)
        case _                                         => None

  private def startsUnterminatedCandidate(value: String, offset: Int): Boolean =
    if offset < 0 || offset >= value.length then false
    else if value.charAt(offset) === '\u001b' && offset + 1 < value.length then
      val introducer = value.charAt(offset + 1)
      introducer === '[' || introducer === ']' || introducer === 'P' || introducer === 'X' ||
      introducer === '^' || introducer === '_'
    else
      val introducer = value.charAt(offset)
      introducer === '\u0090' || introducer === '\u0098' || introducer === '\u009d' ||
      introducer === '\u009e' || introducer === '\u009f'

  private def csiCandidate(value: String, offset: Int): Option[Candidate] =
    var index = offset + 2
    while index < value.length && !(value.charAt(index) >= 0x40 && value.charAt(index) <= 0x7e) do
      index += 1
    if index >= value.length then None
    else
      val end    = index + 1
      val raw    = value.substring(offset, end)
      val update = Option.when(utf8Bytes(raw) <= MaxRecognizedMetadataBytes)(parseCsi(raw)).flatten
      Some(Candidate(end, update))

  private def stringCandidate(
      value: String,
      offset: Int,
      payloadStart: Int,
      isOsc: Boolean,
      executableOsc: Boolean
  ): Option[Candidate] =
    var index = payloadStart
    var end   = -1
    while index < value.length && end < 0 do
      if isOsc && value.charAt(index) === '\u0007' then end = index + 1
      else if value.charAt(index) === '\u009c' then end = index + 1
      else if value.charAt(index) === '\u001b' && index + 1 < value.length &&
        value.charAt(index + 1) === '\\'
      then end = index + 2
      else index += 1
    Option.when(end >= 0) {
      val raw     = value.substring(offset, end)
      val bounded = utf8Bytes(raw) <= MaxRecognizedMetadataBytes
      val update  = Option.when(bounded && executableOsc)(parseOsc(raw)).flatten
      Candidate(end, update)
    }

  private def utf8Bytes(value: String): Int =
    var bytes = 0L
    var index = 0
    while index < value.length && bytes <= MaxRecognizedMetadataBytes do
      val codePoint = value.codePointAt(index)
      val byteCount =
        if codePoint <= 0x7f then 1
        else if codePoint <= 0x7ff then 2
        else if codePoint <= 0xffff then 3
        else 4
      bytes += byteCount
      index += Character.charCount(codePoint)
    if bytes > Int.MaxValue then Int.MaxValue else bytes.toInt

  private def parseOsc(raw: String): Option[Update] =
    val terminatorLength = if raw.endsWith("\u001b\\") then 2 else 1
    val contentEnd       = raw.length - terminatorLength
    val content          = raw.substring(2, contentEnd)
    if !content.startsWith("8;") || (visibleControlText(content) !== content) then None
    else
      val separator = content.indexOf(';', 2)
      if separator < 0 then None
      else
        val uri        = content.substring(separator + 1)
        val terminator = raw.substring(contentEnd)
        val closer     = "\u001b]8;;" + terminator
        Some(Osc8Update(Option.when(uri.nonEmpty)(raw), closer))

  private def parseCsi(raw: String): Option[Update] =
    if raw.endsWith("m") then parseSgr(raw) else None

  private def parseSgr(raw: String): Option[Update] =
    if !raw.endsWith("m") then None
    else
      val body = raw.substring(2, raw.length - 1)
      if !body.forall(ch => ch.isDigit || ch === ';' || ch === ':') then None
      else parseSgrBody(body).map(SgrUpdate.apply)

  private def parseSgrBody(body: String): Option[Vector[(Option[Field], String)]] =
    if body.isEmpty then Some(Vector(None -> ""))
    else
      val parts  = body.split(";", -1).toVector
      val result = Vector.newBuilder[(Option[Field], String)]
      var index  = 0
      var valid  = true
      while index < parts.length && valid do
        val part = parts(index)
        if part.contains(':') then
          colonOperation(part) match
            case Some(operation) => result += operation
            case None            => valid = false
          index += 1
        else
          val number = if part.isEmpty then Some(0) else decimal(part)
          number match
            case Some(code) if code === 38 || code === 48 || code === 58 =>
              semicolonColor(parts, index, code) match
                case Some((operation, consumed)) =>
                  result += operation
                  index += consumed
                case None                        => valid = false
            case Some(code)                                              =>
              simpleOperation(code) match
                case Some(operations) => result ++= operations; index += 1
                case None             => valid = false
            case None                                                    => valid = false
      Option.when(valid)(result.result())

  private def decimal(value: String): Option[Int] =
    Option.when(value.nonEmpty && value.forall(_.isDigit))(value.toIntOption).flatten

  private def simpleOperation(code: Int): Option[Vector[(Option[Field], String)]] =
    def set(field: Field, normalized: Int = code) = Vector(Some(field) -> s"\u001b[${normalized}m")
    def clear(fields: Field*)                     = fields.toVector.map(Some(_) -> "")
    code match
      case 0                                                   => Some(Vector(None -> ""))
      case 1                                                   => Some(set(Field.Bold))
      case 2                                                   => Some(set(Field.Faint))
      case 3 | 20                                              => Some(set(Field.Italic))
      case 4                                                   => Some(set(Field.Underline))
      case 5 | 6                                               => Some(set(Field.Blink))
      case 7                                                   => Some(set(Field.Inverse))
      case 8                                                   => Some(set(Field.Conceal))
      case 9                                                   => Some(set(Field.Crossed))
      case 10                                                  => Some(clear(Field.Font))
      case n if n >= 11 && n <= 19                             => Some(set(Field.Font))
      case 21                                                  => Some(set(Field.Underline, 21))
      case 22                                                  => Some(clear(Field.Bold, Field.Faint))
      case 23                                                  => Some(clear(Field.Italic))
      case 24                                                  => Some(clear(Field.Underline))
      case 25                                                  => Some(clear(Field.Blink))
      case 26                                                  => Some(set(Field.Proportional))
      case 27                                                  => Some(clear(Field.Inverse))
      case 28                                                  => Some(clear(Field.Conceal))
      case 29                                                  => Some(clear(Field.Crossed))
      case n if (n >= 30 && n <= 37) || (n >= 90 && n <= 97)   => Some(set(Field.Foreground))
      case 39                                                  => Some(clear(Field.Foreground))
      case n if (n >= 40 && n <= 47) || (n >= 100 && n <= 107) => Some(set(Field.Background))
      case 49                                                  => Some(clear(Field.Background))
      case 50                                                  => Some(clear(Field.Proportional))
      case 51 | 52                                             => Some(set(Field.Frame))
      case 53                                                  => Some(set(Field.Overline))
      case 54                                                  => Some(clear(Field.Frame))
      case 55                                                  => Some(clear(Field.Overline))
      case 59                                                  => Some(clear(Field.UnderlineColor))
      case n if n >= 60 && n <= 64                             => Some(set(Field.Ideogram))
      case 65                                                  => Some(clear(Field.Ideogram))
      case 73 | 74                                             => Some(set(Field.Script))
      case 75                                                  => Some(clear(Field.Script))
      case _                                                   => None

  private def colorField(code: Int): Field =
    if code === 38 then Field.Foreground
    else if code === 48 then Field.Background
    else Field.UnderlineColor

  private def semicolonColor(
      parts: Vector[String],
      index: Int,
      code: Int
  ): Option[((Option[Field], String), Int)] =
    parts.lift(index + 1).flatMap(decimal).flatMap {
      case 5 =>
        parts.lift(index + 2).flatMap(decimal).filter(inByte).map { value =>
          (Some(colorField(code)) -> s"\u001b[$code;5;${value}m", 3)
        }
      case 2 =>
        val rgb = (1 to 3).flatMap(offset => parts.lift(index + 1 + offset).flatMap(decimal))
        Option.when(rgb.size === 3 && rgb.forall(inByte)) {
          (Some(colorField(code)) -> s"\u001b[$code;2;${rgb.mkString(";")}m", 5)
        }
      case _ => None
    }

  private def colonOperation(part: String): Option[(Option[Field], String)] =
    val values = part.split(":", -1).toVector
    values.headOption.flatMap(decimal).flatMap {
      case 4 if values.size === 2                            =>
        values(1).toIntOption.filter(value => value >= 0 && value <= 5).map { style =>
          if style === 0 then Some(Field.Underline) -> ""
          else Some(Field.Underline)                -> s"\u001b[4:${style}m"
        }
      case code if code === 38 || code === 48 || code === 58 =>
        values.lift(1).flatMap(decimal).flatMap {
          case 5 if values.size === 3                      =>
            decimal(values(2)).filter(inByte).map(value =>
              Some(colorField(code)) -> s"\u001b[$code:5:${value}m"
            )
          case 2 if values.size === 6 && values(2).isEmpty =>
            val rgb = values.drop(3).flatMap(decimal)
            Option.when(rgb.size === 3 && rgb.forall(inByte))(
              Some(colorField(code)) -> s"\u001b[$code:2::${rgb.mkString(":")}m"
            )
          case _                                           => None
        }
      case _                                                 => None
    }

  private def inByte(value: Int): Boolean = value >= 0 && value <= 255

  private def foreachRawGraphemeUnit(value: String)(consume: RawGraphemeUnit => Unit): Unit =
    val engine             = GraphemeBoundaryEngine()
    val state              = AnsiState()
    val raw                = new StringBuilder
    val printable          = new StringBuilder
    var pendingStart       = -1
    var pendingEnd         = -1
    var replayBefore       = ""
    var pendingReplay      = ""
    var closeBeforePending = ""

    def flush(close: String): Unit =
      if printable.nonEmpty then
        consume(RawGraphemeUnit(raw.result(), printable.result(), replayBefore, close))
        raw.clear()
        printable.clear()

    def acceptPrintable(text: String): Unit =
      var index = 0
      while index < text.length do
        val codePoint = text.codePointAt(index)
        if engine.accept(codePoint) && printable.nonEmpty then
          val close = if pendingStart >= 0 then closeBeforePending else state.closeBoundary
          flush(close)
        if printable.isEmpty then
          replayBefore = if pendingStart >= 0 then pendingReplay else state.replay
          if pendingStart >= 0 then raw.append(value.substring(pendingStart, pendingEnd))
          pendingStart = -1
          pendingEnd = -1
        raw.appendAll(Character.toChars(codePoint))
        printable.appendAll(Character.toChars(codePoint))
        index += Character.charCount(codePoint)

    foreachScannedPart(value) {
      case ScannedMetadata(start, end, source, update) =>
        if printable.isEmpty then
          if pendingStart < 0 then
            pendingReplay = state.replay
            closeBeforePending = state.closeBoundary
            pendingStart = start
          pendingEnd = end
        else raw.append(source)
        state.applyUpdate(update)
      case ScannedPrintable(_, _, text)                => acceptPrintable(text)
    }
    if printable.nonEmpty then
      if pendingStart >= 0 then raw.append(value.substring(pendingStart, pendingEnd))
      flush(state.closeBoundary)

  private def withReset(value: String): String =
    if value.endsWith(Reset) then value else value + Reset
