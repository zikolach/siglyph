package scalatui.components

import scalatui.core.MouseEvent
import scalatui.syntax.Equality.*
import scalatui.terminal.MouseWheelDirection

/**
 * Explicit per-session wheel configuration; no platform probing or environment access. Set
 * terminalAccelerated for local macOS, trackpads, or other already-scaled sources. Counts are
 * bounded to 100 rows (500 with Alt).
 */
final case class WheelScrollPolicy(
    fixedLines: Int = 1,
    accelerate: Boolean = false,
    terminalAccelerated: Boolean = false,
    maxAcceleratedLines: Int = 6
):
  require(fixedLines >= 1 && fixedLines <= 100)
  require(maxAcceleratedLines >= 1 && maxAcceleratedLines <= 100)

/**
 * Constant-space, caller-owned gesture state shared by JVM and Native. Call once per original
 * event, before routing remainders to nested views. Time is explicit monotonic milliseconds;
 * backwards time resets the gesture. Serialize calls with input dispatch.
 */
final class WheelScrollController(val policy: WheelScrollPolicy = WheelScrollPolicy()):
  private var previous   = Option.empty[Long]
  private var direction  = 0
  private var averageGap = 0.0

  /** Signed row delta; zero direction neither moves nor advances gesture state. */
  def next(directionSign: Int, nowMillis: Long, alt: Boolean = false): Int =
    val sign = java.lang.Integer.signum(directionSign)
    if sign === 0 then 0
    else
      val gap         = previous.flatMap { before =>
        if nowMillis < before then None
        else
          val elapsed = nowMillis - before
          Option.when(elapsed >= 0L)(elapsed)
      }
      val sameGesture = direction === sign && gap.exists(g => g >= 0L && g <= 200L)
      previous = Some(nowMillis)
      direction = sign
      val lines       =
        if !policy.accelerate || policy.terminalAccelerated then policy.fixedLines
        else if !sameGesture || gap.exists(_ < 5L) then
          averageGap = 0.0
          1
        else
          val elapsed = gap.get.toDouble
          averageGap = if averageGap <= 0.0 then elapsed else (averageGap + elapsed) / 2.0
          math.min(policy.maxAcceleratedLines, math.max(1, (100.0 / averageGap).toInt))
      sign * lines * (if alt then 5 else 1)

  /** Normalize an ORIGINAL semantic wheel event, never an ancestor's remainder. */
  def normalize(event: MouseEvent.Wheel, nowMillis: Long): MouseEvent.Wheel =
    val sign = event.direction match
      case MouseWheelDirection.Up   => -1
      case MouseWheelDirection.Down => 1
      case _                        => 0
    event.copy(lineDelta = next(sign, nowMillis, event.modifiers.alt))
