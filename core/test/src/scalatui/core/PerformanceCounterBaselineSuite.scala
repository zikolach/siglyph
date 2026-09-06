package scalatui.core

import scalatui.components.ScrollView
import scalatui.syntax.Equality.*
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol, VirtualTerminal}

class PerformanceCounterBaselineSuite extends munit.FunSuite:
  private final class FullTranscript(val rows: Int) extends Component:
    override def render(width: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(rows)(index => s"row $index needle"))

  private final class RangedTranscript(val rows: Int)
      extends Component,
        ViewportRangeRenderer:
    override def contentExtent(width: Int): Int = rows

    override def render(width: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(rows)(index => s"row $index needle"))

    override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(rowCount)(index => s"row ${startRow + index} needle"))

  test("checked-in viewport counter baseline bounds visible painting and reuses one render"):
    val transcript = FullTranscript(10000)
    val view       = ScrollView(transcript, primary = true)
    val terminal   = VirtualTerminal(80, 24)
    val tui        = TUI.fullscreen(terminal, view)

    tui.start()
    val initial = tui.testRuntimeCounters.snapshot
    assertEquals(initial.componentRenders, 1L)
    assertEquals(initial.paintedRows, 24L)
    assertEquals(initial.terminalWrites, 3L)

    view.scrollTo(5000)
    tui.flushRender()
    val scrolled = tui.testRuntimeCounters.snapshot
    assertEquals(scrolled.componentRenders - initial.componentRenders, 1L)
    assertEquals(scrolled.paintedRows - initial.paintedRows, 24L)
    assertEquals(scrolled.terminalWrites - initial.terminalWrites, 1L)
    tui.stop()

  test("checked-in search counter baseline scans one revision once"):
    val transcript = RangedTranscript(10000)
    val view       = ScrollView(transcript, primary = true)
    val terminal   = VirtualTerminal(80, 24)
    val tui        = TUI.fullscreen(terminal, view)

    tui.start()
    view.openSearch()
    view.updateSearchQuery("needle", terminal.columns, tui.testRuntimeCounters.recordSearchScans)
    tui.requestRender()
    tui.flushRender()
    val indexed = tui.testRuntimeCounters.snapshot
    assertEquals(indexed.searchScans, 10000L)

    view.scrollBy(100)
    tui.flushRender()
    val reused = tui.testRuntimeCounters.snapshot
    assertEquals(reused.searchScans, 10000L)
    tui.stop()

  test("checked-in image counter baseline encodes only visible typed images"):
    val payload  = Base64ImagePayload.from("YQ==").toOption.get
    val content  = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(" ", " ", " "),
        Vector.tabulate(3)(index =>
          TerminalControlPlacement(
            index,
            0,
            TerminalImageProtocol.encodeKitty(payload, 900 + index, 1, 1)
          )
        ),
        Vector.empty
      )
    val terminal = VirtualTerminal(20, 2)
    val tui      = TUI.fullscreen(terminal, content)

    tui.start()
    val counters = tui.testRuntimeCounters.snapshot
    assertEquals(counters.componentRenders, 1L)
    assertEquals(counters.paintedRows, 2L)
    assertEquals(counters.controlEncodes, 2L)
    assertEquals(counters.terminalWrites, 3L)
    tui.stop()
