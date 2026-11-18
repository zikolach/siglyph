package scalatui.terminal.jvm.interop

import java.util.Objects
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer
import scalatui.scheduling.{ExecutorTaskScheduler, ScheduledTask, TaskScheduler}

/**
 * Unreleased AutoCloseable scheduler adapter. The executor is borrowed and must be closed by its
 * owner after application scopes and this adapter. Close failures propagate, never disappear.
 */
final class JvmScheduler(
    executor: ScheduledExecutorService,
    maxPending: Int,
    onFailure: Consumer[Throwable]
) extends TaskScheduler, AutoCloseable:
  private val callback                                                       = Objects.requireNonNull(onFailure, "onFailure")
  private val delegate                                                       = ExecutorTaskScheduler(
    Objects.requireNonNull(executor, "executor"),
    error => callback.accept(error),
    maxPending
  )
  override def nowMillis: Long                                               = delegate.nowMillis
  override def schedule(delayMillis: Long)(callback: => Unit): ScheduledTask =
    delegate.schedule(delayMillis)(callback)
  override def close(): Unit                                                 = delegate.close()
  def isQuiescent: Boolean                                                   = delegate.isQuiescent
