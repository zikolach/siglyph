package scalatui.terminal

/**
 * Backend abstraction for terminal lifecycle, input, output, and dimensions.
 *
 * [[start]] MUST return without synchronously invoking either registered callback on its calling
 * stack. A backend may deliver callbacks independently from another thread, including before
 * [[start]] returns. Output, cursor, title, progress, drain, and protocol methods MUST also return
 * without synchronously invoking registered input or resize callbacks.
 */
trait Terminal:
  /**
   * Start terminal control and register callback delivery.
   *
   * This method MUST return without invoking either callback synchronously on its calling stack.
   * Callback delivery may begin independently on another thread before this method returns.
   */
  def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit
  def stop(): Unit

  def write(data: String): Unit

  def columns: Int
  def rows: Int

  def moveBy(lines: Int): Unit
  def hideCursor(): Unit
  def showCursor(): Unit
  def clearLine(): Unit
  def clearFromCursor(): Unit
  def clearScreen(): Unit

/** Optional terminal capability for backends that can discard pending input before shutdown. */
trait TerminalInputDrainSupport:
  /**
   * Discard pending input or protocol fragments before terminal shutdown without invoking input
   * callbacks.
   *
   * Implementations MUST keep the operation bounded by `maxMillis` and SHOULD treat `idleMillis` as
   * the maximum quiet period to wait for when they support idle detection.
   */
  def drainInput(maxMillis: Long = 1000L, idleMillis: Long = 50L): Unit

/** Optional terminal capability for backends that can set the terminal window title. */
trait TerminalTitleSupport:
  /** Set the title without synchronously delivering registered input or resize callbacks. */
  def setTitle(title: String): Unit

/** Optional terminal capability for backends that own mouse reporting lifecycle. */
trait TerminalMouseProtocolSupport:
  /** Configure whether start/stop should enable and disable terminal mouse reporting. */
  def mouseReportingEnabled_=(enabled: Boolean): Unit

/** Optional terminal capability for backends that can set terminal progress state. */
trait TerminalProgressSupport:
  /**
   * Set terminal progress state.
   *
   * Implementations emit fire-and-forget terminal protocol output. They do not guarantee that the
   * terminal displays or retains the progress indicator. This method must not synchronously deliver
   * registered input or resize callbacks.
   */
  def setProgress(active: Boolean): Unit

object Terminal:
  private[terminal] val ProgressActiveSequence: String = "\u001b]9;4;3\u0007"
  private[terminal] val ProgressClearSequence: String  = "\u001b]9;4;0\u0007"

  /** Xterm normal mouse tracking and SGR coordinate protocol sequences. */
  object MouseProtocol:
    val EnableNormalTracking: String  = "\u001b[?1000h"
    val DisableNormalTracking: String = "\u001b[?1000l"
    val EnableSgrCoordinates: String  = "\u001b[?1006h"
    val DisableSgrCoordinates: String = "\u001b[?1006l"
    val Enable: String                = EnableNormalTracking + EnableSgrCoordinates
    val Disable: String               = DisableNormalTracking + DisableSgrCoordinates

  /**
   * Set the terminal window title when the backend supports title operations.
   *
   * The base [[Terminal]] abstraction is unchanged. Backends opt in with [[TerminalTitleSupport]].
   * Unsupported terminals return `false` and emit no title escape sequence. Control characters are
   * removed before a title sequence is emitted.
   */
  def setTitle(terminal: Terminal, title: String): Boolean = terminal match
    case titled: TerminalTitleSupport =>
      titled.setTitle(sanitizeTitle(title))
      true
    case _                            => false

  /** Configure terminal mouse reporting when the backend owns that lifecycle. */
  def setMouseReporting(terminal: Terminal, enabled: Boolean): Boolean = terminal match
    case mouse: TerminalMouseProtocolSupport =>
      mouse.mouseReportingEnabled_=(enabled)
      true
    case _                                   => false

  /**
   * Set terminal progress state when the backend supports progress operations.
   *
   * The operation is fire-and-forget. Unsupported terminals return `false` and emit no progress
   * escape sequence.
   */
  def setProgress(terminal: Terminal, active: Boolean): Boolean = terminal match
    case progress: TerminalProgressSupport =>
      progress.setProgress(active)
      true
    case _                                 => false

  /**
   * Discard pending terminal input when the backend supports it.
   *
   * Unsupported terminals return `false` and perform no operation. Backends that support draining
   * are responsible for keeping the operation bounded and not invoking input callbacks.
   */
  def drainInput(
      terminal: Terminal,
      maxMillis: Long = 1000L,
      idleMillis: Long = 50L
  ): Boolean = terminal match
    case drain: TerminalInputDrainSupport =>
      drain.drainInput(math.max(0L, maxMillis), math.max(0L, idleMillis))
      true
    case _                                => false

  private[terminal] def titleSequence(title: String): String =
    s"\u001b]0;${sanitizeTitle(title)}\u0007"

  private[terminal] def sanitizeTitle(title: String): String =
    title.filterNot(_.isControl)
