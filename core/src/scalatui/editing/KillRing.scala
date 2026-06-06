package scalatui.editing

/**
 * Emacs-style kill ring for deleted text.
 *
 * Consecutive kill commands can accumulate into the most recent entry. Backward kills prepend to
 * the accumulated entry while forward kills append, matching `pi-tui` kill/yank behavior. `peek`
 * returns the yank candidate and `rotate()` advances yank-pop cycling.
 *
 * @param maxEntries
 *   maximum kill entries retained; older entries are discarded when the limit is exceeded
 */
final class KillRing(maxEntries: Int = KillRing.DefaultMaxEntries):
  private val limit = math.max(1, maxEntries)
  private var ring  = Vector.empty[String]

  /** Add killed text to the ring, optionally accumulating with the most recent kill. */
  def push(text: String, prepend: Boolean, accumulate: Boolean = false): Unit =
    if text.nonEmpty then
      if accumulate && ring.nonEmpty then
        val last   = ring.last
        val merged = if prepend then text + last else last + text
        ring = ring.dropRight(1) :+ merged
      else ring = (ring :+ text).takeRight(limit)

  /** Most recent yank candidate without modifying the ring. */
  def peek: Option[String] = ring.lastOption

  /** Rotate candidates so a following [[peek]] returns the next yank-pop entry. */
  def rotate(): Unit =
    if ring.length > 1 then
      val last = ring.last
      ring = last +: ring.dropRight(1)

  /** Remove all kill entries. */
  def clear(): Unit = ring = Vector.empty

  /** Number of kill entries currently retained. */
  def length: Int = ring.length

  /** Whether the ring has no kill entries. */
  def isEmpty: Boolean = ring.isEmpty

object KillRing:
  val DefaultMaxEntries: Int = 60
