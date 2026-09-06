package scalatui.core

import scalatui.terminal.{KeyModifiers, MouseButton, MouseButtonState, MouseWheelDirection}

/** Monotonic time source used for click and multi-click classification. */
trait MonotonicClock derives CanEqual:
  /** Current monotonic time in nanoseconds. */
  def nanoTime(): Long

object MonotonicClock:
  /** System monotonic clock used by default on JVM and Scala Native. */
  val System: MonotonicClock = new MonotonicClock:
    override def nanoTime(): Long = java.lang.System.nanoTime()

/**
 * Per-TUI bounds for semantic click, drag, and multi-click classification.
 *
 * Durations are non-negative milliseconds. Distance is the maximum terminal-cell movement retained
 * as a click and repeated-click match. The monotonic clock is called on the serialized runtime
 * input path and lets JVM and Scala Native tests avoid wall-clock timing.
 */
final case class MouseGestureOptions(
    clickMaxDurationMillis: Long = 500L,
    multiClickMaxDelayMillis: Long = 500L,
    maxCellDistance: Int = 1,
    clock: MonotonicClock = MonotonicClock.System
) derives CanEqual:
  require(clickMaxDurationMillis >= 0L, "Click duration must be non-negative")
  require(multiClickMaxDelayMillis >= 0L, "Multi-click delay must be non-negative")
  require(maxCellDistance >= 0, "Mouse cell distance must be non-negative")

/**
 * Committed absolute and component-local geometry for one semantic mouse event. Bounds come from
 * the last accepted layout frame and remain unchanged for this callback.
 */
final case class MouseEventLocation(
    absoluteRow: Int,
    absoluteCol: Int,
    localRow: Int,
    localCol: Int,
    bounds: LayoutBounds
) derives CanEqual

/** Semantic mouse event derived from ordered raw terminal input and committed layout. */
sealed trait MouseEvent derives CanEqual:
  def location: MouseEventLocation
  def modifiers: KeyModifiers

object MouseEvent:
  /** Pointer movement without runtime drag classification. */
  final case class Move(
      location: MouseEventLocation,
      buttonState: MouseButtonState,
      modifiers: KeyModifiers
  ) extends MouseEvent

  /** Button press delivered to the committed target under the pointer. */
  final case class Press(
      location: MouseEventLocation,
      button: MouseButton,
      modifiers: KeyModifiers
  ) extends MouseEvent

  /** Button release delivered to the capture owner or committed target. */
  final case class Release(
      location: MouseEventLocation,
      button: MouseButton,
      modifiers: KeyModifiers
  ) extends MouseEvent

  /** Click completed within configured time and cell-distance bounds. */
  final case class Click(
      location: MouseEventLocation,
      button: MouseButton,
      clickCount: Int,
      modifiers: KeyModifiers
  ) extends MouseEvent

  /** Pressed pointer movement beyond the configured click cell-distance bound. */
  final case class Drag(
      location: MouseEventLocation,
      button: MouseButton,
      modifiers: KeyModifiers
  ) extends MouseEvent

  /**
   * Wheel movement delivered to the committed target under the pointer. `lineDelta` is negative for
   * upward movement and positive for downward movement. The runtime applies acceleration once
   * before routing the signed remainder through targets.
   */
  final case class Wheel(
      location: MouseEventLocation,
      direction: MouseWheelDirection,
      modifiers: KeyModifiers,
      lineDelta: Int
  ) extends MouseEvent

  object Wheel:
    /** Construct an unaccelerated wheel event for direct handler calls. */
    def apply(
        location: MouseEventLocation,
        direction: MouseWheelDirection,
        modifiers: KeyModifiers
    ): Wheel = new Wheel(
      location,
      direction,
      modifiers,
      direction match
        case MouseWheelDirection.Up   => -1
        case MouseWheelDirection.Down => 1
        case _                        => 0
    )

/** Render action requested by a semantic mouse handler. */
enum MouseRenderIntent derives CanEqual:
  case Preserve, Render

/** Pointer-capture action requested by a semantic mouse handler. */
enum MouseCaptureIntent derives CanEqual:
  case Preserve, Capture, Release

/** Keyboard-focus action requested by a semantic mouse handler. */
enum MouseFocusIntent derives CanEqual:
  case Preserve, Request, Clear

/** One semantic mouse-handler result applied by the serialized runtime drain. */
final case class MouseHandlerResult(
    handled: Boolean,
    renderIntent: MouseRenderIntent = MouseRenderIntent.Preserve,
    captureIntent: MouseCaptureIntent = MouseCaptureIntent.Preserve,
    focusIntent: MouseFocusIntent = MouseFocusIntent.Preserve,
    wheelRemainder: Option[Int] = None
) derives CanEqual

object MouseHandlerResult:
  val Ignored: MouseHandlerResult = MouseHandlerResult(handled = false)
  val Handled: MouseHandlerResult = MouseHandlerResult(handled = true)

/** Component capability for semantic mouse gestures derived by the runtime. */
trait MouseEventHandler:
  /**
   * Handle one gesture using committed geometry. Application code runs outside component state, TUI
   * lifecycle, and terminal-output locks. The runtime applies returned intents afterward on its
   * serialized drain.
   */
  def handleMouseEvent(event: MouseEvent): MouseHandlerResult
