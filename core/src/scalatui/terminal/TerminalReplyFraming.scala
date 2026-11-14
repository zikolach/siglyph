package scalatui.terminal

import scalatui.syntax.Equality.*

/** Recognizes only bounded, unambiguous control-string reply headers, never keyboard CSI input. */
private[terminal] object TerminalReplyFraming:
  val MaxHeaderBytes: Int = 64
  private val headers     = Vector(
    "\u001b]10;",
    "\u001b]11;",
    "\u001b]7501;?",
    "\u001bP>|"
  ).map(_.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
  private val palette     = "\u001b]4;".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
  private val graphics    = "\u001b_G".getBytes(java.nio.charset.StandardCharsets.US_ASCII)

  def recognizes(bytes: collection.Seq[Byte]): Boolean =
    if bytes.length > MaxHeaderBytes then false
    else if headers.exists(header => bytes.length === header.length && startsWith(bytes, header))
    then true
    else if bytes.lastOption.contains(';'.toByte) && startsWith(bytes, palette) then
      val count = bytes.length - palette.length - 1
      count >= 1 && count <= 3 && (palette.length until bytes.length - 1).forall(i =>
        digit(bytes(i))
      )
    else if bytes.lastOption.contains(';'.toByte) && startsWith(bytes, graphics) then
      graphicsFields(bytes)
    else false

  private def startsWith(bytes: collection.Seq[Byte], prefix: Array[Byte]): Boolean =
    bytes.length >= prefix.length && prefix.indices.forall(i => bytes(i) === prefix(i))

  private def digit(byte: Byte): Boolean = byte >= '0'.toByte && byte <= '9'.toByte

  private def graphicsFields(bytes: collection.Seq[Byte]): Boolean =
    var index = graphics.length
    val end   = bytes.length - 1
    var valid = index < end
    while valid && index < end do
      if index + 2 >= end || bytes(index) < 'a'.toByte || bytes(index) > 'z'.toByte ||
        (bytes(index + 1) !== '='.toByte)
      then valid = false
      else
        index += 2
        val start = index
        while index < end && digit(bytes(index)) do index += 1
        if index === start then valid = false
        else if index < end then
          if (bytes(index) !== ','.toByte) || index + 1 >= end then valid = false
          else index += 1
    valid && index === end
