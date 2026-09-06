package scalatui.core

/** Explicit JVM and Scala Native destination for bounded fullscreen selection copy. */
enum ClipboardTarget derives CanEqual:
  /** Application-provided host clipboard callback. */
  case Host

/** Whether one explicit clipboard target is configured for this TUI session. */
enum ClipboardTargetSupport derives CanEqual:
  case Supported, Unsupported

/** Result of one explicit fullscreen selection copy request. */
sealed trait ClipboardCopyResult derives CanEqual:
  def target: ClipboardTarget

object ClipboardCopyResult:
  /** The configured target reported that it copied the complete bounded selection. */
  final case class Success(target: ClipboardTarget) extends ClipboardCopyResult

  /** The requested target has no configured implementation for this TUI session. */
  final case class Unsupported(target: ClipboardTarget) extends ClipboardCopyResult

  /** The configured target rejected the copy or threw while processing it. */
  final case class Failure(target: ClipboardTarget, cause: Option[Throwable] = None)
      extends ClipboardCopyResult

/**
 * Portable host clipboard callback.
 *
 * The runtime invokes [[copy]] without holding component-state, lifecycle, or terminal-output
 * locks. Return true only after the complete value has been copied. A false return or thrown
 * exception is reported as [[ClipboardCopyResult.Failure]]. Core does not emit OSC 52 and provides
 * no terminal clipboard authority.
 */
trait HostClipboard:
  /** Copy the complete bounded plain-text selection and report host acceptance. */
  def copy(value: String): Boolean
