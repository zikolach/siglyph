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
    case _                           => throw IllegalArgumentException(
        "Usage: fullscreenNativeDemo [--log-explorer|--live-log-explorer|--table]"
      )
