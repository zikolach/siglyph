package scalatui.components

import scalatui.syntax.Equality.*

/** Bounded ordered effect batches for one component tree or active TUI session. */
private[scalatui] final class ComponentEffectCoordinator private (
    runtimeReady: Option[() => Unit],
    runtimeFailure: Option[Throwable => Unit]
):
  import ComponentEffectCoordinator.*

  private val queue                 = scala.collection.mutable.ArrayDeque.empty[EffectBatch]
  private var phase                 = Phase.Dormant
  private var generation            = 0L
  private var detachedDrainOwned    = false
  private var closingAdmissionsLeft = 0
  private val cleanupAdmission      = new ThreadLocal[Boolean]:
    override def initialValue(): Boolean = false

  def admit[A](operation: ComponentEffects => A): Admission[A] = synchronized {
    val cleanup       = cleanupAdmission.get()
    if phase === Phase.Closing && (!cleanup || closingAdmissionsLeft <= 0) then
      throw ComponentEffectAdmissionException(QueueCapacity)
    if queue.length >= QueueCapacity then throw ComponentEffectAdmissionException(QueueCapacity)
    val effects       = ComponentEffects()
    val result        = operation(effects)
    val queued        = Option.when(effects.nonEmpty) {
      if phase === Phase.Closing then closingAdmissionsLeft -= 1
      val batch = EffectBatch(effects.snapshot, cleanup)
      queue.append(batch)
      batch
    }
    val detachedOwner = queued.nonEmpty && runtimeReady.isEmpty && !detachedDrainOwned
    if detachedOwner then detachedDrainOwned = true
    Admission(result, queued.nonEmpty, detachedOwner)
  }

  def dispatch[A](admission: Admission[A]): A =
    if admission.queued then
      runtimeReady match
        case Some(ready)                     => ready()
        case None if admission.detachedOwner => drainDetached()
        case None                            => ()
    admission.result

  def openGeneration(): Unit = synchronized {
    require(phase === Phase.Dormant, "Component effect coordinator is already active")
    require(queue.isEmpty, "Component effect coordinator has pending detached work")
    generation += 1L
    phase = Phase.Open
  }

  def closeGeneration(): Unit = synchronized {
    if phase === Phase.Open then
      phase = Phase.Closing
      closingAdmissionsLeft = QueueCapacity
  }

  def withLifecycleAdmission[A](operation: => A): A =
    cleanupAdmission.set(true)
    try operation
    finally cleanupAdmission.remove()

  def enqueueCleanup(action: () => Unit): Unit = synchronized {
    require(phase === Phase.Closing, "Component effect cleanup requires a closing generation")
    if queue.length >= QueueCapacity || closingAdmissionsLeft <= 0 then
      throw ComponentEffectAdmissionException(QueueCapacity)
    closingAdmissionsLeft -= 1
    queue.append(EffectBatch(Vector(action), cleanup = true))
  }

  def finishGeneration(): Unit = synchronized {
    require(queue.isEmpty, "Component effect coordinator cleanup requires an empty queue")
    phase = Phase.Dormant
  }

  def hasRuntimeEffects: Boolean = synchronized(queue.nonEmpty)

  def runNextRuntimeBatch(): Unit =
    val batch = synchronized(queue.removeHeadOption())
    batch.foreach { value =>
      cleanupAdmission.set(value.cleanup)
      try value.run().foreach(error => runtimeFailure.foreach(_(error)))
      finally cleanupAdmission.remove()
    }

  private def drainDetached(): Unit =
    var failure = Option.empty[Throwable]
    try
      var continue = true
      while continue do
        val next = synchronized {
          queue.removeHeadOption() match
            case some @ Some(_) => some
            case None           =>
              detachedDrainOwned = false
              continue = false
              None
        }
        next.flatMap(_.run()).foreach { error =>
          failure match
            case Some(first) if first ne error => first.addSuppressed(error)
            case None                          => failure = Some(error)
            case _                             => ()
        }
    finally
      synchronized {
        if detachedDrainOwned && queue.isEmpty then detachedDrainOwned = false
      }
    failure.foreach(throw _)

private[scalatui] object ComponentEffectCoordinator:
  final val QueueCapacity = 4096

  final case class Admission[A](result: A, queued: Boolean, detachedOwner: Boolean)

  private enum Phase derives CanEqual:
    case Dormant, Open, Closing

  private final case class EffectBatch(actions: Vector[() => Unit], cleanup: Boolean):
    def run(): Option[Throwable] =
      var failure = Option.empty[Throwable]
      actions.foreach { action =>
        try action()
        catch
          case error: Throwable => failure match
              case Some(first) if first ne error => first.addSuppressed(error)
              case None                          => failure = Some(error)
              case _                             => ()
      }
      failure

  def detached(): ComponentEffectCoordinator =
    new ComponentEffectCoordinator(None, None)

  def runtime(
      ready: () => Unit,
      failure: Throwable => Unit
  ): ComponentEffectCoordinator =
    new ComponentEffectCoordinator(Some(ready), Some(failure))

private[scalatui] final class ComponentEffectAdmissionException(val capacity: Int)
    extends IllegalStateException(s"Component effect queue capacity exceeded: $capacity")
