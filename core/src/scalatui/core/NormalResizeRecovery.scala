package scalatui.core

/**
 * Current terminal geometry and strict output budget for one normal-screen resize recovery attempt.
 *
 * Siglyph supplies positive `width`, `height`, and `maxRows` values from the current shared
 * JVM/Scala Native runtime session. Applications use `width` to reflow their own semantic durable
 * history and return at most the newest `maxRows` display rows in oldest-to-newest order. Siglyph
 * does not expose or infer terminal scrollback survivors.
 */
final case class NormalResizeRecoveryContext(width: Int, height: Int, maxRows: Int)
    derives CanEqual:
  require(width > 0, "Normal resize recovery width must be positive")
  require(height > 0, "Normal resize recovery height must be positive")
  require(maxRows > 0, "Normal resize recovery row budget must be positive")

/**
 * Synchronous application provider for bounded normal-screen resize recovery.
 *
 * `render` runs as serialized TUI Render work outside lifecycle and terminal-write locks. A resize
 * can discard an unpublished candidate and invoke the provider again, so implementations must be
 * fast, side-effect-light, and retryable. Returned strings are ordinary text-only component lines:
 * existing SGR/OSC 8 allowlisting and width sanitization apply, while typed terminal controls,
 * images, cursors, components, and raw trusted output are intentionally unavailable.
 *
 * The contract is implemented in shared core on JVM and Scala Native. Applications retain and
 * select their own semantic transcript; Siglyph retains no recovery output after publication and
 * cannot promise emulator-independent scrollback deduplication.
 */
trait NormalResizeRecoveryProvider:
  /**
   * Reflow and select at most `context.maxRows` ordinary lines for the current geometry.
   *
   * Lines use oldest-to-newest order within the selected newest durable tail. Returning too many
   * rows fails the runtime before recovery publication; throwing uses normal fail-fast cleanup.
   */
  def render(context: NormalResizeRecoveryContext): Vector[String]

object NormalResizeRecoveryProvider:
  /** Build a provider from a synchronous retryable callback. */
  def apply(callback: NormalResizeRecoveryContext => Vector[String]): NormalResizeRecoveryProvider =
    new NormalResizeRecoveryProvider:
      override def render(context: NormalResizeRecoveryContext): Vector[String] = callback(context)
