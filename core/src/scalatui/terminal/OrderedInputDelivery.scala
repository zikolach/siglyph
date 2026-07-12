package scalatui.terminal

import scalatui.syntax.Equality.*

/** Orders parser batches across backend reader and flush threads for one terminal start. */
private[terminal] final class OrderedInputDelivery:
  private val stateLock    = Object()
  private var generation   = 0L
  private var active       = false
  private var nextBatch    = 0L
  private var nextDelivery = 0L

  def start(resetParser: => Unit): Long = stateLock.synchronized {
    generation += 1
    resetParser
    active = true
    nextBatch = 0L
    nextDelivery = 0L
    stateLock.notifyAll()
    generation
  }

  def stop(expectedGeneration: Long, clearParser: => Unit): Boolean = stateLock.synchronized {
    if active && generation === expectedGeneration then
      active = false
      generation += 1
      clearParser
      stateLock.notifyAll()
      true
    else false
  }

  def clear(action: => Unit): Unit = stateLock.synchronized(action)

  def isActive(expectedGeneration: Long): Boolean = stateLock.synchronized {
    active && generation === expectedGeneration
  }

  def parseAndDeliver(
      expectedGeneration: Long,
      parse: => Vector[TerminalInput]
  )(deliver: TerminalInput => Unit): Unit =
    val batch = stateLock.synchronized {
      Option.when(active && generation === expectedGeneration) {
        val inputs = parse
        val id     = nextBatch
        nextBatch += 1
        id -> inputs
      }
    }

    batch.foreach { (id, inputs) =>
      var interrupted = false
      try
        val accepted = stateLock.synchronized {
          while active && generation === expectedGeneration && (id !== nextDelivery) do
            try stateLock.wait()
            catch case _: InterruptedException => interrupted = true
          active && generation === expectedGeneration
        }

        if accepted then
          try
            inputs.iterator.takeWhile(_ => isActive(expectedGeneration)).foreach(deliver)
          finally
            stateLock.synchronized {
              if active && generation === expectedGeneration && (id === nextDelivery) then
                nextDelivery += 1
                stateLock.notifyAll()
            }
      finally if interrupted then Thread.currentThread().interrupt()
    }
