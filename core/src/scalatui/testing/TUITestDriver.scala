package scalatui.testing

import java.nio.charset.StandardCharsets
import scalatui.core.TUI
import scalatui.terminal.{
  KeyEventType, KeyModifiers, TerminalInput, TerminalInputChunk, TerminalKey, VirtualTerminal
}
import scalatui.unicode.Unicode

/** Limits apply to scripted actions/retained diagnostics, not arbitrary application execution. */
final case class TUITestLimits(
    maxSteps: Int = 10000,
    maxInputBytes: Int = 65536,
    maxOutputChars: Int = 4096,
    maxCells: Int = 1000000
):
  require(maxSteps > 0, "maxSteps must be positive")
  require(maxInputBytes > 0 && maxInputBytes <= 1048576, "maxInputBytes must be 1..1048576")
  require(maxOutputChars >= 0 && maxOutputChars <= 65536, "maxOutputChars must be 0..65536")
  require(maxCells > 0 && maxCells <= 1000000, "maxCells must be 1..1000000")

/**
 * Experimental, framework-independent synchronous test fixture on JVM and Native. Construct with
 * TUITestDriver.withSession. Driver methods are single-threaded/non-reentrant. A flush drains
 * currently available TUI work; it does not await arbitrary workers or timers.
 */
final class TUITestDriver private (
    val tui: TUI,
    terminal: VirtualTerminal,
    val limits: TUITestLimits
):
  private val owner           = Thread.currentThread()
  private var busy            = false
  private var failed          = false
  private var closing         = false
  private var closed          = false
  private var used            = 0
  private var recorded        = ""
  private var truncated       = false
  private var cleanupRecorded = ""
  private var cleanupCut      = false

  private def checkOwner(): Unit =
    if Thread.currentThread() ne owner then
      throw new IllegalStateException("test driver must be used on its owning thread")
    if busy then throw new IllegalStateException("reentrant test driver actions are not supported")

  private def admit(count: Int): Unit =
    checkOwner()
    if closing || closed || failed || !terminal.isRunning then
      throw new IllegalStateException("test driver session is closed, failed or not running")
    if count > limits.maxSteps - used then
      throw new IllegalStateException(
        s"test driver step budget exceeded: $count requested, ${limits.maxSteps - used} remaining"
      )

  private def captureOutput(cleanup: Boolean): Unit =
    val builder   = new StringBuilder
    var remaining = limits.maxOutputChars
    var cut       = false
    terminal.writes.foreach { write =>
      builder.append(write.take(remaining))
      if write.length > remaining then cut = true
      remaining = math.max(0, remaining - write.length)
    }
    if cleanup then
      cleanupRecorded = builder.result()
      cleanupCut = cut
    else
      recorded = builder.result()
      truncated = cut
    terminal.clearWrites()

  private def execute(cleanup: Boolean = false)(action: => Unit): Unit =
    busy = true
    var primary = Option.empty[Throwable]
    try
      terminal.clearWrites()
      action
    catch
      case error: Throwable =>
        failed = true
        primary = Some(error)
        throw error
    finally
      try captureOutput(cleanup)
      catch
        case recording: Throwable =>
          failed = true
          primary match
            case Some(error) => if error ne recording then error.addSuppressed(recording)
            case None        => throw recording
      finally busy = false

  private def start(): Unit =
    checkOwner()
    require(!terminal.isRunning, "factory must return an unstarted TUI")
    execute() {
      tui.start()
      require(terminal.isRunning, "factory must use the supplied VirtualTerminal")
      tui.flushRender()
      tui.rethrowRuntimeFailure()
    }

  private def step(action: => Unit): Unit =
    admit(1)
    used += 1
    execute() {
      action
      tui.flushRender()
      tui.rethrowRuntimeFailure()
    }

  private def inputBytes(value: String): Array[Byte] =
    require(value.length <= limits.maxInputBytes, "input exceeds byte limit")
    val bytes = value.getBytes(StandardCharsets.UTF_8)
    require(bytes.length <= limits.maxInputBytes, "input exceeds byte limit")
    bytes

  /** Deliver one typed event and flush available rendering; no raw terminal parser is simulated. */
  def input(value: TerminalInput): Unit =
    value match
      case TerminalInput.KeyEvent(TerminalKey.Character(text), _, _) => inputBytes(text); ()
      case TerminalInput.KeyEvent(TerminalKey.Unknown(text), _, _)   => inputBytes(text); ()
      case TerminalInput.PasteChunk(chunk)                           =>
        require(chunk.length <= limits.maxInputBytes, "input chunk exceeds byte limit")
      case TerminalInput.RawChunk(chunk)                             =>
        require(chunk.length <= limits.maxInputBytes, "input chunk exceeds byte limit")
      case _                                                         => ()
    step(terminal.sendInput(value))

  def key(
      value: TerminalKey,
      modifiers: KeyModifiers = KeyModifiers.empty,
      eventType: KeyEventType = KeyEventType.Press
  ): Unit =
    input(TerminalInput.KeyEvent(value, modifiers, eventType))

  /**
   * Deliver one Character event per grapheme, rejecting control characters and over-budget batches.
   */
  def typeText(value: String): Unit =
    inputBytes(value)
    require(
      !value.exists(ScreenSnapshot.isControl),
      "typeText accepts printable text; use keys or paste for controls"
    )
    val graphemes = Unicode.graphemeClusters(value)
    admit(graphemes.length)
    graphemes.foreach(value => key(TerminalKey.Character(value)))

  /** Deliver a complete bracketed paste; chunkBytes=1 exercises fragmented UTF-8 decoding. */
  def paste(value: String, chunkBytes: Int = TerminalInputChunk.MaxBytes): Unit =
    require(chunkBytes > 0 && chunkBytes <= TerminalInputChunk.MaxBytes, "invalid paste chunk size")
    val bytes  = inputBytes(value)
    val chunks = (bytes.length + chunkBytes - 1) / chunkBytes
    admit(chunks + 2)
    input(TerminalInput.PasteStart)
    bytes.grouped(chunkBytes).foreach(chunk =>
      input(TerminalInput.PasteChunk(TerminalInputChunk(chunk)))
    )
    input(TerminalInput.PasteEnd)

  def resize(columns: Int, rows: Int): Unit =
    TUITestDriver.validateGeometry(columns, rows, limits)
    step(terminal.resize(columns, rows))

  /** One explicit flush, not an asynchronous idle barrier or a timeout around application code. */
  def flush(): Unit = step(())

  def stepsUsed: Int                  = { checkOwner(); used }
  def lastOutput: String              = { checkOwner(); recorded }
  def outputTruncated: Boolean        = { checkOwner(); truncated }
  def isClosed: Boolean               = { checkOwner(); closed }
  def cleanupOutput: String           = { checkOwner(); cleanupRecorded }
  def cleanupOutputTruncated: Boolean = { checkOwner(); cleanupCut }

  /** Capture the modeled rows, without interpreting retained output as a terminal transcript. */
  def screen: ScreenSnapshot =
    checkOwner()
    ScreenSnapshot(terminal.columns, terminal.rows, terminal.screenLines)

  def assertScreen(expected: ScreenSnapshot): Unit = screen.assertMatches(expected)
  def assertContains(value: String): Unit          = screen.assertContains(value)

  /** Explicit restoration. Failed cleanup may be retried, but no more input is accepted. */
  def close(): Unit =
    checkOwner()
    if !closed then
      closing = true
      execute(cleanup = true) {
        tui.stop()
        if terminal.isRunning then
          throw new IllegalStateException("TUI stop returned before terminal cleanup completed")
        closed = true
        if !failed then tui.rethrowRuntimeFailure()
      }

object TUITestDriver:
  private def validateGeometry(columns: Int, rows: Int, limits: TUITestLimits): Unit =
    require(
      columns >= 0 && columns <= 10000 && rows >= 0 && rows <= 10000,
      "viewport dimensions must be 0..10000"
    )
    require(columns.toLong * rows.toLong <= limits.maxCells.toLong, "viewport exceeds cell budget")

  /**
   * Build against the supplied terminal and return an unstarted TUI. The body runs after startup;
   * cleanup is attempted on every path, preserving primary errors with independent failures
   * suppressed. Direct calls to the returned TUI bypass driver budgets. Fixtures must own external
   * resources.
   */
  def withSession[A](columns: Int = 80, rows: Int = 24, limits: TUITestLimits = TUITestLimits())(
      create: VirtualTerminal => TUI
  )(body: TUITestDriver => A): A =
    validateGeometry(columns, rows, limits)
    val terminal = VirtualTerminal(columns, rows)
    var driver   = Option.empty[TUITestDriver]
    var failure  = Option.empty[Throwable]
    try
      val instance = new TUITestDriver(create(terminal), terminal, limits)
      driver = Some(instance)
      instance.start()
      body(instance)
    catch
      case error: Throwable => failure = Some(error); throw error
    finally
      try
        driver match
          case Some(instance) => instance.close()
          case None           => terminal.stop()
      catch
        case cleanup: Throwable =>
          failure match
            case Some(primary) => if primary ne cleanup then primary.addSuppressed(cleanup)
            case None          => throw cleanup
      finally terminal.clearWrites()
