package scalatui.demo

import scalatui.components.{
  DataTable, ScrollView, ScrollbarMode, StackEntry, StackEntryOptions, TableColumn, TableData, Text,
  VStack
}
import scalatui.core.TUI
import scalatui.terminal.Terminal

/** Runnable generic table fixture; building its indexed 100,000-row model is intentionally O(n). */
object DataTableDemo:
  private final case class Entry(id: Int, name: String, score: Int)

  def apply(terminal: Terminal): TUI =
    val rows     = Vector.tabulate(100000)(index => Entry(index, s"item-$index", index % 100))
    var viewport = Option.empty[ScrollView]
    val table    = DataTable(
      TableData(rows, (entry: Entry) => entry.id.toString),
      Vector(
        TableColumn[Entry]("ID", 8, _.id.toString),
        TableColumn[Entry]("Name", 20, _.name),
        TableColumn[Entry]("Score", 8, _.score.toString)
      ),
      selected =>
        selected.foreach { value =>
          viewport.foreach { view =>
            val line    = value.index + 1 // Document row zero is the header.
            val visible = math.max(1, view.viewportExtent)
            if value.index <= 0 then view.jumpToStart()
            else if value.index >= rows.size - 1 then view.jumpToEnd()
            else if line < view.offset then view.scrollTo(line)
            else if line >= view.offset + visible then view.scrollTo(line - visible + 1)
          }
        }
    )
    val view     = ScrollView(table, followEnd = true, scrollbar = ScrollbarMode.Automatic)
    view.jumpToStart()
    viewport = Some(view)
    val root     = VStack(Seq(
      StackEntry(view, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(
        Text(
          "100,000 rows | Arrows/PgUp/PgDn/Home/End | Esc/Ctrl+C quit",
          paddingX = 0,
          paddingY = 0
        ),
        StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1))
      )
    ))
    val tui      = TUI.fullscreen(terminal, root)
    tui.setFocus(table)
    tui.exitsOnEscape = true
    tui
