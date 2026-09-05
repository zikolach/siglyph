package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender, ContextualComponent, InputResult, TUIContext}
import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInput, TerminalKey}

import java.util.concurrent.atomic.AtomicBoolean

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

  private final case class RenderSnapshot(message: String, frame: String)

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
 * subsequent direct renders. Calls to `tick`, `start`, `stop`, `setMessage`, and `setIndicator` may
 * come from application execution contexts. They serialize with render snapshots on JVM and Scala
 * Native. Styling hooks and TUI render requests run after the loader state boundary is released.
 * Detached calls drain effects synchronously when uncontended. Attached calls use the owning TUI
 * effect coordinator, so reentrant or concurrent calls may return before their render request runs.
 * The component has no third-party runtime dependencies and intentionally does not promise
 * wall-clock timing.
 */
class Loader(initialOptions: LoaderOptions = LoaderOptions()) extends Component,
      ContextualComponent:
  protected val stateBoundary  = ComponentStateBoundary()
  private var currentMessage   = initialOptions.message
  private var currentIndicator = initialOptions.indicator
  private var currentFrame     = 0
  private var isRunning        = false
  private var context          = Option.empty[TUIContext]

  def message: String = stateBoundary(currentMessage)

  def indicator: LoaderIndicatorOptions = stateBoundary(currentIndicator)

  def intervalMs: Int = stateBoundary(currentIndicator.normalizedIntervalMs)

  def running: Boolean = stateBoundary(isRunning)

  def frameIndex: Int = stateBoundary(currentFrame)

  def frame: String = stateBoundary(currentIndicator.frames.lift(currentFrame).getOrElse(""))

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { _ => context = value }

  /** Start tick-driven animation. This method is idempotent and owns no timer. */
  def start(): Unit = stateBoundary.transition { effects =>
    if !isRunning then
      isRunning = true
      deferRender(effects)
  }

  /** Stop tick-driven animation. This method is idempotent and owns no timer. */
  def stop(): Unit = stateBoundary.transition { effects =>
    if isRunning then
      isRunning = false
      deferRender(effects)
  }

  /**
   * Advance the current frame when running.
   *
   * @return
   *   true when the visible frame changed, false when stopped or when there are fewer than two
   *   frames
   */
  def tick(): Boolean = stateBoundary.transition { effects =>
    if !isRunning || currentIndicator.frames.length <= 1 then false
    else
      currentFrame = (currentFrame + 1) % currentIndicator.frames.length
      deferRender(effects)
      true
  }

  /** Update the displayed message and request render when attached to a runtime. */
  def setMessage(value: String): Unit = stateBoundary.transition { effects =>
    currentMessage = value
    deferRender(effects)
  }

  /** Update the indicator, reset to its first frame, and request render when attached. */
  def setIndicator(value: LoaderIndicatorOptions): Unit = stateBoundary.transition { effects =>
    currentIndicator = value
    currentFrame = 0
    deferRender(effects)
  }

  override def render(width: Int): ComponentRender =
    val snapshot = stateBoundary {
      Loader.RenderSnapshot(
        currentMessage,
        currentIndicator.frames.lift(currentFrame).getOrElse("")
      )
    }
    ComponentRender.text {
      val safeWidth = math.max(0, width)
      if safeWidth <= 0 then Vector("")
      else
        val body = Vector(renderLine(snapshot, safeWidth))
        if initialOptions.leadingBlankLine then "" +: body else body
    }

  protected def captureRenderContext(): Option[TUIContext] = context

  protected def deferRender(effects: ComponentEffects): Unit =
    context.foreach(renderContext => effects.add(() => renderContext.requestRender()))

  protected def requestRender(): Unit = requestRender(stateBoundary(captureRenderContext()))

  protected def requestRender(renderContext: Option[TUIContext]): Unit =
    renderContext.foreach(_.requestRender())

  private def renderLine(snapshot: Loader.RenderSnapshot, width: Int): String =
    val horizontal = " ".repeat(math.max(0, initialOptions.paddingX))
    val innerWidth = math.max(0, width - Ansi.visibleWidth(horizontal) * 2)
    val text       = renderText(snapshot, innerWidth)
    val padded     = horizontal + Ansi.truncateToWidth(text, innerWidth, "") + horizontal
    Ansi.truncateToWidth(padded, width, "")

  private def renderText(snapshot: Loader.RenderSnapshot, width: Int): String =
    if width <= 0 then ""
    else
      val styledMessage = initialOptions.messageStyle(snapshot.message)
      if snapshot.frame.isEmpty then styledMessage
      else initialOptions.indicatorStyle(snapshot.frame) + " " + styledMessage

/**
 * Dependency-free cancellation token exposed by [[CancellableLoader]].
 *
 * The token is a small Scala substitute for JavaScript `AbortSignal`: it only reports cancellation
 * state and does not own callbacks, futures, or effect-runtime semantics. Its atomic state gives
 * JVM threads and Scala Native workers cross-execution-context visibility. Exactly one caller can
 * make the transition from active to cancelled.
 */
final class CancellationToken private[components] ():
  private val cancelledState = AtomicBoolean(false)

  /** Return the atomically visible cancellation state. */
  def isCancelled: Boolean = cancelledState.get()

  /** Alias for [[isCancelled]]. */
  def cancelled: Boolean = isCancelled

  private[components] def cancel(): Boolean =
    cancelledState.compareAndSet(false, true)

/**
 * Loader variant that can be cancelled with Escape or explicit [[cancel()]].
 *
 * Cancellation is idempotent. `onCancel` is invoked at most once, cancellation state is exposed
 * through [[token]], and the winning cancellation requests render when attached to a runtime. An
 * uncontended detached call runs the callback before returning. Reentrant, concurrent, or attached
 * calls may return after commit and enqueue. Callback failure propagates through the active
 * detached drain or attached TUI cleanup, with later effect failures suppressed. No background work
 * or external cancellation runtime is introduced.
 */
final class CancellableLoader(options: LoaderOptions = LoaderOptions()) extends Loader(options):
  private val cancellationToken = CancellationToken()
  private var cancelCallback    = () => ()

  def onCancel: () => Unit                = stateBoundary(cancelCallback)
  def onCancel_=(value: () => Unit): Unit = stateBoundary { cancelCallback = value }

  def token: CancellationToken = cancellationToken

  def cancelled: Boolean = cancellationToken.isCancelled

  def aborted: Boolean = cancelled

  /**
   * Atomically cancel this loader.
   *
   * @return
   *   true only for the caller that wins the cancellation transition
   */
  def cancel(): Boolean = stateBoundary.transition { effects =>
    if cancellationToken.cancel() then
      effects.add(cancelCallback)
      deferRender(effects)
      true
    else false
  }

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.Key(TerminalKey.Escape, _) =>
      val changed = cancel()
      if changed then InputResult.Render else InputResult.NoRender
    case _                                        => InputResult.Ignored

  def dispose(): Unit = stop()
