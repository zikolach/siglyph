package scalatui.terminal

/** Typed input delivered by terminal backends to focused components. */
sealed trait TerminalInput derives CanEqual

object TerminalInput:
  final case class KeyEvent(
      key: TerminalKey,
      modifiers: KeyModifiers = KeyModifiers.empty,
      eventType: KeyEventType = KeyEventType.Press
  ) extends TerminalInput
  object Key:
    def apply(key: TerminalKey, modifiers: KeyModifiers = KeyModifiers.empty): KeyEvent =
      KeyEvent(key, modifiers, KeyEventType.Press)

    def unapply(input: TerminalInput): Option[(TerminalKey, KeyModifiers)] = input match
      case event: KeyEvent => Some(event.key -> event.modifiers)
      case _               => None

  final case class Paste(text: String) extends TerminalInput
  final case class Raw(data: String)   extends TerminalInput

/** Key event kind reported by advanced keyboard protocols. */
enum KeyEventType derives CanEqual:
  case Press, Repeat, Release

/** Terminal key identities independent from backend escape sequences. */
sealed trait TerminalKey derives CanEqual

object TerminalKey:
  case object Enter     extends TerminalKey
  case object Escape    extends TerminalKey
  case object Tab       extends TerminalKey
  case object Backspace extends TerminalKey

  /** Insert key from standard terminal insert-key escape sequences. */
  case object Insert                        extends TerminalKey
  case object Delete                        extends TerminalKey
  case object Home                          extends TerminalKey
  case object End                           extends TerminalKey
  case object Up                            extends TerminalKey
  case object Down                          extends TerminalKey
  case object Left                          extends TerminalKey
  case object Right                         extends TerminalKey
  case object PageUp                        extends TerminalKey
  case object PageDown                      extends TerminalKey
  final case class Character(value: String) extends TerminalKey
  final case class Function(index: Int)     extends TerminalKey
  final case class Unknown(name: String)    extends TerminalKey

final case class KeyModifiers(
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    superKey: Boolean = false
) derives CanEqual:
  def isEmpty: Boolean = !ctrl && !shift && !alt && !superKey

object KeyModifiers:
  val empty: KeyModifiers = KeyModifiers()
