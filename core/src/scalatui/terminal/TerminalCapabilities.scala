package scalatui.terminal

import scalatui.syntax.Equality.*

/** Immutable terminal features selected for one runtime session or detached component. */
final case class TerminalCapabilities(
    trueColor: Boolean,
    hyperlinks: Boolean,
    images: Option[ImageProtocol]
) derives CanEqual

/**
 * Per-session choice for one detected terminal capability.
 *
 * [[Detected]] keeps environment detection, [[Forced]] selects a value even when detection is
 * conservative, and [[Disabled]] prevents attached components from enabling the capability.
 */
enum TerminalCapabilityOverride[+A] derives CanEqual:
  case Detected
  case Forced(value: A)
  case Disabled

/**
 * Per-TUI overrides applied independently to true color, OSC 8 links, and image output.
 *
 * [[scalatui.core.TUI]] resolves one immutable effective value at session start on JVM and Scala
 * Native. Overrides are not process-global. Forced values can bypass conservative detection.
 * Disabled values remain a hard ceiling for attached components, including components with fixed
 * local capabilities.
 */
final case class TerminalCapabilityOverrides(
    trueColor: TerminalCapabilityOverride[Boolean] = TerminalCapabilityOverride.Detected,
    hyperlinks: TerminalCapabilityOverride[Boolean] = TerminalCapabilityOverride.Detected,
    images: TerminalCapabilityOverride[ImageProtocol] = TerminalCapabilityOverride.Detected
) derives CanEqual

/**
 * Selects owning-session capabilities or fixed local capabilities for an attached component.
 * Session-disabled values remain disabled for either choice. Detached components cannot infer a TUI
 * session and use their constructor-provided local value.
 */
enum TerminalCapabilitiesSource derives CanEqual:
  case Session, Fixed

sealed trait ImageProtocol derives CanEqual
object ImageProtocol:
  case object Kitty  extends ImageProtocol
  case object ITerm2 extends ImageProtocol

object TerminalCapabilities:
  val Conservative: TerminalCapabilities =
    TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)

  /** Resolve detection and explicit overrides into one immutable session value. */
  def resolve(
      detected: TerminalCapabilities,
      overrides: TerminalCapabilityOverrides
  ): TerminalCapabilities =
    TerminalCapabilities(
      trueColor = resolveValue(detected.trueColor, overrides.trueColor, disabled = false),
      hyperlinks = resolveValue(detected.hyperlinks, overrides.hyperlinks, disabled = false),
      images = resolveOptional(detected.images, overrides.images)
    )

  /**
   * Resolve capabilities for an attached component. Session-disabled values are a hard ceiling for
   * both sources. Other fixed local values remain unchanged.
   */
  def forComponent(
      local: TerminalCapabilities,
      session: TerminalCapabilities,
      overrides: TerminalCapabilityOverrides,
      source: TerminalCapabilitiesSource
  ): TerminalCapabilities =
    source match
      case TerminalCapabilitiesSource.Session => session
      case TerminalCapabilitiesSource.Fixed   =>
        TerminalCapabilities(
          trueColor = ceiling(local.trueColor, overrides.trueColor, disabled = false),
          hyperlinks = ceiling(local.hyperlinks, overrides.hyperlinks, disabled = false),
          images = ceiling(local.images, overrides.images, disabled = None)
        )

  private def resolveValue[A](
      detected: A,
      overrideValue: TerminalCapabilityOverride[A],
      disabled: A
  ): A = overrideValue match
    case TerminalCapabilityOverride.Detected      => detected
    case TerminalCapabilityOverride.Forced(value) => value
    case TerminalCapabilityOverride.Disabled      => disabled

  private def resolveOptional[A](
      detected: Option[A],
      overrideValue: TerminalCapabilityOverride[A]
  ): Option[A] = overrideValue match
    case TerminalCapabilityOverride.Detected      => detected
    case TerminalCapabilityOverride.Forced(value) => Some(value)
    case TerminalCapabilityOverride.Disabled      => None

  private def ceiling[A, B](
      local: B,
      overrideValue: TerminalCapabilityOverride[A],
      disabled: B
  ): B = overrideValue match
    case TerminalCapabilityOverride.Disabled => disabled
    case _                                   => local

  def detect(
      env: Map[String, String] = sys.env,
      tmuxForwardsHyperlinks: => Boolean = false
  ): TerminalCapabilities =
    val termProgram      = env.getOrElse("TERM_PROGRAM", "").toLowerCase
    val terminalEmulator = env.getOrElse("TERMINAL_EMULATOR", "").toLowerCase
    val term             = env.getOrElse("TERM", "").toLowerCase
    val colorTerm        = env.getOrElse("COLORTERM", "").toLowerCase
    val trueColorHint    = (colorTerm === "truecolor") || (colorTerm === "24bit")

    if env.contains("TMUX") || term.startsWith("tmux") then
      TerminalCapabilities(
        trueColor = trueColorHint,
        hyperlinks = tmuxForwardsHyperlinks,
        images = None
      )
    else if term.startsWith("screen") then
      TerminalCapabilities(trueColor = trueColorHint, hyperlinks = false, images = None)
    else if env.contains("KITTY_WINDOW_ID") || (termProgram === "kitty") then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    else if (termProgram === "ghostty") || term.contains("ghostty") || env.contains(
        "GHOSTTY_RESOURCES_DIR"
      )
    then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    else if env.contains("WEZTERM_PANE") || (termProgram === "wezterm") then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    else if (termProgram === "warpterminal") || env.contains("WARP_SESSION_ID") || env.contains(
        "WARP_TERMINAL_SESSION_UUID"
      )
    then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty))
    else if env.contains("ITERM_SESSION_ID") || (termProgram === "iterm.app") then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.ITerm2))
    else if env.contains(
        "WT_SESSION"
      ) || (termProgram === "vscode") || (termProgram === "alacritty") || (termProgram === "zed")
    then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)
    else if terminalEmulator === "jetbrains-jediterm" then
      TerminalCapabilities(trueColor = true, hyperlinks = false, images = None)
    else TerminalCapabilities(trueColor = trueColorHint, hyperlinks = false, images = None)
