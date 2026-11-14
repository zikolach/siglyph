package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.*
import scala.collection.mutable.ArrayBuffer

class ProgramStatusCleanupSuite extends munit.FunSuite:
  private final class FaultTerminal extends Terminal, TerminalProgramStatusSupport:
    val writes                                                                     = ArrayBuffer.empty[String]
    val clearError                                                                 = RuntimeException("status clear failed")
    val backendError                                                               = RuntimeException("backend stop failed")
    var clearFailures                                                              = 0
    var clearAttempts                                                              = 0
    var stopCalls                                                                  = 0
    var failStop                                                                   = false
    var stopResetsStatus                                                           = false
    var backendRunning                                                             = false
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      backendRunning = true
    override def stop(): Unit                                                      =
      stopCalls += 1
      backendRunning = false
      if stopResetsStatus then resetProgramStatus()
      if failStop then throw backendError
    override def write(data: String): Unit                                         =
      if data.contains("7501;state=clear") then
        clearAttempts += 1
        if clearFailures > 0 then
          clearFailures -= 1
          throw clearError
      writes += data
    override def columns: Int                                                      = 80
    override def rows: Int                                                         = 24
    override def moveBy(lines: Int): Unit                                          = ()
    override def hideCursor(): Unit                                                = ()
    override def showCursor(): Unit                                                = ()
    override def clearLine(): Unit                                                 = ()
    override def clearFromCursor(): Unit                                           = ()
    override def clearScreen(): Unit                                               = ()

  private def started(terminal: FaultTerminal): TUI =
    val tui = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
    tui.start()
    terminal.requestProgramStatus(60000L)
    assert(terminal.acceptProgramStatusReply(
      ProgramStatusProtocol.Query,
      System.currentTimeMillis()
    ))
    assert(tui.setTerminalProgramStatus(ProgramStatus(ProgramStatusState.Working)))
    assert(terminal.programStatusCleanupPending)
    tui

  private def finish(tui: TUI, terminal: FaultTerminal): Unit =
    terminal.clearFailures = 0
    terminal.failStop = false
    tui.stop()

  test("failed status clear still stops backend and retries only the outstanding status"):
    val terminal = FaultTerminal()
    val tui      = started(terminal)
    try
      terminal.clearFailures = 1
      val error = intercept[RuntimeException](tui.stop())
      assert(error eq terminal.clearError)
      assertEquals(terminal.stopCalls, 1)
      assert(!terminal.backendRunning)
      assert(terminal.programStatusCleanupPending)
      assert(!terminal.writes.contains(TUI.AlternateScreenExit))
      intercept[IllegalStateException](tui.start())
      tui.stop()
      assertEquals(terminal.stopCalls, 1)
      assertEquals(terminal.clearAttempts, 2)
      assert(!terminal.programStatusCleanupPending)
      assertEquals(terminal.writes.count(_ === TUI.AlternateScreenExit), 1)
      tui.stop()
      assertEquals(terminal.stopCalls, 1)
      assertEquals(terminal.clearAttempts, 2)
    finally finish(tui, terminal)

  test("status failure remains primary and backend failure is suppressed"):
    val terminal = FaultTerminal()
    val tui      = started(terminal)
    try
      terminal.clearFailures = 2
      terminal.failStop = true
      val first  = intercept[RuntimeException](tui.stop())
      assert(first eq terminal.clearError)
      assertEquals(terminal.stopCalls, 1)
      assertEquals(first.getSuppressed.toVector, Vector(terminal.backendError))
      terminal.failStop = false
      val second = intercept[RuntimeException](tui.stop())
      assert(second eq terminal.clearError)
      assertEquals(terminal.stopCalls, 2)
      assert(terminal.programStatusCleanupPending)
      tui.stop()
      assertEquals(terminal.stopCalls, 2)
      assert(!terminal.programStatusCleanupPending)
      assertEquals(terminal.writes.count(_ === TUI.AlternateScreenExit), 1)
    finally finish(tui, terminal)

  test("successful status clear is not repeated when backend shutdown needs retry"):
    val terminal = FaultTerminal()
    val tui      = started(terminal)
    try
      terminal.failStop = true
      val error = intercept[RuntimeException](tui.stop())
      assert(error eq terminal.backendError)
      assertEquals(terminal.stopCalls, 1)
      assertEquals(terminal.clearAttempts, 1)
      assert(!terminal.programStatusCleanupPending)
      terminal.failStop = false
      tui.stop()
      assertEquals(terminal.stopCalls, 2)
      assertEquals(terminal.clearAttempts, 1)
      assertEquals(terminal.writes.count(_ === TUI.AlternateScreenExit), 1)
    finally finish(tui, terminal)

  test("backend-owned successful status reset completes restoration but does not hide first error"):
    val terminal = FaultTerminal()
    val tui      = started(terminal)
    try
      terminal.stopResetsStatus = true
      terminal.clearFailures = 1
      val error = intercept[RuntimeException](tui.stop())
      assert(error eq terminal.clearError)
      assertEquals(terminal.stopCalls, 1)
      assertEquals(terminal.clearAttempts, 2)
      assert(!terminal.programStatusCleanupPending)
      assertEquals(terminal.writes.count(_ === TUI.AlternateScreenExit), 1)
      tui.stop()
      assertEquals(terminal.stopCalls, 1)
    finally finish(tui, terminal)
