package scalatui.editing

/**
 * Small undo-only stack for text editing snapshots.
 *
 * The stack intentionally mirrors `pi-tui`'s undo-only model: callers push immutable or otherwise
 * detached state snapshots before a mutation and pop the most recent snapshot when undo is invoked.
 * The stack does not clone values; components should pass value objects or explicit copies.
 *
 * @param maxEntries
 *   maximum snapshots retained; older entries are discarded when the limit is exceeded
 * @tparam A
 *   snapshot type stored by the component
 */
final class UndoStack[A](maxEntries: Int = UndoStack.DefaultMaxEntries):
  private val limit = math.max(1, maxEntries)
  private var stack = Vector.empty[A]

  /** Push a snapshot onto the stack, discarding oldest snapshots past the configured limit. */
  def push(snapshot: A): Unit =
    stack = (stack :+ snapshot).takeRight(limit)

  /** Pop and return the most recent snapshot, if present. */
  def pop(): Option[A] =
    stack.lastOption.map { snapshot =>
      stack = stack.dropRight(1)
      snapshot
    }

  /** Remove all snapshots. */
  def clear(): Unit = stack = Vector.empty

  /** Number of snapshots currently retained. */
  def length: Int = stack.length

  /** Whether the stack has no snapshots. */
  def isEmpty: Boolean = stack.isEmpty

object UndoStack:
  val DefaultMaxEntries: Int = 100
