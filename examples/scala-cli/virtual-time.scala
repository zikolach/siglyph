#!/usr/bin/env -S scala-cli shebang
//> using scala 3.10.1
//> using nativeVersion 0.5.12
//> using files ../../core/src

import scalatui.components.Text
import scalatui.core.TUI
import scalatui.scheduling.ManualTaskScheduler
import scalatui.testing.TUITestDriver

@main def testVirtualTime(): Unit =
  val clock  = ManualTaskScheduler()
  val status = Text("waiting", paddingX = 0, paddingY = 0)
  TUITestDriver.withSession(40, 3, clock = Some(clock))(terminal =>
    TUI.fullscreen(terminal, status)
  ) { driver =>
    val scope     = driver.tui.openApplicationScope()
    val cancelled = clock.schedule(10) { throw new IllegalStateException("cancelled task ran") }
    assert(cancelled.cancel())
    clock.schedule(25) { scope.submit { status.text = "deadline reached" }; () }
    assert(driver.advanceTimeBy(24).reachedTarget)
    driver.assertContains("waiting")
    assert(driver.advanceTimeBy(1).reachedTarget)
    driver.assertContains("deadline reached")
    driver.assertNoPendingTasks()
  }
  assert(clock.isClosed)
  println("Virtual-time test passed; no pending callbacks or terminal resources remain.")
