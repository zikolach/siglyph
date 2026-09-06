package scalatui.core

/** Numeric runtime work snapshot for deterministic package-local tests and benchmarks. */
private[scalatui] final case class RuntimeCounterSnapshot(
    componentRenders: Long,
    paintedRows: Long,
    terminalWrites: Long,
    controlEncodes: Long,
    searchScans: Long
) derives CanEqual

/**
 * Package-local counters that retain no rendered lines, controls, payloads, or application text.
 */
private[scalatui] final class RuntimeCounters:
  private var componentRenderCount = 0L
  private var paintedRowCount      = 0L
  private var terminalWriteCount   = 0L
  private var controlEncodeCount   = 0L
  private var searchScanCount      = 0L

  def recordComponentRender(): Unit       = synchronized { componentRenderCount += 1 }
  def recordPaintedRows(count: Int): Unit = synchronized { paintedRowCount += math.max(0, count) }
  def recordTerminalWrite(): Unit         = synchronized { terminalWriteCount += 1 }
  def recordControlEncode(): Unit         = synchronized { controlEncodeCount += 1 }
  def recordSearchScans(count: Int): Unit = synchronized { searchScanCount += math.max(0, count) }

  def snapshot: RuntimeCounterSnapshot = synchronized {
    RuntimeCounterSnapshot(
      componentRenderCount,
      paintedRowCount,
      terminalWriteCount,
      controlEncodeCount,
      searchScanCount
    )
  }

private[core] object RuntimeCounterScope:
  private val current = new ThreadLocal[RuntimeCounters | Null]:
    override def initialValue(): RuntimeCounters | Null = null

  def withCounters[A](counters: RuntimeCounters)(action: => A): A =
    val previous = current.get()
    current.set(counters)
    try action
    finally current.set(previous)

  def recordComponentRender(): Unit =
    Option(current.get()).foreach(_.recordComponentRender())
