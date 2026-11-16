package scalatui.demo

import scalatui.terminal.native.PosixTerminal

/** Scala Native launcher for the shared fullscreen transcript example. */
@main def fullscreenNativeDemo(args: String*): Unit =
  args.toList match
    case Nil                         =>
      val demo = FullscreenTranscriptDemo(PosixTerminal())
      try demo.run()
      finally demo.tui.stop()
    case List("--log-explorer")      => LogExplorerDemo(PosixTerminal()).run()
    case List("--live-log-explorer") => LogExplorerDemo(PosixTerminal()).runLive()
    case List("--table")             => DataTableDemo(PosixTerminal()).run()
    case List("--form", palette)     =>
      val theme = scalatui.theme.SemanticTheme.named(palette)
      ThemeFormDemo(PosixTerminal(), theme).tui.run()
    case List("--follow-file", path) =>
      val file = java.nio.file.Paths.get(path)
      LogExplorerDemo(PosixTerminal(), initialGenerated = 0).runFile(file)
    case _                           => throw IllegalArgumentException(
        "Usage: fullscreenNativeDemo [--log-explorer|--live-log-explorer|--table|--follow-file PATH|--form PALETTE]"
      )
