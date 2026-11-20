#!/usr/bin/env -S scala-cli shebang
//> using scala 3.10.1
//> using nativeVersion 0.5.12
//> using files ../../core/src

import scalatui.components.Text
import scalatui.core.{ApplicationSubmission, TUI}
import scalatui.syntax.Equality.*
import scalatui.testing.TUITestDriver

/** Portable deterministic update-scope example; no timer or background producer is implied. */
@main def testApplicationScope(): Unit =
  val status = Text("waiting", paddingX = 0, paddingY = 0)
  TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, status)) { driver =>
    val scope    = driver.tui.openApplicationScope(maxPending = 2)
    val admitted = scope.submit {
      status.text = "first update"
      scope.submit { status.text = "second update" }
    }
    assert(admitted === ApplicationSubmission.Accepted)
    driver.assertContains("second update")
    scope.close()
    assert(scope.isQuiescent)
    assert(scope.submit { status.text = "late result" } === ApplicationSubmission.Closed)
  }
  println("Application scope passed; late updates rejected and terminal restored.")
