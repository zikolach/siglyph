package scalatui.demo

import scalatui.terminal.jvm.SttyTerminal

/** JVM launcher for the shared fullscreen transcript example. */
@main def fullscreenJvmDemo(): Unit =
  val demo = FullscreenTranscriptDemo(SttyTerminal())
  try demo.run()
  finally demo.tui.stop()
