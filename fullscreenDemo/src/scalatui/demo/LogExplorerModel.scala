package scalatui.demo

import scala.collection.mutable
import scalatui.syntax.Equality.*

private[demo] final case class LogEvent(id: Long, level: String, service: String, message: String)
    derives CanEqual:
  def label: String = s"$id $level $service $message"

private[demo] final case class LogExplorerSnapshot(
    rows: Vector[LogEvent],
    selected: Option[LogEvent],
    retained: Int,
    generated: Long,
    evicted: Long,
    filter: String,
    following: Boolean,
    paused: Boolean,
    cancelled: Boolean
)

/** Application-owned, bounded discovery model; no clock, worker or terminal dependencies. */
private[demo] final class LogExplorerModel(val capacity: Int = 200):
  require(capacity >= 1 && capacity <= 10000, "capacity must be between 1 and 10000")
  private val events     = mutable.Queue.empty[LogEvent]
  private var generated  = 0L
  private var evicted    = 0L
  private var query      = ""
  private var selectedId = Option.empty[Long]
  private var following  = true
  private var paused     = false
  private var cancelled  = false

  private def asciiFold(value: String): String =
    value.map(character =>
      if character >= 'A' && character <= 'Z' then (character + 32).toChar else character
    )

  private def matching: Vector[LogEvent] =
    events.iterator.filter(event =>
      asciiFold(event.label).indexOf(query) >= 0
    ).toVector

  private def reconcile(rows: Vector[LogEvent]): Unit =
    if following then selectedId = rows.lastOption.map(_.id)
    else if !rows.exists(event => selectedId.exists(_ === event.id)) then
      selectedId = rows.headOption.map(_.id)

  def snapshot: LogExplorerSnapshot = synchronized {
    val rows = matching
    LogExplorerSnapshot(
      rows,
      rows.find(event => selectedId.exists(_ === event.id)),
      events.size,
      generated,
      evicted,
      query,
      following,
      paused,
      cancelled
    )
  }

  def advance(count: Int): Unit = synchronized {
    require(count >= 0 && count <= 1000, "step must be between 0 and 1000")
    if !paused && !cancelled then
      require(generated <= Long.MaxValue - count.toLong, "event identity exhausted")
      (0 until count).foreach { _ =>
        generated += 1L
        val level   = if generated % 17L === 0L then "ERROR"
        else if generated % 5L === 0L then "WARN" else "INFO"
        val service = Vector("api", "store", "worker")((generated % 3L).toInt)
        if events.size >= capacity then
          events.dequeue()
          evicted += 1L
        events.enqueue(LogEvent(generated, level, service, s"processed batch $generated"))
      }
      reconcile(matching)
  }

  /** Reject oversized committed queries; the Input editor's draft/history is separately owned. */
  def setFilter(value: String): Boolean = synchronized {
    val accepted = value.length <= 256
    if accepted then
      query = asciiFold(value)
      selectedId = None
      reconcile(matching)
    accepted
  }

  def move(delta: Int): Unit = synchronized {
    val rows = matching
    following = false
    if rows.nonEmpty then
      val current = math.max(0, rows.indexWhere(event => selectedId.exists(_ === event.id)))
      val index   =
        math.max(0L, math.min(rows.length.toLong - 1L, current.toLong + delta.toLong)).toInt
      selectedId = Some(rows(index).id)
  }

  def followLatest(): Unit = synchronized {
    following = true
    reconcile(matching)
  }

  def togglePause(): Unit = synchronized { paused = !paused }

  /** Cancellation is terminal for this model; retained rows remain inspectable. */
  def cancel(): Unit = synchronized { cancelled = true }
