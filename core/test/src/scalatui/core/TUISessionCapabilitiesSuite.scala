package scalatui.core

import scalatui.terminal.{
  ImageProtocol,
  TerminalCapabilities,
  TerminalCapabilityOverride,
  TerminalCapabilityOverrides,
  TerminalCapabilitiesSource,
  VirtualTerminal
}

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class TUISessionCapabilitiesSuite extends munit.FunSuite:
  private final class CapabilityProbe extends Component, ContextualComponent:
    @volatile var observed: TerminalCapabilities = TerminalCapabilities.Conservative
    private var context                          = Option.empty[TUIContext]

    override def tuiContext_=(value: Option[TUIContext]): Unit = context = value

    override def render(width: Int): ComponentRender =
      observed = context.map(_.terminalCapabilities).getOrElse(TerminalCapabilities.Conservative)
      ComponentRender.text("")

  test("partial tri-state overrides preserve other detected capabilities"):
    val detected = TerminalCapabilities(
      trueColor = true,
      hyperlinks = false,
      images = Some(ImageProtocol.Kitty)
    )
    val resolved = TerminalCapabilities.resolve(
      detected,
      TerminalCapabilityOverrides(hyperlinks = TerminalCapabilityOverride.Forced(true))
    )

    assertEquals(
      resolved,
      TerminalCapabilities(
        trueColor = true,
        hyperlinks = true,
        images = Some(ImageProtocol.Kitty)
      )
    )

  test("disabled session capabilities are hard ceilings for fixed components"):
    val local     = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(ImageProtocol.Kitty)
    )
    val overrides = TerminalCapabilityOverrides(
      trueColor = TerminalCapabilityOverride.Disabled,
      hyperlinks = TerminalCapabilityOverride.Disabled,
      images = TerminalCapabilityOverride.Disabled
    )

    assertEquals(
      TerminalCapabilities.forComponent(
        local,
        TerminalCapabilities.Conservative,
        overrides,
        TerminalCapabilitiesSource.Fixed
      ),
      TerminalCapabilities.Conservative
    )

  test("detects current Zed environment fixtures"):
    val expected = TerminalCapabilities(trueColor = true, hyperlinks = true, images = None)

    assertEquals(TerminalCapabilities.detect(Map("TERM_PROGRAM" -> "zed")), expected)
    assertEquals(
      TerminalCapabilities.detect(
        Map(
          "ZED_TERM"     -> "true",
          "TERM_PROGRAM" -> "Zed",
          "TERM"         -> "xterm-256color",
          "COLORTERM"    -> "truecolor"
        )
      ),
      expected
    )

  test("multiplexer detection stays conservative until explicitly forced"):
    val detected = TerminalCapabilities.detect(
      Map(
        "TMUX"         -> "1",
        "TERM_PROGRAM" -> "zed",
        "COLORTERM"    -> "truecolor"
      )
    )
    assertEquals(
      detected,
      TerminalCapabilities(trueColor = true, hyperlinks = false, images = None)
    )

    val forced = TerminalCapabilities.resolve(
      detected,
      TerminalCapabilityOverrides(
        hyperlinks = TerminalCapabilityOverride.Forced(true),
        images = TerminalCapabilityOverride.Forced(ImageProtocol.Kitty)
      )
    )
    assertEquals(
      forced,
      TerminalCapabilities(
        trueColor = true,
        hyperlinks = true,
        images = Some(ImageProtocol.Kitty)
      )
    )

  test("concurrent TUI sessions expose only their own immutable capabilities"):
    val kittyProbe  = CapabilityProbe()
    val itermProbe  = CapabilityProbe()
    val kittyTui    = TUI(
      VirtualTerminal(),
      TUIOptions(capabilityOverrides =
        TerminalCapabilityOverrides(
          trueColor = TerminalCapabilityOverride.Forced(true),
          hyperlinks = TerminalCapabilityOverride.Forced(false),
          images = TerminalCapabilityOverride.Forced(ImageProtocol.Kitty)
        )
      )
    )
    val itermTui    = TUI(
      VirtualTerminal(),
      TUIOptions(capabilityOverrides =
        TerminalCapabilityOverrides(
          trueColor = TerminalCapabilityOverride.Disabled,
          hyperlinks = TerminalCapabilityOverride.Forced(true),
          images = TerminalCapabilityOverride.Forced(ImageProtocol.ITerm2)
        )
      )
    )
    kittyTui.addChild(kittyProbe)
    itermTui.addChild(itermProbe)
    val release     = CountDownLatch(1)
    val failure     = AtomicReference[Throwable | Null](null)
    val kittyThread = Thread(() => runSession(kittyTui, release, failure))
    val itermThread = Thread(() => runSession(itermTui, release, failure))

    kittyThread.start()
    itermThread.start()
    release.countDown()
    kittyThread.join()
    itermThread.join()
    Option(failure.get()).foreach(throw _)

    assertEquals(
      kittyProbe.observed,
      TerminalCapabilities(
        trueColor = true,
        hyperlinks = false,
        images = Some(ImageProtocol.Kitty)
      )
    )
    assertEquals(
      itermProbe.observed,
      TerminalCapabilities(
        trueColor = false,
        hyperlinks = true,
        images = Some(ImageProtocol.ITerm2)
      )
    )

  private def runSession(
      tui: TUI,
      release: CountDownLatch,
      failure: AtomicReference[Throwable | Null]
  ): Unit =
    try
      release.await()
      tui.start()
      tui.stop()
    catch case error: Throwable => failure.compareAndSet(null, error)
