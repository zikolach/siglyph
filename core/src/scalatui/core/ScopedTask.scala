package scalatui.core

import scalatui.scheduling.{ScheduledTask, TaskScheduler}
import scalatui.syntax.Equality.*

/** Scope-owned delayed/recurring work. Cancellation never interrupts a claimed user callback. */
final class ScopedTask private[core] (timer: ScopeTimer):
  def cancel(): Boolean                             = timer.cancel()
  def isCancelled: Boolean                          = timer.inspect._1
  def isDone: Boolean                               = timer.inspect._2
  def lastSubmission: Option[ApplicationSubmission] = timer.inspect._3
  def failure: Option[Throwable]                    = timer.inspect._4

/**
 * A provider registration/cleanup is still unresolved; retry close/stop after publication
 * completes.
 */
final class ScheduledCleanupPendingException(val pending: Int)
    extends IllegalStateException(s"$pending scope-owned timer cleanup obligations remain pending")

private[core] final class ScopeTimer(
    owner: TUI,
    scope: ApplicationScope.State,
    scheduler: TaskScheduler,
    interval: Option[Long]
):
  private final class Registration:
    var publishing = true
    var started    = false
    var fired      = false
    var queued     = false
    var delivered  = false
    var cancelling = false
    var handle     = Option.empty[ScheduledTask]
  val task                                        = new ScopedTask(this)
  private var action                              = Option.empty[() => Unit]
  def setActionLocked(callback: () => Unit): Unit = action = Some(callback)
  private var active                              = true
  private var cancelled                           = false
  private var running                             = false
  private var latestRegistration                  = new Registration
  private var registration                        = Option(latestRegistration)
  private var outcome                             = Option.empty[ApplicationSubmission]
  private var error                               = Option.empty[Throwable]

  def inspect: (Boolean, Boolean, Option[ApplicationSubmission], Option[Throwable]) =
    owner.withApplicationLock((
      cancelled,
      !active && !running && registration.isEmpty,
      outcome,
      error
    ))

  private def rememberFailureLocked(failure: Throwable): Unit = error match
    case Some(first) =>
      if (first ne failure) && !first.getSuppressed.exists(_ eq failure) then
        first.addSuppressed(failure)
    case None        => error = Some(failure)

  def disarmLocked(): Unit =
    active = false
    cancelled = true
    action = None

  private def current(reg: Registration): Boolean = registration.exists(_ eq reg)

  private def releaseLocked(reg: Registration): Unit =
    if current(reg) && !active && !reg.publishing && !reg.cancelling &&
      (reg.fired || !reg.started || reg.handle.isEmpty)
    then
      registration = None
      owner.retireScheduledTimerLocked(scope, this)

  def start(delayMillis: Long): Unit =
    val initial = owner.withApplicationLock(registration.get)
    arm(initial, delayMillis, initial = true)

  private def arm(reg: Registration, delayMillis: Long, initial: Boolean): Unit =
    val start = owner.withApplicationLock {
      if current(reg) && active && owner.applicationScopeRunningLocked(scope) then
        reg.started = true
        true
      else
        disarmLocked()
        reg.publishing = false
        releaseLocked(reg)
        false
    }
    if start then
      val handle = try Some(scheduler.schedule(delayMillis) { fire(reg) })
      catch
        case failure: Throwable =>
          owner.withApplicationLock {
            rememberFailureLocked(failure)
            active = false
            action = None
            reg.publishing = false
            reg.started = false
            releaseLocked(reg)
          }
          if initial then throw failure else owner.failApplicationTimer(failure)
          None
      handle.foreach { value =>
        val delivery = owner.withApplicationLock {
          reg.publishing = false
          if !reg.fired then reg.handle = Some(value)
          if !active || !owner.applicationScopeRunningLocked(scope) then
            disarmLocked()
            false
          else if reg.fired && !reg.queued then
            reg.queued = true
            true
          else false
        }
        if delivery then post(reg)
        else if owner.withApplicationLock(!active) then cancelPhysical(reg)
      }

  private def fire(reg: Registration): Unit =
    val delivery = owner.withApplicationLock {
      if !current(reg) || reg.fired then false
      else
        reg.fired = true
        reg.handle = None
        if !active || !owner.applicationScopeRunningLocked(scope) then
          disarmLocked()
          releaseLocked(reg)
          false
        else if !reg.publishing then
          reg.queued = true
          true
        else false
    }
    if delivery then post(reg)

  private def post(reg: Registration): Unit =
    val admitted = owner.submitApplicationUpdate(scope, () => deliver(reg))
    owner.withApplicationLock {
      if latestRegistration eq reg then
        outcome = Some(admitted)
        if admitted !== ApplicationSubmission.Accepted then
          active = false
          action = None
          releaseLocked(reg)
    }

  private def deliver(reg: Registration): Unit =
    val callback = owner.withApplicationLock {
      if current(reg) && active && owner.applicationScopeRunningLocked(scope) then
        outcome = Some(ApplicationSubmission.Accepted)
        running = true
        action
      else None
    }
    try callback.foreach(_())
    catch
      case failure: Throwable =>
        owner.withApplicationLock {
          rememberFailureLocked(failure)
          active = false
          action = None
        }
        throw failure
    finally
      val next = owner.withApplicationLock {
        running = false
        reg.delivered = true
        if current(reg) && active && owner.applicationScopeRunningLocked(scope) && interval.nonEmpty
        then
          val nextReg = new Registration
          registration = Some(nextReg)
          latestRegistration = nextReg
          Some(nextReg -> interval.get)
        else
          active = false
          action = None
          releaseLocked(reg)
          None
      }
      next.foreach(value => arm(value._1, value._2, initial = false))

  private def cancelPhysical(reg: Registration): Unit =
    val handle = owner.withApplicationLock {
      if !reg.publishing && !reg.cancelling && !reg.fired && reg.handle.nonEmpty then
        reg.cancelling = true
        reg.handle
      else
        releaseLocked(reg)
        None
    }
    handle.foreach { value =>
      try
        value.cancel()
        owner.withApplicationLock { reg.handle = None }
      catch
        case failure: Throwable =>
          owner.withApplicationLock { rememberFailureLocked(failure) }
          throw failure
      finally
        owner.withApplicationLock {
          reg.cancelling = false
          releaseLocked(reg)
        }
    }

  def cancel(): Boolean =
    val cancellation = owner.withApplicationLock {
      val prevented = active && (!running || interval.nonEmpty)
      if prevented then disarmLocked()
      prevented -> (if !active then registration else None)
    }
    cancellation._2.foreach(cancelPhysical)
    cancellation._1
