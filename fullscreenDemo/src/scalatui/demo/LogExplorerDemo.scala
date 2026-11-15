package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.components.{
  DataTable, Input, ScrollView, ScrollbarMode, StackEntry, StackEntryOptions, TableColumn,
  TableData, VStack
}
import scalatui.core.{Component, ComponentRender, InputResult, TUI}
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyEventType, KeyModifiers, Terminal, TerminalInput, TerminalKey}

/**
 * Step-driven discovery prototype for roadmap #93; deliberately not an async runtime or table API.
 */
final class LogExplorerDemo private (
    val tui: TUI,
    private[demo] val model: LogExplorerModel,
    private[demo] val table: DataTable[LogEvent]
):
  def run(): Unit =
    try tui.run()
    finally model.cancel()

  def stop(): Unit =
    model.cancel()
    tui.stop()

private final class LogExplorerLines(lines: () => Vector[String]) extends Component:
  override def render(width: Int): ComponentRender =
    ComponentRender.text(lines().map(Ansi.truncateToWidth(_, math.max(0, width), "")))

object LogExplorerDemo:
  def apply(terminal: Terminal, capacity: Int = 200): LogExplorerDemo =
    val model               = LogExplorerModel(capacity)
    model.advance(40)
    var notice              = "Up/Down/PgUp/PgDn browse | Enter filter | Esc/Ctrl+C quit"
    val input               = Input()
    val initial             = model.snapshot
    var publishedGeneration = initial.generated
    var publishedFilter     = initial.filter
    val table               = DataTable(
      TableData(initial.rows, (event: LogEvent) => event.id.toString),
      Vector(
        TableColumn[LogEvent]("ID", 7, _.id.toString),
        TableColumn[LogEvent]("Level", 5, _.level),
        TableColumn[LogEvent]("Service", 7, _.service),
        TableColumn[LogEvent]("Message", 60, _.message)
      )
    )
    initial.selected.foreach(event => table.selectId(event.id.toString))
    val view                = ScrollView(table, followEnd = true, scrollbar = ScrollbarMode.Automatic)
    val header              = LogExplorerLines(() =>
      val state  = model.snapshot
      val source =
        if state.cancelled then "cancelled" else if state.paused then "paused" else "ready"
      Vector(
        s"Logs ${state.retained}/$capacity | evicted ${state.evicted} | $source | filter: ${state.filter}"
      )
    )
    val detail              = LogExplorerLines(() =>
      model.snapshot.selected match
        case Some(event) =>
          Vector(s"Selected #${event.id}: ${event.level} / ${event.service}", event.message)
        case None        => Vector("No matching retained events", notice)
    )
    val footer              = LogExplorerLines(() =>
      Vector(
        "Ctrl+N step | Ctrl+P pause | Ctrl+G follow | Ctrl+X cancel",
        notice
      )
    )
    val root                = VStack(Seq(
      StackEntry(header, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1))),
      StackEntry(input, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1))),
      StackEntry(view, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(detail, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(2))),
      StackEntry(footer, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(2)))
    ))
    val tui                 = TUI.fullscreen(terminal, root)
    def refresh(): Unit     =
      val state = model.snapshot
      if (state.generated !== publishedGeneration) || (state.filter !== publishedFilter) then
        table.setData(TableData(state.rows, (event: LogEvent) => event.id.toString))
        publishedGeneration = state.generated
        publishedFilter = state.filter
      state.selected.foreach(event => table.selectId(event.id.toString))
      view.invalidateSearchDocument()
      if state.following then view.jumpToEnd()
      else
        view.jumpToStart()
        val index = state.rows.indexWhere(event => state.selected.exists(_.id === event.id))
        view.scrollTo(math.max(0, index + 1))
      tui.requestRender()

    input.onSubmit = value =>
      notice = if model.setFilter(value) then
        "Up/Down/PgUp/PgDn browse | Enter filter | Esc/Ctrl+C quit"
      else "Filter rejected: maximum 256 UTF-16 units; previous filter retained"
      refresh()
    tui.addInputListener {
      case TerminalInput.KeyEvent(_, _, KeyEventType.Release) => InputResult.Ignored
      case TerminalInput.Key(key, modifiers)                  =>
        val handled =
          if modifiers === KeyModifiers(ctrl = true) then
            key match
              case TerminalKey.Character("n") => model.advance(20); true
              case TerminalKey.Character("p") => model.togglePause(); true
              case TerminalKey.Character("g") => model.followLatest(); true
              case TerminalKey.Character("x") => model.cancel(); true
              case _                          => false
          else if modifiers.isEmpty then
            key match
              case TerminalKey.Up       => model.move(-1); true
              case TerminalKey.Down     => model.move(1); true
              case TerminalKey.PageUp   => model.move(-10); true
              case TerminalKey.PageDown => model.move(10); true
              case _                    => false
          else false
        if handled then
          refresh()
          InputResult.Render
        else if (
            (key === TerminalKey.Escape && modifiers.isEmpty) ||
            (key === TerminalKey.Character("c") && modifiers === KeyModifiers(ctrl = true))
          )
        then
          model.cancel()
          InputResult.Exit
        else InputResult.Ignored
      case _                                                  => InputResult.Ignored
    }
    tui.setFocus(input)
    new LogExplorerDemo(tui, model, table)
