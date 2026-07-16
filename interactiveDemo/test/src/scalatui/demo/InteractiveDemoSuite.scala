package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.core.TUI
import scalatui.terminal.{KeyModifiers, TerminalInput, TerminalKey, VirtualTerminal}

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class InteractiveDemoSuite extends munit.FunSuite:
  test("interactive demo writes width-safe output at narrow widths"):
    Vector(1, 10, 22, 40, 80).foreach { width =>
      val terminal = VirtualTerminal(width, 20)
      val tui      = TUI(terminal)
      InteractiveDemo.install(tui)

      tui.start()

      assertWidthSafe(terminal, width)
    }

  test("interactive demo remains interactive after narrow resize"):
    val terminal = VirtualTerminal(80, 20)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.resize(1, 10)
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertWidthSafe(terminal, 1)
    assert(terminal.output.nonEmpty, terminal.output)

  test("interactive demo shows slash-command autocomplete overlay"):
    val terminal = VirtualTerminal(60, 40)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()

    val suggestionLines = terminal.viewportLines
    assert(suggestionLines.exists(_.contains("help")), suggestionLines)
    assert(suggestionLines.exists(_.contains("clear")), suggestionLines)
    val editorLine      = suggestionLines.indexWhere(_.contains("Editor"))
    val helpLine        = suggestionLines.indexWhere(_.contains("help"))
    assert(editorLine >= 0, suggestionLines)
    assert(helpLine > editorLine, suggestionLines)

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    tui.requestRender(force = true)
    tui.flushRender()

    assert(
      !terminal.viewportLines.exists(_.contains("help — Show demo help")),
      terminal.viewportLines
    )
    assert(
      terminal.viewportLines.exists(_.contains("Submitted: (none)")),
      terminal.viewportLines
    )

  test("interactive demo shows accurate file-preview hidden-line counts"):
    val terminal = VirtualTerminal(120, 40)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()

    val currentDir = System.getProperty("user.dir")
    val tempFile   = Files.createTempFile("siglyph-demo-preview-", ".txt")
    try
      Files.writeString(
        tempFile,
        (1 to 25).map(i => s"line-$i").mkString("\n"),
        StandardCharsets.UTF_8
      )
      val absolutePath = tempFile.toAbsolutePath.toString

      terminal.sendInput(TerminalInput.Key(TerminalKey.Character("m"), KeyModifiers(ctrl = true)))
      terminal.sendInput(TerminalInput.Key(TerminalKey.Character("p"), KeyModifiers(ctrl = true)))

      currentDir.foreach(_ =>
        terminal.sendInput(TerminalInput.Key(TerminalKey.Backspace))
      )
      absolutePath.foreach { ch =>
        terminal.sendInput(TerminalInput.Key(TerminalKey.Character(ch.toString)))
      }
      terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

      terminal.clearWrites()
      tui.requestRender(force = true)
      tui.flushRender()

      assert(
        terminal.viewportLines.exists(_.contains("... (+7 lines hidden)")),
        terminal.viewportLines
      )
    finally
      Files.deleteIfExists(tempFile)

  test("missing file-manager path ends one submission and keeps path dispatch active"):
    val terminal = VirtualTerminal(100, 30)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()

    val currentDir  = System.getProperty("user.dir")
    val missingPath = Files.createTempFile("siglyph-demo-missing-path-", ".tmp")
    Files.delete(missingPath)
    val missing     = missingPath.toAbsolutePath.toString
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("m"), KeyModifiers(ctrl = true)))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("p"), KeyModifiers(ctrl = true)))
    currentDir.foreach(_ => terminal.sendInput(TerminalInput.Key(TerminalKey.Backspace)))
    missing.foreach(character =>
      terminal.sendInput(TerminalInput.Key(TerminalKey.Character(character.toString)))
    )
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assertEquals(terminal.viewportLines.count(_.contains("Path not found:")), 1)

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))
    assert(
      terminal.viewportLines.exists(_.contains(s"${missing.takeRight(12)}x")),
      terminal.viewportLines
    )

  test("interactive demo lets Tab reach editor autocomplete"):
    val terminal = VirtualTerminal(60, 40)
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

    assert(terminal.viewportLines.exists(_.contains("AGENTS.md")), terminal.viewportLines)
    assert(terminal.viewportLines.exists(_.contains("Editor (focused)")), terminal.viewportLines)

  test("interactive demo actions tick and cancel loader components"):
    val terminal = VirtualTerminal(80, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("t"), KeyModifiers(ctrl = true)))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(
      terminal.viewportLines.exists(_.contains("◓ Tick me from Actions")),
      terminal.viewportLines
    )

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.viewportLines.exists(_.contains("! Cancelled")), terminal.viewportLines)

  test("interactive demo exposes terminal integration actions"):
    val terminal = VirtualTerminal(100, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()

    assert(
      terminal.viewportLines.exists(_.contains("Terminal integration")),
      terminal.viewportLines
    )
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("t"), KeyModifiers(ctrl = true)))
    (1 to 6).foreach(_ => terminal.sendInput(TerminalInput.Key(TerminalKey.Down)))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("\u001b]0;siglyph demo\u0007"), terminal.output)
    assert(
      terminal.viewportLines.exists(_.contains("Terminal title supported")),
      terminal.viewportLines
    )

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("\u001b]9;4;3\u0007"), terminal.output)
    assert(
      terminal.viewportLines.exists(_.contains("Terminal progress on supported")),
      terminal.viewportLines
    )

  test("interactive demo keeps autocomplete overlay safe during narrow resize"):
    val terminal = VirtualTerminal(60, 20)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("/")))
    terminal.clearWrites()
    terminal.resize(12, 8)

    assertWidthSafe(terminal, 12)
    assert(
      terminal.viewportLines.exists(_.contains("help")),
      "expected suggestions after resize"
    )

  test("demo query subscription keeps at most one unanswered query"):
    val subscription = DemoQuerySubscription()
    var starts       = 0
    var started      = 0
    val query        = (_: String => Unit) =>
      starts += 1
      () => ()

    assertEquals(subscription.start(query)(_ => started += 1)((_, _) => ()), Some(1L))
    assertEquals(subscription.start(query)(_ => started += 1)((_, _) => ()), None)
    assertEquals(starts, 1)
    assertEquals(started, 1)

  test("demo query subscription does not retain synchronous completion cancellation"):
    val subscription = DemoQuerySubscription()
    var completions  = Vector.empty[(Long, String)]
    val query        = (callback: String => Unit) =>
      callback("complete")
      () => ()

    assertEquals(
      subscription.start(query)(_ => ())((id, result) => completions :+= id -> result),
      Some(1L)
    )
    assertEquals(
      subscription.start(query)(_ => ())((id, result) => completions :+= id -> result),
      Some(2L)
    )
    assertEquals(completions, Vector(1L -> "complete", 2L -> "complete"))

  test("demo query publishes started before synchronous user-visible completion"):
    val subscription = DemoQuerySubscription()
    var messages     = Vector.empty[String]
    val query        = (callback: String => Unit) =>
      callback("complete")
      () => ()

    assertEquals(
      subscription.start(query)(id => messages :+= s"query #$id started") { (id, result) =>
        messages :+= s"query #$id: $result"
      },
      Some(1L)
    )
    assertEquals(messages, Vector("query #1 started", "query #1: complete"))

  test("thrown demo query invocation releases ownership and preserves the original failure"):
    val subscription = DemoQuerySubscription()
    val failure      = IllegalStateException("query failed")
    val query        = (_: String => Unit) => throw failure

    val thrown = intercept[IllegalStateException] {
      subscription.start(query)(_ => ())((_, _) => ())
    }

    assert(thrown eq failure)
    assertEquals(subscription.start(_ => () => ())(_ => ())((_, _) => ()), Some(2L))

  test("thrown old demo query invocation cannot clear newer ownership"):
    val subscription = DemoQuerySubscription()
    val failure      = RuntimeException("query failed after completion")
    var nestedStart  = Option.empty[Long]
    val query        = (callback: String => Unit) =>
      callback("complete")
      throw failure

    val thrown = intercept[RuntimeException] {
      subscription.start(query)(_ => ()) { (_, _) =>
        nestedStart = subscription.start(_ => () => ())(_ => ())((_, _) => ())
      }
    }

    assert(thrown eq failure)
    assertEquals(nestedStart, Some(2L))
    assertEquals(subscription.start(_ => () => ())(_ => ())((_, _) => ()), None)

  test("old demo query completion cannot clear newer ownership"):
    val subscription = DemoQuerySubscription()
    var callbacks    = Vector.empty[String => Unit]
    var completions  = Vector.empty[Long]
    val query        = (callback: String => Unit) =>
      callbacks :+= callback
      () => ()

    assertEquals(subscription.start(query)(_ => ())((id, _) => completions :+= id), Some(1L))
    subscription.cancelActive()
    assertEquals(subscription.start(query)(_ => ())((id, _) => completions :+= id), Some(2L))

    callbacks.head("late")
    assertEquals(subscription.start(query)(_ => ())((_, _) => ()), None)
    assertEquals(completions, Vector.empty)

    callbacks(1)("current")
    assertEquals(subscription.start(query)(_ => ())((_, _) => ()), Some(3L))
    assertEquals(completions, Vector(2L))

  private def assertWidthSafe(terminal: VirtualTerminal, width: Int): Unit =
    terminal.viewportLines.foreach { line =>
      assert(Ansi.visibleWidth(line) <= width, s"width=$width line=${line}")
    }
