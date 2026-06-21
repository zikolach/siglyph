package scalatui.terminal

import scala.util.Try
import scalatui.syntax.Equality.*

/** RGB color reported by terminal color queries. Channel values use the range 0 through 255. */
final case class RgbColor(red: Int, green: Int, blue: Int) derives CanEqual

/** Terminal color scheme values reported by terminals that support color-scheme queries. */
enum TerminalColorScheme derives CanEqual:
  case Dark, Light

  /** Lowercase protocol/user-facing value: `dark` or `light`. */
  def value: String = this match
    case Dark  => "dark"
    case Light => "light"

/** Shared xterm-compatible terminal color query protocol helpers. */
object TerminalColorProtocol:
  /** OSC 11 request for the terminal's default background color. */
  val BackgroundColorQuery: String = "\u001b]11;?\u0007"

  /** DSR request for the terminal color-scheme preference. */
  val ColorSchemeQuery: String = "\u001b[?996n"

  /** Enable terminal color-scheme change notifications. */
  val EnableColorSchemeNotifications: String = "\u001b[?2031h"

  /** Disable terminal color-scheme change notifications. */
  val DisableColorSchemeNotifications: String = "\u001b[?2031l"

  private val Osc11Prefix            = "\u001b]11;"
  private val Osc11BellSuffix        = "\u0007"
  private val Osc11StSuffix          = "\u001b\\"
  private val ColorSchemeDarkReport  = "\u001b[?997;1n"
  private val ColorSchemeLightReport = "\u001b[?997;2n"

  /** Return true when `data` is an OSC 11 background color response frame. */
  def isOsc11BackgroundColorResponse(data: String): Boolean =
    osc11Payload(data).nonEmpty

  /** Parse an OSC 11 background color response into 8-bit RGB channels. */
  def parseOsc11BackgroundColor(data: String): Option[RgbColor] =
    osc11Payload(data).flatMap(parseColorPayload)

  /** Parse a terminal color-scheme report into `dark` or `light`. */
  def parseTerminalColorSchemeReport(data: String): Option[TerminalColorScheme] = data match
    case ColorSchemeDarkReport  => Some(TerminalColorScheme.Dark)
    case ColorSchemeLightReport => Some(TerminalColorScheme.Light)
    case _                      => None

  private def osc11Payload(data: String): Option[String] =
    if data.startsWith(Osc11Prefix) && data.endsWith(Osc11BellSuffix) then
      Some(data.substring(Osc11Prefix.length, data.length - Osc11BellSuffix.length).trim)
    else if data.startsWith(Osc11Prefix) && data.endsWith(Osc11StSuffix) then
      Some(data.substring(Osc11Prefix.length, data.length - Osc11StSuffix.length).trim)
    else None

  private def parseColorPayload(value: String): Option[RgbColor] =
    if value.startsWith("#") then parseHexColor(value.drop(1))
    else
      val rgb = value.replaceFirst("(?i)^rgba?:", "")
      rgb.split("/", -1).toVector match
        case red +: green +: blue +: _ =>
          for
            r <- parseOscHexChannel(red)
            g <- parseOscHexChannel(green)
            b <- parseOscHexChannel(blue)
          yield RgbColor(r, g, b)
        case _                         => None

  private def parseHexColor(hex: String): Option[RgbColor] =
    if !hex.forall(isHexDigit) then None
    else if hex.length === 6 then
      for
        r <- parseFixedHexByte(hex.substring(0, 2))
        g <- parseFixedHexByte(hex.substring(2, 4))
        b <- parseFixedHexByte(hex.substring(4, 6))
      yield RgbColor(r, g, b)
    else if hex.length === 12 then
      for
        r <- parseOscHexChannel(hex.substring(0, 4))
        g <- parseOscHexChannel(hex.substring(4, 8))
        b <- parseOscHexChannel(hex.substring(8, 12))
      yield RgbColor(r, g, b)
    else None

  private def parseFixedHexByte(value: String): Option[Int] =
    Option.when(value.length === 2 && value.forall(isHexDigit))(Integer.parseInt(value, 16))

  private def parseOscHexChannel(value: String): Option[Int] =
    if value.nonEmpty && value.forall(isHexDigit) then
      Try(BigInt(value, 16)).toOption.flatMap { parsed =>
        val max = BigInt(16).pow(value.length) - 1
        Option.when(max > 0)(math.round((parsed.toDouble / max.toDouble) * 255.0).toInt)
      }
    else None

  private def isHexDigit(char: Char): Boolean =
    (char >= '0' && char <= '9') ||
      (char >= 'a' && char <= 'f') ||
      (char >= 'A' && char <= 'F')
