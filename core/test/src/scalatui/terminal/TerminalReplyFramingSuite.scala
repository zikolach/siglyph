package scalatui.terminal

import scalatui.core.{InputResult, TUI}
import java.io.InputStream
import java.util.concurrent.{CountDownLatch, TimeUnit}

class TerminalReplyFramingSuite extends munit.FunSuite:
  private def chunk(value: String): TerminalInputChunk =
    TerminalInputChunk(value.getBytes("UTF-8"))

  private val replies = Vector(
    ("\u001b]4;1;", "\u001b]4;1;rgb:eaea/6c6c/6d6d\u0007"),
    ("\u001b]10;", "\u001b]10;rgb:ffff/ffff/ffff\u001b\\"),
    ("\u001b]11;", "\u001b]11;#112233\u0007"),
    ("\u001bP>|", "\u001bP>|Ghostty 1.3.1\u001b\\"),
    ("\u001b_Gi=1;", "\u001b_Gi=1;OK\u001b\\"),
    ("\u001b]7501;?", "\u001b]7501;?\u001b\\")
  )

  for (header, reply) <- replies do
    test("recognized reply survives delayed splits: " + header.drop(1)):
      for split <- header.length until reply.length do
        val buffer = TerminalInputBuffer()
        val first  = buffer.processAt(chunk(reply.take(split)), 0L)
        val idle   = buffer.flushIfIdle(5000000000L)
        val rest   = buffer.processAt(chunk(reply.drop(split) + "x\u0007"), 5000000001L)
        val inputs = first ++ idle ++ rest
        assertEquals(idle, Vector.empty)
        assertEquals(
          inputs.collect { case TerminalInput.RawEnd(end) => end },
          Vector(TerminalRawTermination.Complete)
        )
        assertEquals(
          inputs.collect { case TerminalInput.RawChunk(bytes) => bytes.toArray.toVector }.flatten,
          reply.getBytes("UTF-8").toVector
        )
        assertEquals(
          inputs.collect { case key: TerminalInput.KeyEvent => key },
          Vector(
            TerminalInput.Key(TerminalKey.Character("x")),
            TerminalInput.Key(TerminalKey.Character("g"), KeyModifiers(ctrl = true))
          )
        )

  test("zero-byte stream reads do not split a recognized reply"):
    val input    = new InputStream:
      private var reads                                                    = 0
      override def read(): Int                                             = -1
      override def read(bytes: Array[Byte], offset: Int, length: Int): Int =
        reads += 1
        reads match
          case 1 =>
            val prefix = "\u001b]11;#1122".getBytes("UTF-8")
            Array.copy(prefix, 0, bytes, offset, prefix.length)
            prefix.length
          case 2 => 0
          case 3 =>
            val suffix = "33\u0007x".getBytes("UTF-8")
            Array.copy(suffix, 0, bytes, offset, suffix.length)
            suffix.length
          case _ => -1
    val terminal = StreamTerminal(input = input)
    val seen     = CountDownLatch(1)
    val lock     = Object()
    var inputs   = Vector.empty[TerminalInput]
    terminal.start(
      input => {
        lock.synchronized(inputs :+= input)
        input match
          case TerminalInput.Key(TerminalKey.Character("x"), _) => seen.countDown()
          case _                                                => ()
      },
      () => ()
    )
    try
      assert(seen.await(5, TimeUnit.SECONDS))
      val result = lock.synchronized(inputs)
      assertEquals(
        result.collect { case TerminalInput.RawEnd(end) => end },
        Vector(TerminalRawTermination.Complete)
      )
      assertEquals(
        result.collect { case key: TerminalInput.KeyEvent => key },
        Vector(TerminalInput.Key(TerminalKey.Character("x")))
      )
    finally terminal.stop()

  test("ambiguous and unknown string headers retain the ordinary idle cutoff"):
    for value <- Vector(
                   "\u001b]",
                   "\u001bP",
                   "\u001b_",
                   "\u001b]10",
                   "\u001b]4;",
                   "\u001b]4;a;",
                   "\u001b_Gi=1",
                   "\u001b]0;title"
                 )
    do
      val buffer = TerminalInputBuffer()
      buffer.processAt(chunk(value), 0L)
      assertEquals(buffer.nanosUntilFlush(74000000L), Some(1000000L))
      val result = buffer.flushIfIdle(75000000L)
      assertEquals(result.last, TerminalInput.RawEnd(TerminalRawTermination.Incomplete))
      assertEquals(
        buffer.process(chunk("x")),
        Vector(TerminalInput.Key(TerminalKey.Character("x")))
      )

  test("CAN and SUB cancel only the recognized reply and preserve following keys"):
    for cancel <- Vector(0x18.toChar, 0x1a.toChar) do
      val buffer = TerminalInputBuffer()
      val prefix = "\u001b]11;#1122"
      val inputs = buffer.process(chunk(prefix)) ++
        buffer.process(chunk(cancel.toString + "x\u0007"))
      assertEquals(
        inputs.collect { case TerminalInput.RawEnd(end) => end },
        Vector(TerminalRawTermination.Malformed)
      )
      assertEquals(
        inputs.collect { case TerminalInput.RawChunk(value) => value.toArray.toVector }.flatten,
        (prefix + cancel).getBytes("UTF-8").toVector
      )
      assertEquals(
        inputs.collect { case key: TerminalInput.KeyEvent => key },
        Vector(
          TerminalInput.Key(TerminalKey.Character("x")),
          TerminalInput.Key(TerminalKey.Character("g"), KeyModifiers(ctrl = true))
        )
      )

  test("a new escape reparses a key after an unterminated reply even across a delayed ESC"):
    val buffer = TerminalInputBuffer()
    val prefix = "\u001b]11;#1122"
    val first  = buffer.processAt(chunk(prefix + "\u001b"), 0L)
    assertEquals(buffer.flushIfIdle(5000000000L), Vector.empty)
    val inputs = first ++ buffer.processAt(chunk("[Ax"), 5000000001L)
    assertEquals(
      inputs.collect { case TerminalInput.RawEnd(end) => end },
      Vector(TerminalRawTermination.Incomplete)
    )
    assertEquals(
      inputs.collect { case TerminalInput.RawChunk(value) => value.toArray.toVector }.flatten,
      prefix.getBytes("UTF-8").toVector
    )
    assertEquals(
      inputs.collect { case key: TerminalInput.KeyEvent => key },
      Vector(TerminalInput.Key(TerminalKey.Up), TerminalInput.Key(TerminalKey.Character("x")))
    )

  test("oversized recognized replies stream bounded chunks without releasing their tail as keys"):
    val value  = "\u001b]11;" + "f" * (TerminalInputChunk.MaxBytes * 8 + 17) + "\u0007"
    val buffer = TerminalInputBuffer()
    val inputs =
      value.getBytes("UTF-8").grouped(TerminalInputChunk.MaxBytes).toVector.flatMap { bytes =>
        val events = buffer.processAt(TerminalInputChunk(bytes), 0L)
        assertEquals(buffer.flushIfIdle(5000000000L), Vector.empty)
        events
      }
    val chunks = inputs.collect { case TerminalInput.RawChunk(bytes) => bytes }
    assert(chunks.forall(_.length <= TerminalInputChunk.MaxBytes))
    assertEquals(chunks.flatMap(_.toArray).toVector, value.getBytes("UTF-8").toVector)
    assertEquals(inputs.last, TerminalInput.RawEnd(TerminalRawTermination.LimitExceeded(true)))
    assertEquals(inputs.collect { case key: TerminalInput.KeyEvent => key }, Vector.empty)
    assertEquals(buffer.process(chunk("x")), Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("explicit EOF flush preserves a pending ESC and clear resets reply ownership"):
    val buffer = TerminalInputBuffer()
    val value  = "\u001b]11;#1122\u001b"
    val inputs = buffer.process(chunk(value)) ++ buffer.flush()
    assertEquals(inputs.last, TerminalInput.RawEnd(TerminalRawTermination.Incomplete))
    assertEquals(
      inputs.collect { case TerminalInput.RawChunk(bytes) => bytes.toArray.toVector }.flatten,
      value.getBytes("UTF-8").toVector
    )
    assertEquals(buffer.flush(), Vector.empty)
    buffer.process(chunk("\u001b]7501;?"))
    buffer.clear()
    assertEquals(buffer.process(chunk("x")), Vector(TerminalInput.Key(TerminalKey.Character("x"))))

  test("header recognition is bounded and accepts only complete numeric graphics fields"):
    def recognized(value: String): Boolean =
      TerminalReplyFraming.recognizes(value.getBytes("UTF-8").toVector)
    assert(recognized("\u001b_Gi=12,p=3;"))
    assert(!recognized("\u001b_Gi=;"))
    assert(!recognized("\u001b_Gi=1,;"))
    assert(!recognized("\u001b_Gi=1,x=no;"))
    assert(!recognized("\u001b_Gi=" + "1" * TerminalReplyFraming.MaxHeaderBytes + ";"))
    assert(!recognized("\u001b]4;1234;"))

  test("CAN and SUB after a pending ESC do not synthesize Alt control keys"):
    for cancel <- Vector(0x18.toChar, 0x1a.toChar) do
      val buffer = TerminalInputBuffer()
      val prefix = "\u001b]11;#1122\u001b"
      val inputs = buffer.process(chunk(prefix)) ++ buffer.process(chunk(cancel.toString + "x"))
      assertEquals(
        inputs.collect { case TerminalInput.RawEnd(end) => end },
        Vector(TerminalRawTermination.Malformed)
      )
      assertEquals(
        inputs.collect { case TerminalInput.RawChunk(value) => value.toArray.toVector }.flatten,
        (prefix + cancel).getBytes("UTF-8").toVector
      )
      assertEquals(
        inputs.collect { case key: TerminalInput.KeyEvent => key },
        Vector(TerminalInput.Key(TerminalKey.Character("x")))
      )

  private final class ProbeTerminal extends Terminal:
    var receive: TerminalInput => Unit                                             = _ => ()
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      receive = onInput
    override def stop(): Unit                                                      = ()
    override def write(data: String): Unit                                         = ()
    override def columns: Int                                                      = 80
    override def rows: Int                                                         = 24
    override def moveBy(lines: Int): Unit                                          = ()
    override def hideCursor(): Unit                                                = ()
    override def showCursor(): Unit                                                = ()
    override def clearLine(): Unit                                                 = ()
    override def clearFromCursor(): Unit                                           = ()
    override def clearScreen(): Unit                                               = ()

  test("delayed OSC11 completes a TUI query without phantom input"):
    val terminal = ProbeTerminal()
    val tui      = TUI(terminal)
    val buffer   = TerminalInputBuffer()
    var inputs   = Vector.empty[TerminalInput]
    var result   = Option.empty[scalatui.core.TerminalQueryResult[RgbColor]]
    tui.addInputListener { input =>
      inputs :+= input
      InputResult.NoRender
    }
    try
      tui.start()
      tui.queryTerminalBackgroundColor(value => result = Some(value))
      buffer.processAt(chunk("\u001b]11;#1122"), 0L).foreach(terminal.receive)
      buffer.flushIfIdle(100000000L).foreach(terminal.receive)
      buffer.processAt(chunk("33\u0007x"), 100000001L).foreach(terminal.receive)
      assertEquals(result, Some(scalatui.core.TerminalQueryResult.Success(RgbColor(17, 34, 51))))
      assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Character("x"))))
    finally tui.stop()
