package scalatui.benchmark

import scalatui.components.{ScrollView, StackEntry, StackEntryOptions, Text, VStack}
import scalatui.core.{
  Component,
  ComponentRender,
  MouseGestureOptions,
  RuntimeCounterSnapshot,
  StackAlignment,
  TUI,
  TUIOptions,
  TerminalControlPlacement,
  ViewportRangeRenderer
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  KeyModifiers,
  MouseAction,
  MouseButton,
  MouseButtonState,
  TerminalImageProtocol,
  TerminalInput,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions,
  VirtualTerminal
}

/** Fixed dependency-free workloads shared by JVM reports, Native smoke runs, and counter tests. */
private[scalatui] object BenchmarkWorkloads:
  final case class Scale(
      transcriptRows: Int,
      appendRows: Int,
      unicodeRows: Int,
      overlayCount: Int,
      imageCount: Int
  )

  object Scale:
    val Standard: Scale = Scale(50000, 1000, 2000, 32, 96)
    val Quick: Scale    = Scale(10000, 200, 400, 8, 24)

  final case class Observation(counters: RuntimeCounterSnapshot, checksum: Long)

  final case class Scenario(
      name: String,
      metadata: Vector[(String, String)],
      execute: () => Observation
  )

  private final class MutableLines(var lines: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(lines)

  private final class RangedTranscript(val rows: Int, unicode: Boolean = false)
      extends Component,
        ViewportRangeRenderer:
    override def contentExtent(width: Int): Int = rows

    override def render(width: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(rows)(line))

    override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(rowCount)(index => line(startRow + index)))

    private def line(index: Int): String =
      if unicode then s"row $index Cafe\u0301 界🙂 needle"
      else s"row $index transcript needle"

  def scenarios(scale: Scale): Vector[Scenario] = Vector(
    largeTranscriptLayout(scale),
    append(scale),
    differentialTail(scale),
    unicodeReflow(scale),
    overlays(scale),
    nestedScrolling(scale),
    search(scale),
    selectionMapping(scale),
    imageHeavyFrames(scale)
  )

  private def largeTranscriptLayout(scale: Scale): Scenario = Scenario(
    "large-transcript-layout",
    metadata(scale, "operation" -> "initial-layout-and-5000-row-scroll"),
    () =>
      val transcript = RangedTranscript(scale.transcriptRows)
      val view       = ScrollView(transcript, primary = true)
      val terminal   = VirtualTerminal(80, 24)
      val tui        = TUI.fullscreen(terminal, view)
      tui.start()
      view.scrollTo(math.min(5000, scale.transcriptRows - 24))
      tui.flushRender()
      observe(tui, terminal.screenLines.map(_.length.toLong).sum, stop = true)
  )

  private def append(scale: Scale): Scenario = Scenario(
    "append",
    metadata(scale, "appendRows" -> scale.appendRows.toString),
    () =>
      val terminal = VirtualTerminal(80, 24)
      val tui      = TUI(terminal)
      tui.addChild(MutableLines(Vector("prompt")))
      tui.start()
      tui.appendToScrollback(MutableLines(
        Vector.tabulate(scale.appendRows)(index => s"appended row $index")
      ))
      observe(tui, terminal.writes.length.toLong, stop = true)
  )

  private def differentialTail(scale: Scale): Scenario = Scenario(
    "differential-tail-change",
    metadata(scale, "changedRows" -> "1"),
    () =>
      val content  = MutableLines(Vector.tabulate(scale.appendRows)(index => s"stable row $index"))
      val terminal = VirtualTerminal(80, 24)
      val tui      = TUI(terminal)
      tui.addChild(content)
      tui.start()
      content.lines = content.lines.updated(content.lines.length - 1, "changed tail")
      tui.requestRender()
      tui.flushRender()
      observe(tui, terminal.writes.length.toLong, stop = true)
  )

  private def unicodeReflow(scale: Scale): Scenario = Scenario(
    "unicode-wrap-reflow",
    metadata(
      scale,
      "unicodeRows"  -> scale.unicodeRows.toString,
      "initialWidth" -> "80",
      "reflowWidth"  -> "41"
    ),
    () =>
      val content  = Text(
        Vector.fill(scale.unicodeRows)("Cafe\u0301 界🙂 alpha beta gamma delta").mkString("\n"),
        paddingX = 0
      )
      val terminal = VirtualTerminal(80, 24)
      val tui      = TUI(terminal)
      tui.addChild(content)
      tui.start()
      terminal.resize(41, 24)
      tui.flushRender()
      observe(tui, terminal.screenLines.map(_.length.toLong).sum, stop = true)
  )

  private def overlays(scale: Scale): Scenario = Scenario(
    "overlays",
    metadata(scale, "overlayCount" -> scale.overlayCount.toString),
    () =>
      val terminal = VirtualTerminal(80, 24)
      val root     = ScrollView(RangedTranscript(scale.transcriptRows), primary = true)
      val tui      = TUI.fullscreen(terminal, root)
      (0 until scale.overlayCount).foreach(index =>
        tui.showOverlay(MutableLines(Vector(s"overlay $index")))
      )
      tui.start()
      observe(tui, terminal.screenLines.map(_.length.toLong).sum, stop = true)
  )

  private def nestedScrolling(scale: Scale): Scenario = Scenario(
    "nested-scrolling",
    metadata(scale, "scrollOperations" -> "200", "nestingDepth" -> "2"),
    () =>
      val inner    = ScrollView(RangedTranscript(scale.transcriptRows))
      val body     = VStack(
        Seq(
          StackEntry(MutableLines(Vector("header")), StackEntryOptions(basis = Some(1))),
          StackEntry(inner, StackEntryOptions(basis = Some(12), maxSize = Some(12))),
          StackEntry(RangedTranscript(scale.appendRows))
        ),
        alignment = StackAlignment.Stretch
      )
      val outer    = ScrollView(body, primary = true)
      val terminal = VirtualTerminal(80, 24)
      val tui      = TUI.fullscreen(terminal, outer)
      tui.start()
      (0 until 200).foreach { index =>
        if index % 2 === 0 then inner.scrollBy(1) else outer.scrollBy(1)
      }
      tui.flushRender()
      observe(tui, inner.offset.toLong + outer.offset.toLong, stop = true)
  )

  private def search(scale: Scale): Scenario = Scenario(
    "search",
    metadata(
      scale,
      "queryLength"       -> "6",
      "sourceOccurrences" -> scale.transcriptRows.toString,
      "expectedMatches"   -> math
        .min(scale.transcriptRows, ScrollView.MaxRetainedSearchMatches)
        .toString
    ),
    () =>
      val view     = ScrollView(RangedTranscript(scale.transcriptRows), primary = true)
      val terminal = VirtualTerminal(80, 24)
      val tui      = TUI.fullscreen(terminal, view)
      tui.start()
      view.openSearch()
      view.updateSearchQuery("needle", terminal.columns, tui.testRuntimeCounters.recordSearchScans)
      tui.requestRender()
      tui.flushRender()
      observe(tui, view.searchState.matchCount.toLong, stop = true)
  )

  private def selectionMapping(scale: Scale): Scenario = Scenario(
    "selection-mapping",
    metadata(scale, "dragStart" -> "0,0", "dragEnd" -> "23,20"),
    () =>
      val view     = ScrollView(RangedTranscript(scale.transcriptRows, unicode = true), primary = true)
      val terminal = VirtualTerminal(80, 24)
      val options  = TUIOptions(
        mouseTracking = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.Drag)),
        mouseGestures = MouseGestureOptions(maxCellDistance = 0)
      )
      val tui      = TUI.fullscreen(terminal, view, options)
      tui.start()
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Press(MouseButton.Left),
        0,
        0,
        KeyModifiers.empty
      ))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
        23,
        20,
        KeyModifiers.empty
      ))
      terminal.sendMouse(TerminalInput.Mouse(
        MouseAction.Release(MouseButton.Left),
        23,
        20,
        KeyModifiers.empty
      ))
      observe(tui, tui.viewportSelection.fold(0L)(_.endOffset.toLong), stop = true)
  )

  private def imageHeavyFrames(scale: Scale): Scenario = Scenario(
    "image-heavy-frames",
    metadata(scale, "imageCount" -> scale.imageCount.toString, "imageCells" -> "1x1"),
    () =>
      val payload  = Base64ImagePayload.from("YQ==").toOption.get
      val controls = Vector.tabulate(scale.imageCount)(index =>
        TerminalControlPlacement(
          index,
          0,
          TerminalImageProtocol.encodeKitty(payload, 1000 + index, 1, 1)
        )
      )
      val content  = MutableLines(Vector.fill(scale.imageCount)(" "))
      val terminal = VirtualTerminal(80, math.max(24, scale.imageCount))
      val tui      = TUI.fullscreen(
        terminal,
        new Component:
          override def render(width: Int): ComponentRender =
            ComponentRender(content.lines, controls, Vector.empty)
      )
      tui.start()
      tui.requestRender(force = true)
      tui.flushRender()
      observe(tui, controls.length.toLong, stop = true)
  )

  private def observe(tui: TUI, checksum: Long, stop: Boolean): Observation =
    val snapshot = tui.testRuntimeCounters.snapshot
    if stop then tui.stop()
    Observation(snapshot, checksum)

  private def metadata(scale: Scale, values: (String, String)*): Vector[(String, String)] =
    Vector(
      "transcriptRows" -> scale.transcriptRows.toString,
      "terminal"       -> "80x24"
    ) ++ values
