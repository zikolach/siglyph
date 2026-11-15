package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{InputResult, ViewportLayoutEngine}
import scalatui.terminal.{KeyEventType, KeyModifiers, TerminalInput, TerminalKey}

class DataTableSuite extends munit.FunSuite:
  private def data(values: Vector[Int]): TableData[Int] = TableData(values, _.toString)
  private def column: TableColumn[Int]                  = TableColumn("Value", 10, _.toString)

  test("stable IDs are captured once and duplicates/null IDs are rejected"):
    var calls = 0
    val rows  = TableData(Vector(3, 2, 1), value => { calls += 1; value.toString })
    assertEquals(calls, 3)
    assertEquals(rows.indexOf("2"), Some(1))
    assertEquals(rows.indexOf("missing"), None)
    intercept[IllegalArgumentException](data(Vector(1, 1)))
    intercept[IllegalArgumentException](TableData(Vector(1), _ => null))

  test("replacement preserves identity and publishes the current row value"):
    var notifications                                             = Vector.empty[Option[TableSelection[(String, Int)]]]
    def dataset(values: (String, Int)*): TableData[(String, Int)] = TableData(values.toVector, _._1)
    val table                                                     = DataTable(
      dataset("a" -> 1, "b" -> 2),
      Vector(TableColumn[(String, Int)]("Value", 8, _._2.toString)),
      selected => notifications :+= selected
    )
    assert(table.selectId("b"))
    table.setData(dataset("b" -> 20, "a" -> 10))
    assertEquals(table.selection.map(_.index), Some(0))
    assertEquals(table.selection.map(_.row), Some("b" -> 20))
    assertEquals(notifications.last.map(_.row._2), Some(20))
    val before                                                    = table.revision
    assert(!table.selectId("missing"))
    assertEquals(table.revision, before)

  test("deleted and empty selections fall back to a bounded index"):
    val table = DataTable(data(Vector(1, 2, 3, 4)), Vector(column))
    table.selectIndex(2)
    table.setData(data(Vector(10, 20, 30, 40)))
    assertEquals(table.selection.map(_.id), Some("30"))
    table.setData(data(Vector(10)))
    assertEquals(table.selection.map(_.id), Some("10"))
    table.setData(data(Vector.empty))
    assertEquals(table.selection, None)
    table.moveSelection(Int.MinValue)
    table.selectIndex(Int.MaxValue)
    assertEquals(table.selection, None)
    table.setData(data(Vector(7)))
    assertEquals(table.selection.map(_.id), Some("7"))

  test("focused keyboard navigation consumes bounds without duplicate callbacks"):
    var changes = 0
    val table   =
      DataTable(data((0 until 30).toVector), Vector(column), _ => changes += 1, pageSize = 7)
    assertEquals(table.handleInputResult(TerminalInput.Key(TerminalKey.Down)), InputResult.Ignored)
    table.focused = true
    assertEquals(table.handleInputResult(TerminalInput.Key(TerminalKey.Up)), InputResult.Render)
    assertEquals(changes, 0)
    table.handleInputResult(TerminalInput.Key(TerminalKey.PageDown))
    assertEquals(table.selection.map(_.index), Some(7))
    table.handleInputResult(TerminalInput.Key(TerminalKey.PageUp))
    assertEquals(table.selection.map(_.index), Some(0))
    table.handleInputResult(TerminalInput.Key(TerminalKey.End))
    assertEquals(table.selection.map(_.index), Some(29))
    table.handleInputResult(TerminalInput.Key(TerminalKey.Home))
    table.handleInputResult(TerminalInput.KeyEvent(
      TerminalKey.Down,
      KeyModifiers.empty,
      KeyEventType.Release
    ))
    table.handleInputResult(TerminalInput.Key(TerminalKey.Down, KeyModifiers(ctrl = true)))
    assertEquals(table.selection.map(_.index), Some(0))
    table.handleInputResult(TerminalInput.KeyEvent(
      TerminalKey.Down,
      KeyModifiers.empty,
      KeyEventType.Repeat
    ))
    assertEquals(table.selection.map(_.index), Some(1))

  test("selection callbacks are ordered and reentrant without recursive dispatch"):
    var tableRef     = Option.empty[DataTable[Int]]
    var notified     = Vector.empty[Int]
    var depth        = 0
    var maximumDepth = 0
    val table        = DataTable(
      data(Vector(0, 1, 2)),
      Vector(column),
      selected =>
        depth += 1
        maximumDepth = math.max(maximumDepth, depth)
        selected.foreach { value =>
          notified :+= value.index
          if value.index < 2 then tableRef.foreach(_.selectIndex(2))
        }
        depth -= 1
    )
    tableRef = Some(table)
    table.selectIndex(1)
    assertEquals(notified, Vector(1, 2))
    assertEquals(maximumDepth, 1)
    assertEquals(table.selection.map(_.index), Some(2))

  test("callbacks and formatters permit another thread to read component state"):
    var tableRef                    = Option.empty[DataTable[Int]]
    def readOnAnotherThread(): Unit =
      val completed = new java.util.concurrent.CountDownLatch(1)
      val worker    = new Thread(() =>
        tableRef.foreach(_.selection)
        completed.countDown()
      )
      worker.setDaemon(true)
      worker.start()
      assert(completed.await(5L, java.util.concurrent.TimeUnit.SECONDS))
      worker.join(5000L)
      assert(!worker.isAlive)
    val table                       = DataTable(
      data(Vector(1, 2)),
      Vector(TableColumn[Int]("Value", 10, value => { readOnAnotherThread(); value.toString })),
      _ => readOnAnotherThread()
    )
    tableRef = Some(table)
    table.selectIndex(1)
    table.renderRange(12, 1, 1)

  test("100,000-row viewport formats only visible rows and never rebuilds IDs"):
    var keyCalls = 0
    var rendered = Vector.empty[Int]
    val rows     = TableData((0 until 100000).toVector, value => { keyCalls += 1; value.toString })
    val table    = DataTable(
      rows,
      Vector(TableColumn[Int](
        "Value",
        10,
        value =>
          rendered :+= value
          value.toString
      ))
    )
    val view     = ScrollView(table)
    assertEquals(table.contentExtent(16), 100001)
    val first    = ViewportLayoutEngine.layout(view, 16, 8)
    assertEquals(rendered, (0 until 7).toVector)
    assert(first.lines.head.contains("Value"))
    rendered = Vector.empty
    view.scrollBy(50000)
    val second   = ViewportLayoutEngine.layout(view, 16, 8)
    assertEquals(rendered, (49999 until 50007).toVector)
    assertEquals(keyCalls, 100000)
    assertEquals(second.lines.length, 8)
    assert(second.lines.forall(line => Ansi.visibleWidth(line) <= 16))
    table.selectIndex(50000)
    assertEquals(keyCalls, 100000)

  test("empty geometry, out-of-range requests and hidden columns invoke no formatters"):
    var first  = 0
    var second = 0
    val table  = DataTable(
      data(Vector(1, 2)),
      Vector(
        TableColumn[Int]("A", 8, value => { first += 1; value.toString }),
        TableColumn[Int]("B", 8, value => { second += 1; value.toString })
      )
    )
    assertEquals(table.renderRange(0, 0, 100).lines, Vector.empty)
    assertEquals(table.renderRange(10, 0, 0).lines, Vector.empty)
    assertEquals(table.renderRange(10, Int.MaxValue, Int.MaxValue).lines, Vector.empty)
    table.renderRange(2, 1, 1)
    assertEquals(first, 0)
    assertEquals(second, 0)
    assertEquals(table.renderRange(6, 1, 1).lines.map(Ansi.visibleWidth), Vector(6))
    assertEquals(first, 1)
    assertEquals(second, 0)
    assertEquals(table.renderRange(20, -100, 1).lines.length, 1)
    assertEquals(first, 1)
    assertEquals(table.renderRange(20, 0, Int.MaxValue).lines.length, 3)
    assertEquals(table.contentExtent(-1), 0)

  test("cells preserve graphemes and close styles and hyperlinks between columns"):
    val table  = DataTable(
      data(Vector(0)),
      Vector(
        TableColumn[Int]("Wide", 4, _ => "\u001b[31m\u001b]8;;https://example.com\u001b\\红e\u0301"),
        TableColumn[Int]("Next", 1, _ => "Z")
      )
    )
    val line   = table.renderRange(8, 1, 1).lines.head
    assertEquals(Ansi.visibleWidth(line), 8)
    assert(Ansi.strip(line).contains("红e\u0301"))
    assert(line.indexOf("\u001b[0m") >= 0)
    assert(line.indexOf("\u001b[0m") < line.lastIndexOf("Z"))
    assert(line.indexOf("\u001b]8;;\u001b\\") >= 0)
    assert(line.indexOf("\u001b]8;;\u001b\\") < line.lastIndexOf("Z"))
    val narrow = table.renderRange(3, 1, 1).lines.head
    assertEquals(Ansi.visibleWidth(narrow), 3)
    assert(!narrow.contains("红"))

  test("ordinary cell controls remain inert and inside column width"):
    val table =
      DataTable(data(Vector(0)), Vector(TableColumn[Int]("Safe", 60, _ => "a\n\u001b[2Jb")))
    val line  = table.renderRange(64, 1, 1).lines.head
    assert(!line.contains("\n"))
    assert(!line.contains("\u001b[2J"))
    assert(line.contains("\\u000A"))
    assertEquals(Ansi.visibleWidth(line), 64)

  test("formatters observe one immutable render snapshot while reentering selection"):
    var tableRef = Option.empty[DataTable[Int]]
    val table    = DataTable(
      data(Vector(1, 2)),
      Vector(TableColumn[Int](
        "Value",
        8,
        value =>
          tableRef.foreach(_.selectIndex(1))
          value.toString
      ))
    )
    tableRef = Some(table)
    table.focused = true
    val frame    = table.renderRange(12, 1, 2)
    assert(frame.lines.head.startsWith("> "))
    assert(frame.lines(1).startsWith("  "))
    assertEquals(table.selection.map(_.index), Some(1))

  test("columns and page sizes have explicit valid bounds"):
    intercept[IllegalArgumentException](TableColumn[Int]("zero", 0, _.toString))
    intercept[IllegalArgumentException](DataTable(data(Vector(1)), Vector.empty[TableColumn[Int]]))
    intercept[IllegalArgumentException](DataTable(data(Vector(1)), Vector.fill(65)(column)))
    intercept[IllegalArgumentException](DataTable(data(Vector(1)), Vector(column), pageSize = 0))
    val table = DataTable(data(Vector(1)), Vector(column))
    assertEquals(table.render(8).lines.length, 2)
    assertEquals(table.render(0).lines, Vector.empty)
