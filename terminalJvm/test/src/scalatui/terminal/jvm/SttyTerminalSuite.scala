package scalatui.terminal.jvm

import scalatui.terminal.{KittyKeyboardProtocol, KittyKeyboardProtocolState, Terminal}
import scalatui.syntax.Equality.*

import java.io.{ByteArrayOutputStream, IOException, InputStream, OutputStream, PrintStream}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

class SttyTerminalSuite extends munit.FunSuite:
  test("title and progress support writes terminal protocol sequences"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    assertEquals(Terminal.setTitle(terminal, "safe\u001btitle"), true)
    assertEquals(Terminal.setProgress(terminal, active = true), true)
    assertEquals(Terminal.setProgress(terminal, active = false), true)

    val written = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(written.contains("\u001b]0;safetitle\u0007"), written)
    assert(written.contains(Terminal.ProgressActiveSequence), written)
    assert(written.contains(Terminal.ProgressClearSequence), written)

  test("direct title support sanitizes control characters"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.setTitle("safe\u001btitle\u0007")

    assertEquals(
      output.toString(java.nio.charset.StandardCharsets.UTF_8),
      "\u001b]0;safetitle\u0007"
    )

  test("Kitty keyboard negotiation writes enable sequence after accepted response"):
    val output   = ByteArrayOutputStream()
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.requestKittyKeyboardProtocol(timeoutMillis = 1000)
    assertEquals(
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      ),
      true
    )

    val written = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(written.contains(KittyKeyboardProtocol.QuerySequence), written)
    assert(written.contains(KittyKeyboardProtocol.EnableSequence), written)
    assertEquals(terminal.keyboardProtocolState, KittyKeyboardProtocolState.Active(3))

  test("failed Kitty enable retains JVM disable cleanup and retries it on stop"):
    val writes      = scala.collection.mutable.ArrayBuffer.empty[String]
    var failEnable  = true
    var failDisable = true
    val output      = new ByteArrayOutputStream:
      override def flush(): Unit =
        val written = toString(java.nio.charset.StandardCharsets.UTF_8)
        val latest  =
          if written.endsWith(KittyKeyboardProtocol.EnableSequence) then
            KittyKeyboardProtocol.EnableSequence
          else if written.endsWith(KittyKeyboardProtocol.DisableSequence) then
            KittyKeyboardProtocol.DisableSequence
          else ""
        if latest.nonEmpty then writes += latest
        if (latest === KittyKeyboardProtocol.EnableSequence) && failEnable then
          failEnable = false
          throw RuntimeException("injected Kitty enable failure")
        if (latest === KittyKeyboardProtocol.DisableSequence) && failDisable then
          failDisable = false
          throw RuntimeException("injected Kitty disable failure")
    val terminal    = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.requestKittyKeyboardProtocol(timeoutMillis = 1000)
    val enableFailure = intercept[RuntimeException](
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      )
    )
    assertEquals(enableFailure.getMessage, "injected Kitty enable failure")

    val firstStop = intercept[RuntimeException](terminal.stop())
    assertEquals(firstStop.getMessage, "injected Kitty disable failure")
    terminal.stop()
    assertEquals(
      writes.toVector,
      Vector(
        KittyKeyboardProtocol.EnableSequence,
        KittyKeyboardProtocol.DisableSequence,
        KittyKeyboardProtocol.DisableSequence
      )
    )

  test("Kitty keyboard state expires pending negotiation on state read"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    terminal.requestKittyKeyboardProtocol(timeoutMillis = 0)

    val deadline = System.currentTimeMillis() + 1000L
    while
      terminal.keyboardProtocolState match
        case KittyKeyboardProtocolState.Pending(_, _) => System.currentTimeMillis() < deadline
        case _                                        => false
    do
      // Poll the real clock at 1 ms intervals so the strict zero-length deadline can expire.
      Thread.sleep(1)

    assertEquals(
      terminal.keyboardProtocolState,
      KittyKeyboardProtocolState.Inactive,
      "Kitty keyboard negotiation remained pending after the 1000 ms expiry deadline"
    )

  test("interactive start and output-side operations do not synchronously deliver callbacks"):
    val terminal        = SttyTerminal()
    val caller          = Thread.currentThread()
    var inlineCallbacks = 0
    terminal.start(
      _ => if Thread.currentThread() eq caller then inlineCallbacks += 1,
      () => if Thread.currentThread() eq caller then inlineCallbacks += 1
    )
    try
      terminal.write("")
      terminal.hideCursor()
      terminal.showCursor()
      terminal.clearLine()
      terminal.clearFromCursor()
      terminal.clearScreen()
      terminal.moveBy(1)
      terminal.drainInput()
      Terminal.setTitle(terminal, "test")
      Terminal.setProgress(terminal, active = true)
      terminal.requestKittyKeyboardProtocol(timeoutMillis = 100)
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      )
      terminal.disableKittyKeyboardProtocol()
      assertEquals(inlineCallbacks, 0)
    finally terminal.stop()

  test("duplicate running start is distinct from incomplete cleanup rejection"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    terminal.start(_ => (), () => ())
    try
      val duplicate = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
      assertEquals(duplicate.getMessage, "SttyTerminal is already running")

      terminal.cleanupFailureForTesting = name =>
        Option.when(name === "input")(RuntimeException("injected input cleanup failure"))
      intercept[RuntimeException](terminal.stop())

      val incompleteCleanup = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
      assertEquals(
        incompleteCleanup.getMessage,
        "cannot start SttyTerminal while cleanup from the previous generation remains incomplete"
      )
    finally
      terminal.cleanupFailureForTesting = _ => None
      terminal.stop()

  test("restart rejects the actual old JVM resize worker until its callback returns"):
    val sizeQueries   = AtomicInteger(0)
    val resizeStarted = CountDownLatch(1)
    val releaseResize = CountDownLatch(1)
    val terminal      = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () =>
        if sizeQueries.getAndIncrement() === 0 then Some(24 -> 80)
        else Some(25                                        -> 81)
    )
    terminal.start(
      _ => (),
      () => {
        resizeStarted.countDown()
        var released = false
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while !released && System.nanoTime() < deadline do
          try
            released = releaseResize.await(
              math.max(1L, deadline - System.nanoTime()),
              TimeUnit.NANOSECONDS
            )
          catch case _: InterruptedException => ()
        if !released then
          throw AssertionError("old JVM resize worker was not released within 5000 ms")
      }
    )
    assert(resizeStarted.await(1, TimeUnit.SECONDS))
    terminal.stop()

    val failure = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
    assert(failure.getMessage.contains("previous resize worker"), failure.getMessage)

    releaseResize.countDown()
    val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    var restarted = false
    while !restarted && System.nanoTime() < deadline do
      try
        terminal.start(_ => (), () => ())
        restarted = true
      catch case _: IllegalStateException => Thread.onSpinWait()
    assert(restarted, "old resize worker did not terminate before restart deadline")
    terminal.stop()

  test("cleanup retries only the failed JVM obligation and rejects restart until it succeeds"):
    Vector("kitty", "paste", "input", "termios").foreach { failedObligation =>
      val terminal = SttyTerminal(
        input = InputStream.nullInputStream(),
        output = ByteArrayOutputStream(),
        columnsOverride = Some(80),
        rowsOverride = Some(24),
        sizeQuery = () => Some(24 -> 80)
      )
      terminal.start(_ => (), () => ())
      terminal.requestKittyKeyboardProtocol(timeoutMillis = 1000)
      assert(
        terminal.acceptKittyKeyboardProtocolResponse(
          "\u001b[?3u",
          nowMillis = System.currentTimeMillis()
        )
      )

      val attempts = scala.collection.mutable.ArrayBuffer.empty[String]
      var failed   = false
      terminal.cleanupFailureForTesting = name =>
        attempts += name
        Option.when((name === failedObligation) && !failed) {
          failed = true
          RuntimeException(s"injected $name cleanup failure")
        }

      val first = intercept[RuntimeException](terminal.stop())
      assertEquals(first.getMessage, s"injected $failedObligation cleanup failure")
      assertEquals(attempts.toVector, Vector("kitty", "paste", "input", "termios"))
      intercept[IllegalStateException](terminal.start(_ => (), () => ()))

      attempts.clear()
      terminal.stop()
      assertEquals(attempts.toVector, Vector(failedObligation))
    }

  test("cleanup aggregates later JVM obligation failures as suppressed"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    terminal.start(_ => (), () => ())
    terminal.requestKittyKeyboardProtocol(timeoutMillis = 1000)
    assert(
      terminal.acceptKittyKeyboardProtocolResponse(
        "\u001b[?3u",
        nowMillis = System.currentTimeMillis()
      )
    )
    terminal.cleanupFailureForTesting = name => Some(RuntimeException(name))

    val failure = intercept[RuntimeException](terminal.stop())

    assertEquals(failure.getMessage, "kitty")
    assertEquals(
      failure.getSuppressed.toVector.map(_.getMessage),
      Vector("paste", "input", "termios")
    )
    terminal.cleanupFailureForTesting = _ => None
    terminal.stop()

  test("initial terminal state capture failure has actionable diagnostic and original cause"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    val original = RuntimeException("missing controlling terminal")
    terminal.sttyFailureForTesting = args => Option.when(args === "-g")(original)

    val failure = intercept[IllegalStateException](terminal.start(_ => (), () => ()))

    assert(failure.getMessage.contains("accessible interactive /dev/tty"), failure.getMessage)
    assert(failure.getMessage.contains("StreamTerminal"), failure.getMessage)
    assert(failure.getCause eq original)

  test("failed paste enable is disabled before a later JVM start"):
    val output   = new ByteArrayOutputStream:
      private var failEnable     = true
      override def flush(): Unit =
        if failEnable && toString(java.nio.charset.StandardCharsets.UTF_8).endsWith("\u001b[?2004h")
        then
          failEnable = false
          throw RuntimeException("injected paste enable failure")
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    val failure = intercept[RuntimeException](terminal.start(_ => (), () => ()))
    assertEquals(failure.getMessage, "injected paste enable failure")
    val written = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(written.contains("\u001b[?2004h"), written)
    assert(written.endsWith("\u001b[?2004l"), written)

    terminal.start(_ => (), () => ())
    terminal.stop()

  test("failed paste enable retains JVM cleanup obligation when disable fails"):
    val output   = new ByteArrayOutputStream:
      override def flush(): Unit =
        if toString(java.nio.charset.StandardCharsets.UTF_8).endsWith("\u001b[?2004h") then
          throw RuntimeException("injected paste enable failure")
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    terminal.cleanupFailureForTesting = name =>
      Option.when(name === "paste")(RuntimeException("injected paste disable failure"))

    val failure = intercept[RuntimeException](terminal.start(_ => (), () => ()))
    assertEquals(failure.getMessage, "injected paste enable failure")
    assertEquals(
      failure.getSuppressed.toVector.map(_.getMessage),
      Vector("injected paste disable failure")
    )
    intercept[IllegalStateException](terminal.start(_ => (), () => ()))

    terminal.cleanupFailureForTesting = _ => None
    terminal.stop()

  test("suppressed PrintStream failure retains paste cleanup and rejects restart"):
    val output   = PrintStream(new OutputStream:
      override def write(value: Int): Unit = throw IOException("injected output failure"))
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = output,
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )

    val failure = intercept[IOException](terminal.start(_ => (), () => ()))
    assert(failure.getMessage.contains("suppressed output failure"), failure.getMessage)
    assertEquals(failure.getSuppressed.toVector.map(_.getClass), Vector(classOf[IOException]))

    val restartFailure = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
    assert(restartFailure.getMessage.contains("cleanup"), restartFailure.getMessage)

  test("later stty startup failures retain their original classification"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    val original = RuntimeException("raw mode failed")
    terminal.sttyFailureForTesting = args =>
      Option.when(args === "raw -echo min 1 time 0")(original)

    val failure = intercept[RuntimeException](terminal.start(_ => (), () => ()))
    assert(failure eq original)
    assertEquals(failure.getCause, null)

  test("later stty restoration failures retain their original classification"):
    val terminal = SttyTerminal(
      input = InputStream.nullInputStream(),
      output = ByteArrayOutputStream(),
      columnsOverride = Some(80),
      rowsOverride = Some(24),
      sizeQuery = () => Some(24 -> 80)
    )
    terminal.start(_ => (), () => ())
    val original = RuntimeException("restoration failed")
    terminal.sttyFailureForTesting = args =>
      Option.when((args !== "-g") && (args !== "raw -echo min 1 time 0"))(original)

    val failure = intercept[RuntimeException](terminal.stop())
    assert(failure eq original)
    assertEquals(failure.getCause, null)

    terminal.sttyFailureForTesting = _ => None
    terminal.stop()
