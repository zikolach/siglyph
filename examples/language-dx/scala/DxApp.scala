import scalatui.components.{DataTable, Input, TableColumn, TableData}
import scalatui.core.{ApplicationSubmission, TUIOptions, TUIScreenMode}
import scalatui.dsl.App
import scalatui.scheduling.ManualTaskScheduler
import scalatui.testing.TUITestDriver
import scalatui.terminal.TerminalKey
import scalatui.theme.SemanticTheme

object DxApp:
  final case class Row(id: String, name: String)
  def main(args: Array[String]): Unit =
    val rows                                      = Vector(Row("a", "alpha"), Row("b", "界beta"))
    def data(values: Vector[Row]): TableData[Row] = TableData(values, _.id)
    val table                                     = DataTable(
      data(rows),
      Vector(TableColumn[Row]("Name", 16, _.name)),
      theme = SemanticTheme.Monochrome
    )
    val filter                                    = Input()
    var submitted                                 = ""
    filter.onSubmit = query =>
      submitted = query
      table.setData(data(rows.filter(_.name.contains(query))))
    val app                                       =
      App(TUIOptions(screenMode = TUIScreenMode.Alternate)).add(filter, table).focused(filter)
    val clock                                     = ManualTaskScheduler(8)
    TUITestDriver.withSession(columns = 40, rows = 8, clock = Some(clock))(app.build) { driver =>
      table.selectId("b")
      driver.paste("beta")
      driver.key(TerminalKey.Enter)
      assert(table.selection.exists(_.id.equals("b")))
      table.setData(data(Vector.empty))
      assert(table.selection.isEmpty)
      val scope = driver.tui.openApplicationScope(1, 1)
      filter.setValue("")
      driver.key(TerminalKey.Enter)
      driver.typeText("absent")
      val task  =
        scope.schedule(clock, 1L) { table.setData(data(rows.filter(_.name.contains(submitted)))) }
      driver.advanceTimeBy(1L, 8)
      assert(table.data.size.equals(2), "unsubmitted filter changed rows")
      driver.key(TerminalKey.Enter)
      assert(table.data.size.equals(0), "submitted filter ignored")
      driver.resize(12, 8)
      driver.assertContains("Name")
      scope.close()
      assert(scope.submit(sys.error("stale update")).equals(ApplicationSubmission.Closed))
      assert(task.isDone)
    }
    assert(clock.isClosed)
    println("Scala DX consumer passed")
