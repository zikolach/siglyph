package scalatui.demo

import scalatui.terminal.native.PosixTerminal

/** Scala Native launcher for the shared fullscreen transcript example. */
@main def fullscreenNativeDemo(): Unit =
  val demo = FullscreenTranscriptDemo(PosixTerminal())
  try demo.run()
  finally demo.tui.stop()
