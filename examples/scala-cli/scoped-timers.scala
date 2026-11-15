#!/usr/bin/env -S scala-cli shebang
//> using scala 3.9.0
//> using nativeVersion 0.5.12
//> using files ../../core/src

import java.util.concurrent.{CountDownLatch, ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.components.Text
import scalatui.core.TUI
import scalatui.scheduling.ExecutorTaskScheduler
import scalatui.testing.TUITestDriver

@main def testScopedTimers(): Unit =
  val executor           = new ScheduledThreadPoolExecutor(1)
  val done               = new CountDownLatch(1)
  val failure            = new AtomicReference[Throwable]()
  val scheduler          = ExecutorTaskScheduler(executor, error => { failure.set(error); done.countDown() })
  def stopWorker(): Unit =
    try scheduler.close()
    finally
      executor.shutdownNow()
      assert(executor.awaitTermination(5L, TimeUnit.SECONDS))
  try
    val status = Text("waiting", paddingX = 0, paddingY = 0)
    TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, status)) { driver =>
      val scope = driver.tui.openApplicationScope()
      try
        scope.schedule(scheduler, 60000) {
          throw new IllegalStateException("cancelled deadline ran")
        }
        var ticks     = 0
        scope.scheduleEvery(scheduler, 0, 10) { task =>
          ticks += 1
          status.text = s"tick $ticks"
          if ticks >= 3 then
            task.cancel()
            done.countDown()
        }
        val completed = done.await(5L, TimeUnit.SECONDS)
        driver.flush()
        Option(failure.get()).foreach(throw _)
        assert(completed)
        scope.close() // Also cancels the unused 60-second deadline.
        stopWorker()  // Executor ownership stays with the application.
        driver.flush()
        driver.assertContains("tick 3")
        assert(scope.isQuiescent)
      finally
        try scope.close()
        finally stopWorker()
    }
  finally stopWorker()
  println("Scoped timers passed; recurrence, pending timers and worker cleanup completed.")
