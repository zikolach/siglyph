package scalatui.dsl

import scalatui.components.{DataTable, Input, TableColumn, TableData}
import scalatui.core.{ApplicationSubmission, TUIOptions, TUIScreenMode}
import scalatui.scheduling.ManualTaskScheduler
import scalatui.testing.TUITestDriver
import scalatui.terminal.VirtualTerminal

final class AppSuite extends munit.FunSuite:
  private final class CountingTerminal(failOnStart: Option[Throwable] = None)
      extends scalatui.terminal.Terminal:
    private val delegate = VirtualTerminal()
    var starts           = 0
    export delegate.{
      stop, write, columns, rows, moveBy, hideCursor, showCursor, clearLine, clearFromCursor,
      clearScreen, isRunning
    }
    override def start(
        onInput: scalatui.terminal.TerminalInput => Unit,
        onResize: () => Unit
    ): Unit              =
      starts += 1
      assertEquals(starts, 1, "run must never restart after setup")
      failOnStart.foreach(throw _)
      delegate.start(onInput, onResize)

  test("runWithSetup does not restart after setup stops or requests exit"):
    for stop <- Vector(true, false) do
      val terminal = CountingTerminal()
      val tui      = App().build(terminal)
      tui.runWithSetup(runtime => if stop then runtime.stop() else runtime.requestExit())
      assertEquals(terminal.starts, 1)
      assert(!terminal.isRunning)

  test("runWithSetup preserves a worker failure recorded before waiting"):
    val terminal = CountingTerminal()
    val tui      = App().build(terminal)
    val expected = new IllegalStateException("fast worker failure")
    val actual   = intercept[IllegalStateException] {
      tui.runWithSetup { runtime =>
        val scope  = runtime.openApplicationScope(1, 1)
        val worker = new Thread(() => {
          try { scope.submit { throw expected }; () }
          catch case _: IllegalStateException => ()
        })
        worker.start()
        worker.join()
      }
    }
    assert(actual eq expected)
    assertEquals(terminal.starts, 1)
    assert(!terminal.isRunning)

  test("runWithSetup skips setup after startup failure"):
    val expected = new IllegalStateException("startup failure")
    val terminal = CountingTerminal(Some(expected))
    val actual   = intercept[IllegalStateException] {
      App().build(terminal).runWithSetup(_ => fail("setup ran after failed startup"))
    }
    assert(actual eq expected)
    assertEquals(terminal.starts, 1)

  test("typed composition preserves configuration and does not start the backend"):
    val input      = Input()
    val base       = App()
    val configured = base.add(input).focused(input)
    val terminal   = VirtualTerminal()
    val tui        = configured.build(terminal)
    assertEquals(base.children.size, 0)
    assertEquals(tui.children, Vector(input))
    assert(!terminal.isRunning)
    intercept[IllegalArgumentException](base.focused(input).build(terminal))

  test("table filtering and scheduled delivery retain IDs and close stale scopes"):
    val rows  = Vector("alpha", "界beta")
    val table = DataTable(
      TableData(rows, identity[String]),
      Vector(TableColumn[String]("Name", 16, identity))
    )
    val input = Input()
    input.onSubmit =
      query => table.setData(TableData(rows.filter(_.contains(query)), identity[String]))
    val clock = ManualTaskScheduler()
    var stale = Option.empty[scalatui.core.ApplicationScope]
    val app   = App(TUIOptions(screenMode = TUIScreenMode.Alternate)).add(input, table).focused(input)
    TUITestDriver.withSession(clock = Some(clock))(app.build) { driver =>
      table.selectId("界beta")
      input.onSubmit("beta")
      assertEquals(table.selection.map(_.id), Some("界beta"))
      input.onSubmit("absent")
      assert(table.selection.isEmpty)
      val scope = driver.tui.openApplicationScope(1, 1)
      stale = Some(scope)
      scope.schedule(clock, 1L) { table.setData(TableData(rows, identity[String])) }
      driver.advanceTimeBy(1L)
      assertEquals(table.data.size, 2)
      driver.resize(12, 8)
      driver.assertContains("Name")
      scope.close()
      assertEquals(scope.submit(fail("closed callback executed")), ApplicationSubmission.Closed)
    }
    assert(stale.get.isClosed)
    assert(clock.isClosed)

  test("bounded admission and restart reject stale work and cancel timers"):
    val terminal = VirtualTerminal()
    val tui      = App().build(terminal)
    val clock    = ManualTaskScheduler()
    tui.start()
    try
      val scope     = tui.openApplicationScope(1, 1)
      var delivered = 0
      assertEquals(
        scope.submit {
          assertEquals(scope.submit { delivered += 1 }, ApplicationSubmission.Accepted)
          assertEquals(scope.submit { fail("full callback executed") }, ApplicationSubmission.Full)
        },
        ApplicationSubmission.Accepted
      )
      assertEquals(delivered, 1)
      val cancelled = scope.schedule(clock, 1L) { fail("cancelled callback executed") }
      assert(cancelled.cancel())
      val stale     = scope.schedule(clock, 2L) { fail("stale timer executed") }
      tui.stop()
      tui.start()
      assertEquals(scope.submit { fail("stale scope executed") }, ApplicationSubmission.Closed)
      clock.advanceBy(3L)
      assert(stale.isCancelled)
      assertEquals(clock.pendingCount, 0)
    finally
      tui.stop()
      clock.close()
