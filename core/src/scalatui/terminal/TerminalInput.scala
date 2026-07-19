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

  /** Start of a bracketed paste. Marker bytes are not included in paste chunks. */
  case object PasteStart extends TerminalInput

  /** Bounded copied paste bytes delivered without whole-paste accumulation. */
  final case class PasteChunk(chunk: TerminalInputChunk) extends TerminalInput

  /** End of a complete bracketed paste. Marker bytes are not included in paste chunks. */
  case object PasteEnd extends TerminalInput

  /** Start of an untyped byte sequence whose chunks include its exact introducer bytes. */
  final case class RawStart(kind: TerminalRawKind) extends TerminalInput

  /** Bounded copied bytes from an untyped sequence, including introducer and terminator bytes. */
  final case class RawChunk(chunk: TerminalInputChunk) extends TerminalInput

  /** End and parsing outcome of an untyped byte sequence. */
  final case class RawEnd(termination: TerminalRawTermination) extends TerminalInput

  /** Parsed terminal mouse event using zero-based terminal cell coordinates. */
  final case class Mouse(
      action: MouseAction,
      row: Int,
      col: Int,
      modifiers: KeyModifiers = KeyModifiers.empty
  ) extends TerminalInput

/** Classification of a streamed untyped terminal byte sequence. */
enum TerminalRawKind derives CanEqual:
  case Csi, Osc, Dcs, Apc, Ss3, Escape, Utf8, Bytes

/** Reason a streamed raw sequence ended. */
enum TerminalRawTermination derives CanEqual:
  case Complete, Malformed, Incomplete
  case LimitExceeded(terminated: Boolean)

/**
 * Immutable terminal input bytes with length 1 through [[TerminalInputChunk.MaxBytes]]. Factories
 * copy their input and [[toArray]] returns a copy.
 */
final class TerminalInputChunk private (private val bytes: Array[Byte]) derives CanEqual:
  def length: Int          = bytes.length
  def toArray: Array[Byte] = bytes.clone()

  override def equals(other: Any): Boolean = other match
    case chunk: TerminalInputChunk => java.util.Arrays.equals(bytes, chunk.bytes)
    case _                         => false

  override def hashCode(): Int = java.util.Arrays.hashCode(bytes)

object TerminalInputChunk:
  val MaxBytes: Int = 4096

  def apply(bytes: Array[Byte]): TerminalInputChunk =
    require(bytes.nonEmpty && bytes.length <= MaxBytes, s"chunk length must be 1..$MaxBytes")
    new TerminalInputChunk(bytes.clone())

  def apply(bytes: Array[Byte], offset: Int, length: Int): TerminalInputChunk =
    require(length > 0 && length <= MaxBytes, s"chunk length must be 1..$MaxBytes")
    require(offset >= 0 && offset <= bytes.length - length, "chunk range is outside input")
    new TerminalInputChunk(java.util.Arrays.copyOfRange(bytes, offset, offset + length))

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

  /** SGR button identity bits without modifier, motion, or wheel flags. */
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
