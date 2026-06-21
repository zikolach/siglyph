package scalatui.terminal

/** Backend abstraction for terminal lifecycle, input, output, and dimensions. */
trait Terminal:
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

/** Optional terminal capability for backends that can set the terminal window title. */
trait TerminalTitleSupport:
  /** Set the terminal window title. Callers use [[Terminal.setTitle]] to detect support. */
  def setTitle(title: String): Unit

/** Optional terminal capability for backends that can set terminal progress state. */
trait TerminalProgressSupport:
  /**
   * Set terminal progress state.
   *
   * Implementations emit fire-and-forget terminal protocol output. They do not guarantee that the
   * terminal displays or retains the progress indicator.
   */
  def setProgress(active: Boolean): Unit

object Terminal:
  private[terminal] val ProgressActiveSequence: String = "\u001b]9;4;3\u0007"
  private[terminal] val ProgressClearSequence: String  = "\u001b]9;4;0\u0007"

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

  private[terminal] def titleSequence(title: String): String =
    s"\u001b]0;${sanitizeTitle(title)}\u0007"

  private[terminal] def sanitizeTitle(title: String): String =
    title.filterNot(_.isControl)
