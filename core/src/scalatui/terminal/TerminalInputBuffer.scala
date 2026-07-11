package scalatui.terminal

import scalatui.syntax.Equality.*

import scala.collection.mutable.ArrayBuffer

/** Bounded byte parser for fragmented terminal input. */
final class TerminalInputBuffer:
  import TerminalInputBuffer.*

  private var mode: Mode     = Mode.Normal
  private val candidate      = ArrayBuffer.empty[Byte]
  private val pasteTail      = ArrayBuffer.empty[Byte]
  private val confirmedPaste = ArrayBuffer.empty[Byte]

  def process(chunk: TerminalInputChunk): Vector[TerminalInput] =
    val out = Vector.newBuilder[TerminalInput]
    chunk.toArray.foreach(byte => accept(byte, out))
    out.result()

  def flush(): Vector[TerminalInput] =
    val out = Vector.newBuilder[TerminalInput]
    mode match
      case Mode.Normal if candidate.length === 1 && candidate.head === Esc =>
        out += TerminalInputParser.parseTyped(candidate.toArray).get
      case Mode.Normal if candidate.nonEmpty                               =>
        emitRaw(candidate.toArray, kindOf(candidate), TerminalRawTermination.Incomplete, out)
      case Mode.Paste                                                      => ()
      case Mode.Raw(kind, exceeded, _)                                     =>
        emitRawChunk(candidate.toArray, out)
        out += TerminalInput.RawEnd(if exceeded then TerminalRawTermination.LimitExceeded(false)
        else TerminalRawTermination.Incomplete)
      case _                                                               => ()
    if mode !== Mode.Paste then clear()
    out.result()

  def clear(): Unit =
    mode = Mode.Normal
    candidate.clear()
    pasteTail.clear()
    confirmedPaste.clear()

  private def accept(byte: Byte, out: Builder): Unit = mode match
    case Mode.Paste                            => acceptPaste(byte, out)
    case Mode.Raw(kind, exceeded, previousEsc) =>
      acceptRaw(byte, kind, exceeded, previousEsc, out)
    case Mode.Normal                           => acceptNormal(byte, out)

  private def acceptNormal(byte: Byte, out: Builder): Unit =
    if candidate.length === MaxTypedSequenceBytes then
      val kind        = kindOf(candidate)
      val previousEsc = candidate.lastOption.contains(Esc)
      out += TerminalInput.RawStart(kind)
      emitRawChunk(candidate.toArray, out)
      candidate.clear()
      emitRawChunk(Array(byte), out)
      if rawTerminated(kind, byte, previousEsc) then
        out += TerminalInput.RawEnd(TerminalRawTermination.LimitExceeded(true))
        mode = Mode.Normal
      else mode = Mode.Raw(kind, exceeded = true, previousEsc = byte === Esc)
    else
      candidate += byte
      if startsWith(candidate, PasteStart) && candidate.length === PasteStart.length then
        candidate.clear()
        mode = Mode.Paste
        out += TerminalInput.PasteStart
      else if !isPrefixOf(PasteStart, candidate) then
        completeLength(candidate) match
          case Some(length) if length === candidate.length                 =>
            val bytes = candidate.toArray
            candidate.clear()
            if (bytes.head !== Esc) && !validUtf8Scalar(bytes) then
              emitRaw(bytes, TerminalRawKind.Utf8, TerminalRawTermination.Malformed, out)
            else
              TerminalInputParser.parseTyped(bytes) match
                case Some(input) => out += input
                case None        =>
                  emitRaw(bytes, kindOf(bytes), TerminalRawTermination.Complete, out)
          case None if candidate.headOption.exists(value => value !== Esc) =>
            utf8Length(candidate.head) match
              case 1                                     =>
                val bytes = candidate.toArray
                candidate.clear()
                if validUtf8Scalar(bytes) then
                  TerminalInputParser.parseTyped(bytes) match
                    case Some(input) => out += input
                    case None        =>
                      emitRaw(bytes, TerminalRawKind.Bytes, TerminalRawTermination.Malformed, out)
                else emitRaw(bytes, TerminalRawKind.Utf8, TerminalRawTermination.Malformed, out)
              case length if candidate.length === length =>
                val bytes = candidate.toArray
                candidate.clear()
                if validUtf8Scalar(bytes) then
                  TerminalInputParser.parseTyped(bytes) match
                    case Some(input) => out += input
                    case None        =>
                      emitRaw(bytes, TerminalRawKind.Utf8, TerminalRawTermination.Malformed, out)
                else emitRaw(bytes, TerminalRawKind.Utf8, TerminalRawTermination.Malformed, out)
              case _                                     => ()
          case _                                                           => ()

  private def acceptPaste(byte: Byte, out: Builder): Unit =
    pasteTail += byte
    while pasteTail.nonEmpty && !isPrefixOf(PasteEnd, pasteTail) do
      confirmedPaste += pasteTail.remove(0)
      if confirmedPaste.length === TerminalInputChunk.MaxBytes then emitConfirmedPaste(out)
    if pasteTail.length === PasteEnd.length then
      pasteTail.clear()
      emitConfirmedPaste(out)
      mode = Mode.Normal
      out += TerminalInput.PasteEnd

  private def acceptRaw(
      byte: Byte,
      kind: TerminalRawKind,
      exceeded: Boolean,
      previousEsc: Boolean,
      out: Builder
  ): Unit =
    candidate += byte
    val terminated = rawTerminated(kind, byte, previousEsc)
    if candidate.length === TerminalInputChunk.MaxBytes then
      emitRawChunk(candidate.toArray, out)
      candidate.clear()
    if terminated then
      emitRawChunk(candidate.toArray, out)
      candidate.clear()
      out += TerminalInput.RawEnd(
        if exceeded then TerminalRawTermination.LimitExceeded(true)
        else TerminalRawTermination.Complete
      )
      mode = Mode.Normal
    else mode = Mode.Raw(kind, exceeded, previousEsc = byte === Esc)

  private def emitConfirmedPaste(out: Builder): Unit =
    if confirmedPaste.nonEmpty then
      out += TerminalInput.PasteChunk(TerminalInputChunk(confirmedPaste.toArray))
      confirmedPaste.clear()

  private def emitRaw(
      bytes: Array[Byte],
      kind: TerminalRawKind,
      end: TerminalRawTermination,
      out: Builder
  ): Unit =
    out += TerminalInput.RawStart(kind)
    bytes.grouped(TerminalInputChunk.MaxBytes).foreach(part =>
      out += TerminalInput.RawChunk(TerminalInputChunk(part))
    )
    out += TerminalInput.RawEnd(end)

  private def emitRawChunk(bytes: Array[Byte], out: Builder): Unit =
    bytes.grouped(TerminalInputChunk.MaxBytes).foreach(part =>
      out += TerminalInput.RawChunk(TerminalInputChunk(part))
    )

object TerminalInputBuffer:
  val MaxTypedSequenceBytes: Int = 4096
  private val Esc: Byte          = 0x1b
  private val PasteStart         = Array[Byte](0x1b, 0x5b, 0x32, 0x30, 0x30, 0x7e)
  private val PasteEnd           = Array[Byte](0x1b, 0x5b, 0x32, 0x30, 0x31, 0x7e)
  private type Builder = scala.collection.mutable.Builder[TerminalInput, Vector[TerminalInput]]

  private enum Mode:
    case Normal, Paste
    case Raw(kind: TerminalRawKind, exceeded: Boolean, previousEsc: Boolean)

  private def startsWith(value: collection.Seq[Byte], prefix: Array[Byte]): Boolean =
    value.length >= prefix.length && prefix.indices.forall(index => value(index) === prefix(index))

  private def isPrefixOf(value: Array[Byte], prefix: collection.Seq[Byte]): Boolean =
    prefix.length <= value.length && prefix.indices.forall(index => prefix(index) === value(index))

  private def utf8Length(byte: Byte): Int =
    val value = byte & 0xff
    if value < 0x80 then 1
    else if value >= 0xc2 && value <= 0xdf then 2
    else if value >= 0xe0 && value <= 0xef then 3
    else if value >= 0xf0 && value <= 0xf4 then 4
    else 1

  private def validUtf8Scalar(bytes: Array[Byte]): Boolean =
    def continuation(index: Int): Boolean =
      index < bytes.length && (unsigned(bytes(index)) & 0xc0) === 0x80

    unsigned(bytes(0)) match
      case first if first < 0x80                   => bytes.length === 1
      case first if first >= 0xc2 && first <= 0xdf =>
        bytes.length === 2 && continuation(1)
      case 0xe0                                    =>
        bytes.length === 3 && unsigned(bytes(1)) >= 0xa0 && unsigned(bytes(1)) <= 0xbf &&
        continuation(2)
      case first if first >= 0xe1 && first <= 0xec =>
        bytes.length === 3 && continuation(1) && continuation(2)
      case 0xed                                    =>
        bytes.length === 3 && unsigned(bytes(1)) >= 0x80 && unsigned(bytes(1)) <= 0x9f &&
        continuation(2)
      case first if first >= 0xee && first <= 0xef =>
        bytes.length === 3 && continuation(1) && continuation(2)
      case 0xf0                                    =>
        bytes.length === 4 && unsigned(bytes(1)) >= 0x90 && unsigned(bytes(1)) <= 0xbf &&
        continuation(2) && continuation(3)
      case first if first >= 0xf1 && first <= 0xf3 =>
        bytes.length === 4 && continuation(1) && continuation(2) && continuation(3)
      case 0xf4                                    =>
        bytes.length === 4 && unsigned(bytes(1)) >= 0x80 && unsigned(bytes(1)) <= 0x8f &&
        continuation(2) && continuation(3)
      case _                                       => false

  private def completeLength(value: collection.Seq[Byte]): Option[Int] =
    if value.isEmpty then None
    else if value.head !== Esc then
      Option.when(value.length >= utf8Length(value.head))(utf8Length(value.head))
    else if value.length === 1 then None
    else
      value(1) match
        case 0x5b               => value.indices.drop(2).find(index =>
            unsigned(value(index)) >= 0x40 && unsigned(value(index)) <= 0x7e
          ).map(_ + 1)
        case 0x4f               => Option.when(value.length >= 3)(3)
        case 0x5d | 0x50 | 0x5f => terminatedLength(value)
        case _                  => Some(2)

  private def terminatedLength(value: collection.Seq[Byte]): Option[Int] =
    value.indices.drop(2).find { index =>
      unsigned(value(index)) === 0x07 ||
      (value(index) === Esc && index + 1 < value.length && unsigned(value(index + 1)) === 0x5c)
    }.map(index => if unsigned(value(index)) === 0x07 then index + 1 else index + 2)

  private def rawTerminated(kind: TerminalRawKind, byte: Byte, previousEsc: Boolean): Boolean =
    kind match
      case TerminalRawKind.Csi                                             => unsigned(byte) >= 0x40 && unsigned(byte) <= 0x7e
      case TerminalRawKind.Osc | TerminalRawKind.Dcs | TerminalRawKind.Apc =>
        unsigned(byte) === 0x07 || (previousEsc && unsigned(byte) === 0x5c)
      case TerminalRawKind.Ss3                                             => true
      case _                                                               => false

  private def kindOf(value: collection.Seq[Byte]): TerminalRawKind =
    if value.headOption.contains(Esc) then
      value.lengthCompare(1) match
        case 0 => TerminalRawKind.Escape
        case _ => value(1) match
            case 0x5b => TerminalRawKind.Csi
            case 0x5d => TerminalRawKind.Osc
            case 0x50 => TerminalRawKind.Dcs
            case 0x5f => TerminalRawKind.Apc
            case 0x4f => TerminalRawKind.Ss3
            case _    => TerminalRawKind.Escape
    else if value.headOption.exists(byte => (byte & 0x80) !== 0) then TerminalRawKind.Utf8
    else TerminalRawKind.Bytes

  private def unsigned(byte: Byte): Int = byte & 0xff
