package scalatui.core

import scalatui.terminal.VirtualTerminal

import scala.collection.mutable.ArrayBuffer

class TUIDiagnosticsSuite extends munit.FunSuite:
  test("default options keep diagnostics disabled and clear normal scrollback on resize"):
    val options  = TUIOptions()
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, options)
    tui.addChild(FixedLine("default"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(18, 4)

    assertEquals(options.diagnosticObserver, None)
    assertEquals(options.normalResizeClearPolicy, NormalResizeClearPolicy.ClearScrollback)
    assert(terminal.output.contains(TUI.NormalScreenClear), terminal.output)

  test("preserve-scrollback policy clears and homes viewport without CSI 3 J"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(
      terminal,
      TUIOptions(normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback)
    )
    tui.addChild(FixedLine("preserved"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(18, 4)

    assert(terminal.output.contains(TUI.NormalScreenViewportClear), terminal.output)
    assert(!terminal.output.contains("\u001b[3J"), terminal.output)
    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)

  test("preserve-scrollback resize recomputes overlays with the same clear policy"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(
      terminal,
      TUIOptions(normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback)
    )
    tui.addChild(FixedLine("base"))
    tui.start()
    tui.showOverlay(
      FixedLine("overlay"),
      OverlayOptions(width = Some(OverlaySize.Absolute(8)), focusCapturing = false)
    )
    terminal.clearWrites()

    terminal.resize(10, 3)

    assert(terminal.output.contains(TUI.NormalScreenViewportClear), terminal.output)
    assert(!terminal.output.contains("\u001b[3J"), terminal.output)
    assert(terminal.output.contains("overlay"), terminal.output)

  test("alternate-screen resize ignores normal preserve-scrollback policy"):
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(
      terminal,
      TUIOptions(
        screenMode = TUIScreenMode.Alternate,
        normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback
      )
    )
    tui.addChild(FixedLine("alternate"))
    tui.start()
    terminal.clearWrites()

    terminal.resize(18, 4)

    assert(terminal.output.contains(TUI.AlternateScreenClear), terminal.output)
    assert(!terminal.output.contains("\u001b[3J"), terminal.output)
    assert(!terminal.output.contains(TUI.AlternateScreenEnter), terminal.output)

  test("diagnostics emit ordered redacted resize redraw and write metadata"):
    val events        = ArrayBuffer.empty[TUIDiagnosticEvent]
    val observer      = TUIDiagnosticObserver(events += _)
    val terminal      = VirtualTerminal(20, 5)
    val tui           = TUI(terminal, TUIOptions(diagnosticObserver = Some(observer)))
    tui.addChild(FixedLine("application-secret"))
    tui.start()
    val startupEvents = events.toVector
    assertEquals(
      startupEvents.collect { case TUIDiagnosticEvent.Lifecycle(state, _) => state },
      Vector(TUIDiagnosticLifecycleState.Starting, TUIDiagnosticLifecycleState.Running)
    )
    assert(
      startupEvents.exists {
        case TUIDiagnosticEvent.Redraw(TUIDiagnosticRedrawKind.Full, _, _, _, 0, None, _) => true
        case _                                                                            => false
      },
      startupEvents.toString
    )
    assert(
      startupEvents.exists {
        case TUIDiagnosticEvent.Write(TUIDiagnosticWriteKind.Render, bytes) => bytes > 0
        case _                                                              => false
      },
      startupEvents.toString
    )
    events.clear()

    terminal.resize(18, 4)

    val resizeIndex = events.indexWhere(_.isInstanceOf[TUIDiagnosticEvent.Resize])
    val redrawIndex = events.indexWhere {
      case TUIDiagnosticEvent.Redraw(
            TUIDiagnosticRedrawKind.Full,
            18,
            4,
            _,
            0,
            Some(TUIDiagnosticClearReason.Resize),
            TUIScreenMode.Normal
          ) => true
      case _ => false
    }
    val writeIndex  = events.indexWhere {
      case TUIDiagnosticEvent.Write(TUIDiagnosticWriteKind.Render, byteCount) => byteCount > 0
      case _                                                                  => false
    }

    assert(resizeIndex >= 0, events.toString)
    assert(redrawIndex > resizeIndex, events.toString)
    assert(writeIndex > redrawIndex, events.toString)
    assert(!events.mkString("|").contains("application-secret"), events.toString)

  test("diagnostic observers remain isolated between TUI instances"):
    val firstEvents    = ArrayBuffer.empty[TUIDiagnosticEvent]
    val secondEvents   = ArrayBuffer.empty[TUIDiagnosticEvent]
    val firstTerminal  = VirtualTerminal(20, 5)
    val secondTerminal = VirtualTerminal(20, 5)
    val first          = TUI(
      firstTerminal,
      TUIOptions(diagnosticObserver = Some(TUIDiagnosticObserver(firstEvents += _)))
    )
    val second         = TUI(
      secondTerminal,
      TUIOptions(diagnosticObserver = Some(TUIDiagnosticObserver(secondEvents += _)))
    )
    first.addChild(FixedLine("first"))
    second.addChild(FixedLine("second"))
    first.start()
    second.start()
    firstEvents.clear()
    secondEvents.clear()

    firstTerminal.resize(18, 4)

    assert(firstEvents.exists(_.isInstanceOf[TUIDiagnosticEvent.Resize]), firstEvents.toString)
    assertEquals(secondEvents.toVector, Vector.empty)

  test("throwing diagnostic observer is disabled and cannot prevent cleanup"):
    var calls    = 0
    val observer = TUIDiagnosticObserver { _ =>
      calls += 1
      throw RuntimeException("observer failed")
    }
    val terminal = VirtualTerminal(20, 5)
    val tui      = TUI(terminal, TUIOptions(diagnosticObserver = Some(observer)))
    tui.addChild(FixedLine("safe"))

    tui.start()
    terminal.resize(18, 4)
    tui.stop()

    assertEquals(calls, 1)
    assertEquals(terminal.isRunning, false)

  private final class FixedLine(value: String) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(value)
