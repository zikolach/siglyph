package scalatui.demo

import scalatui.core.TUI
import scalatui.core.TUIOptions
import scalatui.terminal.jvm.SttyTerminal
import scalatui.syntax.Equality.*

private def parseBoolean(value: String): Option[Boolean] =
  value.trim.toLowerCase match
    case "1" | "true" | "yes" | "on"  => Some(true)
    case "0" | "false" | "no" | "off" => Some(false)
    case _                            => None

private def parseHardwareCursorArgument(args: Seq[String]): Option[Boolean] =
  args.collectFirst {
    case "--hardware-cursor" | "--hardware-cursor-positioning"       => true
    case "--no-hardware-cursor" | "--no-hardware-cursor-positioning" => false
  }

private def printUsage(): Unit =
  println(
    "Usage: interactiveJvmDemo [--hardware-cursor|--hardware-cursor-positioning|--no-hardware-cursor]"
  )
  println("  --hardware-cursor         Enable hardware cursor positioning")
  println("  --no-hardware-cursor      Keep fake-cursor-only behavior")
  println("  Environment: SCALATUI_HARDWARE_CURSOR_POSITIONING=true|false")

@main def interactiveJvmDemo(args: String*): Unit =
  if args.exists(arg => arg === "-h" || arg === "--help") then
    printUsage()
  else
    val optionEnabled =
      parseHardwareCursorArgument(args)
        .orElse(sys.env.get("SCALATUI_HARDWARE_CURSOR_POSITIONING").flatMap(parseBoolean))
        .getOrElse(false)
    val tui           = TUI(SttyTerminal(), TUIOptions(hardwareCursorPositioning = optionEnabled))
    if optionEnabled then println("Demo: hardware cursor positioning enabled")
    InteractiveDemo.install(tui)
    tui.run()
