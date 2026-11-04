package scalatui.core

/** Neutral prepared transcript data; scheduling and generation ownership remain in the runtime. */
private[core] final case class PreparedResizeRecovery(
    lines: Vector[String],
    maxRows: Int,
    generation: Long
)

/** Optional renderer capability for publishing transcript output before the retained frame. */
private[core] trait AppendPublisher:
  def publishAppend(appended: PreparedFrame, retained: PreparedFrame, terminalHeight: Int): Unit

/** Optional renderer capability for preparing normal-screen resize recovery text. */
private[core] trait ResizeRecoveryRenderer:
  def prepareResizeRecovery(lines: Vector[String], width: Int): Vector[String]
