import java.util.concurrent.Executors
import scala.util.Using
import scalatui.components.{DataTable, Input, TableColumn, TableData, Text}
import scalatui.core.{InputResult, TUIOptions, TUIScreenMode}
import scalatui.dsl.App
import scalatui.scheduling.ExecutorTaskScheduler
import scalatui.terminal.{KeyEventType, TerminalInput, TerminalKey}
import scalatui.terminal.jvm.SttyTerminal
import scalatui.syntax.Equality.*

/** JVM interactive counterpart; the deterministic DxApp also runs on Native. */
object DxInteractive:
  final case class Row(id: String, name: String)
  def main(args: Array[String]): Unit = Using.Manager { use =>
    val executor        = use(Executors.newSingleThreadScheduledExecutor())
    val scheduler       = ExecutorTaskScheduler(executor, error => throw error, 8)
    use(new AutoCloseable { def close(): Unit = scheduler.close() })
    var rows            = Vector(Row("a", "alpha"), Row("b", "界beta"))
    val filter          = Input()
    val table           = DataTable(TableData(rows, _.id), Vector(TableColumn[Row]("Name", 16, _.name)))
    var submitted       = ""
    def refresh(): Unit = table.setData(TableData(rows.filter(_.name.contains(submitted)), _.id))
    filter.onSubmit = query =>
      submitted = query
      refresh()
    val app             = App(TUIOptions(screenMode = TUIScreenMode.Alternate))
      .add(filter, table, Text("Enter: filter | Tab: focus | arrows: select | Esc: exit"))
      .focused(filter)
    val tui             = app.build(SttyTerminal())
    use(new AutoCloseable { def close(): Unit = tui.stop() })
    var first           = true
    tui.addInputListener {
      case TerminalInput.KeyEvent(TerminalKey.Tab, _, kind) if kind !== KeyEventType.Release    =>
        first = !first
        tui.setFocus(if first then filter else table)
        InputResult.Render
      case TerminalInput.KeyEvent(TerminalKey.Escape, _, kind) if kind !== KeyEventType.Release =>
        InputResult.Exit
      case _                                                                                    => InputResult.Ignored
    }
    tui.runWithSetup { runtime =>
      val scope = runtime.openApplicationScope(8, 1)
      var tick  = 0
      scope.scheduleEvery(scheduler, 1000L, 1000L) { _ =>
        tick += 1
        rows = Vector(rows.head, Row("b", s"界beta $tick"))
        refresh()
      }
    }
  }.get
