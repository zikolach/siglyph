package scalatui.core

import scalatui.TestInputStreams

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  MouseInputContext,
  Terminal,
  TerminalImageProtocol,
  TerminalInput,
  VirtualTerminal
}

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.collection.mutable.ArrayBuffer

class NormalResizeRecoverySuite extends munit.FunSuite:
  private final class MutableFrame(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  private final class ProbeTerminal(initialColumns: Int = 20, initialRows: Int = 8)
      extends Terminal:
    val delegate                                                                   = VirtualTerminal(initialColumns, initialRows)
    var startCount                                                                 = 0
    val failNextRenderWrite                                                        = AtomicBoolean(false)
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      startCount += 1
      delegate.start(onInput, onResize)
    override def stop(): Unit                                                      = delegate.stop()
    override def write(data: String): Unit                                         =
      if failNextRenderWrite.compareAndSet(true, false) && data.contains(TUI.SyncStart) then
        delegate.write(data)
        throw IllegalStateException("sensitive write failure")
      delegate.write(data)
    override def columns: Int                                                      = delegate.columns
    override def rows: Int                                                         = delegate.rows
    override def moveBy(lines: Int): Unit                                          = delegate.moveBy(lines)
    override def hideCursor(): Unit                                                = delegate.hideCursor()
    override def showCursor(): Unit                                                = delegate.showCursor()
    override def clearLine(): Unit                                                 = delegate.clearLine()
    override def clearFromCursor(): Unit                                           = delegate.clearFromCursor()
    override def clearScreen(): Unit                                               = delegate.clearScreen()

    def resize(columns: Int, rows: Int): Unit = delegate.resize(columns, rows)

  private def options(
      provider: NormalResizeRecoveryProvider,
      observer: Option[TUIDiagnosticObserver] = None,
      hardwareCursor: Boolean = false,
      mouseInput: Boolean = false
  ): TUIOptions = TUIOptions(
    hardwareCursorPositioning = hardwareCursor,
    mouseInput = mouseInput,
    normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
    diagnosticObserver = observer,
    normalResizeRecovery = Some(provider)
  )

  private def running(
      terminal: VirtualTerminal,
      component: Component,
      provider: NormalResizeRecoveryProvider,
      observer: Option[TUIDiagnosticObserver] = None,
      hardwareCursor: Boolean = false,
      mouseInput: Boolean = false
  ): TUI =
    val tui = TUI(terminal, options(provider, observer, hardwareCursor, mouseInput))
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()
    tui

  test("recovery public context validates positive geometry and options remain additive") {
    assertEquals(TUIOptions().normalResizeRecovery, None)
    assertEquals(NormalResizeRecoveryContext(20, 8, 3).maxRows, 3)
    intercept[IllegalArgumentException](NormalResizeRecoveryContext(0, 8, 3))
    intercept[IllegalArgumentException](NormalResizeRecoveryContext(20, 0, 3))
    intercept[IllegalArgumentException](NormalResizeRecoveryContext(20, 8, 0))
  }

  test("incompatible recovery options fail before terminal startup or output") {
    val provider = NormalResizeRecoveryProvider(_ => Vector.empty)
    val clear    = ProbeTerminal()
    val clearTui = TUI(clear, TUIOptions(normalResizeRecovery = Some(provider)))
    intercept[IllegalArgumentException](clearTui.start())
    assertEquals(clear.startCount, 0)
    assertEquals(clear.delegate.output, "")

    val alternate    = ProbeTerminal()
    val alternateTui = TUI(
      alternate,
      TUIOptions(
        screenMode = TUIScreenMode.Alternate,
        normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
        normalResizeRecovery = Some(provider)
      )
    )
    intercept[IllegalArgumentException](alternateTui.start())
    assertEquals(alternate.startCount, 0)
    assertEquals(alternate.delegate.output, "")
  }

  test("provider runs only for committed resize and generic work remains ordinary") {
    val calls    = AtomicInteger(0)
    val provider = NormalResizeRecoveryProvider { _ =>
      calls.incrementAndGet()
      Vector("history")
    }
    val terminal = VirtualTerminal(20, 8)
    val live     = MutableFrame(ComponentRender.text("live"))
    val tui      = running(terminal, live, provider)

    assertEquals(calls.get(), 0)
    tui.requestRender(force = true)
    tui.flushRender()
    assertEquals(calls.get(), 0)
    tui.appendToScrollback(MutableFrame(ComponentRender.text("append")))
    assertEquals(calls.get(), 0)
    live.frame = ComponentRender.text("changed")
    tui.requestRender()
    tui.flushRender()
    assertEquals(calls.get(), 0)
    TestInputStreams.parse("\u001b[6;12;24t").foreach(terminal.sendInput)
    assertEquals(calls.get(), 0)

    terminal.resize(19, 8)
    assertEquals(calls.get(), 1)
    tui.stop()
  }

  test("budget follows live footprint overlay extension typed rows and empty anchor") {
    val contexts = ArrayBuffer.empty[NormalResizeRecoveryContext]
    val provider = NormalResizeRecoveryProvider { context =>
      contexts += context
      Vector.empty
    }
    val terminal = VirtualTerminal(20, 10)
    val live     = MutableFrame(ComponentRender.text(Vector("one", "two", "three")))
    val tui      = running(terminal, live, provider)
    terminal.resize(19, 10)
    assertEquals(contexts.last.maxRows, 7)

    tui.showOverlay(
      MutableFrame(ComponentRender.text(Vector("overlay-1", "overlay-2"))),
      OverlayOptions(
        row = Some(OverlaySize.Absolute(6)),
        col = Some(OverlaySize.Absolute(0)),
        width = Some(OverlaySize.Absolute(10)),
        focusCapturing = false
      )
    )
    terminal.resize(18, 10)
    assertEquals(contexts.last.maxRows, 2)
    tui.stop()

    val emptyContexts = ArrayBuffer.empty[NormalResizeRecoveryContext]
    val emptyTerminal = VirtualTerminal(20, 10)
    val emptyTui      = running(
      emptyTerminal,
      MutableFrame(ComponentRender.empty),
      NormalResizeRecoveryProvider { context =>
        emptyContexts += context
        Vector.empty
      }
    )
    emptyTerminal.resize(19, 10)
    assertEquals(emptyContexts.last.maxRows, 9)
    emptyTui.stop()

    val payload       = Base64ImagePayload.from("YQ==").toOption.get
    val kitty         = TerminalImageProtocol.encodeKitty(payload, 987654, 1, 3)
    val controlFrame  = ComponentRender(
      Vector.fill(3)(" "),
      Vector(TerminalControlPlacement(0, 0, kitty)),
      Vector.empty
    )
    val controlBudget = ArrayBuffer.empty[Int]
    val controlTerm   = VirtualTerminal(20, 10)
    val controlTui    = running(
      controlTerm,
      MutableFrame(controlFrame),
      NormalResizeRecoveryProvider { context =>
        controlBudget += context.maxRows
        Vector.empty
      }
    )
    controlTerm.resize(19, 10)
    assertEquals(controlBudget.last, 7)
    controlTui.stop()
  }

  test("full live footprint uses zero budget and skips provider") {
    val calls    = AtomicInteger(0)
    val terminal = VirtualTerminal(20, 2)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.text(Vector("one", "two"))),
      NormalResizeRecoveryProvider { _ =>
        calls.incrementAndGet()
        Vector("never")
      }
    )
    terminal.resize(19, 2)
    assertEquals(calls.get(), 0)
    assert(terminal.output.contains("one"))
    assert(terminal.output.contains("two"))
    tui.stop()
  }

  test("recovery sanitizes text and commits before retained frame in one write") {
    val terminal = VirtualTerminal(6, 6)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.text("live")),
      NormalResizeRecoveryProvider(_ =>
        Vector("abcdefghi", "\u001b[31mred\u001b[0m", "\u001b]1337;secret\u0007")
      )
    )

    terminal.resize(5, 6)

    val output = terminal.output
    assert(output.startsWith(TUI.SyncStart + TUI.AutoWrapOff + TUI.NormalScreenViewportClear))
    assert(!output.contains("\u001b[3J"))
    assert(output.indexOf("abcde") < output.indexOf("live"))
    assert(output.contains("\\u00"))
    assert(!output.contains("secret"))
    assert(output.endsWith(TUI.SyncEnd + TUI.AutoWrapOn))
    assertEquals(terminal.writes.length, 1)
    assertEquals(tui.sanitizedLineCount, 2)
    assertEquals(tui.lastSanitizedLine, None)
    tui.stop()
  }

  test("empty recovery adds no blank transition") {
    val terminal = VirtualTerminal(20, 6)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.text("live")),
      NormalResizeRecoveryProvider(_ => Vector.empty)
    )
    terminal.resize(19, 6)
    assert(!terminal.output.contains(TUI.NormalScreenViewportClear + "\r\n"))
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("live"))
    tui.stop()
  }

  test("retained image cleanup and iTerm2 append rejection remain live-frame owned") {
    val payload       = Base64ImagePayload.from("YQ==").toOption.get
    val kitty         = TerminalImageProtocol.encodeKitty(payload, 246810, 1, 1)
    val kittyTerminal = VirtualTerminal(20, 6)
    val kittyTui      = running(
      kittyTerminal,
      MutableFrame(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, kitty)),
        Vector.empty
      )),
      NormalResizeRecoveryProvider(_ => Vector("history"))
    )
    kittyTerminal.resize(19, 6)
    val kittyOutput   = kittyTerminal.output
    assert(kittyOutput.indexOf("a=d") < kittyOutput.indexOf("history"))
    assert(kittyOutput.indexOf("history") < kittyOutput.indexOf("a=T"))
    kittyTui.stop()

    val iterm         = TerminalImageProtocol.encodeITerm2(payload, None, 1, 1)
    val itermTerminal = VirtualTerminal(20, 6)
    val itermTui      = running(
      itermTerminal,
      MutableFrame(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, iterm)),
        Vector.empty
      )),
      NormalResizeRecoveryProvider(_ => Vector("history"))
    )
    itermTerminal.resize(19, 6)
    assert(itermTerminal.output.indexOf("history") < itermTerminal.output.indexOf("1337;File="))
    itermTerminal.clearWrites()
    var result        = Option.empty[AppendResult]
    itermTui.appendToScrollback(
      MutableFrame(ComponentRender.text("append")),
      value => result = Some(value)
    )
    assertEquals(result, Some(AppendResult.Rejected(AppendRejection.RetainedITerm2Control)))
    assertEquals(itermTerminal.output, "")
    itermTui.stop()
  }

  test("recovery remains outside baseline and later append preserves chronology") {
    val terminal = VirtualTerminal(20, 8)
    val live     = MutableFrame(ComponentRender.text("live"))
    val tui      = running(
      terminal,
      live,
      NormalResizeRecoveryProvider(_ => Vector("A"))
    )
    terminal.resize(19, 8)
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("A", "live"))

    terminal.clearWrites()
    live.frame = ComponentRender.text("live-2")
    tui.requestRender()
    tui.flushRender()
    assert(!terminal.output.contains("A"))
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("A", "live-2"))

    terminal.clearWrites()
    tui.appendToScrollback(MutableFrame(ComponentRender.text("B")))
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("A", "B", "live-2"))
    assert(!terminal.output.contains("A"))
    tui.stop()
  }

  test("empty retained frame reserves an anchor below recovery for later append") {
    val terminal = VirtualTerminal(20, 5)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.empty),
      NormalResizeRecoveryProvider(_ => Vector("A"))
    )
    terminal.resize(19, 5)
    assertEquals(terminal.cursorPosition._1, 1)

    tui.appendToScrollback(MutableFrame(ComponentRender.text("B")))
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("A", "B"))
    tui.stop()
  }

  test("empty-frame append scrolling preserves the physical anchor for later mouse layout") {
    final class MouseLine extends Component, MouseInputHandler:
      var received                                                      = Option.empty[MouseInputContext]
      override def render(width: Int): ComponentRender                  = ComponentRender.text("live")
      override def handleMouse(context: MouseInputContext): InputResult =
        received = Some(context)
        InputResult.NoRender

    val terminal = VirtualTerminal(20, 3)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.empty),
      NormalResizeRecoveryProvider(context =>
        Vector.tabulate(context.maxRows)(index => s"A${index + 1}")
      ),
      mouseInput = true
    )
    terminal.resize(19, 3)
    tui.appendToScrollback(MutableFrame(ComponentRender.text("B")))
    val target   = MouseLine()
    tui.addChild(target)
    tui.requestRender()
    tui.flushRender()
    assertEquals(terminal.screenLines.filter(_.nonEmpty), Vector("A2", "B", "live"))

    terminal.sendMouse(TerminalInput.Mouse(
      scalatui.terminal.MouseAction.Press(scalatui.terminal.MouseButton.Left),
      row = 2,
      col = 0
    ))
    assertEquals(target.received.map(_.boundsRow), Some(2))
    tui.stop()
  }

  test("stale candidate is discarded and yields before retrying latest geometry") {
    val contexts = ArrayBuffer.empty[NormalResizeRecoveryContext]
    val events   = ArrayBuffer.empty[TUIDiagnosticEvent]
    val terminal = VirtualTerminal(20, 8)
    var tui      = Option.empty[TUI]
    val provider = NormalResizeRecoveryProvider { context =>
      contexts += context
      if contexts.length === 1 then
        terminal.resize(12, 6)
        tui.get.addChild(MutableFrame(ComponentRender.text("added-before-retry")))
      Vector(s"history-${context.width}")
    }
    tui = Some(running(
      terminal,
      MutableFrame(ComponentRender.text("live")),
      provider,
      Some(TUIDiagnosticObserver(events += _))
    ))
    terminal.resize(16, 7)

    assertEquals(contexts.map(_.width).toVector, Vector(16, 12))
    assert(!terminal.output.contains("history-16"))
    assertEquals(terminal.output.split("history-12", -1).length - 1, 1)
    assert(terminal.output.contains("added-before"))
    assert(events.exists {
      case TUIDiagnosticEvent.ResizeRecovery(
            TUIDiagnosticResizeRecoveryOutcome.Discarded,
            Some(TUIDiagnosticResizeRecoveryFailure.StaleGeometry),
            _,
            _,
            _
          ) => true
      case _ => false
    })
    assert(events.exists {
      case TUIDiagnosticEvent.ResizeRecovery(
            TUIDiagnosticResizeRecoveryOutcome.Completed,
            None,
            _,
            1,
            _
          ) => true
      case _ => false
    })
    tui.get.stop()
  }

  test("repeated width and height resizes select only each current semantic tail") {
    val contexts = ArrayBuffer.empty[NormalResizeRecoveryContext]
    val terminal = VirtualTerminal(20, 8)
    val tui      = running(
      terminal,
      MutableFrame(ComponentRender.text("live")),
      NormalResizeRecoveryProvider { context =>
        contexts += context
        Vector(s"tail-${context.width}-${context.height}")
      }
    )

    terminal.resize(15, 7)
    assert(terminal.output.contains("tail-15-7"))
    terminal.clearWrites()
    terminal.resize(10, 5)
    assertEquals(
      contexts.map(context => context.width -> context.height).toVector,
      Vector(15 -> 7, 10 -> 5)
    )
    assert(!terminal.output.contains("tail-15-7"))
    assertEquals(terminal.output.split("tail-10-5", -1).length - 1, 1)
    tui.stop()
  }

  test(
    "provider work is owner serialized outside lifecycle lock and follow-up render is non-recursive"
  ) {
    val calls     = AtomicInteger(0)
    val lockProbe = CountDownLatch(1)
    val terminal  = VirtualTerminal(20, 8)
    var tui       = Option.empty[TUI]
    val provider  = NormalResizeRecoveryProvider { _ =>
      calls.incrementAndGet()
      val probe = Thread(() => {
        tui.get.children
        lockProbe.countDown()
      })
      probe.start()
      assert(lockProbe.await(5, TimeUnit.SECONDS), "lifecycle lock remained held during provider")
      tui.get.requestRender()
      tui.get.flushRender()
      Vector("history")
    }
    tui = Some(running(terminal, MutableFrame(ComponentRender.text("live")), provider))
    terminal.resize(19, 8)
    assertEquals(calls.get(), 1)
    tui.get.stop()
  }

  test(
    "oversized and throwing providers fail before recovery publication with redacted diagnostics"
  ) {
    def runFailure(
        provider: NormalResizeRecoveryProvider
    ): (Vector[TUIDiagnosticEvent], String, Boolean) =
      val events   = ArrayBuffer.empty[TUIDiagnosticEvent]
      val terminal = VirtualTerminal(20, 4)
      val tui      = running(
        terminal,
        MutableFrame(ComponentRender.text("live")),
        provider,
        Some(TUIDiagnosticObserver(events += _))
      )
      terminal.resize(19, 4)
      (events.toVector, terminal.output, terminal.isRunning)

    val oversized = runFailure(NormalResizeRecoveryProvider(context =>
      Vector.fill(context.maxRows + 1)("secret-row")
    ))
    assert(!oversized._2.contains("secret-row"))
    assertEquals(oversized._3, false)
    assert(oversized._1.exists {
      case TUIDiagnosticEvent.ResizeRecovery(
            TUIDiagnosticResizeRecoveryOutcome.Failed,
            Some(TUIDiagnosticResizeRecoveryFailure.RowBudget),
            _,
            _,
            _
          ) => true
      case _ => false
    })

    val thrown      = runFailure(NormalResizeRecoveryProvider(_ =>
      throw IllegalStateException("sensitive provider message")
    ))
    assertEquals(thrown._3, false)
    val diagnostics = thrown._1.mkString
    assert(!diagnostics.contains("sensitive"))
    assert(thrown._1.exists {
      case TUIDiagnosticEvent.ResizeRecovery(
            TUIDiagnosticResizeRecoveryOutcome.Failed,
            Some(TUIDiagnosticResizeRecoveryFailure.Provider),
            _,
            0,
            _
          ) => true
      case _ => false
    })
  }

  test("combined write failure is diagnosed and terminal cleanup completes") {
    val events   = ArrayBuffer.empty[TUIDiagnosticEvent]
    val terminal = ProbeTerminal()
    val tui      = TUI(
      terminal,
      options(
        NormalResizeRecoveryProvider(_ => Vector("secret-history")),
        Some(TUIDiagnosticObserver(events += _))
      )
    )
    tui.addChild(MutableFrame(ComponentRender.text("live")))
    tui.start()
    terminal.delegate.clearWrites()
    terminal.failNextRenderWrite.set(true)

    terminal.resize(19, 8)

    assertEquals(terminal.delegate.isRunning, false)
    assert(terminal.delegate.output.contains("secret-history"))
    assert(events.exists {
      case TUIDiagnosticEvent.ResizeRecovery(
            TUIDiagnosticResizeRecoveryOutcome.Failed,
            Some(TUIDiagnosticResizeRecoveryFailure.Write),
            _,
            1,
            _
          ) => true
      case _ => false
    })
  }

  test("stop from provider discards candidate before publication") {
    val terminal = VirtualTerminal(20, 8)
    var tui      = Option.empty[TUI]
    val provider = NormalResizeRecoveryProvider { _ =>
      tui.get.stop()
      Vector("must-not-publish")
    }
    tui = Some(running(terminal, MutableFrame(ComponentRender.text("live")), provider))

    terminal.resize(19, 8)

    assert(!terminal.output.contains("must-not-publish"))
    assertEquals(terminal.isRunning, false)
  }

  test("hardware cursor and mouse routing use recovered live-frame origin") {
    final class MouseCursor extends Component, MouseInputHandler:
      var received                                                      = Option.empty[MouseInputContext]
      override def render(width: Int): ComponentRender                  = ComponentRender(
        Vector("live"),
        Vector.empty,
        Vector(CursorPlacement(0, 1))
      )
      override def handleMouse(context: MouseInputContext): InputResult =
        received = Some(context)
        InputResult.NoRender

    val terminal = VirtualTerminal(20, 8)
    val target   = MouseCursor()
    val tui      = running(
      terminal,
      target,
      NormalResizeRecoveryProvider(_ => Vector("A", "B")),
      hardwareCursor = true,
      mouseInput = true
    )
    terminal.resize(19, 8)
    assertEquals(terminal.cursorPosition, 2 -> 1)

    terminal.sendMouse(TerminalInput.Mouse(
      scalatui.terminal.MouseAction.Press(scalatui.terminal.MouseButton.Left),
      row = 2,
      col = 1
    ))
    assertEquals(target.received.map(_.localRow), Some(0))
    assertEquals(target.received.map(_.boundsRow), Some(2))
    tui.stop()
  }
