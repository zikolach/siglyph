package scalatui.components

import scalatui.core.TUIContext

/** Shared JVM and Scala Native serialization boundary for built-in mutable components. */
private[scalatui] final class ComponentStateBoundary:
  private val detachedCoordinator = ComponentEffectCoordinator.detached()
  private var coordinator         = detachedCoordinator
  private var detachPending       = false

  /** Capture or mutate component-owned state without invoking application or runtime code. */
  def apply[A](operation: => A): A = synchronized(operation)

  /** Commit one state transition and enqueue its immutable effects in coordinator order. */
  def transition[A](operation: ComponentEffects => A): A =
    val (selected, admission) = synchronized {
      coordinator -> coordinator.admit(operation)
    }
    selected.dispatch(admission)

  /**
   * Atomically change this boundary's context binding and enqueue attach or detach effects through
   * the coordinator that owns that transition.
   */
  def transitionContext[A](
      current: => Option[TUIContext],
      next: Option[TUIContext]
  )(operation: ComponentEffects => A): A =
    val (selected, admission) = synchronized {
      val previous  = current
      if detachPending && next.nonEmpty then
        throw IllegalStateException("A contextual component detach is still pending")
      if previous.nonEmpty && next.nonEmpty && !previous.exists(existing =>
          next.exists(_ eq existing)
        )
      then
        throw IllegalStateException(
          "A contextual component must detach before attaching to another TUI context"
        )
      val selected  = previous.orElse(next).map(_.componentEffectCoordinator).getOrElse(
        coordinator
      )
      val admission = selected.admit { effects =>
        val result = operation(effects)
        if previous.nonEmpty && next.isEmpty then
          detachPending = true
          effects.add(() =>
            synchronized {
              coordinator = detachedCoordinator
              detachPending = false
            }
          )
        result
      }
      if previous.isEmpty && next.nonEmpty then coordinator = selected
      selected -> admission
    }
    selected.dispatch(admission)

/** Ordered application and runtime effects captured by one component state transition. */
private[scalatui] final class ComponentEffects:
  private val actions = scala.collection.mutable.ArrayBuffer.empty[() => Unit]

  def add(action: () => Unit): Unit         = actions += action
  def nonEmpty: Boolean                     = actions.nonEmpty
  def addAll(other: ComponentEffects): Unit = actions ++= other.snapshot
  def snapshot: Vector[() => Unit]          = actions.toVector
