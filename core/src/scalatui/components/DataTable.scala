package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component, ComponentRender, ContextualComponent, Focusable, InputResult, TUIContext,
  ViewportRangeRenderer
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyEventType, TerminalInput, TerminalKey}

/**
 * Experimental typed table for JVM and Native. Put it inside ScrollView to use visible-range
 * rendering; render(width) deliberately remains the complete-document compatibility path.
 *
 * Row zero is a non-sticky header. The table formats only requested rows/visible columns, with no
 * offscreen cache. Model construction, application filtering and sorting are separate costs;
 * comparison sorting is typically O(n log n), not visible-row work. Cell formatters and selection
 * callbacks run outside the component state boundary. Rows must be immutable and formatters
 * side-effect free. Callers own parent scrolling and search invalidation.
 */
final class DataTable[A](
    initialData: TableData[A],
    val columns: Vector[TableColumn[A]],
    onSelectionChange: Option[TableSelection[A]] => Unit = (_: Option[TableSelection[A]]) => (),
    val pageSize: Int = 10
) extends Component, Focusable, ContextualComponent, ViewportRangeRenderer:
  require(columns.nonEmpty && columns.size <= 64, "table requires between 1 and 64 columns")
  require(pageSize > 0, "table page size must be positive")
  private val stateBoundary   = ComponentStateBoundary()
  private var currentData     = initialData
  private var selectedIndex   = if initialData.size > 0 then 0 else -1
  private var isFocused       = false
  private var context         = Option.empty[TUIContext]
  private var currentRevision = 0L

  private final case class RenderSnapshot(data: TableData[A], selected: Int, focused: Boolean)

  def data: TableData[A]                   = stateBoundary(currentData)
  def revision: Long                       = stateBoundary(currentRevision)
  def selection: Option[TableSelection[A]] = stateBoundary(selectionLocked)

  private def selectionLocked: Option[TableSelection[A]] =
    Option.when(selectedIndex >= 0)(TableSelection(
      currentData.ids(selectedIndex),
      selectedIndex,
      currentData.rows(selectedIndex)
    ))

  private def bumpRevision(): Unit =
    require(currentRevision < Long.MaxValue, "table revision exhausted")
    currentRevision += 1L

  private def deferRender(effects: ComponentEffects): Unit =
    context.foreach(value => effects.add(() => value.requestRender()))

  private def publishSelection(effects: ComponentEffects): Unit =
    val selected = selectionLocked
    effects.add(() => onSelectionChange(selected))
    deferRender(effects)

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { _ => context = value }

  override def focused: Boolean                = stateBoundary(isFocused)
  override def focused_=(value: Boolean): Unit = stateBoundary.transition { effects =>
    if value !== isFocused then
      bumpRevision()
      isFocused = value
      deferRender(effects)
  }

  /** Preserve the selected stable ID, otherwise clamp its old index; publish the new row value. */
  def setData(value: TableData[A]): Unit = stateBoundary.transition { effects =>
    val retained  = selectionLocked.flatMap(selected => value.indexOf(selected.id))
    val nextIndex = if value.size <= 0 then -1
    else retained.getOrElse(math.max(0, math.min(selectedIndex, value.size - 1)))
    bumpRevision()
    currentData = value
    selectedIndex = nextIndex
    publishSelection(effects)
  }

  private def selectLocked(index: Long, effects: ComponentEffects): Unit =
    val next = if currentData.size <= 0 then -1
    else math.max(0L, math.min(index, currentData.size.toLong - 1L)).toInt
    if next !== selectedIndex then
      bumpRevision()
      selectedIndex = next
      publishSelection(effects)

  /** Clamp an index to the current data. Empty data stays unselected. */
  def selectIndex(index: Int): Unit = stateBoundary.transition { effects =>
    selectLocked(index.toLong, effects)
  }

  /** Select a known stable ID; an unknown ID leaves selection and revision unchanged. */
  def selectId(id: String): Boolean = stateBoundary.transition { effects =>
    val index = currentData.indexOf(id)
    index.foreach(value => selectLocked(value.toLong, effects))
    index.nonEmpty
  }

  def moveSelection(delta: Int): Unit = stateBoundary.transition { effects =>
    selectLocked(selectedIndex.toLong + delta.toLong, effects)
  }

  override def handleInputResult(input: TerminalInput): InputResult = input match
    case TerminalInput.KeyEvent(key, modifiers, kind)
        if focused && modifiers.isEmpty && (kind !== KeyEventType.Release) =>
      key match
        case TerminalKey.Up       => moveSelection(-1); InputResult.Render
        case TerminalKey.Down     => moveSelection(1); InputResult.Render
        case TerminalKey.PageUp   => moveSelection(-pageSize); InputResult.Render
        case TerminalKey.PageDown => moveSelection(pageSize); InputResult.Render
        case TerminalKey.Home     => selectIndex(0); InputResult.Render
        case TerminalKey.End      => selectIndex(Int.MaxValue); InputResult.Render
        case _                    => InputResult.Ignored
    case _ => InputResult.Ignored

  override def contentExtent(width: Int): Int =
    if width <= 0 then 0 else stateBoundary(currentData.size + 1)

  override def render(width: Int): ComponentRender = renderRange(width, 0, Int.MaxValue)

  override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
    if width <= 0 || rowCount <= 0 then ComponentRender.text(Vector.empty)
    else
      val snapshot = stateBoundary(RenderSnapshot(currentData, selectedIndex, isFocused))
      val start    = math.max(0, startRow)
      val end      = math.min(snapshot.data.size.toLong + 1L, start.toLong + rowCount.toLong).toInt
      val rows     = (start until end).iterator.map { row =>
        val selected    = row > 0 && (row - 1 === snapshot.selected)
        val marker      = if selected then (if snapshot.focused then "> " else "* ") else "  "
        val builder     = new StringBuilder(marker.take(width))
        var remaining   = math.max(0, width - 2)
        var columnIndex = 0
        while columnIndex < columns.size && remaining > 0 do
          if columnIndex > 0 then
            builder.append(' ')
            remaining -= 1
          if remaining > 0 then
            val column = columns(columnIndex)
            val cells  = math.min(column.preferredWidth, remaining)
            val text   = if row <= 0 then column.title else column.cell(snapshot.data.rows(row - 1))
            val slice  = Ansi.sliceByColumns(text, 0, cells)
            builder.append(slice.text)
            builder.append(" " * (cells - slice.width))
            remaining -= cells
          columnIndex += 1
        builder.append(" " * remaining)
        builder.result()
      }.toVector
      ComponentRender.text(rows)
