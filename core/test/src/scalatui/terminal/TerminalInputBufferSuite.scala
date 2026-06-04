package scalatui.terminal

class TerminalInputBufferSuite extends munit.FunSuite:
  test("buffers split CSI arrow sequence"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process("\u001b["), Vector.empty)
    assertEquals(buffer.process("A"), Vector(TerminalInput.Key(TerminalKey.Up)))

  test("buffers split modified key sequence"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process("\u001b[1;"), Vector.empty)
    assertEquals(
      buffer.process("5D"),
      Vector(TerminalInput.Key(TerminalKey.Left, KeyModifiers(ctrl = true)))
    )

  test("buffers split bracketed paste"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process("\u001b[200~hel"), Vector.empty)
    assertEquals(buffer.process("lo\n"), Vector.empty)
    assertEquals(buffer.process("world\u001b[201~"), Vector(TerminalInput.Paste("hello\nworld")))

  test("flush emits incomplete escape as raw"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process("\u001b[1;"), Vector.empty)
    assertEquals(buffer.flush(), Vector(TerminalInput.Raw("\u001b[1;")))

  test("flush emits incomplete bracketed paste as raw"):
    val buffer = TerminalInputBuffer()
    assertEquals(buffer.process("\u001b[200~hello"), Vector.empty)
    assertEquals(buffer.flush(), Vector(TerminalInput.Raw("\u001b[200~hello")))

  test("emits normal characters and complete escapes in order"):
    val buffer = TerminalInputBuffer()
    assertEquals(
      buffer.process("a\u001b[Bb"),
      Vector(
        TerminalInput.Key(TerminalKey.Character("a")),
        TerminalInput.Key(TerminalKey.Down),
        TerminalInput.Key(TerminalKey.Character("b"))
      )
    )
