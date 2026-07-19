package scalatui.core

import scalatui.TestInputStreams

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.components.{
  Box,
  Editor,
  EditorOptions,
  Loader,
  LoaderIndicatorOptions,
  LoaderOptions,
  Text
}
import scalatui.terminal.{
  ImageCellDimensions,
  KeyEventType,
  KeyModifiers,
  MouseAction,
  MouseButton,
  MouseInputContext,
  MouseWheelDirection,
  RgbColor,
  Terminal,
  TerminalColorProtocol,
  TerminalColorScheme,
  TerminalCursorProtocol,
  TerminalImageProtocol,
  TerminalInputDrainSupport,
  TerminalInput,
  TerminalKey,
  VirtualTerminal
}

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

class TUISuite extends munit.FunSuite:
  private def awaitWaiting(thread: Thread, description: String): Unit =
    val deadline = System.currentTimeMillis() + 5000L
    while (thread.getState !== Thread.State.WAITING) && System.currentTimeMillis() < deadline do
      Thread.`yield`()
    assert(
      thread.getState === Thread.State.WAITING,
      s"$description did not enter its waiting state before the deadline"
    )

  final class MutableLine(var value: String) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(Vector(value))

  final class ReadyLine(ready: CountDownLatch, value: String) extends Component:
    override def render(width: Int): ComponentRender =
      ready.countDown()
      ComponentRender.text(Vector(value))

  final class MutableFrame(var values: Vector[String])  extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(values)
  final class MutableRender(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  private val formerCursorApc = "\u001b_" + "pi" + ":c\u001b\\"

  final class AsyncStartupTerminal(
      inputs: Vector[TerminalInput],
      publishResize: Boolean = false,
      gatePublication: Boolean = false
  ) extends Terminal:
    private val writesBuffer        = scala.collection.mutable.ArrayBuffer.empty[String]
    private val publicationStarted  = CountDownLatch(1)
    private val publicationGate     = CountDownLatch(if gatePublication then 1 else 0)
    private val publicationComplete = CountDownLatch(1)
    private val publicationFailure  = AtomicReference[Throwable]()
    private val publicationThread   = AtomicReference[Thread]()
    private var inputHandler        = Option.empty[TerminalInput => Unit]

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      inputHandler = Some(onInput)
      val publisher = Thread(() =>
        try
          publicationStarted.countDown()
          publicationGate.await()
          if publishResize then onResize()
          inputs.foreach(onInput)
        catch case failure: Throwable => publicationFailure.set(failure)
        finally publicationComplete.countDown()
      )
      publisher.setDaemon(true)
      publicationThread.set(publisher)
      publisher.start()

    def awaitPublicationStart(): Unit =
      assert(publicationStarted.await(1, TimeUnit.SECONDS), "startup publisher did not start")

    def releasePublication(): Unit = publicationGate.countDown()

    def joinPublisher(): Unit =
      val publisher = publicationThread.get()
      if publisher ne null then
        publisher.join(1000)
        assert(!publisher.isAlive, "startup publisher did not terminate")

    def publicationFinished: Boolean = publicationComplete.getCount <= 0L

    def awaitPublication(): Unit =
      assert(publicationComplete.await(1, TimeUnit.SECONDS), "startup publication did not finish")
      Option(publicationFailure.get()).foreach(throw _)

    def emit(input: TerminalInput): Unit = inputHandler.foreach(handler => handler(input))

    override def stop(): Unit = ()

    override def write(data: String): Unit = writesBuffer.synchronized(writesBuffer += data)

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit =
      if lines > 0 then write(s"\u001b[${lines}B")
      else if lines < 0 then write(s"\u001b[${-lines}A")

    override def hideCursor(): Unit      = write("\u001b[?25l")
    override def showCursor(): Unit      = write("\u001b[?25h")
    override def clearLine(): Unit       = write("\u001b[K")
    override def clearFromCursor(): Unit = write("\u001b[J")
    override def clearScreen(): Unit     = write("\u001b[2J\u001b[H")

    def output: String = writesBuffer.synchronized(writesBuffer.mkString)

  final class StartFailingTerminal extends Terminal:
    private val writesBuffer = scala.collection.mutable.ArrayBuffer.empty[String]
    var stopCalled           = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      throw RuntimeException("start failed")

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit = writesBuffer += data

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = write("\u001b[?25l")
    override def showCursor(): Unit       = write("\u001b[?25h")
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = write("\u001b[2J\u001b[H")

    def output: String = writesBuffer.mkString
  final class MouseLine(
      name: String,
      lines: Vector[String] = Vector("line"),
      result: InputResult = InputResult.Render
  ) extends Component,
        MouseInputHandler:
    val events                                                        = scala.collection.mutable.ArrayBuffer.empty[(String, MouseInputContext)]
    var renders                                                       = 0
    override def render(width: Int): ComponentRender                  =
      renders += 1
      ComponentRender.text(lines)
    override def handleMouse(context: MouseInputContext): InputResult =
      events += name -> context
      result

  final class IgnoringMouseLine(name: String, lines: Vector[String] = Vector("line"))
      extends Component,
        MouseInputHandler:
    val events                                                        = scala.collection.mutable.ArrayBuffer.empty[String]
    override def render(width: Int): ComponentRender                  = ComponentRender.text(lines)
    override def handleMouse(context: MouseInputContext): InputResult =
      events += name
      InputResult.Ignored

  final class MutableLayoutLine(var target: Component) extends Component:
    var beforeLayout: () => Unit = () => ()

    override def render(width: Int): ComponentRender = ComponentRender.text("same")

    override def renderFrame(width: Int, row: Int, col: Int): RenderedFrame =
      beforeLayout()
      val rendered = render(width)
      val bounds   = LayoutBounds(row, col, width, rendered.lines.length)
      RenderedFrame(rendered, LayoutNode(this, bounds, Vector(LayoutNode(target, bounds))))

  final class FocusableLine extends Component, Focusable:
    private var isFocused                            = false
    override def focused: Boolean                    = isFocused
    override def focused_=(value: Boolean): Unit     = isFocused = value
    override def render(width: Int): ComponentRender =
      ComponentRender.text(if focused then "focused" else "plain")

  final class QueryFailingTerminal(failingWrite: String) extends Terminal:
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = ()

    override def write(data: String): Unit =
      if data.equals(failingWrite) then throw RuntimeException("write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = ()
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class StopCleanupFailingTerminal(failingWrite: String) extends Terminal:
    @volatile var showCursorCalled = false
    @volatile var stopCalled       = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit =
      if data.equals(failingWrite) then throw RuntimeException("write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = showCursorCalled = true
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class PartialAutoWrapRenderFailingTerminal extends Terminal:
    val writes                     = scala.collection.mutable.ArrayBuffer.empty[String]
    @volatile var showCursorCalled = false
    @volatile var stopCalled       = false
    private var failedRenderWrite  = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit =
      if data.contains(TUI.AutoWrapOff) && !failedRenderWrite then
        failedRenderWrite = true
        writes += TUI.AutoWrapOff
        throw RuntimeException("render write failed")
      writes += data

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = showCursorCalled = true
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  final class RenderAndStopFailingTerminal extends Terminal:
    private var failedRenderWrite = false

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = throw RuntimeException("stop failed")

    override def write(data: String): Unit =
      if data.contains(TUI.AutoWrapOff) && !failedRenderWrite then
        failedRenderWrite = true
        throw RuntimeException("render write failed")

    override def columns: Int = 20
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = ()
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()
  final class StartupFailingMouseTerminal  extends Terminal,
        scalatui.terminal.TerminalMouseProtocolSupport:
    private var mouseReportingEnabled = false
    val writes                        = scala.collection.mutable.ArrayBuffer.empty[String]
    var stopCalls                     = 0

    override def mouseReportingEnabled_=(enabled: Boolean): Unit =
      mouseReportingEnabled = enabled

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      if mouseReportingEnabled then write(Terminal.MouseProtocol.Enable)
      throw RuntimeException("start failed")

    override def stop(): Unit =
      stopCalls += 1
      if mouseReportingEnabled then write(Terminal.MouseProtocol.Disable)

    override def write(data: String): Unit = writes += data
    override def columns: Int              = 20
    override def rows: Int                 = 5
    override def moveBy(lines: Int): Unit  = ()
    override def hideCursor(): Unit        = ()
    override def showCursor(): Unit        = ()
    override def clearLine(): Unit         = ()
    override def clearFromCursor(): Unit   = ()
    override def clearScreen(): Unit       = ()

  final class CursorQuerySwitchingTerminal extends Terminal,
        scalatui.terminal.TerminalMouseProtocolSupport:
    private var inputHandler: TerminalInput => Unit = _ => ()
    private var resizeHandler: () => Unit           = () => ()
    private var mouseReportingEnabled               = false
    var repliesToCursorQueries                      = true
    var cursorRow                                   = 0
    val writes                                      = scala.collection.mutable.ArrayBuffer.empty[String]

    override def mouseReportingEnabled_=(enabled: Boolean): Unit =
      mouseReportingEnabled = enabled

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      inputHandler = onInput
      resizeHandler = onResize
      if mouseReportingEnabled then write(Terminal.MouseProtocol.Enable)

    override def stop(): Unit =
      if mouseReportingEnabled then write(Terminal.MouseProtocol.Disable)
      inputHandler = _ => ()
      resizeHandler = () => ()

    override def write(data: String): Unit =
      writes += data
      if repliesToCursorQueries && data.contains(TerminalCursorProtocol.CursorPositionQuery) then
        val delivery = Thread(() => sendCursorReport(), "cursor-query-switching-terminal-input")
        delivery.setDaemon(true)
        delivery.start()

    override def columns: Int             = 20
    override def rows: Int                = 10
    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = ()
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

    def sendMouse(input: TerminalInput.Mouse): Unit = inputHandler(input)
    def sendResize(): Unit                          = resizeHandler()
    def sendCursorReport(): Unit                    =
      val response = s"\u001b[${cursorRow + 1};1R"
      scalatui.terminal.TerminalInputBuffer()
        .process(scalatui.terminal.TerminalInputChunk(
          response.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ))
        .foreach(inputHandler)

  final class DrainingTerminal extends Terminal, TerminalInputDrainSupport:
    var drainCalls     = 0
    var stopCalls      = 0
    var lastMaxMillis  = Option.empty[Long]
    var lastIdleMillis = Option.empty[Long]

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()

    override def stop(): Unit = stopCalls += 1

    override def drainInput(maxMillis: Long, idleMillis: Long): Unit =
      assert(maxMillis >= 0L)
      assert(idleMillis >= 0L)
      lastMaxMillis = Some(maxMillis)
      lastIdleMillis = Some(idleMillis)
      drainCalls += 1

    override def write(data: String): Unit = ()
    override def columns: Int              = 20
    override def rows: Int                 = 5
    override def moveBy(lines: Int): Unit  = ()
    override def hideCursor(): Unit        = ()
    override def showCursor(): Unit        = ()
    override def clearLine(): Unit         = ()
    override def clearFromCursor(): Unit   = ()
    override def clearScreen(): Unit       = ()

  test("first render writes full frame with synchronized output"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.contains(TUI.SyncStart))
    assert(output.contains("hello" + TUI.LineReset))
    assert(output.contains(TUI.SyncEnd))
    assert(!output.contains("\u001b[2J\u001b[H\u001b[3J"), output)
    assertNoAlternateScreen(output)

  test("default mode stop does not emit alternate screen"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    tui.stop()

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(output.contains("\r\n\u001b[?25h"), output)

  test("alternate-screen mode enters before first rendered frame"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    tui.start()

    val output = terminal.output
    assert(output.startsWith(TUI.AlternateScreenEnter), output)
    assert(output.indexOf(TUI.AlternateScreenEnter) < output.indexOf(TUI.SyncStart), output)
    assert(
      output.contains(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("alternate-screen mode handles independent resize publication during terminal start"):
    val terminal = AsyncStartupTerminal(Vector.empty, publishResize = true)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    tui.start()
    terminal.awaitPublication()

    val output = terminal.output
    assert(output.startsWith(TUI.AlternateScreenEnter), output)
    assert(output.indexOf(TUI.AlternateScreenEnter) < output.indexOf(TUI.SyncStart), output)
    assert(output.contains("hello" + TUI.LineReset), output)

  test("start returns while independent startup publication remains gated"):
    val terminal      = AsyncStartupTerminal(Vector.empty, gatePublication = true)
    val tui           = TUI(terminal)
    val startReturned = CountDownLatch(1)
    val startFailure  = AtomicReference[Throwable]()
    val startThread   = Thread(() =>
      try tui.start()
      catch case failure: Throwable => startFailure.set(failure)
      finally startReturned.countDown()
    )

    startThread.start()
    try
      terminal.awaitPublicationStart()
      assert(startReturned.await(1, TimeUnit.SECONDS), "TUI start waited for startup publisher")
      Option(startFailure.get()).foreach(throw _)
      assert(!terminal.publicationFinished, "startup publication completed while gated")
    finally
      terminal.releasePublication()
      terminal.joinPublisher()
      startThread.join(1000)
      assert(!startThread.isAlive, "TUI start thread did not terminate")

  test("independent startup input publication preserves input order"):
    val terminal  = AsyncStartupTerminal(Vector(
      TerminalInput.Key(TerminalKey.Character("a")),
      TerminalInput.Key(TerminalKey.Character("b")),
      TerminalInput.Key(TerminalKey.Character("c"))
    ))
    val received  = scala.collection.mutable.ArrayBuffer.empty[String]
    val component = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(Vector(received.mkString))

      override def handleInputResult(input: TerminalInput): InputResult = input match
        case TerminalInput.Key(TerminalKey.Character(value), _) =>
          received += value
          InputResult.Render
        case _                                                  => InputResult.Ignored
    val tui       = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(component)
    tui.setFocus(component)

    tui.start()
    terminal.awaitPublication()

    assertEquals(received.toVector, Vector("a", "b", "c"))

  test("input emitted while startup input drains preserves order"):
    val terminal  = AsyncStartupTerminal(Vector(
      TerminalInput.Key(TerminalKey.Character("a"))
    ))
    val received  = scala.collection.mutable.ArrayBuffer.empty[String]
    val component = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(Vector(received.mkString))

      override def handleInputResult(input: TerminalInput): InputResult = input match
        case TerminalInput.Key(TerminalKey.Character("a"), _)   =>
          terminal.emit(TerminalInput.Key(TerminalKey.Character("b")))
          received += "a"
          InputResult.Render
        case TerminalInput.Key(TerminalKey.Character(value), _) =>
          received += value
          InputResult.Render
        case _                                                  => InputResult.Ignored
    val tui       = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(component)
    tui.setFocus(component)

    tui.start()
    terminal.awaitPublication()

    assertEquals(received.toVector, Vector("a", "b"))

  test("alternate-screen mode does not enter when terminal start fails"):
    val terminal = StartFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    interceptMessage[RuntimeException]("start failed"):
      tui.start()

    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)
    assert(terminal.stopCalled)

  test("alternate-screen mode exits on stop without cursor parking"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableFrame(Vector("first", "second")))
    tui.start()
    terminal.clearWrites()

    tui.stop()

    val output = terminal.output
    assert(output.contains("\u001b[?25h"), output)
    assert(output.contains(TUI.AlternateScreenExit), output)
    assertEquals(countOccurrences(output, TUI.AlternateScreenExit), 1)
    assert(!output.contains("\r\n"), output)

  test("alternate-screen mode stop is idempotent"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    tui.stop()
    tui.stop()

    assertEquals(countOccurrences(terminal.output, TUI.AlternateScreenExit), 1)

  test("alternate-screen render write failure restores terminal state"):
    val terminal = PartialAutoWrapRenderFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    val output = terminal.writes.mkString
    assertEquals(failure.getMessage, "render write failed")
    assert(output.contains(TUI.AlternateScreenEnter), output)
    assert(output.contains(TUI.AutoWrapOff), output)
    assert(output.contains(TUI.AutoWrapOn), output)
    assert(output.contains(TUI.AlternateScreenExit), output)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("startup failure preserves original exception when cleanup fails"):
    val terminal = RenderAndStopFailingTerminal()
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "render write failed")
    assertEquals(failure.getSuppressed.toVector.map(_.getMessage), Vector("stop failed"))

  test("render write failure restores autowrap during cleanup"):
    val terminal = PartialAutoWrapRenderFailingTerminal()
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "render write failed")
    assert(terminal.writes.contains(TUI.AutoWrapOff), terminal.writes.toVector)
    assert(terminal.writes.contains(TUI.AutoWrapOn), terminal.writes.toVector)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("mouse reporting is disabled by default"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)

    tui.start()
    tui.stop()

    assert(!terminal.output.contains(Terminal.MouseProtocol.Enable), terminal.output)
    assert(!terminal.output.contains(Terminal.MouseProtocol.Disable), terminal.output)

  test("mouse input is ignored when mouse option is disabled"):
    val terminal = VirtualTerminal(20, 5)
    val target   = MouseLine("target")
    var observed = false
    val tui      = TUI(terminal)
    tui.addChild(target)
    tui.addInputListener {
      case _: TerminalInput.Mouse =>
        observed = true
        InputResult.Render
      case _                      => InputResult.Ignored
    }

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))

    assert(!observed)
    assertEquals(target.events.toVector, Vector.empty)

  test("mouse reporting option enables on start and disables on stop"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))

    tui.start()
    tui.stop()

    assert(terminal.output.contains(Terminal.MouseProtocol.Enable), terminal.output)
    assert(terminal.output.contains(Terminal.MouseProtocol.Disable), terminal.output)

  test("mouse reporting startup failure disables reporting"):
    val terminal = StartupFailingMouseTerminal()
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))

    intercept[RuntimeException](tui.start())

    assert(terminal.writes.contains(Terminal.MouseProtocol.Enable), terminal.writes.toVector)
    assert(terminal.writes.contains(Terminal.MouseProtocol.Disable), terminal.writes.toVector)
    assertEquals(terminal.stopCalls, 1)

  test("mouse routing tries deepest child before ancestor and preserves focus"):
    val terminal = VirtualTerminal(20, 5)
    val parent   = MouseLine("parent", result = InputResult.Render)
    val child    = MouseLine("child")
    val focus    = FocusableLine()
    val nested   = Container()
    nested.addChild(child)
    val root     = Container()
    root.addChild(parent)
    root.addChild(nested)
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(root)
    tui.addChild(focus)
    tui.setFocus(focus)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 1, col = 0))

    assertEquals(child.events.map(_._1).toVector, Vector("child"))
    assertEquals(parent.events.map(_._1).toVector, Vector.empty)
    assert(focus.focused)

  test("mouse routing reaches a child through normalized Box padding"):
    val terminal = VirtualTerminal(20, 10)
    val child    = MouseLine("boxed")
    val box      = Box(paddingX = 2, paddingY = 1)
    box.addChild(child)
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(box)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 1, col = 2))

    assertEquals(child.events.map(_._1).toVector, Vector("boxed"))
    val context = child.events.head._2
    assertEquals(context.boundsRow, 1)
    assertEquals(context.boundsCol, 2)
    assertEquals(context.boundsWidth, 16)
    assertEquals(context.boundsHeight, 1)
    assertEquals(context.localRow, 0)
    assertEquals(context.localCol, 0)

  test("resize-invalidated render keeps the committed mouse layout until replacement"):
    val terminal  = VirtualTerminal(20, 5)
    val committed = MouseLine("committed")
    val rejected  = MouseLine("rejected")
    val layout    = MutableLayoutLine(committed)
    val tui       = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(layout)
    tui.start()

    layout.target = rejected
    var injected = false
    layout.beforeLayout = () =>
      if !injected then
        injected = true
        layout.beforeLayout = () => ()
        terminal.resize(20, 5)
        terminal.sendMouse(
          TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0)
        )

    tui.requestRender()
    tui.flushRender()

    assertEquals(committed.events.map(_._1).toVector, Vector("committed"))
    assertEquals(rejected.events.map(_._1).toVector, Vector.empty)

  test("accepted no-output render publishes changed logical mouse ownership"):
    val terminal    = VirtualTerminal(20, 5)
    val previous    = MouseLine("previous")
    val replacement = MouseLine("replacement")
    val layout      = MutableLayoutLine(previous)
    val tui         = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(layout)
    tui.start()

    layout.target = replacement
    tui.requestRender()
    tui.flushRender()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))

    assertEquals(previous.events.map(_._1).toVector, Vector.empty)
    assertEquals(replacement.events.map(_._1).toVector, Vector("replacement"))

  test("mouse routing maps terminal rows to frame rows when the frame starts below prior output"):
    val terminal = VirtualTerminal(20, 10)
    terminal.setCursorPosition(row = 4)
    val target   = MouseLine("target")
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(target)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))

    assertEquals(target.events.map(_._1).toVector, Vector("target"))
    assertEquals(target.events.head._2.boundsRow, 4)
    assertEquals(target.events.head._2.localRow, 0)

  test("mouse routing accounts for terminal scrolling during the initial frame render"):
    val terminal = VirtualTerminal(20, 5)
    terminal.setCursorPosition(row = 4)
    val target   = MouseLine("target", Vector("one", "two", "three"))
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(target)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))

    assertEquals(target.events.map(_._1).toVector, Vector("target"))
    assertEquals(target.events.head._2.boundsRow, 2)
    assertEquals(target.events.head._2.localRow, 2)

  test("mouse routing clears stale frame origin when cursor-position query times out"):
    val terminal = CursorQuerySwitchingTerminal()
    val target   = MouseLine("target")
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(target)

    terminal.cursorRow = 4
    terminal.repliesToCursorQueries = true
    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))
    assertEquals(target.events.map(_._1).toVector, Vector("target"))
    tui.stop()

    target.events.clear()
    terminal.repliesToCursorQueries = false
    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))
    tui.stop()

    assertEquals(target.events.toVector, Vector.empty)

  test("mouse routing accepts a cursor-position report after the startup timeout"):
    val terminal = CursorQuerySwitchingTerminal()
    val target   = MouseLine("target")
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(target)

    terminal.cursorRow = 4
    terminal.repliesToCursorQueries = false
    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))
    assertEquals(target.events.toVector, Vector.empty)

    terminal.sendCursorReport()
    assertEquals(target.renders, 2)
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 4, col = 0))
    tui.stop()

    assertEquals(target.events.map(_._1).toVector, Vector("target"))

  test("mouse routing falls back to ancestor when child ignores"):
    val terminal = VirtualTerminal(20, 5)
    val parent   = MouseLine("parent")
    val child    = IgnoringMouseLine("child")
    val nested   = new Component with MouseInputHandler:
      override def render(width: Int): ComponentRender                        =
        ComponentRender.text("nested")
      override def renderFrame(width: Int, row: Int, col: Int): RenderedFrame =
        RenderedFrame(
          ComponentRender.text("nested"),
          LayoutNode(
            this,
            LayoutBounds(row, col, width, 1),
            Vector(
              LayoutNode(child, LayoutBounds(row, col, width, 1))
            )
          )
        )
      override def handleMouse(context: MouseInputContext): InputResult       =
        parent.handleMouse(context)
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(nested)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))

    assertEquals(child.events.toVector, Vector("child"))
    assertEquals(parent.events.map(_._1).toVector, Vector("parent"))

  test("mouse routing uses top overlay then lower visible overlay before base"):
    val terminal = VirtualTerminal(20, 5)
    val base     = MouseLine("base", Vector("base"))
    val lower    = MouseLine("lower", Vector("lower"))
    val top      = MouseLine("top", Vector("top"))
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(base)
    tui.showOverlay(
      lower,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    tui.showOverlay(
      top,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))

    assertEquals(top.events.map(_._1).toVector, Vector("top"))
    assertEquals(lower.events.map(_._1).toVector, Vector.empty)
    assertEquals(base.events.map(_._1).toVector, Vector.empty)

  test("mouse routing falls back to lower overlay and skips hidden overlay"):
    val terminal = VirtualTerminal(20, 5)
    val lower    = MouseLine("lower", Vector("lower"))
    val hidden   = MouseLine("hidden", Vector("hidden"))
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(MutableLine("base"))
    tui.showOverlay(
      lower,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    val handle   = tui.showOverlay(
      hidden,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    handle.setHidden(true)

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))

    assertEquals(hidden.events.map(_._1).toVector, Vector.empty)
    assertEquals(lower.events.map(_._1).toVector, Vector("lower"))

  test("mouse routing ignores events before retained layout and unhandled targets"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    val focus    = FocusableLine()
    tui.addChild(focus)
    tui.setFocus(focus)

    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 0, col = 0))
    assert(focus.focused)

  test("overlay mouse bounds use clamped and clipped layout"):
    val terminal = VirtualTerminal(10, 3)
    val overlay  = MouseLine("overlay", Vector("a", "b", "c", "d"))
    val tui      = TUI(terminal, TUIOptions(mouseInput = true))
    tui.addChild(MutableLine("base"))
    tui.showOverlay(
      overlay,
      OverlayOptions(
        width = Some(OverlaySize.Absolute(4)),
        maxHeight = Some(OverlaySize.Absolute(2)),
        row = Some(OverlaySize.Absolute(99)),
        col = Some(OverlaySize.Absolute(99))
      )
    )

    tui.start()
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 1, col = 6))
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), row = 3, col = 6))

    assertEquals(overlay.events.map(_._2.boundsRow).toVector, Vector(1))
    assertEquals(overlay.events.map(_._2.boundsCol).toVector, Vector(6))
    assertEquals(overlay.events.map(_._2.boundsHeight).toVector, Vector(2))

  test("invalid child cursor geometry cannot use a sibling row before synchronized output"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableRender(ComponentRender(
      Vector("line"),
      Vector.empty,
      Vector(CursorPlacement(1, 0))
    )))
    tui.addChild(MutableLine("sibling"))

    intercept[IllegalArgumentException](tui.start())

    assert(!terminal.output.contains(TUI.SyncStart), terminal.output)

  test("TUI selects the first row-major structured cursor candidate"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableRender(ComponentRender(
      Vector("abcd", "next"),
      Vector.empty,
      Vector(CursorPlacement(1, 0), CursorPlacement(0, 3), CursorPlacement(0, 1))
    )))

    tui.start()

    assert(terminal.output.contains(s"\u001b[1A\r\u001b[1C${TUI.SyncEnd}"), terminal.output)

  test("hardware cursor positioning is disabled by default"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableRender(ComponentRender(
      Vector("ab\u001b[7mc\u001b[27m"),
      Vector.empty,
      Vector(CursorPlacement(0, 2))
    )))

    tui.start()

    assert(terminal.output.contains("ab\u001b[7mc\u001b[27m" + TUI.LineReset), terminal.output)
    assert(!terminal.output.contains(s"\r\u001b[2C${TUI.SyncEnd}"), terminal.output)

  test("hardware cursor positioning moves to structured metadata when enabled"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableRender(ComponentRender(
      Vector("ab\u001b[7mc\u001b[27m"),
      Vector.empty,
      Vector(CursorPlacement(0, 2))
    )))

    tui.start()

    assert(terminal.output.contains(s"\r\u001b[2C${TUI.SyncEnd}"), terminal.output)

  test("hardware cursor positioning preserves no-marker render behavior"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableLine("hello"))

    tui.start()

    assert(terminal.output.contains("hello" + TUI.LineReset + TUI.SyncEnd), terminal.output)
    assert(!terminal.output.contains("\u001b[2C"), terminal.output)

  test("ordinary former cursor APC bytes are visible and cannot override structured metadata"):
    val terminal = VirtualTerminal(80, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableRender(ComponentRender(
      Vector("a" + formerCursorApc + "b"),
      Vector.empty,
      Vector(CursorPlacement(0, 1))
    )))

    tui.start()

    assert(terminal.output.contains(Ansi.visibleControlText(formerCursorApc)), terminal.output)
    assert(terminal.output.contains(s"\r\u001b[1C${TUI.SyncEnd}"), terminal.output)

  test("overlay clipping removes cursor candidates outside clipped rows"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableRender(ComponentRender(
      Vector("base", "", "tail"),
      Vector.empty,
      Vector(CursorPlacement(2, 4))
    )))
    tui.showOverlay(
      MutableRender(ComponentRender(
        Vector("x", "y"),
        Vector.empty,
        Vector(CursorPlacement(1, 0))
      )),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(1)),
        maxHeight = Some(OverlaySize.Absolute(1)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(0)),
        focusCapturing = false
      )
    )

    tui.start()

    assert(terminal.output.contains(s"\r\u001b[4C${TUI.SyncEnd}"), terminal.output)

  test("overlay validates raw cursor metadata before clipping or parent composition"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableFrame(Vector("base", "parent row")))
    tui.showOverlay(
      MutableRender(ComponentRender(
        Vector("overlay"),
        Vector.empty,
        Vector(CursorPlacement(1, 0))
      )),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(7)),
        maxHeight = Some(OverlaySize.Absolute(1)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(0)),
        focusCapturing = false
      )
    )

    intercept[IllegalArgumentException](tui.start())

    assert(!terminal.output.contains(TUI.SyncStart), terminal.output)

  test("cursor-only differential update moves hardware cursor without repainting"):
    val terminal  = VirtualTerminal(20, 5)
    val component = MutableRender(ComponentRender(
      Vector("abc"),
      Vector.empty,
      Vector(CursorPlacement(0, 1))
    ))
    val tui       = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = component.frame.copy(cursorPlacements = Vector(CursorPlacement(0, 2)))
    tui.requestRender()
    tui.flushRender()

    assertEquals(terminal.output, "\r\u001b[2C")

  test("partial render moves to first changed line and writes changed tail"):
    val terminal = VirtualTerminal(20, 5)
    val first    = MutableLine("first")
    val second   = MutableLine("second")
    val tui      = TUI(terminal)
    tui.addChild(first)
    tui.addChild(second)
    tui.start()
    terminal.clearWrites()

    second.value = "changed"
    tui.requestRender()
    tui.flushRender()

    val output = terminal.output
    assert(output.startsWith(TUI.SyncStart), output)
    assert(output.contains("\u001b[J"), output)
    assert(output.contains("changed" + TUI.LineReset), output)
    assert(!output.contains("first" + TUI.LineReset), output)

  test("width change uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("width change redraws with autowrap disabled to avoid resize reflow"):
    val terminal = VirtualTerminal(5, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcde"))
    tui.start()

    val initialOutput = terminal.output
    assert(initialOutput.contains(TUI.AutoWrapOff + "abcde" + TUI.LineReset), initialOutput)
    assert(initialOutput.endsWith(TUI.SyncEnd + TUI.AutoWrapOn), initialOutput)
    terminal.clearWrites()

    terminal.resize(3, 5)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("abc" + Ansi.Reset + TUI.LineReset), output)
    assert(output.endsWith(TUI.SyncEnd + TUI.AutoWrapOn), output)

  test("font-size-like resize uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(8, 4)
    val tui      = TUI(terminal)
    tui.addChild(MutableFrame(Vector("abcdefgh", "ijklmnop", "qrstuvwx")))
    tui.start()

    val initialOutput = terminal.output
    assert(initialOutput.contains("abcdefgh" + TUI.LineReset), initialOutput)
    terminal.clearWrites()

    terminal.resize(5, 2)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("abcde" + Ansi.Reset + TUI.LineReset), output)
    assert(output.contains("ijklm" + Ansi.Reset + TUI.LineReset), output)
    assert(output.contains("qrstu" + Ansi.Reset + TUI.LineReset), output)

  test("alternate-screen resize redraw clears viewport without scrollback clear"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(30, 5)

    val output = terminal.output
    assert(!output.contains(TUI.AlternateScreenEnter), output)
    assert(!output.contains("\u001b[3J"), output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("over-wide lines are sanitized instead of failing"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcd"))

    tui.start()

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)
    assertEquals(tui.lastSanitizedLine.map(_.originalWidth), Some(4))

  test("height change uses pi-tui full clear redraw"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("hello"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(20, 3)

    val output = terminal.output
    assertNoAlternateScreen(output)
    assert(
      output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      output
    )
    assert(output.contains("hello" + TUI.LineReset), output)

  test("zero terminal dimensions are clamped before component rendering"):
    val terminal      = VirtualTerminal(0, 0)
    var renderedWidth = 0
    val component     = new Component:
      override def render(width: Int): ComponentRender =
        renderedWidth = width
        ComponentRender.text("x")
    val tui           = TUI(terminal)
    tui.addChild(component)

    tui.start()

    assertEquals(renderedWidth, 1)
    assert(terminal.output.contains("x" + TUI.LineReset), terminal.output)

  test("styled and unicode over-wide lines are sanitized by visible width"):
    val terminal = VirtualTerminal(3, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("\u001b[31ma界b"))

    tui.start()

    val plainLines  = terminal.viewportLines.filter(_.nonEmpty)
    val contentLine = plainLines.find(_.contains("a界"))
    assert(contentLine.nonEmpty)
    assert(contentLine.exists(Ansi.visibleWidth(_) <= 3))
    assertEquals(tui.sanitizedLineCount, 1)

  test("one-column render remains width safe"):
    val terminal = VirtualTerminal(1, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("界x"))

    tui.start()

    assert(terminal.viewportLines.forall(Ansi.visibleWidth(_) <= 1), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("shrinking terminal to one column repaints width-safe output with pi-tui full clear"):
    val terminal = VirtualTerminal(4, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("界ab"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(1, 5)

    assertNoAlternateScreen(terminal.output)
    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      terminal.output
    )
    assert(terminal.viewportLines.forall(Ansi.visibleWidth(_) <= 1), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("full-frame output makes unsupported controls visible and inert"):
    val terminal = VirtualTerminal(80, 5)
    val rejected = "\u001b]52;c;payload\u0007\u001b_Gkitty\u001b\\a\u0008b"
    val tui      = TUI(terminal)
    tui.addChild(MutableLine(rejected))

    tui.start()

    assert(!terminal.output.contains("\u001b]52;c;payload"))
    assert(!terminal.output.contains("\u001b_Gkitty"))
    assert(terminal.output.contains(Ansi.visibleControlText(rejected)))
  test("full-frame output keeps complete and unterminated DCS payloads inert"):
    val embedded = "payload\u001b[31m\u001b]8;;https://example.com\u0007link"
    Vector("\u001bP" + embedded + "\u001b\\", "\u001bP" + embedded).foreach { candidate =>
      val terminal = VirtualTerminal(256, 5)
      val tui      = TUI(terminal)
      tui.addChild(MutableLine(candidate))

      tui.start()

      assert(!terminal.output.contains("\u001bP"), terminal.output)
      assert(!terminal.output.contains("\u001b]8;;https://example.com"), terminal.output)
      assert(terminal.output.contains(Ansi.visibleControlText(candidate)), terminal.output)
    }

  test("input-triggered over-wide render is sanitized without uncaught exception"):
    val terminal  = VirtualTerminal(3, 5)
    val component = new Component:
      var value                                            = "ok"
      override def handleInput(input: TerminalInput): Unit = value = "abcdef"
      override def render(width: Int): ComponentRender     = ComponentRender.text(Vector(value))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assert(terminal.output.contains("abc" + Ansi.Reset + TUI.LineReset), terminal.output)
    assertEquals(tui.sanitizedLineCount, 1)

  test("unrecoverable render failure restores terminal state through run"):
    val terminal  = VirtualTerminal(20, 5)
    val ready     = CountDownLatch(1)
    val component = new Component:
      var fail                                             = false
      override def handleInput(input: TerminalInput): Unit = fail = true
      override def render(width: Int): ComponentRender     =
        ready.countDown()
        if fail then throw RuntimeException("boom")
        ComponentRender.text("stable")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val failure   = AtomicReference[Throwable](null)
    val thread    = Thread(() =>
      try tui.run()
      catch case e: Throwable => failure.set(e)
    )
    thread.start()
    assert(
      ready.await(5, TimeUnit.SECONDS),
      "TUI run did not complete its initial render before failure input"
    )

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after render failure")

    assertEquals(Option(failure.get()).map(_.getMessage), Some("boom"))
    assert(terminal.output.contains("\u001b[?25h"), terminal.output)

  test("requestRender coalesces until flush"):
    val terminal = VirtualTerminal(20, 5)
    val line     = MutableLine("one")
    val tui      = TUI(terminal)
    tui.addChild(line)
    tui.start()
    terminal.clearWrites()

    line.value = "two"
    tui.requestRender()
    tui.requestRender()
    assertEquals(terminal.output, "")

    tui.flushRender()
    assert(terminal.output.contains("two" + TUI.LineReset), terminal.output)

  test("contextual loader state changes request coalesced renders and detach safely"):
    val terminal = VirtualTerminal(20, 5)
    val loader   = Loader(LoaderOptions(
      indicator = LoaderIndicatorOptions(Vector("a", "b")),
      leadingBlankLine = false
    ))
    val tui      = TUI(terminal)
    tui.addChild(loader)
    tui.start()
    terminal.clearWrites()

    loader.setMessage("Go")
    assertEquals(terminal.output, "")
    tui.flushRender()
    assert(terminal.output.contains("a Go"), terminal.output)

    terminal.clearWrites()
    loader.start()
    loader.tick()
    assertEquals(terminal.output, "")
    tui.flushRender()
    assert(terminal.output.contains("b Go"), terminal.output)

    terminal.clearWrites()
    tui.removeChild(loader)
    assert(!tui.children.exists(_ eq loader))
    loader.setMessage("detached")
    tui.flushRender()
    assertEquals(terminal.output, "")

  test("focused component receives input and rerenders"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      var value                                            = "empty"
      override def handleInput(input: TerminalInput): Unit = input match
        case TerminalInput.Key(TerminalKey.Character(text), _) => value = text
        case _                                                 => ()
      override def render(width: Int): ComponentRender     = ComponentRender.text(Vector(value))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assert(terminal.output.contains("x" + TUI.LineReset), terminal.output)

  test("global input listener observes typed input before focused component"):
    val terminal     = VirtualTerminal(20, 5)
    var listenerLog  = Vector.empty[TerminalInput]
    var componentLog = Vector.empty[TerminalInput]
    val component    = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        componentLog :+= input
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui          = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.addInputListener { input =>
      listenerLog :+= input
      InputResult.Ignored
    }
    tui.start()

    val input = TerminalInput.Key(TerminalKey.Character("x"))
    terminal.sendInput(input)

    assertEquals(listenerLog, Vector(input))
    assertEquals(componentLog, Vector(input))

  test("global input listener handled result stops focused routing and can render"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = 0
    val line      = MutableLine("before")
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered += 1
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("component"))
    val tui       = TUI(terminal)
    tui.addChild(line)
    tui.addChild(component)
    tui.setFocus(component)
    tui.addInputListener { _ =>
      line.value = "after"
      InputResult.Render
    }
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, 0)
    assert(terminal.output.contains("after" + TUI.LineReset), terminal.output)

  test("global input listener can be removed"):
    val terminal       = VirtualTerminal(20, 5)
    var listenerCalls  = 0
    var componentCalls = 0
    val component      = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        componentCalls += 1
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui            = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val remove         = tui.addInputListener { _ =>
      listenerCalls += 1
      InputResult.Ignored
    }
    tui.start()

    remove()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(listenerCalls, 0)
    assertEquals(componentCalls, 1)

  test("global input listener can request exit"):
    val terminal = VirtualTerminal(20, 5)
    val ready    = CountDownLatch(1)
    val tui      = TUI(terminal)
    tui.addChild(ReadyLine(ready, "stable"))
    tui.addInputListener(_ => InputResult.Exit)
    val thread   = Thread(() => tui.run())
    thread.start()
    assert(
      ready.await(5, TimeUnit.SECONDS),
      "TUI run did not complete its initial render before listener exit input"
    )

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after listener requested exit")

    assert(terminal.output.contains("\u001b[?25h"), terminal.output)

  test("key release is ignored by default"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = 0
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered += 1
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    terminal.sendInput(TerminalInput.KeyEvent(
      TerminalKey.Character("x"),
      eventType = scalatui.terminal.KeyEventType.Release
    ))

    assertEquals(delivered, 0)

  test("key release is delivered to components that opt in"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Option.empty[TerminalInput]
    val component = new Component:
      override def wantsKeyRelease: Boolean                             = true
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered = Some(input)
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    val release = TerminalInput.KeyEvent(
      TerminalKey.Character("x"),
      eventType = scalatui.terminal.KeyEventType.Release
    )
    terminal.sendInput(release)

    assertEquals(delivered, Some(release))

  test("editor submit callback mutations rerender immediately"):
    val terminal = VirtualTerminal(40, 8)
    val output   = Text("Submitted: (none)")
    val editor   = Editor(
      "hello",
      EditorOptions(onSubmit = text => output.text = s"Submitted: $text")
    )
    val tui      = TUI(terminal)
    tui.addChild(output)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("Submitted: hello"), terminal.output)

  test("input result can report handled input without render"):
    val terminal  = VirtualTerminal(20, 5)
    var handled   = 0
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        handled += 1
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(handled, 1)
    assertEquals(terminal.output, "")

  test("input result can report ignored input"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult = InputResult.Ignored
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(terminal.output, "")

  test("input result can request exit"):
    val terminal  = VirtualTerminal(20, 5)
    val ready     = CountDownLatch(1)
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult = InputResult.Exit
      override def render(width: Int): ComponentRender                  =
        ready.countDown()
        ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    val thread    = Thread(() => tui.run())
    thread.start()
    assert(
      ready.await(5, TimeUnit.SECONDS),
      "TUI run did not complete its initial render before component exit input"
    )

    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after component requested exit")

    assert(terminal.output.contains("\r\n\u001b[?25h"), terminal.output)

  test("TUI title and progress helpers report virtual terminal support"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)

    assertEquals(tui.setTerminalTitle("demo"), true)
    assertEquals(tui.setTerminalProgress(active = true), true)
    assertEquals(tui.setTerminalProgress(active = false), true)

    assert(terminal.output.contains("\u001b]0;demo\u0007"), terminal.output)
    assert(terminal.output.contains("\u001b]9;4;3\u0007"), terminal.output)
    assert(terminal.output.contains("\u001b]9;4;0\u0007"), terminal.output)

  test("TUI background color query completes with success"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    terminal.clearWrites()
    var results  = Vector.empty[TerminalQueryResult[RgbColor]]

    tui.queryTerminalBackgroundColor(result => results :+= result)
    assert(terminal.output.contains(TerminalColorProtocol.BackgroundColorQuery), terminal.output)
    TestInputStreams.parse("\u001b]11;#112233\u0007").foreach(terminal.sendInput)

    assertEquals(results, Vector(TerminalQueryResult.Success(RgbColor(17, 34, 51))))

  test("TUI queries complete recognized malformed replies as invalid responses"):
    val terminal   = VirtualTerminal(20, 5)
    val tui        = TUI(terminal)
    tui.start()
    var background = Vector.empty[TerminalQueryResult[RgbColor]]
    var scheme     = Vector.empty[TerminalQueryResult[TerminalColorScheme]]

    tui.queryTerminalBackgroundColor(result => background :+= result)
    TestInputStreams.parse("\u001b]11;not-a-color\u0007").foreach(terminal.sendInput)
    tui.queryTerminalColorScheme(result => scheme :+= result)
    TestInputStreams.parse("\u001b[?997;3n").foreach(terminal.sendInput)

    assertEquals(background, Vector(TerminalQueryResult.InvalidResponse))
    assertEquals(scheme, Vector(TerminalQueryResult.InvalidResponse))

  test("TUI stopped query completes synchronously without writing"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var result   = Option.empty[TerminalQueryResult[RgbColor]]

    val cancel = tui.queryTerminalBackgroundColor(value => result = Some(value))

    assertEquals(result, Some(TerminalQueryResult.Stopped))
    assertEquals(terminal.output, "")
    cancel()
    cancel()

  test("TUI query cancellation is idempotent and silent"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    var calls    = 0

    val cancel = tui.queryTerminalColorScheme(_ => calls += 1)
    cancel()
    cancel()
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)

    assertEquals(calls, 0)

  test("TUI query emission failure completes with failed and stops"):
    val terminal = QueryFailingTerminal(TerminalColorProtocol.BackgroundColorQuery)
    val tui      = TUI(terminal)
    tui.start()
    var result   = Option.empty[TerminalQueryResult[RgbColor]]

    tui.queryTerminalBackgroundColor(value => result = Some(value))

    assert(result.exists {
      case TerminalQueryResult.Failed(cause) => cause.getMessage.equals("write failed")
      case _                                 => false
    })

  test("TUI color-scheme query completes with success"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()
    var results  = Vector.empty[TerminalQueryResult[TerminalColorScheme]]

    tui.queryTerminalColorScheme(result => results :+= result)
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)

    assertEquals(
      results,
      Vector(TerminalQueryResult.Success(TerminalColorScheme.Light))
    )

  test("color-scheme notifications can be enabled disabled and unsubscribed"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var schemes  = Vector.empty[TerminalColorScheme]
    val remove   = tui.onTerminalColorSchemeChange(scheme => schemes :+= scheme)

    tui.start()
    terminal.clearWrites()
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector.empty)

    tui.setTerminalColorSchemeNotifications(enabled = true)
    assert(
      terminal.output.contains(TerminalColorProtocol.EnableColorSchemeNotifications),
      terminal.output
    )
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector(TerminalColorScheme.Dark))

    terminal.clearWrites()
    remove()
    TestInputStreams.parse("\u001b[?997;2n").foreach(terminal.sendInput)
    assertEquals(schemes, Vector(TerminalColorScheme.Dark))

    tui.setTerminalColorSchemeNotifications(enabled = false)
    assert(
      terminal.output.contains(TerminalColorProtocol.DisableColorSchemeNotifications),
      terminal.output
    )

  test("color-scheme listeners run outside lifecycle lock"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    var joined   = false
    tui.onTerminalColorSchemeChange { _ =>
      val thread = Thread(() => tui.requestRender())
      thread.start()
      thread.join(1000)
      joined = !thread.isAlive
    }

    tui.start()
    tui.setTerminalColorSchemeNotifications(enabled = true)
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)

    assertEquals(joined, true)

  test("terminal protocol replies are not routed to focused component"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    tui.queryTerminalBackgroundColor(_ => ())
    TestInputStreams.parse("\u001b]11;not-a-color\u0007").foreach(terminal.sendInput)
    TestInputStreams.parse("\u001b[?997;1n").foreach(terminal.sendInput)
    TestInputStreams.parse("\u001b[?997;3n").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("terminal protocol replies are not routed to global input listeners"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val tui       = TUI(terminal)
    tui.addInputListener { input =>
      delivered :+= input
      InputResult.Ignored
    }
    tui.start()

    TestInputStreams.parse("\u001b[6;12;24t").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("cell-size reply is consumed before component input routing"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    TestInputStreams.parse("\u001b[6;12;24t").foreach(terminal.sendInput)
    assertEquals(delivered, Vector.empty)
    assertEquals(TerminalImageProtocol.cellDimensions, ImageCellDimensions(24, 12))

  test("valid cell-size reply triggers repaint"):
    val terminal  = VirtualTerminal(20, 5)
    val component = new Component:
      override def render(width: Int): ComponentRender =
        val dimensions = TerminalImageProtocol.cellDimensions
        ComponentRender.text(s"${dimensions.widthPx}x${dimensions.heightPx}")
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    TestInputStreams.parse("\u001b[6;10;20t").foreach(terminal.sendInput)

    assert(terminal.output.contains("20x10"), terminal.output)

  test("cell-size reply does not block following input"):
    val terminal  = VirtualTerminal(20, 5)
    var delivered = Vector.empty[TerminalInput]
    val component = new Component:
      override def handleInputResult(input: TerminalInput): InputResult =
        delivered :+= input
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("stable"))
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.setFocus(component)
    tui.start()

    TestInputStreams.parse("\u001b[6;0;24t").foreach(terminal.sendInput)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(delivered, Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  private def assertNoAlternateScreen(output: String): Unit =
    assert(!output.contains(TUI.AlternateScreenEnter), output)
    assert(!output.contains(TUI.AlternateScreenExit), output)

  private def countOccurrences(value: String, needle: String): Int =
    if needle.isEmpty then 0
    else value.sliding(needle.length).count(_ === needle)

  test("typed escape exits when configured"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.exitsOnEscape = true
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Escape))
    tui.run()

    assertEquals(terminal.isRunning, false)

  test("run exits on ctrl+c and stop positions cursor below content"):
    val terminal = VirtualTerminal(20, 5)
    val ready    = CountDownLatch(1)
    val tui      = TUI(terminal)
    tui.addChild(ReadyLine(ready, "hello"))
    val thread   = Thread(() => tui.run())
    thread.start()
    assert(
      ready.await(5, TimeUnit.SECONDS),
      "TUI run did not complete its initial render before ctrl+c"
    )
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after ctrl+c")

    assert(terminal.output.contains("\r\n\u001b[?25h"), terminal.output)

  test("stop restores cursor stops terminal and notifies run when cleanup write fails"):
    val terminal = StopCleanupFailingTerminal(TerminalColorProtocol.DisableColorSchemeNotifications)
    val tui      = TUI(terminal)
    tui.setTerminalColorSchemeNotifications(enabled = true)
    tui.start()
    val failure  = AtomicReference[Throwable](null)
    val thread   = Thread(() =>
      try tui.run()
      catch case e: Throwable => failure.set(e)
    )
    thread.start()
    awaitWaiting(thread, "TUI run thread")

    intercept[RuntimeException](tui.stop())
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after stop cleanup failure")

    assertEquals(failure.get(), null)
    assertEquals(terminal.showCursorCalled, true)
    assertEquals(terminal.stopCalled, true)

  test("stop drains supported terminal before terminal stop and remains idempotent"):
    val terminal = DrainingTerminal()
    val tui      = TUI(terminal)
    tui.start()

    tui.stop()
    tui.stop()

    assertEquals(terminal.drainCalls, 1)
    assertEquals(terminal.stopCalls, 1)

  test("terminal drain helper clamps negative timeout values"):
    val terminal = DrainingTerminal()

    val drained = Terminal.drainInput(terminal, maxMillis = -1L, idleMillis = -2L)

    assertEquals(drained, true)
    assertEquals(terminal.lastMaxMillis, Some(0L))
    assertEquals(terminal.lastIdleMillis, Some(0L))

  test("stop works without optional terminal drain support"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal)
    tui.start()

    tui.stop()

    assertEquals(terminal.isRunning, false)

  test("run does not exit on ctrl+c key release when component opts in"):
    val terminal        = VirtualTerminal(20, 5)
    val ready           = CountDownLatch(1)
    val releaseObserved = CountDownLatch(1)
    val tui             = TUI(terminal)
    val component       = new Component:
      override def wantsKeyRelease: Boolean                             = true
      override def handleInputResult(input: TerminalInput): InputResult =
        input match
          case TerminalInput.KeyEvent(_, _, KeyEventType.Release) => releaseObserved.countDown()
          case _                                                  => ()
        InputResult.NoRender
      override def render(width: Int): ComponentRender                  =
        ready.countDown()
        ComponentRender.text(Vector("stable"))
    tui.addChild(component)
    tui.setFocus(component)
    val thread          = Thread(() => tui.run())
    thread.start()
    assert(
      ready.await(5, TimeUnit.SECONDS),
      "TUI run did not complete its initial render before key release"
    )

    terminal.sendInput(TerminalInput.KeyEvent(
      TerminalKey.Character("c"),
      KeyModifiers(ctrl = true),
      KeyEventType.Release
    ))
    assert(
      releaseObserved.await(5, TimeUnit.SECONDS),
      "focused component did not observe ctrl+c key release"
    )

    assertEquals(thread.isAlive, true)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true)))
    thread.join(5000)
    assert(!thread.isAlive, "TUI run did not terminate after ctrl+c press")

  test("visible overlay is composited over base content"):
    val terminal = VirtualTerminal(10, 3)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("abcdef"))
    tui.showOverlay(
      MutableLine("XYZ"),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(3)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(1))
      )
    )

    tui.start()

    assert(terminal.viewportLines.exists(_.contains("aXYZe")), terminal.output)

  test("non-capturing overlay preserves base input focus"):
    val terminal    = VirtualTerminal(10, 3)
    var baseHits    = 0
    var overlayHits = 0
    val base        = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("base"))
      override def handleInputResult(input: TerminalInput): InputResult =
        baseHits += 1
        InputResult.NoRender
    val overlay     = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("over"))
      override def handleInputResult(input: TerminalInput): InputResult =
        overlayHits += 1
        InputResult.NoRender
    val tui         = TUI(terminal)
    tui.addChild(base)
    tui.setFocus(base)
    tui.showOverlay(overlay, OverlayOptions(focusCapturing = false))
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(baseHits, 1)
    assertEquals(overlayHits, 0)

  test("topmost capturing overlay receives input and restores focus when hidden"):
    val terminal     = VirtualTerminal(10, 3)
    var baseFocused  = false
    var firstHits    = 0
    var secondHits   = 0
    val base         = new Component with Focusable:
      override def render(width: Int): ComponentRender = ComponentRender.text(Vector("base"))
      override def focused: Boolean                    = baseFocused
      override def focused_=(value: Boolean): Unit     = baseFocused = value
    val first        = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("one"))
      override def handleInputResult(input: TerminalInput): InputResult =
        firstHits += 1
        InputResult.NoRender
    val second       = new Component:
      override def render(width: Int): ComponentRender                  = ComponentRender.text(Vector("two"))
      override def handleInputResult(input: TerminalInput): InputResult =
        secondHits += 1
        InputResult.NoRender
    val tui          = TUI(terminal)
    tui.addChild(base)
    tui.setFocus(base)
    val firstHandle  = tui.showOverlay(
      first,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    val secondHandle = tui.showOverlay(
      second,
      OverlayOptions(row = Some(OverlaySize.Absolute(0)), col = Some(OverlaySize.Absolute(0)))
    )
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    secondHandle.hide()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("y")))
    firstHandle.hide()

    assertEquals(secondHits, 1)
    assertEquals(firstHits, 1)
    assertEquals(baseFocused, true)

  test("overlay layout is recomputed after resize with pi-tui full clear"):
    val terminal = VirtualTerminal(20, 4)
    val tui      = TUI(terminal)
    tui.addChild(MutableLine("base"))
    tui.showOverlay(
      MutableLine("wide"),
      OverlayOptions(width = Some(OverlaySize.Percent(100)), anchor = OverlayAnchor.BottomLeft)
    )
    tui.start()
    terminal.clearWrites()

    terminal.resize(2, 2)

    assertNoAlternateScreen(terminal.output)
    assert(
      terminal.output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + "\u001b[2J\u001b[H\u001b[3J"),
      terminal.output
    )
    assert(terminal.viewportLines.exists(_.contains("wi")), terminal.output)
