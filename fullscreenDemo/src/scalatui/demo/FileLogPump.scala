package scalatui.demo

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scalatui.core.{ApplicationScope, ApplicationSubmission}
import scalatui.syntax.Equality.*

/** One producer thread; at most one immutable batch waits for serialized UI acknowledgement. */
private[demo] final class FileLogPump(
    reader: FileLogReader,
    scope: ApplicationScope,
    canRead: () => Boolean,
    consume: Either[String, FileLogBatch] => Boolean
):
  private val active        = new AtomicBoolean(true)
  private val inFlight      = new AtomicBoolean(false)
  private val pending       = new AtomicReference[Option[Either[String, FileLogBatch]]](None)
  private val currentStatus = new AtomicReference[String]("opening file")
  def status: String        = currentStatus.get()
  def cancel(): Unit        =
    active.set(false)
    pending.set(None)
  def poll(): Unit          =
    if active.get() && !scope.isClosed && canRead() then
      if pending.get().isEmpty then
        pending.set(Some(reader.poll()))
        if !active.get() then pending.set(None)
      if active.get() && inFlight.compareAndSet(false, true) then
        val batch  = pending.get()
        val result = scope.submit {
          try
            batch.foreach { value =>
              if active.get() && consume(value) then
                currentStatus.set(value.fold(
                  error => s"error: $error",
                  result => s"file ${result.offset} bytes"
                ))
                pending.set(None)
            }
          finally inFlight.set(false)
        }
        if result !== ApplicationSubmission.Accepted then inFlight.set(false)
