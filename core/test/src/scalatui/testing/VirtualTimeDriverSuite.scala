package scalatui.testing

import scalatui.components.Text
import scalatui.core.{ApplicationSubmission, TUI}
import scalatui.scheduling.ManualTaskScheduler
import scalatui.terminal.{Terminal, TerminalInput, VirtualTerminal}

class VirtualTimeDriverSuite extends munit.FunSuite:
  test("virtual deadlines drive scoped UI updates and flush after each callback"):
    val clock = ManualTaskScheduler()
    val text  = Text("before", paddingX = 0, paddingY = 0)
    TUITestDriver.withSession(30, 3, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, text)
    ) { driver =>
      val scope = driver.tui.openApplicationScope()
      clock.schedule(50) { scope.submit { text.text = "after" }; () }
      assertEquals(driver.pendingScheduledTasks, 1)
      assert(driver.advanceTimeBy(49).reachedTarget)
      driver.assertContains("before")
      assertEquals(driver.advanceTimeBy(1).callbacksRun, 1)
      driver.assertContains("after")
      driver.assertNoPendingTasks()
      assertEquals(driver.stepsUsed, 2)
    }
    assert(clock.isClosed)

  test("clock callback budget returns unfinished work without an unbounded drain"):
    val clock = ManualTaskScheduler()
    TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, Text("ready"))
    ) { driver =>
      clock.schedule(10) { () }
      clock.schedule(20) { () }
      val result = driver.advanceTimeTo(100, maxCallbacks = 1)
      assert(!result.reachedTarget)
      assertEquals(result.nowMillis, 10L)
      assertEquals(driver.pendingScheduledTasks, 1)
      assert(driver.advanceTimeTo(result.targetMillis, maxCallbacks = 1).reachedTarget)
      driver.assertNoPendingTasks()
    }

  test("invalid advancement is rejected before consuming a script step"):
    val clock = ManualTaskScheduler()
    TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, Text("ready"))
    ) { driver =>
      intercept[IllegalArgumentException](driver.advanceTimeBy(-1))
      intercept[IllegalArgumentException](driver.advanceTimeBy(1, maxCallbacks = 0))
      assertEquals(driver.stepsUsed, 0)
      assertEquals(clock.nowMillis, 0L)
    }
    TUITestDriver.withSession(20, 2)(terminal => TUI.fullscreen(terminal, Text("ready"))) {
      driver =>
        intercept[IllegalStateException](driver.advanceTimeBy(1))
        assertEquals(driver.stepsUsed, 0)
    }

  test("fixture teardown reports and cancels pending tasks while restoring the terminal"):
    val clock       = ManualTaskScheduler()
    var terminalRef = Option.empty[VirtualTerminal]
    var ran         = false
    val error       = intercept[PendingScheduledTasksException] {
      TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
        terminalRef = Some(terminal)
        TUI.fullscreen(terminal, Text("ready"))
      ) { driver =>
        clock.schedule(100) { ran = true }
        intercept[AssertionError](driver.assertNoPendingTasks())
      }
    }
    assertEquals(error.pending, 1)
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)
    assert(terminalRef.exists(terminal => !terminal.isRunning))
    assert(!ran)

  test("body failures stay primary when clock teardown finds leaked callbacks"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalArgumentException("body failed")
    val caught  = intercept[IllegalArgumentException] {
      TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
        TUI.fullscreen(terminal, Text("ready"))
      ) { _ =>
        clock.schedule(10) { () }
        throw failure
      }
    }
    assert(caught eq failure)
    assert(caught.getSuppressed.exists(_.isInstanceOf[PendingScheduledTasksException]))
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)

  test("failed factory still closes its transferred clock"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalStateException("factory failed")
    clock.schedule(100) { () }
    val caught  = intercept[IllegalStateException] {
      TUITestDriver.withSession(clock = Some(clock))(_ => throw failure)(_ => ())
    }
    assert(caught eq failure)
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)
    assert(caught.getSuppressed.exists(_.isInstanceOf[PendingScheduledTasksException]))

  test("a scoped runtime failure stops advancement before later deadlines"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalStateException("update failed")
    var later   = false
    val caught  = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
        TUI.fullscreen(terminal, Text("ready"))
      ) { driver =>
        val scope = driver.tui.openApplicationScope()
        clock.schedule(10) { scope.submit { throw failure }; () }
        clock.schedule(20) { later = true }
        driver.advanceTimeTo(100)
      }
    }
    assert(caught eq failure)
    assertEquals(clock.nowMillis, 10L)
    assert(!later)
    assert(clock.isClosed)
    assert(caught.getSuppressed.exists(_.isInstanceOf[PendingScheduledTasksException]))

  test("unobserved direct runtime failure outranks a pending-task leak"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalStateException("direct update failed")
    val caught  = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
        TUI.fullscreen(terminal, Text("ready"))
      ) { driver =>
        clock.schedule(100) { () }
        driver.tui.openApplicationScope().submit { throw failure }
      }
    }
    assert(caught eq failure)
    assert(caught.getSuppressed.exists(_.isInstanceOf[PendingScheduledTasksException]))
    assert(clock.isClosed)

  test("terminal cleanup failure does not prevent cancelling the owned clock"):
    val clock   = ManualTaskScheduler()
    val failure = new IllegalStateException("backend stop failed")
    val caught  = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
        val proxy = new Terminal:
          override def start(input: TerminalInput => Unit, resize: () => Unit): Unit =
            terminal.start(input, resize)
          override def stop(): Unit                                                  = { terminal.stop(); throw failure }
          override def write(value: String): Unit                                    = terminal.write(value)
          override def columns: Int                                                  = terminal.columns
          override def rows: Int                                                     = terminal.rows
          override def moveBy(lines: Int): Unit                                      = terminal.moveBy(lines)
          override def hideCursor(): Unit                                            = terminal.hideCursor()
          override def showCursor(): Unit                                            = terminal.showCursor()
          override def clearLine(): Unit                                             = terminal.clearLine()
          override def clearFromCursor(): Unit                                       = terminal.clearFromCursor()
          override def clearScreen(): Unit                                           = terminal.clearScreen()
        TUI.fullscreen(proxy, Text("ready"))
      ) { _ => clock.schedule(100) { () } }
    }
    assert(caught eq failure)
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)
    assert(caught.getSuppressed.exists(_.isInstanceOf[PendingScheduledTasksException]))

  test("late virtual callbacks cannot publish through a scope from an older session"):
    val clock = ManualTaskScheduler()
    TUITestDriver.withSession(20, 2, clock = Some(clock))(terminal =>
      TUI.fullscreen(terminal, Text("ready"))
    ) { driver =>
      val old    = driver.tui.openApplicationScope()
      var ran    = false
      var result = Option.empty[ApplicationSubmission]
      clock.schedule(50) { result = Some(old.submit { ran = true }) }
      driver.tui.stop()
      driver.tui.start()
      val fresh  = driver.tui.openApplicationScope()
      assertEquals(fresh.submit { () }, ApplicationSubmission.Accepted)
      driver.advanceTimeBy(50)
      assertEquals(result, Some(ApplicationSubmission.Closed))
      assert(!ran)
      driver.assertNoPendingTasks()
    }
