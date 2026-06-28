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

  /** Parsed terminal mouse event using zero-based terminal cell coordinates. */
  final case class Mouse(
      action: MouseAction,
      row: Int,
      col: Int,
      modifiers: KeyModifiers = KeyModifiers.empty
  ) extends TerminalInput

  final case class Raw(data: String) extends TerminalInput

/** Mouse action reported by supported terminal mouse protocols. */
sealed trait MouseAction derives CanEqual

object MouseAction:
  /** Mouse button press. */
  final case class Press(button: MouseButton) extends MouseAction

  /** Mouse button release. */
  final case class Release(button: MouseButton) extends MouseAction

  /** Mouse wheel movement. */
  final case class Wheel(direction: MouseWheelDirection) extends MouseAction

/** Mouse button identity parsed from terminal button codes. */
sealed trait MouseButton derives CanEqual

object MouseButton:
  case object Left   extends MouseButton
  case object Middle extends MouseButton
  case object Right  extends MouseButton

  /** Valid mouse button code without a named mapping. */
  final case class Other(code: Int) extends MouseButton

/** Mouse wheel direction parsed from terminal wheel reports. */
enum MouseWheelDirection derives CanEqual:
  case Up, Down, Left, Right

/** Target-local context passed to components that opt into mouse handling. */
final case class MouseInputContext(
    input: TerminalInput.Mouse,
    boundsRow: Int,
    boundsCol: Int,
    boundsWidth: Int,
    boundsHeight: Int,
    localRow: Int,
    localCol: Int
) derives CanEqual

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
