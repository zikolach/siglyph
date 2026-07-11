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

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))
    tui.requestRender(force = true)
    tui.flushRender()

    val postEnter = Ansi.strip(terminal.output)
    val lastFrame = postEnter.split("siglyph showcase demo").last
    assert(
      !lastFrame.contains("help — Show demo help"),
      postEnter
    )
    assert(
      lastFrame.contains("Submitted: (none)"),
      postEnter
    )

  test("interactive demo shows accurate file-preview hidden-line counts"):
    val terminal = VirtualTerminal(120, 40)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()

    val currentDir = System.getProperty("user.dir")
    val tempFile   = Files.createTempFile("scala-tui-demo-preview-", ".txt")
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

      val output    = Ansi.strip(terminal.output)
      val lastFrame = output.split("siglyph showcase demo").last
      assert(lastFrame.contains("... (+7 lines hidden)"), output)
    finally
      Files.deleteIfExists(tempFile)

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
    assert(output.contains("AGENTS.md"), output)
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
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(
      Ansi.strip(terminal.output).contains("◓ Tick me from Actions"),
      Ansi.strip(terminal.output)
    )

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(Ansi.strip(terminal.output).contains("! Cancelled"), Ansi.strip(terminal.output))

  test("interactive demo exposes terminal integration actions"):
    val terminal = VirtualTerminal(100, 24)
    val tui      = TUI(terminal)
    InteractiveDemo.install(tui)
    tui.start()

    assert(
      Ansi.strip(terminal.output).contains("Terminal integration"),
      Ansi.strip(terminal.output)
    )
    terminal.clearWrites()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("t"), KeyModifiers(ctrl = true)))
    (1 to 6).foreach(_ => terminal.sendInput(TerminalInput.Key(TerminalKey.Down)))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("\u001b]0;siglyph demo\u0007"), terminal.output)
    assert(
      Ansi.strip(terminal.output).contains("Terminal title supported"),
      Ansi.strip(terminal.output)
    )

    terminal.clearWrites()
    terminal.sendInput(TerminalInput.Key(TerminalKey.Down))
    terminal.sendInput(TerminalInput.Key(TerminalKey.Enter))

    assert(terminal.output.contains("\u001b]9;4;3\u0007"), terminal.output)
    assert(
      Ansi.strip(terminal.output).contains("Terminal progress on supported"),
      Ansi.strip(terminal.output)
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

    assertWidthSafe(terminal.output, 12)
    assert(Ansi.strip(terminal.output).contains("help"), "expected suggestions after resize")

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

  private def assertWidthSafe(output: String, width: Int): Unit =
    val visibleLines = Ansi.strip(output).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)
    visibleLines.foreach { line =>
      assert(Ansi.visibleWidth(line) <= width, s"width=$width line=${line}")
    }
