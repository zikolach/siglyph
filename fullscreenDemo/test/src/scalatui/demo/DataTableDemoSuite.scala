package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.terminal.{TerminalInput, TerminalKey, VirtualTerminal}

class DataTableDemoSuite extends munit.FunSuite:
  test("generic table reveals Home and End selection and survives narrow resize"):
    val terminal = VirtualTerminal(80, 12)
    val tui      = DataTableDemo(terminal)
    try
      tui.start()
      terminal.sendInput(TerminalInput.Key(TerminalKey.End))
      tui.flushRender()
      assert(terminal.screenLines.exists(_.contains("item-99999")))
      terminal.resize(70, 8)
      tui.flushRender()
      assert(terminal.screenLines.exists(_.contains("item-99999")))
      terminal.sendInput(TerminalInput.Key(TerminalKey.Home))
      tui.flushRender()
      assert(terminal.screenLines.exists(_.contains("item-0")))
      assert(terminal.screenLines.exists(_.contains("Name")))
      terminal.resize(5, 3)
      tui.flushRender()
      assertEquals(terminal.screenLines.length, 3)
      assert(terminal.screenLines.forall(line => Ansi.visibleWidth(line) <= 5))
    finally tui.stop()
    assert(!terminal.isRunning)
