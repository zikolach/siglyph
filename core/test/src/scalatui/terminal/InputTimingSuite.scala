package scalatui.terminal

class InputTimingSuite extends munit.FunSuite:
  private final class SteppedTiming extends InputTiming:
    var now                                                = 0L
    var waits                                              = Vector.empty[Long]
    override def nanoTime(): Long                          = now
    override def await(monitor: Object, nanos: Long): Unit = waits :+= nanos

  test("incomplete CSI flushes exactly at its monotonic deadline"):
    val timing        = SteppedTiming()
    val delivery      = OrderedInputDelivery(timing)
    val buffer        = TerminalInputBuffer()
    val generation    = delivery.start(buffer.clear())
    var inputs        = Vector.empty[TerminalInput]
    def flush(): Unit =
      delivery.awaitFlushAndDeliver(
        generation,
        buffer.nanosUntilFlush(timing.nanoTime()),
        buffer.flushIfIdle(timing.nanoTime()),
        TerminalInputBuffer.IncompleteSequenceFlushNanos
      )(input => inputs :+= input)
    delivery.parseAndDeliver(
      generation,
      buffer.processAt(
        TerminalInputChunk("\u001b[".getBytes("UTF-8")),
        timing.nanoTime()
      )
    )(input => inputs :+= input)
    flush()
    assertEquals(timing.waits, Vector(75000000L))
    timing.now = 74000000L
    flush()
    assertEquals(timing.waits.last, 1000000L)
    assertEquals(inputs, Vector.empty)
    timing.now = 75000000L
    flush()
    assertEquals(inputs.head, TerminalInput.RawStart(TerminalRawKind.Csi))
    assertEquals(inputs.last, TerminalInput.RawEnd(TerminalRawTermination.Incomplete))
    assertEquals(inputs.size, 3)

  test("lone escape retains its shorter production deadline"):
    val timing     = SteppedTiming()
    val delivery   = OrderedInputDelivery(timing)
    val buffer     = TerminalInputBuffer()
    val generation = delivery.start(buffer.clear())
    var inputs     = Vector.empty[TerminalInput]
    delivery.parseAndDeliver(
      generation,
      buffer.processAt(
        TerminalInputChunk("\u001b".getBytes("UTF-8")),
        timing.nanoTime()
      )
    )(input => inputs :+= input)
    assertEquals(buffer.nanosUntilFlush(34000000L), Some(1000000L))
    timing.now = 35000000L
    delivery.awaitFlushAndDeliver(
      generation,
      buffer.nanosUntilFlush(timing.nanoTime()),
      buffer.flushIfIdle(timing.nanoTime()),
      TerminalInputBuffer.IncompleteSequenceFlushNanos
    )(input => inputs :+= input)
    assertEquals(inputs, Vector(TerminalInput.Key(TerminalKey.Escape)))
