package scalatui.terminal

import scalatui.syntax.Equality.*

import scala.annotation.static

import java.util.Base64

/**
 * Validated standard-base64 image payload shared by JVM and Scala Native terminal protocols.
 *
 * Validation checks base64 syntax only. It does not validate image format, dimensions, size, or
 * terminal support. Accepted source text is preserved exactly.
 */
final class Base64ImagePayload private (val value: String):
  override def equals(other: Any): Boolean = other match
    case that: Base64ImagePayload => value === that.value
    case _                        => false

  override def hashCode(): Int  = value.hashCode
  override def toString: String = value

object Base64ImagePayload:
  /**
   * Validate standard base64 with identical lexical rules on JVM and Scala Native.
   *
   * Empty and unpadded input with length remainder two or three modulo four are accepted when the
   * platform decoder accepts them. Remainder one, non-standard alphabet characters, and malformed
   * terminal padding are rejected. Decoder validation transiently allocates and discards decoded
   * bytes proportional to payload size. Accepted text is not normalized or re-encoded.
   */
  @static def from(value: String): Either[Base64ImagePayloadError, Base64ImagePayload] =
    if (value ne null) && hasValidLexicalForm(value) then
      try
        Base64.getDecoder.decode(value)
        Right(new Base64ImagePayload(value))
      catch case _: IllegalArgumentException => Left(Base64ImagePayloadError.InvalidStandardBase64)
    else Left(Base64ImagePayloadError.InvalidStandardBase64)

  /** Encode bytes as a validated, standard padded base64 payload on JVM and Scala Native. */
  @static def encode(bytes: Array[Byte]): Base64ImagePayload =
    new Base64ImagePayload(Base64.getEncoder.encodeToString(bytes))

  private def hasValidLexicalForm(value: String): Boolean =
    var index      = 0
    var dataLength = value.length
    var padding    = 0
    var valid      = true
    while index < value.length && valid do
      val char = value.charAt(index)
      if padding === 0 then
        if char === '=' then
          dataLength = index
          padding = 1
        else valid = isStandardAlphabet(char)
      else if char === '=' then padding += 1
      else valid = false
      index += 1

    val paddingValid =
      if padding === 0 then dataLength % 4 !== 1
      else
        (padding === 1 || padding === 2) &&
        value.length                   % 4 === 0 &&
        dataLength                     % 4 === (if padding === 1 then 3 else 2)
    valid && paddingValid

  private def isStandardAlphabet(char: Char): Boolean =
    (char >= 'A' && char <= 'Z') ||
      (char >= 'a' && char <= 'z') ||
      (char >= '0' && char <= '9') ||
      char === '+' || char === '/'

/**
 * Typed reason that `scalatui.terminal.Base64ImagePayload.from` rejected raw input.
 *
 * The same error contract applies on JVM and Scala Native. It reports standard-base64 syntax only,
 * not image format, dimensions, payload size, or terminal support. Validation may transiently
 * allocate decoded bytes before returning this error.
 */
enum Base64ImagePayloadError derives CanEqual:
  /** Input is not decoder-valid standard base64 under the shared JVM/Native lexical contract. */
  case InvalidStandardBase64

  /** Human-readable validation failure. */
  def message: String = this match
    case InvalidStandardBase64 => "Image payload must be valid standard base64"
