package scalatui.terminal

/** Buffers raw terminal chunks until complete input sequences are available.
  *
  * This keeps terminal backends independent from escape-sequence fragmentation:
  * reads may split CSI keys or bracketed paste markers across arbitrary chunks.
  */
final class TerminalInputBuffer:
  import TerminalInputBuffer.*

  private var buffer = ""
  private var pasteMode = false
  private var pasteBuffer = ""

  def process(chunk: String): Vector[TerminalInput] =
    if chunk.isEmpty then Vector.empty
    buffer += chunk
    drain()

  def flush(): Vector[TerminalInput] =
    val pending = if pasteMode then PasteStart + pasteBuffer + buffer else buffer
    buffer = ""
    pasteMode = false
    pasteBuffer = ""
    if pending.isEmpty then Vector.empty else Vector(TerminalInputParser.parseOne(pending))

  def clear(): Unit =
    buffer = ""
    pasteMode = false
    pasteBuffer = ""

  private def drain(): Vector[TerminalInput] =
    val out = Vector.newBuilder[TerminalInput]
    var continue = true
    while continue do
      if pasteMode then
        pasteBuffer += buffer
        buffer = ""
        val end = pasteBuffer.indexOf(PasteEnd)
        if end >= 0 then
          val pasted = pasteBuffer.substring(0, end)
          val remaining = pasteBuffer.substring(end + PasteEnd.length)
          pasteMode = false
          pasteBuffer = ""
          out += TerminalInput.Paste(pasted)
          buffer = remaining
        else continue = false
      else
        val pasteStart = buffer.indexOf(PasteStart)
        if pasteStart >= 0 then
          val before = buffer.substring(0, pasteStart)
          emitComplete(before, out)
          buffer = buffer.substring(pasteStart + PasteStart.length)
          pasteMode = true
        else
          val before = buffer
          emitComplete(before, out)
          continue = false
    out.result()

  private def emitComplete(input: String, out: VectorBuilder[TerminalInput]): Unit =
    var rest = input
    while rest.nonEmpty do
      readComplete(rest) match
        case Some((seq, remaining)) =>
          out += TerminalInputParser.parseOne(seq)
          rest = remaining
          buffer = remaining
        case None =>
          buffer = rest
          rest = ""

  private def readComplete(value: String): Option[(String, String)] =
    if value.isEmpty then None
    else if value.charAt(0) != Esc then
      val cpLen = Character.charCount(value.codePointAt(0))
      Some(value.substring(0, cpLen) -> value.substring(cpLen))
    else completeEscapeLength(value).map(len => value.substring(0, len) -> value.substring(len))

  private def completeEscapeLength(value: String): Option[Int] =
    if value.length == 1 then None
    else value.charAt(1) match
      case '[' => completeCsiLength(value)
      case 'O' => if value.length >= 3 then Some(3) else None
      case ']' | 'P' | '_' => completeTerminatedLength(value)
      case _ => Some(math.min(2, value.length))

  private def completeCsiLength(value: String): Option[Int] =
    if value.length < 3 then None
    else
      var i = 2
      while i < value.length do
        val ch = value.charAt(i)
        if ch >= 0x40 && ch <= 0x7e then return Some(i + 1)
        i += 1
      None

  private def completeTerminatedLength(value: String): Option[Int] =
    var i = 2
    while i < value.length do
      if value.charAt(i) == '\u0007' then return Some(i + 1)
      if value.charAt(i) == Esc && i + 1 < value.length && value.charAt(i + 1) == '\\' then return Some(i + 2)
      i += 1
    None

object TerminalInputBuffer:
  private val Esc = '\u001b'
  private val PasteStart = "\u001b[200~"
  private val PasteEnd = "\u001b[201~"
  private type VectorBuilder[A] = scala.collection.mutable.Builder[A, Vector[A]]
