package scalatui.demo

import scalatui.terminal.jvm.SttyTerminal

/** JVM launcher for the shared fullscreen transcript example. */
@main def fullscreenJvmDemo(args: String*): Unit =
  args.toList match
    case Nil             =>
      val demo = FullscreenTranscriptDemo(SttyTerminal())
      try demo.run()
      finally demo.tui.stop()
    case List("--table") => DataTableDemo(SttyTerminal()).run()
    case _               => throw IllegalArgumentException("Usage: fullscreenJvmDemo [--table]")
