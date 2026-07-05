package scalatui.terminal

import scalatui.syntax.Equality.*

/** Parser for common terminal escape sequences into typed terminal input. */
object TerminalInputParser:
  private val PasteStart = "\u001b[200~"
  private val PasteEnd   = "\u001b[201~"

  private val SimpleKeys: Map[String, TerminalInput] = Map(
    "\r"           -> key(TerminalKey.Enter),
    "\n"           -> key(TerminalKey.Enter),
    "\u001b"       -> key(TerminalKey.Escape),
    "\t"           -> key(TerminalKey.Tab),
    "\u0001"       -> key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)),
    "\u0002"       -> key(TerminalKey.Character("b"), KeyModifiers(ctrl = true)),
    "\u0003"       -> key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)),
    "\u0004"       -> key(TerminalKey.Character("d"), KeyModifiers(ctrl = true)),
    "\u0005"       -> key(TerminalKey.Character("e"), KeyModifiers(ctrl = true)),
    "\u0006"       -> key(TerminalKey.Character("f"), KeyModifiers(ctrl = true)),
    "\u000b"       -> key(TerminalKey.Character("k"), KeyModifiers(ctrl = true)),
    "\u000c"       -> key(TerminalKey.Character("l"), KeyModifiers(ctrl = true)),
    "\u000f"       -> key(TerminalKey.Character("o"), KeyModifiers(ctrl = true)),
    "\u0014"       -> key(TerminalKey.Character("t"), KeyModifiers(ctrl = true)),
    "\u0015"       -> key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)),
    "\u0017"       -> key(TerminalKey.Character("w"), KeyModifiers(ctrl = true)),
    "\u0019"       -> key(TerminalKey.Character("y"), KeyModifiers(ctrl = true)),
    "\u001f"       -> key(TerminalKey.Character("-"), KeyModifiers(ctrl = true)),
    "\u001d"       -> key(TerminalKey.Character("]"), KeyModifiers(ctrl = true)),
    "\u001b\u001d" -> key(TerminalKey.Character("]"), KeyModifiers(ctrl = true, alt = true)),
    "\u001b\u007f" -> key(TerminalKey.Backspace, KeyModifiers(alt = true)),
    "\u001bB"      -> key(TerminalKey.Left, KeyModifiers(alt = true)),
    "\u001bF"      -> key(TerminalKey.Right, KeyModifiers(alt = true)),
    "\u001bb"      -> key(TerminalKey.Left, KeyModifiers(alt = true)),
    "\u001bf"      -> key(TerminalKey.Right, KeyModifiers(alt = true)),
    "\u007f"       -> key(TerminalKey.Backspace),
    "\b"           -> key(TerminalKey.Backspace),
    "\u001b[A"     -> key(TerminalKey.Up),
    "\u001b[B"     -> key(TerminalKey.Down),
    "\u001b[C"     -> key(TerminalKey.Right),
    "\u001b[D"     -> key(TerminalKey.Left),
    "\u001b[H"     -> key(TerminalKey.Home),
    "\u001b[F"     -> key(TerminalKey.End),
    "\u001bOH"     -> key(TerminalKey.Home),
    "\u001bOF"     -> key(TerminalKey.End),
    "\u001b[2~"    -> key(TerminalKey.Insert),
    "\u001b[3~"    -> key(TerminalKey.Delete),
    "\u001b[5~"    -> key(TerminalKey.PageUp),
    "\u001b[6~"    -> key(TerminalKey.PageDown),
    "\u001b[Z"     -> key(TerminalKey.Tab, KeyModifiers(shift = true))
  )

  private val ModifiedArrow   = "\u001b\\[1;(\\d+)(?::\\d+)?([ABCDHF])".r
  private val ModifiedFunc    = "\u001b\\[(\\d+);(\\d+)(?::\\d+)?~".r
  private val CsiU            = "\u001b\\[(\\d+)(?::(\\d*))?(?::(\\d+))?(?:;(\\d+))?(?::(\\d+))?u".r
  private val ModifyOtherKeys = "\u001b\\[27;(\\d+);(\\d+)~".r

  def parse(data: String): Vector[TerminalInput] =
    if data.isEmpty then Vector.empty
    else parsePaste(data).getOrElse(Vector(parseOne(data)))

  def parseOne(data: String): TerminalInput =
    SimpleKeys.getOrElse(
      data,
      parseModified(data).getOrElse(parsePrintable(data).getOrElse(TerminalInput.Raw(data)))
    )

  private def parsePaste(data: String): Option[Vector[TerminalInput]] =
    if data.startsWith(PasteStart) && data.endsWith(PasteEnd) then
      Some(Vector(TerminalInput.Paste(data.substring(
        PasteStart.length,
        data.length - PasteEnd.length
      ))))
    else None

  private def parseModified(data: String): Option[TerminalInput] = data match
    case ModifiedArrow(modText, code)                         =>
      Some(key(arrowKey(code), decodeModifiers(modText.toInt - 1)))
    case ModifiedFunc(numText, modText)                       =>
      functionKey(numText.toInt).map(key(_, decodeModifiers(modText.toInt - 1)))
    case ModifyOtherKeys(modText, cpText)                     =>
      Some(key(codePointKey(cpText.toInt), decodeModifiers(modText.toInt - 1)))
    case CsiU(cpText, _shifted, baseText, modText, eventText) =>
      val modifiers = Option(modText).fold(KeyModifiers.empty)(m => decodeModifiers(m.toInt - 1))
      val eventType = Option(eventText).flatMap(decodeEventType).getOrElse(KeyEventType.Press)
      Some(key(codePointKey(csiUCodePoint(cpText.toInt, Option(baseText))), modifiers, eventType))
    case _                                                    => None

  private def parsePrintable(data: String): Option[TerminalInput] =
    if data.startsWith("\u001b") && data.length > 1 then
      val rest = data.substring(1)
      if rest.codePointCount(0, rest.length) === 1 && rest.codePointAt(0) >= 32 then
        Some(key(TerminalKey.Character(rest), KeyModifiers(alt = true)))
      else None
    else if data.codePointCount(0, data.length) === 1 && data.codePointAt(0) >= 32 then
      Some(key(TerminalKey.Character(data)))
    else None

  private def key(
      key: TerminalKey,
      modifiers: KeyModifiers = KeyModifiers.empty,
      eventType: KeyEventType = KeyEventType.Press
  ): TerminalInput.KeyEvent =
    TerminalInput.KeyEvent(key, modifiers, eventType)

  private def arrowKey(code: String): TerminalKey = code match
    case "A"   => TerminalKey.Up
    case "B"   => TerminalKey.Down
    case "C"   => TerminalKey.Right
    case "D"   => TerminalKey.Left
    case "H"   => TerminalKey.Home
    case "F"   => TerminalKey.End
    case other => TerminalKey.Unknown(other)

  private def functionKey(number: Int): Option[TerminalKey] = number match
    case 2  => Some(TerminalKey.Insert)
    case 3  => Some(TerminalKey.Delete)
    case 5  => Some(TerminalKey.PageUp)
    case 6  => Some(TerminalKey.PageDown)
    case 13 => Some(TerminalKey.Enter)
    case _  => None

  private def codePointKey(codePoint: Int): TerminalKey = codePoint match
    case 9              => TerminalKey.Tab
    case 13             => TerminalKey.Enter
    case 27             => TerminalKey.Escape
    case 127            => TerminalKey.Backspace
    case cp if cp >= 32 => TerminalKey.Character(new String(Character.toChars(cp)))
    case cp             => TerminalKey.Unknown(s"U+$cp")

  private def csiUCodePoint(codePoint: Int, base: Option[String]): Int =
    base.flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0).getOrElse(codePoint)

  private def decodeEventType(value: String): Option[KeyEventType] = value match
    case "1" => Some(KeyEventType.Press)
    case "2" => Some(KeyEventType.Repeat)
    case "3" => Some(KeyEventType.Release)
    case _   => None

  private def decodeModifiers(mask: Int): KeyModifiers =
    KeyModifiers(
      shift = (mask & 1) !== 0,
      alt = (mask & 2) !== 0,
      ctrl = (mask & 4) !== 0,
      superKey = (mask & 8) !== 0
    )
