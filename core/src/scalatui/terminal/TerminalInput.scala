package scalatui.terminal

/** Typed input delivered by terminal backends to focused components. */
sealed trait TerminalInput derives CanEqual

object TerminalInput:
  final case class Key(key: TerminalKey, modifiers: KeyModifiers = KeyModifiers.empty) extends TerminalInput
  final case class Paste(text: String) extends TerminalInput
  final case class Raw(data: String) extends TerminalInput

/** Terminal key identities independent from backend escape sequences. */
sealed trait TerminalKey derives CanEqual

object TerminalKey:
  case object Enter extends TerminalKey
  case object Escape extends TerminalKey
  case object Tab extends TerminalKey
  case object Backspace extends TerminalKey
  case object Delete extends TerminalKey
  case object Home extends TerminalKey
  case object End extends TerminalKey
  case object Up extends TerminalKey
  case object Down extends TerminalKey
  case object Left extends TerminalKey
  case object Right extends TerminalKey
  final case class Character(value: String) extends TerminalKey
  final case class Function(index: Int) extends TerminalKey
  final case class Unknown(name: String) extends TerminalKey

final case class KeyModifiers(
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    superKey: Boolean = false
) derives CanEqual:
  def isEmpty: Boolean = !ctrl && !shift && !alt && !superKey

object KeyModifiers:
  val empty: KeyModifiers = KeyModifiers()
