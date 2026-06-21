package scalatui.terminal

import scalatui.syntax.Equality.*

final case class TerminalCapabilities(
    trueColor: Boolean,
    hyperlinks: Boolean,
    images: Option[ImageProtocol]
) derives CanEqual

sealed trait ImageProtocol derives CanEqual
object ImageProtocol:
  case object Kitty  extends ImageProtocol
  case object ITerm2 extends ImageProtocol

object TerminalCapabilities:
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
      ) || (termProgram === "vscode") || (termProgram === "alacritty")
    then
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)
    else if terminalEmulator === "jetbrains-jediterm" then
      TerminalCapabilities(trueColor = true, hyperlinks = false, images = None)
    else TerminalCapabilities(trueColor = trueColorHint, hyperlinks = false, images = None)
