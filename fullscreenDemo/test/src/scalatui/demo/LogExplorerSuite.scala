package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.scheduling.ManualTaskScheduler
import scalatui.testing.TUITestDriver
import scalatui.terminal.{
  KeyEventType, KeyModifiers, Terminal, TerminalInput, TerminalKey, VirtualTerminal
}

class LogExplorerSuite extends munit.FunSuite:
  test("100,000 deterministic events retain only the configured tail"):
    val model = LogExplorerModel(200)
    (0 until 100).foreach(_ => model.advance(1000))
    val state = model.snapshot
    assertEquals(state.generated, 100000L)
    assertEquals(state.evicted, 99800L)
    assertEquals(state.retained, 200)
    assertEquals(state.rows.head.id, 99801L)
    assertEquals(state.rows.last.id, 100000L)
    assertEquals(state.selected.map(_.id), Some(100000L))

  test("generation is reproducible regardless of batch partition"):
    val first  = LogExplorerModel(80)
    val second = LogExplorerModel(80)
    first.advance(100)
    (0 until 10).foreach(_ => second.advance(10))
    assertEquals(first.snapshot.rows, second.snapshot.rows)

  test("browse selection survives ingestion and falls back after eviction"):
    val model = LogExplorerModel(5)
    model.advance(5)
    model.move(-2)
    assertEquals(model.snapshot.selected.map(_.id), Some(3L))
    model.advance(2)
    assertEquals(model.snapshot.selected.map(_.id), Some(3L))
    model.advance(1)
    assertEquals(model.snapshot.selected.map(_.id), Some(4L))
    model.followLatest()
    assertEquals(model.snapshot.selected.map(_.id), Some(8L))

  test("filtering uses retained rows and has defined empty and navigation behavior"):
    val model = LogExplorerModel(40)
    model.advance(40)
    assert(model.setFilter("ERROR"))
    assertEquals(model.snapshot.rows.map(_.id), Vector(17L, 34L))
    model.move(Int.MinValue)
    assertEquals(model.snapshot.selected.map(_.id), Some(17L))
    model.move(Int.MaxValue)
    assertEquals(model.snapshot.selected.map(_.id), Some(34L))
    assert(model.setFilter("not present"))
    model.move(1)
    assertEquals(model.snapshot.selected, None)
    assert(model.setFilter(""))
    assertEquals(model.snapshot.selected.map(_.id), Some(1L))

  test("pause consumes no IDs and cancellation cannot be resumed"):
    val model = LogExplorerModel(5)
    model.advance(2)
    model.togglePause()
    model.advance(3)
    assertEquals(model.snapshot.generated, 2L)
    model.togglePause()
    model.advance(1)
    assertEquals(model.snapshot.generated, 3L)
    model.cancel()
    model.cancel()
    model.togglePause()
    model.togglePause()
    model.advance(10)
    assertEquals(model.snapshot.generated, 3L)
    assert(model.snapshot.cancelled)

  test("ASCII query folding preserves the committed UTF-16 limit on both runtimes"):
    val model = LogExplorerModel()
    assert(model.setFilter("\u0130" * 256))
    assertEquals(model.snapshot.filter.length, 256)
    assert(model.setFilter("InFo"))
    assertEquals(model.snapshot.filter, "info")

  test("limits reject invalid requests without changing committed data"):
    intercept[IllegalArgumentException](LogExplorerModel(0))
    intercept[IllegalArgumentException](LogExplorerModel(10001))
    val model = LogExplorerModel(1)
    model.advance(2)
    intercept[IllegalArgumentException](model.advance(-1))
    intercept[IllegalArgumentException](model.advance(1001))
    assert(model.setFilter("WARN"))
    assert(!model.setFilter("x" * 257))
    assertEquals(model.snapshot.filter, "warn")
    assertEquals(model.snapshot.generated, 2L)
    assertEquals(model.snapshot.evicted, 1L)

  private def withLogDriver(capacity: Int =
    200)(test: (LogExplorerDemo, TUITestDriver) => Unit): Unit =
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(90, 16)(terminal =>
      val demo = LogExplorerDemo(terminal, capacity)
      application = Some(demo)
      demo.tui
    ) { driver =>
      val demo = application.get
      try test(demo, driver)
      finally demo.model.cancel() // Application-owned source state, not a driver task scope.
    }

  private def key(terminal: VirtualTerminal, value: TerminalKey, ctrl: Boolean = false): Unit =
    terminal.sendInput(TerminalInput.Key(value, KeyModifiers(ctrl = ctrl)))

  test("typed UI filtering, navigation, steps, pause and cancel share one model"):
    withLogDriver(capacity = 60) { (demo, driver) =>
      driver.paste("ERROR", chunkBytes = 1)
      driver.key(TerminalKey.Enter)
      driver.key(TerminalKey.Up)
      assertEquals(demo.model.snapshot.selected.map(_.id), Some(17L))
      driver.assertContains("Selected #17")
      driver.key(TerminalKey.Character("n"), KeyModifiers(ctrl = true))
      assertEquals(demo.model.snapshot.generated, 60L)
      assertEquals(demo.model.snapshot.selected.map(_.id), Some(17L))
      driver.key(TerminalKey.Character("p"), KeyModifiers(ctrl = true))
      driver.key(TerminalKey.Character("n"), KeyModifiers(ctrl = true))
      assertEquals(demo.model.snapshot.generated, 60L)
      driver.key(TerminalKey.Character("p"), KeyModifiers(ctrl = true))
      driver.key(TerminalKey.Character("g"), KeyModifiers(ctrl = true))
      assertEquals(demo.model.snapshot.selected.map(_.id), Some(51L))
      driver.key(TerminalKey.Character("x"), KeyModifiers(ctrl = true))
      driver.key(TerminalKey.Character("n"), KeyModifiers(ctrl = true))
      assertEquals(demo.model.snapshot.generated, 60L)
      assert(demo.model.snapshot.cancelled)
    }

  test("virtual input deadlines deterministically advance and pause the reference app"):
    val clock       = ManualTaskScheduler()
    var application = Option.empty[LogExplorerDemo]
    TUITestDriver.withSession(90, 16, clock = Some(clock))(terminal =>
      val demo = LogExplorerDemo(terminal)
      application = Some(demo)
      clock.schedule(50) { key(terminal, TerminalKey.Character("n"), ctrl = true) }
      clock.schedule(100) { key(terminal, TerminalKey.Character("p"), ctrl = true) }
      clock.schedule(150) { key(terminal, TerminalKey.Character("n"), ctrl = true) }
      demo.tui
    ) { driver =>
      val demo = application.get
      try
        driver.advanceTimeBy(49)
        assertEquals(demo.model.snapshot.generated, 40L)
        driver.advanceTimeBy(1)
        assertEquals(demo.model.snapshot.generated, 60L)
        driver.assertContains("Logs 60/200")
        driver.advanceTimeTo(150)
        assertEquals(demo.model.snapshot.generated, 60L)
        assert(demo.model.snapshot.paused)
        driver.assertNoPendingTasks()
      finally demo.model.cancel()
    }
    assert(clock.isClosed)

  test("table integration reuses indexed data during selection and replaces it on ingestion"):
    withLogDriver() { (demo, driver) =>
      val initial = demo.table.data
      driver.key(TerminalKey.Up)
      assert(demo.table.data eq initial)
      assertEquals(demo.table.selection.map(_.id), demo.model.snapshot.selected.map(_.id.toString))
      driver.key(TerminalKey.Character("n"), KeyModifiers(ctrl = true))
      assert(!(demo.table.data eq initial))
      assertEquals(demo.table.selection.map(_.id), demo.model.snapshot.selected.map(_.id.toString))
      driver.assertContains("Selected #39")
    }

  test("release events do not advance the source and stop rejects later ticks"):
    val terminal = VirtualTerminal(60, 12)
    val demo     = LogExplorerDemo(terminal)
    try
      demo.tui.start()
      terminal.sendInput(TerminalInput.KeyEvent(
        TerminalKey.Character("n"),
        KeyModifiers(ctrl = true),
        KeyEventType.Release
      ))
      assertEquals(demo.model.snapshot.generated, 40L)
    finally demo.stop()
    demo.model.advance(20)
    assertEquals(demo.model.snapshot.generated, 40L)
    demo.stop()

  test("empty filtering and narrow resize preserve viewport bounds"):
    val terminal = VirtualTerminal(80, 14)
    val demo     = LogExplorerDemo(terminal)
    try
      demo.tui.start()
      "missing".foreach(c => key(terminal, TerminalKey.Character(c.toString)))
      key(terminal, TerminalKey.Enter)
      demo.tui.flushRender()
      assert(terminal.screenLines.exists(_.contains("No matching retained events")))
      Seq((12, 8), (1, 1), (90, 16)).foreach { dimensions =>
        terminal.resize(dimensions._1, dimensions._2)
        demo.tui.flushRender()
        assertEquals(terminal.screenLines.length, dimensions._2)
        assert(terminal.screenLines.forall(line => Ansi.visibleWidth(line) <= dimensions._1))
      }
    finally demo.stop()

  test("Escape cancels the source; explicit stop restores the virtual terminal"):
    val terminal = VirtualTerminal(80, 12)
    val demo     = LogExplorerDemo(terminal)
    try
      demo.tui.start()
      key(terminal, TerminalKey.Escape)
      assert(demo.model.snapshot.cancelled)
    finally demo.stop()
    assert(!terminal.isRunning)

  test("run cancels application state when terminal startup fails"):
    val failure  = new IllegalStateException("start failed")
    val terminal = new Terminal:
      override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = throw failure
      override def stop(): Unit                                                      = ()
      override def write(data: String): Unit                                         = ()
      override def columns: Int                                                      = 80
      override def rows: Int                                                         = 12
      override def moveBy(lines: Int): Unit                                          = ()
      override def hideCursor(): Unit                                                = ()
      override def showCursor(): Unit                                                = ()
      override def clearLine(): Unit                                                 = ()
      override def clearFromCursor(): Unit                                           = ()
      override def clearScreen(): Unit                                               = ()
    val demo     = LogExplorerDemo(terminal)
    val caught   = intercept[IllegalStateException](demo.run())
    assert(caught eq failure)
    assert(demo.model.snapshot.cancelled)
