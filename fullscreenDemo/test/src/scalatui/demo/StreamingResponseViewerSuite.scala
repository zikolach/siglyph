package scalatui.demo

import scalatui.scheduling.ManualTaskScheduler
import scalatui.ansi.Ansi
import scalatui.markdown.{
  BasicMarkdownParser, BasicMarkdownRenderer, MarkdownBlock, StreamingMarkdown
}
import scalatui.terminal.{
  MouseAction, MouseButton, MouseButtonState, TerminalInput, TerminalKey, VirtualTerminal
}

class StreamingResponseViewerSuite extends munit.FunSuite:
  test("viewer follows at bottom but retains manually scrolled offset and stable-prefix selection"):
    val terminal = VirtualTerminal(60, 9)
    val viewer   = StreamingResponseViewer(terminal)
    viewer.tui.start()
    try
      (1 to 20).foreach(index => viewer.append(s"Stable paragraph $index.\n\n"))
      viewer.tui.flushRender()
      assert(viewer.view.isFollowingEnd)
      assertEquals(viewer.view.offset, viewer.view.contentExtent - viewer.view.viewportExtent)
      viewer.view.scrollTo(0)
      viewer.tui.flushRender()
      assert(!viewer.view.isFollowingEnd)
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), 0, 0))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        0,
        6
      ))
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), 0, 6))
      val selected = viewer.view.selection
      assert(selected.nonEmpty)
      val work     = viewer.response.stats
      viewer.append("New **partial")
      viewer.tui.flushRender()
      assertEquals(viewer.view.offset, 0)
      assertEquals(viewer.view.selection, selected)
      assert(viewer.response.stats.reusedBlocks > work.reusedBlocks)
      viewer.view.jumpToEnd()
      viewer.append(" response**\n\nlatest")
      viewer.tui.flushRender()
      assert(viewer.view.isFollowingEnd)
      assertEquals(viewer.view.offset, viewer.view.contentExtent - viewer.view.viewportExtent)
    finally viewer.tui.stop()

  test("active drag continues across scoped streaming ticks while scrolled away"):
    val terminal  = VirtualTerminal(60, 12)
    val viewer    = StreamingResponseViewer(terminal)
    val scheduler = ManualTaskScheduler()
    viewer.tui.start()
    val scope     = viewer.tui.openApplicationScope()
    try
      viewer.attach(scope, scheduler)
      viewer.editor.insertAtCursor("selection regression")
      terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
      while viewer.view.contentExtent < 35 do
        scheduler.advanceBy(StreamingResponseSamples.IntervalMillis)
        viewer.tui.flushRender()
      assert(!viewer.response.isFinished)
      val rows        = viewer.response.render(60).lines
      val documentRow = rows.indices.drop(5).find(index => Ansi.strip(rows(index)).length > 20).get
      viewer.view.scrollTo(documentRow - 2)
      viewer.tui.flushRender()
      val offset      = viewer.view.offset
      assert(offset > 0)
      assert(!viewer.view.isFollowingEnd)
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), 2, 6))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        2,
        9
      ))
      val before      = viewer.view.selection.get
      scheduler.advanceBy(StreamingResponseSamples.IntervalMillis)
      viewer.tui.flushRender()
      assert(!viewer.response.isFinished)
      assertEquals(viewer.view.offset, offset)
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        2,
        14
      ))
      val after       = viewer.view.selection.get
      assertEquals(after.startOffset, before.startOffset)
      assert(after.endOffset > before.endOffset)
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), 2, 14))
      assertEquals(viewer.view.selection, Some(after))
    finally
      scope.close()
      viewer.tui.stop()
      scheduler.close()

  test("viewer clears selection when provisional tail changes preceding displayed text"):
    val terminal = VirtualTerminal(60, 9)
    val viewer   = StreamingResponseViewer(terminal)
    viewer.tui.start()
    try
      viewer.append("[label")
      viewer.tui.flushRender()
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), 0, 0))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        0,
        4
      ))
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), 0, 4))
      assert(viewer.view.selection.nonEmpty)
      viewer.append("](https://pending")
      viewer.tui.flushRender()
      assertEquals(viewer.view.selection, None)
    finally viewer.tui.stop()

  test("selection prefix guards use newline-free offsets after many short rows"):
    val terminal = VirtualTerminal(60, 9)
    val viewer   = StreamingResponseViewer(terminal)
    viewer.tui.start()
    try
      viewer.append("a\n\n" * 10 + "[label")
      viewer.tui.flushRender()
      val row = viewer.view.contentExtent - viewer.view.offset - 1
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row, 0))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        row,
        4
      ))
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), row, 4))
      assert(viewer.view.selection.nonEmpty)
      viewer.append("](https://pending")
      viewer.tui.flushRender()
      assertEquals(viewer.view.selection, None)
    finally viewer.tui.stop()

  test("demo fixture contains four full tables and eight substantial code fences within capacity"):
    val source = StreamingResponseSamples.response("p" * 256)
    assert(source.length < 64 * 1024)
    val blocks = BasicMarkdownParser.parse(source).toOption.get
    val tables = blocks.collect { case table: MarkdownBlock.Table => table }
    val code   = blocks.collect { case block: MarkdownBlock.CodeBlock => block }
    assertEquals(tables.size, 4)
    assert(tables.forall(_.rows.size >= 13))
    assertEquals(code.size, 8)
    assertEquals(code.count(_.text.linesIterator.size >= 24), 4)

  for width <- Vector(20, 80, 160) do
    test(
      s"large tables and code stay width-bounded across split fence delimiters at $width columns"
    ):
      val source = StreamingResponseSamples.response("table and code regression")
      val stream = StreamingMarkdown()
      val cuts   =
        (Vector(0, source.length) ++
          (193 until source.length by 193) ++
          "```".r.findAllMatchIn(source).flatMap(m =>
            Vector(m.start + 1, m.start + 2)
          )).distinct.sorted
      cuts.sliding(2).foreach { pair =>
        assert(stream.append(source.substring(pair.head, pair.last)))
        val lines = stream.render(width).lines
        assert(lines.forall(line => Ansi.visibleWidth(line) <= width))
      }
      stream.finish()
      assertEquals(stream.text, source)
      assertEquals(stream.render(width).lines, BasicMarkdownRenderer().render(source, width))

  for (columns, rows) <- Vector((80, 24), (120, 40), (160, 50)) do
    test(s"demo spans several normal-size viewports at $columns by $rows"):
      val terminal = VirtualTerminal(columns, rows)
      val viewer   = StreamingResponseViewer(terminal)
      viewer.tui.start()
      try
        assert(viewer.append(StreamingResponseSamples.response("scroll test")))
        viewer.tui.flushRender()
        assert(viewer.view.contentExtent > viewer.view.viewportExtent * 3)
        viewer.view.scrollTo(5)
        viewer.tui.flushRender()
        assert(!viewer.view.isFollowingEnd)
        assert(viewer.append("\n\nMore output after manually scrolling.\n"))
        viewer.tui.flushRender()
        assertEquals(viewer.view.offset, 5)
        viewer.view.jumpToEnd()
        viewer.tui.flushRender()
        assertEquals(viewer.view.offset, viewer.view.contentExtent - viewer.view.viewportExtent)
      finally viewer.tui.stop()

  test("composer streams through owned scoped timer and stop cancels outstanding delivery"):
    val terminal  = VirtualTerminal(60, 9)
    val viewer    = StreamingResponseViewer(terminal)
    val scheduler = ManualTaskScheduler()
    viewer.tui.start()
    val scope     = viewer.tui.openApplicationScope()
    try
      viewer.attach(scope, scheduler)
      viewer.editor.insertAtCursor("hello")
      terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
      viewer.tui.flushRender()
      assertEquals(scope.scheduledCount, 1)
      val expected = StreamingResponseSamples.response("hello")
      val ticks    = (expected.length + StreamingResponseSamples.ChunkUnits - 1) /
        StreamingResponseSamples.ChunkUnits + 1
      (1 to ticks).foreach { _ =>
        scheduler.advanceBy(StreamingResponseSamples.IntervalMillis)
        viewer.tui.flushRender()
      }
      assert(viewer.response.isFinished)
      assertEquals(viewer.response.text, expected)
      assertEquals(scope.scheduledCount, 0)
      viewer.editor.insertAtCursor("again")
      terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
      viewer.tui.flushRender()
      assertEquals(scope.scheduledCount, 1)
      viewer.tui.stop()
      assert(scope.isClosed)
      assertEquals(scheduler.pendingCount, 0)
    finally
      scope.close()
      viewer.tui.stop()
      scheduler.close()
