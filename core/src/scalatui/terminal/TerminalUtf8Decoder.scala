package scalatui.terminal

import scalatui.syntax.Equality.*

/** Incremental UTF-8 decoder for streamed terminal text. */
final class TerminalUtf8Decoder:
  private var carried = Array.emptyByteArray

  def process(chunk: TerminalInputChunk): String =
    val bytes = carried ++ chunk.toArray
    val split = TerminalUtf8Internals.completePrefixLength(bytes)
    carried = java.util.Arrays.copyOfRange(bytes, split, bytes.length)
    String(bytes, 0, split, java.nio.charset.StandardCharsets.UTF_8)

  def flush(): String =
    val result = if carried.isEmpty then "" else "\ufffd"
    carried = Array.emptyByteArray
    result

  def clear(): Unit = carried = Array.emptyByteArray

private object TerminalUtf8Internals:
  def completePrefixLength(bytes: Array[Byte]): Int =
    var index        = bytes.length
    var continuation = 0
    while index > 0 && continuation < 3 && (bytes(index - 1) & 0xc0) === 0x80 do
      continuation += 1
      index -= 1
    if index === bytes.length then
      if index > 0 && expected(bytes(index - 1)) > 1 then index - 1 else index
    else if index === 0 then bytes.length
    else
      val needed = expected(bytes(index - 1))
      if needed > 1 && needed > continuation + 1 then index - 1 else bytes.length

  private def expected(byte: Byte): Int =
    val value = byte & 0xff
    if value < 0x80 then 1
    else if value >= 0xc2 && value <= 0xdf then 2
    else if value >= 0xe0 && value <= 0xef then 3
    else if value >= 0xf0 && value <= 0xf4 then 4
    else 1
