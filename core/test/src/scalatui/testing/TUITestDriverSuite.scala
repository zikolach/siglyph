package scalatui.testing

import scalatui.components.{Input, StackEntry, Text, VStack}
import scalatui.core.{Component, ComponentRender, TUI}
import scalatui.terminal.{Terminal, TerminalInput, TerminalKey, VirtualTerminal}

class TUITestDriverSuite extends munit.FunSuite:
  private def inputTui(terminal: VirtualTerminal, input: Input): TUI =
    val tui = TUI.fullscreen(terminal, input)
    tui.setFocus(input)
    tui

  test("grapheme typing and byte-fragmented paste preserve application text"):
    val input = Input()
    TUITestDriver.withSession(40, 3)(terminal => inputTui(terminal, input)) { driver =>
      driver.typeText("e\u0301👩‍💻")
      assertEquals(driver.stepsUsed, 2)
      assertEquals(input.value, "e\u0301👩‍💻")
      driver.paste("界🐱", chunkBytes = 1)
      assertEquals(input.value, "e\u0301👩‍💻界🐱")
      assertEquals(driver.stepsUsed, 11)
      driver.assertContains("界🐱")
    }

  test("text and paste budget admission is atomic"):
    val input = Input()
    TUITestDriver.withSession(20, 2, TUITestLimits(maxSteps = 3))(terminal =>
      inputTui(terminal, input)
    ) { driver =>
      intercept[IllegalStateException](driver.typeText("four"))
      intercept[IllegalStateException](driver.paste("é", chunkBytes = 1))
      assertEquals(input.value, "")
      assertEquals(driver.stepsUsed, 0)
      driver.paste("a", chunkBytes = 1)
      assertEquals(input.value, "a")
      assertEquals(driver.stepsUsed, 3)
      intercept[IllegalStateException](driver.key(TerminalKey.Enter))
    }

  test("byte limits, printable text and resize are checked before delivery"):
    val input = Input()
    TUITestDriver.withSession(10, 2, TUITestLimits(maxInputBytes = 3, maxCells = 20))(terminal =>
      inputTui(terminal, input)
    ) { driver =>
      intercept[IllegalArgumentException](driver.typeText("🐱"))
      intercept[IllegalArgumentException](driver.typeText("\n"))
      intercept[IllegalArgumentException](driver.paste("x", chunkBytes = 0))
      intercept[IllegalArgumentException](driver.resize(11, 2))
      intercept[IllegalArgumentException](driver.resize(-1, 2))
      assertEquals(driver.stepsUsed, 0)
      assertEquals(driver.screen.columns, 10)
      driver.typeText("界")
      assertEquals(input.value, "界")
    }

  test("identical scripts produce identical immutable snapshots"):
    def scenario(): Vector[ScreenSnapshot] =
      val input = Input()
      TUITestDriver.withSession(20, 3)(terminal => inputTui(terminal, input)) { driver =>
        driver.typeText("hello")
        val first = driver.screen
        driver.paste("界", chunkBytes = 1)
        driver.resize(30, 4)
        Vector(first, driver.screen)
      }
    assertEquals(scenario(), scenario())

  test("geometry limits fail before factory invocation"):
    var factories = 0
    intercept[IllegalArgumentException] {
      TUITestDriver.withSession(10000, 10000)(terminal =>
        factories += 1
        TUI(terminal)
      )(_ => ())
    }
    assertEquals(factories, 0)
    intercept[IllegalArgumentException](TUITestLimits(maxSteps = 0))
    intercept[IllegalArgumentException](TUITestLimits(maxOutputChars = -1))

  test("screen snapshots preserve Unicode and normalize trailing ASCII blanks"):
    TUITestDriver.withSession(8, 2)(terminal =>
      TUI.fullscreen(terminal, Text("界e\u0301", paddingX = 0, paddingY = 0))
    ) { driver =>
      val snapshot = driver.screen
      driver.assertScreen(ScreenSnapshot(8, 2, Vector("界e\u0301  ", "")))
      driver.resize(10, 3)
      assertEquals(snapshot.columns, 8)
      assertEquals(snapshot.rows, 2)
      assertEquals(driver.screen.rows, 3)
      assertEquals(driver.screen.columns, 10)
      driver.resize(0, 0)
      assertEquals(driver.screen, ScreenSnapshot(0, 0, Vector.empty))
    }

  test("mismatch reports geometry and only bounded differing rows"):
    val actual   = ScreenSnapshot(500, 40, Vector.fill(40)("a" * 500))
    val expected = ScreenSnapshot(500, 40, Vector.fill(40)("b" * 500))
    val error    = intercept[AssertionError](actual.assertMatches(expected))
    assert(error.getMessage.contains("expected 500x40, actual 500x40"))
    assert(error.getMessage.contains("row 0"))
    assert(!error.getMessage.contains("row 6"))
    assert(error.getMessage.length < 3000)
    val absent   = intercept[AssertionError](actual.assertContains("missing\u001b[2J"))
    assert(!absent.getMessage.contains("\u001b"))
    assert(absent.getMessage.contains("\\u001B"))
    intercept[IllegalArgumentException](actual.assertContains(""))
    intercept[IllegalArgumentException](ScreenSnapshot(1, 1, Vector("界")))

  test("mismatch previews include differences beyond a long common prefix"):
    val prefix = "a" * 300
    val error  = intercept[AssertionError] {
      ScreenSnapshot(400, 1, Vector(prefix + "ACTUAL"))
        .assertMatches(ScreenSnapshot(400, 1, Vector(prefix + "EXPECTED")))
    }
    assert(error.getMessage.contains("offset 300"))
    assert(error.getMessage.contains("ACTUAL"))
    assert(error.getMessage.contains("EXPECTED"))
    assertEquals(ScreenSnapshot.preview("a" * 159 + "🐱"), "a" * 159 + "…")

  test("close retains action diagnostics separately from restoration writes"):
    val input    = Input()
    var held     = Option.empty[TUITestDriver]
    var recorded = ""
    TUITestDriver.withSession(30, 3)(terminal => inputTui(terminal, input)) { driver =>
      held = Some(driver)
      driver.typeText("a")
      recorded = driver.lastOutput
    }
    assertEquals(held.get.lastOutput, recorded)
    assert(held.get.cleanupOutput.contains("\u001b[?1049l"))
    assert(!held.get.cleanupOutputTruncated)

  test("focus changes work with the existing TUI API"):
    val first  = Input()
    val second = Input()
    TUITestDriver.withSession(20, 4)(terminal =>
      val tui = TUI.fullscreen(terminal, VStack(Seq(StackEntry(first), StackEntry(second))))
      tui.setFocus(first)
      tui
    ) { driver =>
      driver.typeText("a")
      driver.tui.setFocus(second)
      driver.typeText("b")
      assertEquals(first.value, "a")
      assertEquals(second.value, "b")
    }

  test("recording is capped per action and does not retain VirtualTerminal history"):
    var terminalRef = Option.empty[VirtualTerminal]
    TUITestDriver.withSession(40, 3, TUITestLimits(maxOutputChars = 4))(terminal =>
      terminalRef = Some(terminal)
      TUI.fullscreen(terminal, Text("long output", paddingX = 0))
    ) { driver =>
      assert(driver.outputTruncated)
      assert(driver.lastOutput.length <= 4)
      (0 until 20).foreach { _ =>
        driver.tui.requestRender(force = true)
        driver.flush()
        assert(driver.lastOutput.length <= 4)
        assert(terminalRef.exists(_.writes.isEmpty))
      }
    }

  test("managed completion closes the driver and rejects later actions"):
    val driver = TUITestDriver.withSession(20, 2)(terminal =>
      TUI.fullscreen(terminal, Text("ready"))
    )(identity)
    assert(driver.isClosed)
    driver.close()
    intercept[IllegalStateException](driver.flush())

  test("input callback failures surface without entering the blocking run loop"):
    val failure = new IllegalStateException("callback failed")
    TUITestDriver.withSession(20, 2)(terminal =>
      val tui = TUI.fullscreen(terminal, Text("ready"))
      tui.addInputListener(_ => throw failure)
      tui
    ) { driver =>
      val caught = intercept[IllegalStateException](driver.key(TerminalKey.Character("x")))
      assert(caught eq failure)
      intercept[IllegalStateException](driver.flush())
      assertEquals(driver.screen.rows, 2)
    }

  test("render failures poison actions but still permit managed cleanup"):
    var explode   = false
    val failure   = new IllegalStateException("render failed")
    val component = new Component:
      override def render(width: Int): ComponentRender =
        if explode then throw failure else ComponentRender.text("ready")
    TUITestDriver.withSession(20, 2)(terminal => TUI.fullscreen(terminal, component)) { driver =>
      explode = true
      driver.tui.requestRender()
      val caught = intercept[IllegalStateException](driver.flush())
      assert(caught eq failure)
      intercept[IllegalStateException](driver.key(TerminalKey.Enter))
    }

  test("unobserved failures from direct TUI work are reported during managed cleanup"):
    var terminalRef = Option.empty[VirtualTerminal]
    val failure     = new IllegalStateException("direct callback failed")
    val caught      = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2)(terminal =>
        terminalRef = Some(terminal)
        val tui = TUI.fullscreen(terminal, Text("ready"))
        tui.addInputListener(_ => throw failure)
        tui
      ) { _ => terminalRef.foreach(_.sendInput(TerminalInput.Key(TerminalKey.Enter))) }
    }
    assert(caught eq failure)

  private final class StopFailureTerminal(
      delegate: VirtualTerminal,
      shouldFail: () => Boolean,
      error: Throwable
  ) extends Terminal:
    override def start(input: TerminalInput => Unit, resize: () => Unit): Unit =
      delegate.start(input, resize)
    override def stop(): Unit                                                  =
      delegate.stop()
      if shouldFail() then throw error
    override def write(value: String): Unit                                    = delegate.write(value)
    override def columns: Int                                                  = delegate.columns
    override def rows: Int                                                     = delegate.rows
    override def moveBy(lines: Int): Unit                                      = delegate.moveBy(lines)
    override def hideCursor(): Unit                                            = delegate.hideCursor()
    override def showCursor(): Unit                                            = delegate.showCursor()
    override def clearLine(): Unit                                             = delegate.clearLine()
    override def clearFromCursor(): Unit                                       = delegate.clearFromCursor()
    override def clearScreen(): Unit                                           = delegate.clearScreen()

  test("body failure remains primary and independent stop failure is suppressed"):
    val bodyFailure = new IllegalArgumentException("body failed")
    val stopFailure = new IllegalStateException("stop failed")
    val caught      = intercept[IllegalArgumentException] {
      TUITestDriver.withSession(20, 2)(terminal =>
        TUI.fullscreen(StopFailureTerminal(terminal, () => true, stopFailure), Text("ready"))
      )(_ => throw bodyFailure)
    }
    assert(caught eq bodyFailure)
    assert(caught.getSuppressed.exists(_ eq stopFailure))

  test("failed cleanup is visible and can be explicitly retried"):
    var shouldFail = true
    var held       = Option.empty[TUITestDriver]
    val failure    = new IllegalStateException("stop failed")
    val caught     = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2)(terminal =>
        TUI.fullscreen(StopFailureTerminal(terminal, () => shouldFail, failure), Text("ready"))
      ) { driver => held = Some(driver) }
    }
    assert(caught eq failure)
    assert(!held.get.isClosed)
    shouldFail = false
    held.get.close()
    assert(held.get.isClosed)

  test("startup and factory failures do not skip terminal cleanup"):
    val failure     = new IllegalStateException("startup failed")
    var terminalRef = Option.empty[VirtualTerminal]
    val component   = new Component:
      override def render(width: Int): ComponentRender = throw failure
    val caught      = intercept[IllegalStateException] {
      TUITestDriver.withSession(20, 2)(terminal =>
        terminalRef = Some(terminal)
        TUI.fullscreen(terminal, component)
      )(_ => fail("body must not run"))
    }
    assert(caught eq failure)
    assert(terminalRef.exists(terminal => !terminal.isRunning))
    intercept[IllegalStateException] {
      TUITestDriver.withSession()(terminal => throw failure)(_ => ())
    }

  test("a factory using a different terminal is rejected and its TUI is stopped"):
    val wrong = VirtualTerminal()
    intercept[IllegalArgumentException] {
      TUITestDriver.withSession()(_ => TUI.fullscreen(wrong, Text("wrong")))(_ => ())
    }
    assert(!wrong.isRunning)

  test("reentrant actions are rejected and the outer callback can handle that rejection"):
    var held = Option.empty[TUITestDriver]
    TUITestDriver.withSession(20, 2)(terminal =>
      val tui = TUI.fullscreen(terminal, Text("ready"))
      tui.addInputListener { _ =>
        intercept[IllegalStateException](held.get.flush())
        scalatui.core.InputResult.Ignored
      }
      tui
    ) { driver =>
      held = Some(driver)
      driver.key(TerminalKey.Enter)
      assertEquals(driver.stepsUsed, 1)
    }

  test("cross-thread actions are rejected without consuming steps"):
    TUITestDriver.withSession(20, 2)(terminal => TUI.fullscreen(terminal, Text("ready"))) {
      driver =>
        val result = new java.util.concurrent.atomic.AtomicReference[Throwable]()
        val worker = new Thread(() =>
          try driver.flush()
          catch case error: Throwable => result.set(error)
        )
        worker.start()
        worker.join(5000L)
        assert(!worker.isAlive)
        assert(result.get().isInstanceOf[IllegalStateException])
        assertEquals(driver.stepsUsed, 0)
    }
