package scalatui.demo

import scalatui.core.TUI
import scalatui.terminal.{TerminalInput, TerminalKey, VirtualTerminal}

class ConversationalComposerSuite extends munit.FunSuite:
  test("shared composer submits local echo and restores a Unicode draft across resize and history"):
    val terminal = VirtualTerminal(40, 80)
    val tui      = TUI(terminal)
    ConversationalComposerDemo.install(tui)
    tui.start()
    try
      def press(key: TerminalKey): Unit =
        terminal.sendInput(TerminalInput.Key(key))
        tui.flushRender()
      press(TerminalKey.Character("hello"))
      press(TerminalKey.Enter)
      assert(terminal.viewportLines.exists(_.contains("Local echo: hello")))
      val draft                         = "Draft 界 e\u0301 🙂 across wraps"
      press(TerminalKey.Character(draft))
      terminal.resize(12, 80)
      tui.flushRender()
      press(TerminalKey.Home)
      press(TerminalKey.Up)
      press(TerminalKey.Down)
      terminal.resize(60, 80)
      tui.flushRender()
      press(TerminalKey.Enter)
      assert(terminal.viewportLines.exists(_.contains(s"You: $draft")), terminal.viewportLines)
      assert(
        terminal.viewportLines.exists(_.contains(s"Local echo: $draft")),
        terminal.viewportLines
      )
    finally tui.stop()
    assert(!terminal.isRunning)
