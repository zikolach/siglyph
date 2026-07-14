#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.5.0
//> using dep io.github.zikolach::siglyph-terminal-jvm:0.5.0
//> using dep io.github.zikolach::siglyph-image:0.5.0

import java.nio.file.Paths

import scalatui.components.*
import scalatui.core.TUI
import scalatui.image.{Image, ImageSource}
import scalatui.terminal.TerminalCapabilities
import scalatui.terminal.jvm.SttyTerminal

@main def imageDemo(path: String): Unit =
  val source = ImageSource.fromFile(Paths.get(path)) match
    case Right(value) => value
    case Left(error)  => throw IllegalArgumentException(error.message)

  val capabilities = TerminalCapabilities.detect()
  val tui          = TUI(SttyTerminal())
  tui.exitsOnEscape = true

  tui.addChild(Text("siglyph image demo"))
  tui.addChild(Text(s"Detected image protocol: ${capabilities.images}"))
  tui.addChild(
    Text("Supported terminals render the image; unsupported terminals render fallback text.")
  )
  tui.addChild(Spacer(1))
  // Image returns reserved ordinary rows plus a typed control. TUI encodes it at final output.
  tui.addChild(Image(source.payload, source.dimensions, capabilities))
  tui.addChild(Spacer(1))
  tui.addChild(Text("This line should appear below the image. Esc or Ctrl+C exits."))
  tui.run()
