package scalatui.demo

import scalatui.terminal.jvm.SttyTerminal

/** JVM launcher for the shared fullscreen transcript example. */
@main def fullscreenJvmDemo(args: String*): Unit =
  args.toList match
    case Nil                         =>
      val demo = FullscreenTranscriptDemo(SttyTerminal())
      try demo.run()
      finally demo.tui.stop()
    case List("--log-explorer")      => LogExplorerDemo(SttyTerminal()).run()
    case List("--live-log-explorer") => LogExplorerDemo(SttyTerminal()).runLive()
    case List("--table")             => DataTableDemo(SttyTerminal()).run()
    case List("--form", palette)     =>
      val theme = scalatui.theme.SemanticTheme.named(palette)
      ThemeFormDemo(SttyTerminal(), theme).tui.run()
    case List("--follow-file", path) =>
      val file = java.nio.file.Paths.get(path)
      LogExplorerDemo(SttyTerminal(), initialGenerated = 0).runFile(file)
    case _                           => throw IllegalArgumentException(
        "Usage: fullscreenJvmDemo [--log-explorer|--live-log-explorer|--table|--follow-file PATH|--form PALETTE]"
      )
