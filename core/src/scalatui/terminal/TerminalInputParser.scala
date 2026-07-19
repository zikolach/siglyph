package scalatui.terminal

import scalatui.syntax.Equality.*

/** Parser for common terminal escape sequences into typed terminal input. */
object TerminalInputParser:
  private final case class FixedKey(
      sequence: String,
      terminalKey: TerminalKey,
      modifiers: KeyModifiers = KeyModifiers.empty
  )

  private val Ctrl                = KeyModifiers(ctrl = true)
  private val Alt                 = KeyModifiers(alt = true)
  private val Shift               = KeyModifiers(shift = true)
  private val CtrlAlt             = KeyModifiers(ctrl = true, alt = true)
  private val fixedControlKeys    = Vector(
    FixedKey("\u0000", TerminalKey.Character(" "), Ctrl),
    FixedKey("\u001c", TerminalKey.Character("\\"), Ctrl),
    FixedKey("\u001d", TerminalKey.Character("]"), Ctrl),
    FixedKey("\u001e", TerminalKey.Character("^"), Ctrl),
    FixedKey("\u001f", TerminalKey.Character("-"), Ctrl)
  ) ++ (1 to 26).collect {
    case code if (code !== 8) && (code !== 9) && (code !== 10) && (code !== 13) =>
      FixedKey(code.toChar.toString, TerminalKey.Character(('a' + code - 1).toChar.toString), Ctrl)
  }
  private val fixedAltControlKeys = fixedControlKeys.map(entry =>
    entry.copy(sequence = "\u001b" + entry.sequence, modifiers = entry.modifiers.copy(alt = true))
  )
  private val fixedKeys           = Vector(
    FixedKey("\r", TerminalKey.Enter),
    FixedKey("\n", TerminalKey.Enter),
    FixedKey("\u001b", TerminalKey.Escape),
    FixedKey("\t", TerminalKey.Tab),
    FixedKey("\u007f", TerminalKey.Backspace),
    FixedKey("\b", TerminalKey.Backspace),
    FixedKey("\u001b\u007f", TerminalKey.Backspace, Alt),
    FixedKey("\u001b\b", TerminalKey.Backspace, Alt),
    FixedKey("\u001b\u001b", TerminalKey.Character("["), CtrlAlt),
    FixedKey("\u001b[A", TerminalKey.Up),
    FixedKey("\u001b[B", TerminalKey.Down),
    FixedKey("\u001b[C", TerminalKey.Right),
    FixedKey("\u001b[D", TerminalKey.Left),
    FixedKey("\u001b[H", TerminalKey.Home),
    FixedKey("\u001b[F", TerminalKey.End),
    FixedKey("\u001bOA", TerminalKey.Up),
    FixedKey("\u001bOB", TerminalKey.Down),
    FixedKey("\u001bOC", TerminalKey.Right),
    FixedKey("\u001bOD", TerminalKey.Left),
    FixedKey("\u001bOH", TerminalKey.Home),
    FixedKey("\u001bOF", TerminalKey.End),
    FixedKey("\u001b[E", TerminalKey.Clear),
    FixedKey("\u001bOE", TerminalKey.Clear),
    FixedKey("\u001b[1~", TerminalKey.Home),
    FixedKey("\u001b[2~", TerminalKey.Insert),
    FixedKey("\u001b[3~", TerminalKey.Delete),
    FixedKey("\u001b[4~", TerminalKey.End),
    FixedKey("\u001b[5~", TerminalKey.PageUp),
    FixedKey("\u001b[6~", TerminalKey.PageDown),
    FixedKey("\u001b[7~", TerminalKey.Home),
    FixedKey("\u001b[8~", TerminalKey.End),
    FixedKey("\u001b[[5~", TerminalKey.PageUp),
    FixedKey("\u001b[[6~", TerminalKey.PageDown),
    FixedKey("\u001b[Z", TerminalKey.Tab, Shift),
    FixedKey("\u001b[a", TerminalKey.Up, Shift),
    FixedKey("\u001b[b", TerminalKey.Down, Shift),
    FixedKey("\u001b[c", TerminalKey.Right, Shift),
    FixedKey("\u001b[d", TerminalKey.Left, Shift),
    FixedKey("\u001b[e", TerminalKey.Clear, Shift),
    FixedKey("\u001bOa", TerminalKey.Up, Ctrl),
    FixedKey("\u001bOb", TerminalKey.Down, Ctrl),
    FixedKey("\u001bOc", TerminalKey.Right, Ctrl),
    FixedKey("\u001bOd", TerminalKey.Left, Ctrl),
    FixedKey("\u001bOe", TerminalKey.Clear, Ctrl),
    FixedKey("\u001b[2$", TerminalKey.Insert, Shift),
    FixedKey("\u001b[3$", TerminalKey.Delete, Shift),
    FixedKey("\u001b[5$", TerminalKey.PageUp, Shift),
    FixedKey("\u001b[6$", TerminalKey.PageDown, Shift),
    FixedKey("\u001b[7$", TerminalKey.Home, Shift),
    FixedKey("\u001b[8$", TerminalKey.End, Shift),
    FixedKey("\u001b[2^", TerminalKey.Insert, Ctrl),
    FixedKey("\u001b[3^", TerminalKey.Delete, Ctrl),
    FixedKey("\u001b[5^", TerminalKey.PageUp, Ctrl),
    FixedKey("\u001b[6^", TerminalKey.PageDown, Ctrl),
    FixedKey("\u001b[7^", TerminalKey.Home, Ctrl),
    FixedKey("\u001b[8^", TerminalKey.End, Ctrl),
    FixedKey("\u001bB", TerminalKey.Left, Alt),
    FixedKey("\u001bF", TerminalKey.Right, Alt),
    FixedKey("\u001bb", TerminalKey.Left, Alt),
    FixedKey("\u001bf", TerminalKey.Right, Alt),
    FixedKey("\u001bp", TerminalKey.Up, Alt),
    FixedKey("\u001bn", TerminalKey.Down, Alt),
    FixedKey("\u001bOP", TerminalKey.Function(1)),
    FixedKey("\u001bOQ", TerminalKey.Function(2)),
    FixedKey("\u001bOR", TerminalKey.Function(3)),
    FixedKey("\u001bOS", TerminalKey.Function(4)),
    FixedKey("\u001b[11~", TerminalKey.Function(1)),
    FixedKey("\u001b[12~", TerminalKey.Function(2)),
    FixedKey("\u001b[13~", TerminalKey.Function(3)),
    FixedKey("\u001b[14~", TerminalKey.Function(4)),
    FixedKey("\u001b[15~", TerminalKey.Function(5)),
    FixedKey("\u001b[17~", TerminalKey.Function(6)),
    FixedKey("\u001b[18~", TerminalKey.Function(7)),
    FixedKey("\u001b[19~", TerminalKey.Function(8)),
    FixedKey("\u001b[20~", TerminalKey.Function(9)),
    FixedKey("\u001b[21~", TerminalKey.Function(10)),
    FixedKey("\u001b[23~", TerminalKey.Function(11)),
    FixedKey("\u001b[24~", TerminalKey.Function(12)),
    FixedKey("\u001b[[A", TerminalKey.Function(1)),
    FixedKey("\u001b[[B", TerminalKey.Function(2)),
    FixedKey("\u001b[[C", TerminalKey.Function(3)),
    FixedKey("\u001b[[D", TerminalKey.Function(4)),
    FixedKey("\u001b[[E", TerminalKey.Function(5))
  ) ++ fixedControlKeys ++ fixedAltControlKeys
  private val ModifiedCsi         = "\u001b\\[1;(\\d+)(?::\\d+)?([ABCDHFPQRS])".r
  private val ModifiedFunc        = "\u001b\\[(\\d+);(\\d+)(?::\\d+)?~".r
  private val CsiU                = "\u001b\\[(\\d+)(?::(\\d*))?(?::(\\d+))?(?:;(\\d+))?(?::(\\d+))?u".r
  private val ModifyOtherKeys     = "\u001b\\[27;(\\d+);(\\d+)~".r
  private val SgrMouse            = "\u001b\\[<(\\d+);(-?\\d+);(-?\\d+)([Mm])".r

  private[terminal] def parseTyped(bytes: Array[Byte]): Option[TerminalInput] =
    val data = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
    fixedKeys.find(_.sequence === data).map(entry =>
      key(entry.terminalKey, entry.modifiers)
    ).orElse(
      parseMouse(data)
    ).orElse(parseModified(data)).orElse(
      parsePrintable(data)
    )

  private def parseModified(data: String): Option[TerminalInput] =
    scala.util.Try(parseModifiedUnsafe(data)).toOption.flatten

  private def parseMouse(data: String): Option[TerminalInput] = data match
    case SgrMouse(codeText, colText, rowText, suffix) =>
      val parsed =
        for
          code <- parseInt(codeText)
          col  <- parseInt(colText)
          row  <- parseInt(rowText)
        yield (code, col, row)
      parsed match
        case Some((code, col, row)) if row > 0 && col > 0 =>
          val modifiers = KeyModifiers(
            shift = (code & 4) !== 0,
            alt = (code & 8) !== 0,
            ctrl = (code & 16) !== 0,
            superKey = false
          )
          val action    =
            if (code & 64) !== 0 then MouseAction.Wheel(wheelDirection(code & 3))
            else if suffix === "m" then MouseAction.Release(mouseButton(code))
            else MouseAction.Press(mouseButton(code))
          Some(TerminalInput.Mouse(action, row - 1, col - 1, modifiers))
        case _                                            => None
    case _                                            => None

  private def parseModifiedUnsafe(data: String): Option[TerminalInput] = data match
    case ModifiedCsi(_, "R")                                  =>
      // CSI row ; col R is also the terminal cursor-position report. Keep the ambiguous bytes raw
      // so a TUI with an outstanding DSR query can correlate them; F3 remains available through
      // the unambiguous SS3, legacy-tilde, and bare CSI encodings.
      None
    case ModifiedCsi(modText, code)                           =>
      modifiedCsiKey(code).map(key(_, decodeModifiers(modText.toInt - 1)))
    case ModifiedFunc(numText, modText)                       =>
      functionKey(numText.toInt).map(key(_, decodeModifiers(modText.toInt - 1)))
    case ModifyOtherKeys(modText, cpText)                     =>
      Some(key(codePointKey(cpText.toInt), decodeModifiers(modText.toInt - 1)))
    case CsiU(cpText, _shifted, baseText, modText, eventText) =>
      val modifiers  = Option(modText).fold(KeyModifiers.empty)(m => decodeModifiers(m.toInt - 1))
      val eventType  = Option(eventText).flatMap(decodeEventType).getOrElse(KeyEventType.Press)
      val codePoint  = csiUCodePoint(cpText.toInt, Option(baseText))
      val normalized = normalizeShiftedLetter(codePoint, modifiers)
      Some(key(codePointKey(normalized), modifiers, eventType))
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

  private def modifiedCsiKey(code: String): Option[TerminalKey] = code match
    case "P"                               => Some(TerminalKey.Function(1))
    case "Q"                               => Some(TerminalKey.Function(2))
    case "R"                               => Some(TerminalKey.Function(3))
    case "S"                               => Some(TerminalKey.Function(4))
    case value if "ABCDHF".contains(value) => Some(arrowKey(value))
    case _                                 => None

  private def functionKey(number: Int): Option[TerminalKey] = number match
    case 2  => Some(TerminalKey.Insert)
    case 3  => Some(TerminalKey.Delete)
    case 5  => Some(TerminalKey.PageUp)
    case 6  => Some(TerminalKey.PageDown)
    case 13 => Some(TerminalKey.Enter)
    case 11 => Some(TerminalKey.Function(1))
    case 12 => Some(TerminalKey.Function(2))
    case 14 => Some(TerminalKey.Function(4))
    case 15 => Some(TerminalKey.Function(5))
    case 17 => Some(TerminalKey.Function(6))
    case 18 => Some(TerminalKey.Function(7))
    case 19 => Some(TerminalKey.Function(8))
    case 20 => Some(TerminalKey.Function(9))
    case 21 => Some(TerminalKey.Function(10))
    case 23 => Some(TerminalKey.Function(11))
    case 24 => Some(TerminalKey.Function(12))
    case _  => None

  private def parseInt(value: String): Option[Int] =
    scala.util.Try(value.toInt).toOption

  private def mouseButton(code: Int): MouseButton =
    val identity = code & ~(4 | 8 | 16 | 32 | 64)
    identity match
      case 0 => MouseButton.Left
      case 1 => MouseButton.Middle
      case 2 => MouseButton.Right
      case _ => MouseButton.Other(identity)

  private def wheelDirection(code: Int): MouseWheelDirection = code match
    case 0 => MouseWheelDirection.Up
    case 1 => MouseWheelDirection.Down
    case 2 => MouseWheelDirection.Left
    case _ => MouseWheelDirection.Right

  private def codePointKey(codePoint: Int): TerminalKey = codePoint match
    case 9              => TerminalKey.Tab
    case 13             => TerminalKey.Enter
    case 27             => TerminalKey.Escape
    case 127            => TerminalKey.Backspace
    case 57399          => TerminalKey.Character("0")
    case 57400          => TerminalKey.Character("1")
    case 57401          => TerminalKey.Character("2")
    case 57402          => TerminalKey.Character("3")
    case 57403          => TerminalKey.Character("4")
    case 57404          => TerminalKey.Character("5")
    case 57405          => TerminalKey.Character("6")
    case 57406          => TerminalKey.Character("7")
    case 57407          => TerminalKey.Character("8")
    case 57408          => TerminalKey.Character("9")
    case 57409          => TerminalKey.Character(".")
    case 57410          => TerminalKey.Character("/")
    case 57411          => TerminalKey.Character("*")
    case 57412          => TerminalKey.Character("-")
    case 57413          => TerminalKey.Character("+")
    case 57414          => TerminalKey.Enter
    case 57415          => TerminalKey.Character("=")
    case 57416          => TerminalKey.Character(",")
    case 57417          => TerminalKey.Left
    case 57418          => TerminalKey.Right
    case 57419          => TerminalKey.Up
    case 57420          => TerminalKey.Down
    case 57421          => TerminalKey.PageUp
    case 57422          => TerminalKey.PageDown
    case 57423          => TerminalKey.Home
    case 57424          => TerminalKey.End
    case 57425          => TerminalKey.Insert
    case 57426          => TerminalKey.Delete
    case cp if cp >= 32 => TerminalKey.Character(new String(Character.toChars(cp)))
    case cp             => TerminalKey.Unknown(s"U+$cp")

  private def csiUCodePoint(codePoint: Int, base: Option[String]): Int =
    base.flatMap(value => scala.util.Try(value.toInt).toOption).filter(_ > 0).getOrElse(codePoint)

  private def normalizeShiftedLetter(codePoint: Int, modifiers: KeyModifiers): Int =
    if modifiers.shift && codePoint >= 'A'.toInt && codePoint <= 'Z'.toInt then codePoint + 32
    else codePoint

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
