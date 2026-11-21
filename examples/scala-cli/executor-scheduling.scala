#!/usr/bin/env -S scala-cli shebang
//> using scala 3.10.1
//> using nativeVersion 0.5.12
//> using files ../../core/src

import java.util.concurrent.{CountDownLatch, ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.components.Text
import scalatui.core.TUI
import scalatui.scheduling.ExecutorTaskScheduler
import scalatui.testing.TUITestDriver

@main def testExecutorScheduling(): Unit =
  val executor           = new ScheduledThreadPoolExecutor(1)
  val completed          = new CountDownLatch(1)
  val failure            = new AtomicReference[Throwable]()
  val scheduler          = ExecutorTaskScheduler(
    executor,
    error =>
      failure.set(error)
      completed.countDown()
  )
  def stopWorker(): Unit =
    try scheduler.close()
    finally
      executor.shutdownNow()
      assert(executor.awaitTermination(5L, TimeUnit.SECONDS))
  try
    val status = Text("waiting", paddingX = 0, paddingY = 0)
    TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, status)) { driver =>
      try
        val scope = driver.tui.openApplicationScope()
        scheduler.schedule(0) {
          scope.submit { status.text = "executor update" }
          completed.countDown()
        }
        assert(completed.await(5L, TimeUnit.SECONDS))
        Option(failure.get()).foreach(throw _)
        driver.flush()
        driver.assertContains("executor update")
      finally stopWorker()
    }
  finally stopWorker()
  assert(scheduler.isQuiescent)
  println("Executor scheduling passed; worker and terminal cleanup completed.")
