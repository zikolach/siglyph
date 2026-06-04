package scalatui.terminal

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class StreamTerminalSuite extends munit.FunSuite:
  test("stream terminal writes to provided output stream"):
    val out = ByteArrayOutputStream()
    val terminal = StreamTerminal(output = out, initialColumns = 10, initialRows = 2)
    terminal.write("hello")
    assertEquals(out.toString("UTF-8"), "hello")

  test("stream terminal parses input without raw mode"):
    val in = ByteArrayInputStream("x".getBytes("UTF-8"))
    val terminal = StreamTerminal(input = in)
    var inputs = Vector.empty[TerminalInput]
    terminal.start(input => inputs :+= input, () => ())
    Thread.sleep(50)
    terminal.stop()
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Character("x"))))
