package scalatui.demo

import scalatui.core.ClipboardTarget
import scalatui.terminal.{
  KeyModifiers,
  MouseAction,
  MouseButton,
  MouseButtonState,
  TerminalInput,
  TerminalKey,
  VirtualTerminal
}

class FullscreenTranscriptDemoSuite extends munit.FunSuite:
  test("shared fullscreen example lays out, searches, selects, and copies through host callback"):
    val terminal = VirtualTerminal(50, 8)
    val demo     = FullscreenTranscriptDemo(terminal)

    demo.tui.start()
    demo.append("searchable transcript row")
    demo.tui.flushRender()
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("f"),
      KeyModifiers(ctrl = true, shift = true)
    ))
    "searchable".foreach(character =>
      terminal.sendInput(TerminalInput.Key(TerminalKey.Character(character.toString)))
    )
    assertEquals(demo.tui.viewportSearchState.map(_.matchCount), Some(1))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Escape))

    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Press(MouseButton.Left), 0, 0))
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Move(MouseButtonState.Pressed(MouseButton.Left)),
      0,
      6
    ))
    terminal.sendMouse(TerminalInput.Mouse(MouseAction.Release(MouseButton.Left), 0, 6))
    assert(demo.tui.viewportSelection.nonEmpty)
    assertEquals(
      demo.tui.clipboardSupport(ClipboardTarget.Host),
      scalatui.core.ClipboardTargetSupport.Supported
    )
    terminal.sendInput(TerminalInput.Key(
      TerminalKey.Character("c"),
      KeyModifiers(ctrl = true, alt = true)
    ))
    assert(demo.tui.lastClipboardCopyResult.exists(
      _.isInstanceOf[scalatui.core.ClipboardCopyResult.Success]
    ))
    assert(!terminal.output.contains("\u001b]52;"))
    demo.tui.stop()
