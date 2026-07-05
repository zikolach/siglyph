package scalatui.extras

import scalatui.syntax.Equality.*

import scala.collection.mutable.ArrayBuffer

/**
 * Applies one Boolean expansion state to explicitly registered [[Expandable]] instances.
 *
 * The controller has no `TUI` dependency and does not request rendering. Applications own
 * keybindings, render scheduling, and lifecycle registration.
 */
final class ExpansionController(initiallyExpanded: Boolean = false):
  private val registered = ArrayBuffer.empty[Expandable]
  private var state      = initiallyExpanded

  /** Current controller state. */
  def expanded: Boolean = state

  /** Number of currently registered expandables. */
  def size: Int = registered.size

  /** Register an expandable and immediately apply the current controller state to it. */
  def register(expandable: Expandable): Unit =
    if !registered.exists(existing => sameReference(existing, expandable)) then
      registered += expandable
      expandable.setExpanded(state)

  /** Unregister an expandable. Returns true when a registered instance was removed. */
  def unregister(expandable: Expandable): Boolean =
    val index = registered.indexWhere(existing => sameReference(existing, expandable))
    if index >= 0 then
      registered.remove(index)
      true
    else false

  /** Remove all registered expandables without mutating their current state. */
  def clear(): Unit = registered.clear()

  /** Apply a new state to registered expandables. Returns true only when the state changed. */
  def setExpanded(expanded: Boolean): Boolean =
    if state === expanded then false
    else
      state = expanded
      registered.toVector.foreach(_.setExpanded(expanded))
      true

  /** Toggle the state and apply it to registered expandables. */
  def toggle(): Boolean = setExpanded(!state)

  private def sameReference(left: Expandable, right: Expandable): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]
