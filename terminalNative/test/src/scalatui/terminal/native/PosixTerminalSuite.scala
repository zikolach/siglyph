package scalatui.terminal.native

import scalatui.terminal.{KittyKeyboardProtocol, Terminal}

import java.io.{IOException, OutputStream, PrintStream}

class PosixTerminalSuite extends munit.FunSuite:
  test("output-side capabilities do not require or invoke callback delivery"):
    val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)

    terminal.write("")
    terminal.hideCursor()
    terminal.showCursor()
    terminal.clearLine()
    terminal.clearFromCursor()
    terminal.clearScreen()
    terminal.moveBy(1)
    assertEquals(Terminal.setTitle(terminal, "test"), true)
    assertEquals(Terminal.setProgress(terminal, active = true), true)
    terminal.requestKittyKeyboardProtocol(timeoutMillis = 100)

    assertEquals(
      terminal.keyboardProtocolState.isInstanceOf[
        scalatui.terminal.KittyKeyboardProtocolState.Pending
      ],
      true
    )
    assert(KittyKeyboardProtocol.QuerySequence.nonEmpty)

  test("failed Kitty enable retains Native disable cleanup and retries it on stop"):
    val terminal    = PosixTerminal(initialColumns = 80, initialRows = 24)
    val writes      = scala.collection.mutable.ArrayBuffer.empty[String]
    var failEnable  = true
    var failDisable = true
    terminal.writeFailureForTesting = data =>
      writes += data
      if data == KittyKeyboardProtocol.EnableSequence && failEnable then
        failEnable = false
        Some(RuntimeException("injected Kitty enable failure"))
      else if data == KittyKeyboardProtocol.DisableSequence && failDisable then
        failDisable = false
        Some(RuntimeException("injected Kitty disable failure"))
      else None

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
        KittyKeyboardProtocol.QuerySequence,
        KittyKeyboardProtocol.EnableSequence,
        KittyKeyboardProtocol.DisableSequence,
        KittyKeyboardProtocol.DisableSequence
      )
    )

  test("interactive start and output-side operations do not synchronously deliver callbacks"):
    val terminal        = PosixTerminal(initialColumns = 80, initialRows = 24)
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

  test("restart rejects a live old Native flush worker"):
    val terminal          = PosixTerminal(initialColumns = 80, initialRows = 24)
    @volatile var release = false
    val worker            = Thread(() => while !release do Thread.sleep(1), "old-posix-flush-worker")
    worker.start()
    terminal.retainFlushThreadForTesting(worker)

    try
      val failure = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
      assert(failure.getMessage.contains("previous flush worker"), failure.getMessage)
    finally
      release = true
      worker.join()

  test("cleanup retries only the failed Native obligation and rejects restart until it succeeds"):
    Vector("input", "kitty", "paste", "termios").foreach { failedObligation =>
      val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)
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
        Option.when(name == failedObligation && !failed) {
          failed = true
          RuntimeException(s"injected $name cleanup failure")
        }

      val first = intercept[RuntimeException](terminal.stop())
      assertEquals(first.getMessage, s"injected $failedObligation cleanup failure")
      assertEquals(attempts.toVector, Vector("input", "kitty", "paste", "termios"))
      intercept[IllegalStateException](terminal.start(_ => (), () => ()))

      attempts.clear()
      terminal.stop()
      assertEquals(attempts.toVector, Vector(failedObligation))
    }

  test("failed paste enable is disabled before a later Native start"):
    val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)
    val writes   = scala.collection.mutable.ArrayBuffer.empty[String]
    var failed   = false
    terminal.writeFailureForTesting = data =>
      writes += data
      Option.when(data == "\u001b[?2004h" && !failed) {
        failed = true
        RuntimeException("injected paste enable failure")
      }

    val failure = intercept[RuntimeException](terminal.start(_ => (), () => ()))
    assertEquals(failure.getMessage, "injected paste enable failure")
    assertEquals(writes.toVector, Vector("\u001b[?2004h", "\u001b[?2004l"))

    terminal.start(_ => (), () => ())
    terminal.stop()

  test("failed paste enable retains Native cleanup obligation when disable fails"):
    val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)
    terminal.writeFailureForTesting = data =>
      Option.when(data == "\u001b[?2004h")(RuntimeException("injected paste enable failure"))
    terminal.cleanupFailureForTesting = name =>
      Option.when(name == "paste")(RuntimeException("injected paste disable failure"))

    val failure = intercept[RuntimeException](terminal.start(_ => (), () => ()))
    assertEquals(failure.getMessage, "injected paste enable failure")
    assertEquals(
      failure.getSuppressed.toVector.map(_.getMessage),
      Vector("injected paste disable failure")
    )
    intercept[IllegalStateException](terminal.start(_ => (), () => ()))

    terminal.writeFailureForTesting = _ => None
    terminal.cleanupFailureForTesting = _ => None
    terminal.stop()

  test("Native output replacement cannot hide the writing PrintStream failure"):
    val originalOutput    = System.out
    val replacementOutput = PrintStream(new OutputStream:
      override def write(value: Int): Unit = throw IOException("replacement output failure"))
    val failingOutput     = PrintStream(new OutputStream:
      override def write(value: Int): Unit =
        System.setOut(replacementOutput)
        throw IOException("original output failure"))
    val terminal          = PosixTerminal(initialColumns = 80, initialRows = 24)

    System.setOut(failingOutput)
    try
      val failure = intercept[IOException](terminal.start(_ => (), () => ()))
      assert(failure.getMessage.contains("suppressed output failure"), failure.getMessage)
      assertEquals(failure.getSuppressed.toVector.map(_.getClass), Vector(classOf[IOException]))

      val stopFailure = intercept[IOException](terminal.stop())
      assert(stopFailure.getMessage.contains("suppressed output failure"), stopFailure.getMessage)

      val restartFailure = intercept[IllegalStateException](terminal.start(_ => (), () => ()))
      assert(restartFailure.getMessage.contains("cleanup"), restartFailure.getMessage)
    finally
      System.setOut(originalOutput)
      terminal.stop()

  test("Native cleanup aggregates failures in obligation order"):
    val terminal = PosixTerminal(initialColumns = 80, initialRows = 24)
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
    assertEquals(failure.getMessage, "input")
    assertEquals(
      failure.getSuppressed.toVector.map(_.getMessage),
      Vector("kitty", "paste", "termios")
    )

    terminal.cleanupFailureForTesting = _ => None
    terminal.stop()
