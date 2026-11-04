package scalatui.terminal

/** Monotonic input deadlines and monitor waits; callers hold the monitor while awaiting. */
private[scalatui] trait InputTiming:
  def nanoTime(): Long
  def await(monitor: Object, nanos: Long): Unit

private[scalatui] object InputTiming:
  val System: InputTiming = new InputTiming:
    override def nanoTime(): Long                          = java.lang.System.nanoTime()
    override def await(monitor: Object, nanos: Long): Unit =
      monitor.wait(nanos / 1000000L, (nanos % 1000000L).toInt)
