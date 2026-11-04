package scalatui.core

import scalatui.terminal.VirtualTerminal

class RendererCapabilitiesSuite extends munit.FunSuite:
  private def policy(mode: TUIScreenMode, fullscreen: Boolean = false): RendererPolicy =
    val terminal = new VirtualTerminal()
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val options  = TUIOptions(screenMode = mode)
    val redraw: (
        TUIDiagnosticRedrawKind,
        Int,
        Int,
        Int,
        Int,
        Option[TUIDiagnosticClearReason]
    ) => Unit    = (_, _, _, _, _, _) => ()
    if fullscreen then
      new FullscreenViewportPolicy(terminal, options, counters, services, redraw)
    else new NormalScreenPolicy(terminal, options, counters, services, redraw)

  test("normal screen supports append publishing and resize recovery") {
    val renderer = policy(TUIScreenMode.Normal)
    assert(renderer.isInstanceOf[AppendPublisher])
    assert(renderer.isInstanceOf[ResizeRecoveryRenderer])
    assert(!renderer.isAlternateScreen)
    assert(!renderer.isFullscreenViewport)
  }

  test("width-only alternate screen retains transcript capabilities") {
    val renderer = policy(TUIScreenMode.Alternate)
    assert(renderer.isInstanceOf[AppendPublisher])
    assert(renderer.isInstanceOf[ResizeRecoveryRenderer])
    assert(renderer.isAlternateScreen)
    assert(!renderer.isFullscreenViewport)
  }

  test("fullscreen viewport exposes neither transcript capability") {
    val renderer = policy(TUIScreenMode.Alternate, fullscreen = true)
    assert(!renderer.isInstanceOf[AppendPublisher])
    assert(!renderer.isInstanceOf[ResizeRecoveryRenderer])
    assert(renderer.isAlternateScreen)
    assert(renderer.isFullscreenViewport)
  }
