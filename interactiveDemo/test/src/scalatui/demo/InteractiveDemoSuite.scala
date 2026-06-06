package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.core.TUI
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey, VirtualTerminal}

class InteractiveDemoSuite extends munit.FunSuite:
  test("interactive demo writes width-safe output at narrow widths"):
    Vector(1, 10, 22, 40, 80).foreach { width =>
      val terminal = VirtualTerminal(width, 20)
      val tui      = TUI(terminal)
      InteractiveDemo.install(tui)

      tui.start()

      assertWidthSafe(terminal.output, width)
    }

  test("interactive demo remains interactive after narrow resize"):
    val terminal = VirtualTerminal(80, 20)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.resize(1, 10)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertWidthSafe(terminal.output, 1)
    assert(terminal.output.nonEmpty, terminal.output)

  test("interactive demo shows slash-command autocomplete overlay"):
    val terminal = VirtualTerminal(60, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val suggestions = Ansi.strip(terminal.output)
    assert(suggestions.contains("help"), suggestions)
    assert(suggestions.contains("clear"), suggestions)
    val lines       = suggestions.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    val editorLine  = lines.indexWhere(_.contains("Editor"))
    val helpLine    = lines.indexWhere(_.contains("help"))
    assert(editorLine >= 0, suggestions)
    assert(helpLine > editorLine, suggestions)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(Ansi.strip(terminal.output).contains("/clear"), Ansi.strip(terminal.output))

  test("interactive demo lets Tab reach editor autocomplete"):
    val terminal = VirtualTerminal(60, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character(".")))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Tab))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val output = Ansi.strip(terminal.output)
    assert(output.contains("README.md"), output)
    assert(output.contains("Editor (focused)"), output)

  test("interactive demo actions tick and cancel loader components"):
    val terminal = VirtualTerminal(80, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("t"), KeyModifiers(ctrl = true)))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(Ansi.strip(terminal.output).contains("◓ Tick me from Actions"), terminal.output)

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(Ansi.strip(terminal.output).contains("! Cancelled"), terminal.output)

  test("interactive demo keeps autocomplete overlay safe during narrow resize"):
    val terminal = VirtualTerminal(60, 20)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    terminal.resize(12, 8)

    assertWidthSafe(terminal.output, 12)
    assert(Ansi.strip(terminal.output).contains("help"), "expected suggestions after resize")

  private def assertWidthSafe(output: String, width: Int): Unit =
    val visibleLines = Ansi.strip(output).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)
    visibleLines.foreach { line =>
      assert(Ansi.visibleWidth(line) <= width, s"width=$width line=${line}")
    }
