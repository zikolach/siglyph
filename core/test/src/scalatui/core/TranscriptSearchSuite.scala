package scalatui.core

import scalatui.components.{HStack, ScrollView, StackEntry, StackEntryOptions, Text}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  KeyModifiers,
  TerminalImageProtocol,
  TerminalInput,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalKey,
  VirtualTerminal
}

import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.*

class TranscriptSearchSuite extends munit.FunSuite:
  override def munitTimeout: Duration = 180.seconds

  private final class Lines(var values: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(values)

  test("unchanged large transcript scrolling reuses one bounded search index"):
    val rowCount    = 10000
    var fullRenders = 0
    val transcript  = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = rowCount
      override def render(width: Int): ComponentRender                                    =
        fullRenders += 1
        ComponentRender.text(Vector.tabulate(rowCount)(index => s"row $index needle"))
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        ComponentRender.text(
          Vector.tabulate(rowCount)(index => s"row ${startRow + index} needle")
        )
    val view        = ScrollView(transcript, primary = true)
    val terminal    = VirtualTerminal(24, 4)
    val tui         = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "needle")
    val indexed = tui.testRuntimeCounters.snapshot.searchScans
    view.scrollBy(5000)
    tui.flushRender()
    terminal.resize(24, 5)

    assertEquals(indexed, rowCount.toLong)
    assertEquals(tui.testRuntimeCounters.snapshot.searchScans, indexed)
    assertEquals(fullRenders, 0)
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(rowCount))
    tui.stop()

  test("search normalizes Unicode and highlights complete wide graphemes"):
    val view     = ScrollView(Lines(Vector("Cafe\u0301 界界", "other")), primary = true)
    val terminal = VirtualTerminal(14, 3)
    val tui      = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "CAFÉ")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    assert(terminal.output.contains("\u001b[1;7mCafe\u0301\u001b[0m"), terminal.output)

    backspace(terminal, 4)
    typeText(terminal, "界")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(2))
    assert(terminal.output.contains("\u001b[1;7m界\u001b[0m"), terminal.output)
    tui.stop()

  test("fragmented terminal input edits search before the focused component"):
    var underlyingInputs = 0
    val focused          = new Component with Focusable:
      private var isFocused                                             = false
      override def render(width: Int): ComponentRender                  = ComponentRender.text("editor")
      override def focused: Boolean                                     = isFocused
      override def focused_=(value: Boolean): Unit                      = isFocused = value
      override def handleInputResult(input: TerminalInput): InputResult =
        underlyingInputs += 1
        input match
          case TerminalInput.KeyEvent(
                TerminalKey.Character("f"),
                KeyModifiers(true, true, false, false),
                _
              ) => InputResult.Ignored
          case _ => InputResult.Render
    val view             = ScrollView(Lines(Vector("héllo", "tail")), primary = true)
    val root             = scalatui.components.VStack(Seq(
      scalatui.components.StackEntry(view, scalatui.components.StackEntryOptions(grow = 1)),
      scalatui.components.StackEntry(
        focused,
        scalatui.components.StackEntryOptions(basis = Some(1), maxSize = Some(1))
      )
    ))
    val terminal         = VirtualTerminal(12, 3)
    val tui              = TUI.fullscreen(terminal, root)
    tui.setFocus(focused)

    tui.start()
    openSearch(terminal)
    val buffer = TerminalInputBuffer()
    val bytes  = "hé".getBytes(StandardCharsets.UTF_8)
    Vector(bytes.take(2), bytes.drop(2)).foreach { fragment =>
      buffer.process(TerminalInputChunk(fragment)).foreach(terminal.sendInput)
    }

    assertEquals(tui.viewportSearchState.map(_.query), Some("hé"))
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    assertEquals(underlyingInputs, 1)
    tui.stop()

  test("nested primary search uses its committed content width"):
    var renderedWidths = Vector.empty[Int]
    val transcript     = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 1
      override def render(width: Int): ComponentRender                                    =
        fail("nested range transcript must not use full rendering")
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        renderedWidths :+= width
        ComponentRender.text(Option.when(startRow === 0 && rowCount > 0)("needle").toVector)
    val view           = ScrollView(transcript, primary = true)
    val root           = HStack(Seq(
      StackEntry(Lines(Vector("side")), StackEntryOptions(basis = Some(5), maxSize = Some(5))),
      StackEntry(view, StackEntryOptions(grow = 1))
    ))
    val terminal       = VirtualTerminal(12, 3)
    val tui            = TUI.fullscreen(terminal, root)

    tui.start()
    renderedWidths = Vector.empty
    openSearch(terminal)
    typeText(terminal, "n")

    assertEquals(renderedWidths.distinct, Vector(9))
    tui.stop()
    assertEquals(renderedWidths.distinct, Vector(9))
    tui.stop()

  test("nested primary search preserves a committed zero content width"):
    var renderedWidths = Vector.empty[Int]
    val transcript     = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 1
      override def render(width: Int): ComponentRender                                    =
        fail("nested range transcript must not use full rendering")
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        renderedWidths :+= width
        ComponentRender.text(Option.when(startRow === 0 && rowCount > 0)("needle").toVector)
    val view           = ScrollView(transcript, primary = true)
    val root           = HStack(Seq(
      StackEntry(
        Lines(Vector("side")),
        StackEntryOptions(basis = Some(12), shrink = 0, maxSize = Some(12))
      ),
      StackEntry(view, StackEntryOptions(grow = 1))
    ))
    val terminal       = VirtualTerminal(12, 3)
    val tui            = TUI.fullscreen(terminal, root)

    tui.start()
    renderedWidths = Vector.empty
    openSearch(terminal)
    typeText(terminal, "n")

    assertEquals(renderedWidths.distinct, Vector(0))
    tui.stop()

  test("global listeners can intercept viewport and search commands"):
    val view     = ScrollView(Lines(Vector.tabulate(20)(index => s"row $index")), primary = true)
    val terminal = VirtualTerminal(12, 3)
    val tui      = TUI.fullscreen(terminal, view)
    var inputs   = 0
    tui.addInputListener { _ =>
      inputs += 1
      InputResult.NoRender
    }

    tui.start()
    terminal.sendInput(TerminalInput.Key(TerminalKey.PageDown))
    openSearch(terminal)

    assertEquals(inputs, 2)
    assertEquals(view.offset, 0)
    assertEquals(tui.viewportSearchState.map(_.active), Some(false))
    tui.stop()

  test("active search preserves unsupported-key fallback and Ctrl+C exit"):
    var focusedInputs = Vector.empty[TerminalInput]
    val focused       = new Component with Focusable:
      private var isFocused                                             = false
      override def render(width: Int): ComponentRender                  = ComponentRender.text("focus")
      override def focused: Boolean                                     = isFocused
      override def focused_=(value: Boolean): Unit                      = isFocused = value
      override def handleInputResult(input: TerminalInput): InputResult =
        focusedInputs :+= input
        InputResult.Ignored
    val view          = ScrollView(Lines(Vector("needle", "tail")), primary = true)
    val root          = scalatui.components.VStack(Seq(
      StackEntry(view, StackEntryOptions(grow = 1)),
      StackEntry(focused, StackEntryOptions(basis = Some(1), maxSize = Some(1)))
    ))
    val terminal      = VirtualTerminal(12, 3)
    val tui           = TUI.fullscreen(terminal, root)
    tui.setFocus(focused)
    tui.start()
    openSearch(terminal)
    focusedInputs = Vector.empty

    terminal.sendInput(TerminalInput.Key(TerminalKey.Up))
    terminal.sendInput(TerminalInput.KeyEvent(
      TerminalKey.Character("c"),
      KeyModifiers(ctrl = true),
      scalatui.terminal.KeyEventType.Press
    ))

    tui.run()

    assertEquals(focusedInputs, Vector(TerminalInput.Key(TerminalKey.Up)))
    assert(!terminal.isRunning)

  test("opening search renders its status immediately"):
    val view     = ScrollView(Lines(Vector("needle", "tail")), primary = true)
    val terminal = VirtualTerminal(12, 3)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()

    openSearch(terminal)

    assert(terminal.screenLines.last.startsWith("Search: "), terminal.screenLines.toString)
    tui.stop()

  test("search follows height-responsive visibility"):
    val content  = scalatui.components.VStack(Seq(
      StackEntry(
        Lines(Vector("hidden needle")),
        StackEntryOptions(visible = viewport => viewport.height >= 4)
      ),
      StackEntry(Lines(Vector("always")))
    ))
    val view     = ScrollView(content, primary = true)
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "needle")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))

    terminal.resize(16, 5)

    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    tui.stop()

  test("resize rebuilds the width-keyed index and preserves current repeated match"):
    val text     = Text(
      Vector.tabulate(10)(index =>
        if index === 2 || index === 8 then s"needle $index" else s"row $index"
      )
        .mkString("\n"),
      paddingX = 0
    )
    val view     = ScrollView(text, primary = true)
    val terminal = VirtualTerminal(24, 4)
    val tui      = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "needle")
    next(terminal)
    val before = tui.testRuntimeCounters.snapshot.searchScans
    assertEquals(tui.viewportSearchState.flatMap(_.currentMatch), Some(2))

    terminal.resize(8, 5)

    assert(tui.testRuntimeCounters.snapshot.searchScans > before)
    assertEquals(
      tui.viewportSearchState.map(state => state.matchCount -> state.currentMatch),
      Some(2 -> Some(2))
    )
    assert(view.offset > 0)
    tui.stop()

  test("zero and repeated matches support next previous and close"):
    val view     = ScrollView(Lines(Vector("hit hit hit", "tail")), primary = true)
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "missing")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))
    backspace(terminal, "missing".length)
    typeText(terminal, "hit")
    assertEquals(tui.viewportSearchState.flatMap(_.currentMatch), Some(1))
    next(terminal)
    assertEquals(tui.viewportSearchState.flatMap(_.currentMatch), Some(2))
    previous(terminal)
    assertEquals(tui.viewportSearchState.flatMap(_.currentMatch), Some(1))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Escape))
    assertEquals(tui.viewportSearchState.map(_.active), Some(false))
    tui.stop()

  test("active search captures overlay and editor input while manual scrolling preserves state"):
    var overlayInputs = 0
    val overlay       = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text("overlay")
      override def handleInputResult(input: TerminalInput): InputResult =
        overlayInputs += 1
        InputResult.Ignored
    val view          = ScrollView(
      Lines(Vector.tabulate(20)(index => s"match $index")),
      primary = true
    )
    val terminal      = VirtualTerminal(16, 4)
    val tui           = TUI.fullscreen(terminal, view)
    tui.showOverlay(overlay, OverlayOptions(focusCapturing = true))

    tui.start()
    openSearch(terminal)
    typeText(terminal, "match")
    val before = tui.viewportSearchState
    view.scrollBy(5)
    tui.flushRender()

    assertEquals(overlayInputs, 1)
    assertEquals(tui.viewportSearchState, before)
    assertEquals(view.offset, 5)
    assert(terminal.screenLines.last.startsWith("Search: match"))
    tui.stop()

  test("content revision invalidates cached rows without retaining old matches"):
    final class MutableTranscript extends Component with ContextualComponent:
      private var values                                         = Vector("old match")
      private var context                                        = Option.empty[TUIContext]
      def replace(value: String): Unit                           =
        values = Vector(value)
        context.foreach(_.requestRender())
      override def render(width: Int): ComponentRender           = ComponentRender.text(values)
      override def tuiContext_=(value: Option[TUIContext]): Unit = context = value

    val content  = MutableTranscript()
    val view     = ScrollView(content, primary = true)
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "match")
    val before = tui.testRuntimeCounters.snapshot.searchScans
    content.replace("new value")
    view.invalidateSearchDocument()
    tui.flushRender()

    assert(tui.testRuntimeCounters.snapshot.searchScans > before)
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))
    tui.stop()

  test("typed search input retains only complete graphemes within configured bounds"):
    val view     = ScrollView(
      Lines(Vector("aé😀 target")),
      primary = true,
      maxSearchQueryGraphemes = 2,
      maxSearchQueryUtf8Bytes = 3
    )
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    openSearch(terminal)

    typeText(terminal, "aé😀z")

    assertEquals(tui.viewportSearchState.map(_.query), Some("aé"))
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    tui.stop()

  test("fragmented paste buffers within bounds and refreshes only at PasteEnd"):
    val view     = ScrollView(
      Lines(Vector("aé a")),
      primary = true,
      maxSearchQueryGraphemes = 2,
      maxSearchQueryUtf8Bytes = 3
    )
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    openSearch(terminal)
    val before   = tui.testRuntimeCounters.snapshot.componentRenders
    val bytes    = "aé😀".getBytes(StandardCharsets.UTF_8)

    terminal.sendInput(TerminalInput.PasteStart)
    bytes.grouped(1).foreach(chunk =>
      terminal.sendInput(TerminalInput.PasteChunk(
        TerminalInputChunk(chunk)
      ))
    )

    assertEquals(tui.viewportSearchState.map(_.query), Some(""))
    assertEquals(tui.testRuntimeCounters.snapshot.componentRenders, before)
    terminal.sendInput(TerminalInput.PasteEnd)
    assertEquals(tui.viewportSearchState.map(_.query), Some("aé"))
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    assert(tui.testRuntimeCounters.snapshot.componentRenders > before)
    tui.stop()

  test("content replacement clears a fragmented search paste buffer"):
    val view     = ScrollView(Lines(Vector("old")), primary = true)
    val terminal = VirtualTerminal(16, 3)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    openSearch(terminal)
    terminal.sendInput(TerminalInput.PasteStart)
    terminal.sendInput(TerminalInput.PasteChunk(TerminalInputChunk(
      "retained-secret".getBytes(StandardCharsets.UTF_8)
    )))

    view.replaceContent(Lines(Vector("replacement")))
    tui.flushRender()
    openSearch(terminal)
    terminal.sendInput(TerminalInput.PasteEnd)

    assertEquals(tui.viewportSearchState.map(_.query), Some(""))
    assert(!terminal.output.contains("retained-secret"))
    tui.stop()

  test("search indexing and retained matches stop at deterministic hard limits"):
    val rows     = Vector.fill(scalatui.components.ScrollView.MaxRetainedSearchRows + 10)("x x")
    val view     = ScrollView(Lines(rows), primary = true, maxSearchMatches = 3)
    val terminal = VirtualTerminal(8, 2)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    openSearch(terminal)
    typeText(terminal, "x")

    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(3))
    assertEquals(
      tui.testRuntimeCounters.snapshot.searchScans,
      scalatui.components.ScrollView.MaxRetainedSearchRows.toLong
    )
    tui.stop()

  test("ANSI metadata and typed controls are excluded and replacement and stop clear search"):
    val control    = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("AAAA").toOption.get,
      imageId = 701,
      widthCells = 1,
      heightCells = 1
    )
    val controlled = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("\u001b[31mplain\u001b[0m"),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector.empty
      )
    val view       = ScrollView(controlled, primary = true)
    val terminal   = VirtualTerminal(16, 3)
    val tui        = TUI.fullscreen(terminal, view)

    tui.start()
    openSearch(terminal)
    typeText(terminal, "31m")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))
    backspace(terminal, 3)
    typeText(terminal, "AAAA")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))

    view.replaceContent(Lines(Vector("replacement")))
    tui.flushRender()
    assertEquals(
      tui.viewportSearchState,
      Some(scalatui.components.ViewportSearchState(false, "", 0, None))
    )

    openSearch(terminal)
    typeText(terminal, "replacement")
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(1))
    tui.stop()
    assertEquals(
      tui.viewportSearchState,
      Some(scalatui.components.ViewportSearchState(false, "", 0, None))
    )

  test("blocked stale search build does not retain the index monitor or survive replacement"):
    val entered              = CountDownLatch(1)
    val release              = CountDownLatch(1)
    @volatile var blockRange = false
    val oldContent           = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         = 1
      override def render(width: Int): ComponentRender                                    = ComponentRender.text("old needle")
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        if blockRange then
          entered.countDown()
          release.await()
        ComponentRender.text(Option.when(startRow === 0)("old needle").toVector)
    val view                 = ScrollView(oldContent, primary = true)
    val terminal             = VirtualTerminal(16, 2)
    val tui                  = TUI.fullscreen(terminal, view)
    tui.start()
    blockRange = true
    val search               = Thread(() => {
      openSearch(terminal)
      typeText(terminal, "needle")
    })
    search.start()
    assert(entered.await(2, TimeUnit.SECONDS))

    view.replaceContent(Lines(Vector("new content")))
    assertEquals(view.child.isInstanceOf[Lines], true)
    release.countDown()
    search.join(2000)
    tui.flushRender()

    assertEquals(search.isAlive, false)
    assertEquals(tui.viewportSearchState.map(_.query), Some(""))
    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))
    tui.stop()

  test("oversized grapheme scan unit stops search without payload retention"):
    val hugeCluster = "a" + "\u0301".repeat(5000)
    val view        = ScrollView(Lines(Vector(hugeCluster + "SECRET")), primary = true)
    val terminal    = VirtualTerminal(16, 2)
    val tui         = TUI.fullscreen(terminal, view)
    tui.start()
    openSearch(terminal)
    typeText(terminal, "SECRET")

    assertEquals(tui.viewportSearchState.map(_.matchCount), Some(0))
    assert(tui.testRuntimeCounters.snapshot.searchScans <= 3L)
    tui.stop()

  private def openSearch(terminal: VirtualTerminal): Unit =
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("f"),
      KeyModifiers(ctrl = true, shift = true)
    ))

  private def typeText(terminal: VirtualTerminal, value: String): Unit =
    scalatui.unicode.Unicode.graphemeClusters(value).foreach { grapheme =>
      terminal.sendInput(TerminalInput.Key(TerminalKey.Character(grapheme)))
    }

  private def backspace(terminal: VirtualTerminal, count: Int): Unit =
    Vector.fill(count)(()).foreach(_ =>
      terminal.sendInput(TerminalInput.Key(TerminalKey.Backspace))
    )

  private def next(terminal: VirtualTerminal): Unit =
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

  private def previous(terminal: VirtualTerminal): Unit =
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter, KeyModifiers(shift = true)))
