package scalatui.terminal

import scalatui.syntax.Equality.*

/**
 * Backend abstraction for terminal lifecycle, input, output, and dimensions.
 *
 * [[start]] MUST return without synchronously invoking any registered callback on its calling
 * stack. A backend may deliver callbacks independently from another thread, including before
 * [[start]] returns. Output, cursor, title, progress, drain, and protocol methods MUST also return
 * without synchronously invoking registered callbacks.
 */
trait Terminal:
  /**
   * Start terminal control and register callback delivery.
   *
   * This method MUST return without invoking either callback synchronously on its calling stack.
   * Callback delivery may begin independently on another thread before this method returns.
   */
  def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit

  /**
   * Start terminal control and register input, resize, and worker-failure callback delivery.
   *
   * The default implementation preserves source compatibility by delegating to the two-callback
   * overload. Backends that own workers override this overload and report unexpected active-worker
   * failures through `onFailure`. EOF, interruption caused by [[stop]], and stale-generation
   * termination are normal outcomes. Each worker reports at most once per active generation, while
   * independent workers may each report once. A failure claimed while its generation is active is
   * still delivered if another failure callback starts cleanup. No callback runs synchronously on
   * this method's calling stack.
   */
  def start(
      onInput: TerminalInput => Unit,
      onResize: () => Unit,
      onFailure: Throwable => Unit
  ): Unit = start(onInput, onResize)

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

/** Xterm mouse tracking mode selected for one terminal lifecycle. */
enum TerminalMouseTrackingMode derives CanEqual:
  case Disabled, Basic, Drag, AllMotion

/** Typed per-TUI mouse tracking request. */
final case class TerminalMouseTrackingOptions(
    mode: TerminalMouseTrackingMode,
    allowAllMotionInMultiplexer: Boolean = false
) derives CanEqual

object TerminalMouseTrackingOptions:
  /**
   * Resolve legacy basic input and an optional typed request. All-motion degrades to button-motion
   * in tmux and screen unless this TUI instance explicitly permits all-motion forwarding.
   */
  def resolve(
      legacyMouseInput: Boolean,
      request: Option[TerminalMouseTrackingOptions],
      env: Map[String, String] = sys.env
  ): TerminalMouseTrackingMode =
    val requested   = request.map(_.mode).getOrElse(
      if legacyMouseInput then TerminalMouseTrackingMode.Basic
      else TerminalMouseTrackingMode.Disabled
    )
    val multiplexer = env.contains("TMUX") || env.get("TERM").exists(value =>
      value.startsWith("tmux") || value.startsWith("screen")
    )
    if requested === TerminalMouseTrackingMode.AllMotion && multiplexer &&
      !request.exists(_.allowAllMotionInMultiplexer)
    then TerminalMouseTrackingMode.Drag
    else requested

/** Optional terminal capability for backends that own mouse reporting lifecycle. */
trait TerminalMouseProtocolSupport:
  /** Configure legacy basic reporting intent for a later backend start. */
  def mouseReportingEnabled_=(enabled: Boolean): Unit

  /**
   * Configure the minimum tracking mode for a later backend start. Existing implementations retain
   * source compatibility and map every enabled mode to basic reporting.
   */
  def mouseTrackingMode_=(mode: TerminalMouseTrackingMode): Unit =
    mouseReportingEnabled_=(mode !== TerminalMouseTrackingMode.Disabled)

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

  /** Xterm mouse tracking and SGR coordinate protocol sequences. */
  object MouseProtocol:
    val EnableNormalTracking: String        = "\u001b[?1000h"
    val DisableNormalTracking: String       = "\u001b[?1000l"
    val EnableButtonMotionTracking: String  = "\u001b[?1002h"
    val DisableButtonMotionTracking: String = "\u001b[?1002l"
    val EnableAllMotionTracking: String     = "\u001b[?1003h"
    val DisableAllMotionTracking: String    = "\u001b[?1003l"
    val EnableSgrCoordinates: String        = "\u001b[?1006h"
    val DisableSgrCoordinates: String       = "\u001b[?1006l"
    val Enable: String                      = EnableNormalTracking + EnableSgrCoordinates
    val Disable: String                     = DisableNormalTracking + DisableSgrCoordinates

    def enable(mode: TerminalMouseTrackingMode): String = mode match
      case TerminalMouseTrackingMode.Disabled  => ""
      case TerminalMouseTrackingMode.Basic     => EnableNormalTracking + EnableSgrCoordinates
      case TerminalMouseTrackingMode.Drag      => EnableButtonMotionTracking + EnableSgrCoordinates
      case TerminalMouseTrackingMode.AllMotion => EnableAllMotionTracking + EnableSgrCoordinates

    def disable(mode: TerminalMouseTrackingMode): String = mode match
      case TerminalMouseTrackingMode.Disabled  => ""
      case TerminalMouseTrackingMode.Basic     => DisableNormalTracking + DisableSgrCoordinates
      case TerminalMouseTrackingMode.Drag      => DisableButtonMotionTracking + DisableSgrCoordinates
      case TerminalMouseTrackingMode.AllMotion => DisableAllMotionTracking + DisableSgrCoordinates

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

  /**
   * Configure terminal mouse reporting for backend lifecycle start/stop.
   *
   * This records lifecycle intent on supporting backends. It does not immediately write terminal
   * protocol sequences for an already running backend.
   */
  def setMouseReporting(terminal: Terminal, enabled: Boolean): Boolean =
    setMouseTracking(
      terminal,
      if enabled then TerminalMouseTrackingMode.Basic else TerminalMouseTrackingMode.Disabled
    )

  /** Configure one typed mouse tracking mode for backend lifecycle start and stop. */
  def setMouseTracking(terminal: Terminal, mode: TerminalMouseTrackingMode): Boolean =
    terminal match
      case mouse: TerminalMouseProtocolSupport =>
        mouse.mouseTrackingMode_=(mode)
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
