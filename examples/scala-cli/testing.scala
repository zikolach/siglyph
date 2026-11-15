#!/usr/bin/env -S scala-cli shebang
//> using scala 3.9.0
//> using nativeVersion 0.5.12
//> using files ../../core/src

import scalatui.components.Input
import scalatui.core.TUI
import scalatui.syntax.Equality.*
import scalatui.terminal.TerminalKey
import scalatui.testing.TUITestDriver

/** Source-checkout consumer test: no real TTY, MUnit or published unreleased artifact needed. */
@main def testApplication(): Unit =
  val input     = Input()
  var submitted = ""
  val snapshot  = TUITestDriver.withSession(40, 4)(terminal =>
    val tui = TUI.fullscreen(terminal, input)
    input.onSubmit = value => submitted = value
    tui.setFocus(input)
    tui
  ) { driver =>
    driver.typeText("hello ")
    driver.paste("界", chunkBytes = 1)
    driver.key(TerminalKey.Enter)
    assert(submitted === "hello 界")
    driver.assertContains("hello 界")
    driver.screen
  }
  assert(snapshot.columns === 40)
  println("Application test passed; managed terminal cleanup completed.")
