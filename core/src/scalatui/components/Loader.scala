package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender, ContextualComponent, InputResult, TUIContext}
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}

/**
 * Indicator frames and interval metadata for [[Loader]].
 *
 * The interval is metadata for application-owned scheduling or a future runtime scheduler. The
 * shared core loader does not own a background timer.
 *
 * @param frames
 *   animation frames; an empty vector hides the indicator
 * @param intervalMs
 *   desired interval metadata in milliseconds for application-owned tick scheduling
 */
final case class LoaderIndicatorOptions(
    frames: Vector[String] = Loader.DefaultFrames,
    intervalMs: Int = Loader.DefaultIntervalMs
):
  def normalizedIntervalMs: Int = if intervalMs > 0 then intervalMs else Loader.DefaultIntervalMs

/**
 * Configuration for [[Loader]].
 *
 * Style functions may add ANSI escapes. Rendering remains ANSI-aware and width-safe.
 */
final case class LoaderOptions(
    message: String = "Loading...",
    indicator: LoaderIndicatorOptions = LoaderIndicatorOptions(),
    indicatorStyle: String => String = identity,
    messageStyle: String => String = identity,
    paddingX: Int = 1,
    leadingBlankLine: Boolean = true
)

object Loader:
  /** Default braille spinner frames matching `pi-tui`'s visual style. */
  val DefaultFrames: Vector[String] = Vector("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

  /** Default interval metadata, in milliseconds, matching upstream `pi-tui`. */
  val DefaultIntervalMs: Int = 80

/**
 * Tick-driven loader component for long-running work.
 *
 * `Loader` renders an optional indicator frame and styled message as width-safe terminal output.
 * Unlike `pi-tui`'s Node implementation, this shared JVM/Native component does not own a timer or
 * background thread. Applications or future scheduler APIs drive animation by calling [[tick()]].
 * `start()` and `stop()` only update running state; `tick()` advances frames only while running.
 *
 * When attached to a [[scalatui.core.TUI]], state changes request renders through
 * [[scalatui.core.TUIContext TUIContext]]. Without a context, state changes are safe and affect
 * subsequent direct renders. The component has no third-party runtime dependencies and
 * intentionally does not promise wall-clock timing.
 */
class Loader(initialOptions: LoaderOptions = LoaderOptions()) extends Component,
      ContextualComponent:
  private var currentMessage   = initialOptions.message
  private var currentIndicator = initialOptions.indicator
  private var currentFrame     = 0
  private var isRunning        = false
  private var context          = Option.empty[TUIContext]

  def message: String = currentMessage

  def indicator: LoaderIndicatorOptions = currentIndicator

  def intervalMs: Int = currentIndicator.normalizedIntervalMs

  def running: Boolean = isRunning

  def frameIndex: Int = currentFrame

  def frame: String = currentIndicator.frames.lift(currentFrame).getOrElse("")

  override def tuiContext_=(value: Option[TUIContext]): Unit = context = value

  /** Start tick-driven animation. This method is idempotent and owns no timer. */
  def start(): Unit =
    if !isRunning then
      isRunning = true
      requestRender()

  /** Stop tick-driven animation. This method is idempotent and owns no timer. */
  def stop(): Unit =
    if isRunning then
      isRunning = false
      requestRender()

  /**
   * Advance the current frame when running.
   *
   * @return
   *   true when the visible frame changed, false when stopped or when there are fewer than two
   *   frames
   */
  def tick(): Boolean =
    if !isRunning then false
    else if currentIndicator.frames.length <= 1 then
      requestRender()
      false
    else
      currentFrame = (currentFrame + 1) % currentIndicator.frames.length
      requestRender()
      true

  /** Update the displayed message and request render when attached to a runtime. */
  def setMessage(value: String): Unit =
    currentMessage = value
    requestRender()

  /** Update the indicator, reset to its first frame, and request render when attached. */
  def setIndicator(value: LoaderIndicatorOptions): Unit =
    currentIndicator = value
    currentFrame = 0
    requestRender()

  override def render(width: Int): ComponentRender = ComponentRender.text {
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then Vector("")
    else
      val body = Vector(renderLine(safeWidth))
      if initialOptions.leadingBlankLine then "" +: body else body
  }

  protected def requestRender(): Unit = context.foreach(_.requestRender())

  private def renderLine(width: Int): String =
    val horizontal = " ".repeat(math.max(0, initialOptions.paddingX))
    val innerWidth = math.max(0, width - Ansi.visibleWidth(horizontal) * 2)
    val text       = renderText(innerWidth)
    val padded     = horizontal + Ansi.truncateToWidth(text, innerWidth, "") + horizontal
    Ansi.truncateToWidth(padded, width, "")

  private def renderText(width: Int): String =
    if width <= 0 then ""
    else
      val styledMessage = initialOptions.messageStyle(currentMessage)
      val current       = frame
      if current.isEmpty then styledMessage
      else initialOptions.indicatorStyle(current) + " " + styledMessage

/**
 * Dependency-free cancellation token exposed by [[CancellableLoader]].
 *
 * The token is a small Scala substitute for JavaScript `AbortSignal`: it only reports cancellation
 * state and does not own callbacks, futures, or effect-runtime semantics.
 */
final class CancellationToken private[components] ():
  private var cancelledState = false

  def isCancelled: Boolean = cancelledState

  def cancelled: Boolean = cancelledState

  private[components] def cancel(): Boolean =
    if cancelledState then false
    else
      cancelledState = true
      true

/**
 * Loader variant that can be cancelled with Escape or explicit [[cancel()]].
 *
 * Cancellation is idempotent. `onCancel` is invoked at most once, cancellation state is exposed
 * through [[token]], and cancellation requests render when attached to a runtime. No background
 * work or external cancellation runtime is introduced.
 */
final class CancellableLoader(options: LoaderOptions = LoaderOptions()) extends Loader(options):
  private val cancellationToken = CancellationToken()

  var onCancel: () => Unit = () => ()

  def token: CancellationToken = cancellationToken

  def cancelled: Boolean = cancellationToken.isCancelled

  def aborted: Boolean = cancelled

  def cancel(): Boolean =
    if cancellationToken.cancel() then
      onCancel()
      requestRender()
      true
    else false

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Escape, _) =>
      val changed = cancel()
      if changed then InputResult.Render else InputResult.NoRender
    case _                                        => InputResult.Ignored

  def dispose(): Unit = stop()
