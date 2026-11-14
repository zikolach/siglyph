package scalatui.terminal

import scalatui.syntax.Equality.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Root program status; independent of OSC 9;4 progress. */
enum ProgramStatusState derives CanEqual:
  case Idle, Working, Blocked, Done, Error, Clear

/** Reason a blocked program requires user intervention. */
enum ProgramStatusKind derives CanEqual:
  case Permission, Question, Auth

/** Invalid app identifiers are omitted; messages are sanitized and bounded on the wire. */
final case class ProgramStatus(
    state: ProgramStatusState,
    app: Option[String] = None,
    kind: Option[ProgramStatusKind] = None,
    message: String = ""
) derives CanEqual

/** Bounded OSC 7501 root-record encoding. */
object ProgramStatusProtocol:
  val Query: String                      = "\u001b]7501;?\u001b\\"
  private val Reply                      = "\u001b\\]7501;\\?[^\\x07\\x1b]*(?:\\x07|\u001b\\\\)".r
  def isReply(sequence: String): Boolean =
    sequence.length <= 4096 && Reply.matches(sequence)

  def format(status: ProgramStatus): String =
    val pairs   = Vector.newBuilder[String]
    pairs +=
      s"state=${status.state.toString.map(c => if c >= 'A' && c <= 'Z' then (c + 32).toChar else c)}"
    status.app.filter(value => value.length <= 32 && value.matches("[A-Za-z0-9_.+-]{1,32}"))
      .foreach(value => pairs += s"app=$value")
    if status.state === ProgramStatusState.Blocked then
      status.kind.foreach(value =>
        pairs +=
          s"kind=${value.toString.map(c => if c >= 'A' && c <= 'Z' then (c + 32).toChar else c)}"
      )
    val text    = new StringBuilder
    var index   = 0
    var bytes   = 0
    // Scan only a bounded prefix: even hostile giant messages cannot grow retained work.
    while index < status.message.length && bytes < 2048 do
      val cp   = status.message.codePointAt(index)
      val safe = if Character.isISOControl(cp) || cp === 0x2028 || cp === 0x2029 then 32
      else if cp >= 0xd800 && cp <= 0xdfff then 0xfffd else cp
      val size =
        if safe < 0x80 then 1 else if safe < 0x800 then 2 else if safe < 0x10000 then 3 else 4
      if bytes + size > 2048 then bytes = 2048
      else
        text.appendAll(Character.toChars(safe))
        bytes += size
      index += Character.charCount(cp)
    val message = text.toString.trim
    if message.nonEmpty then
      pairs += s"msg=${Base64.getEncoder.encodeToString(message.getBytes(UTF_8))}"
    "\u001b]7501;" + pairs.result().mkString(":") + "\u001b\\"

/**
 * Optional backend mixin. No method is added to Terminal; callers serialize with other output.
 * Reports before a timely positive handshake are no-ops. Failed clears remain retryable.
 */
trait TerminalProgramStatusSupport:
  self: Terminal =>
  private val programStatusSession                                                = ProgramStatusSession(write)
  final def requestProgramStatus(timeoutMillis: Long = 100L): Unit                = synchronized {
    programStatusSession.request(System.currentTimeMillis(), timeoutMillis)
  }
  final def acceptProgramStatusReply(reply: String, arrivalMillis: Long): Boolean = synchronized {
    programStatusSession.receive(reply, arrivalMillis)
  }
  final def programStatusSupported: Boolean                                       = synchronized { programStatusSession.supported }
  final def setProgramStatus(status: ProgramStatus): Boolean                      = synchronized {
    programStatusSession.report(status)
  }
  final def resetProgramStatus(): Unit                                            = synchronized { programStatusSession.reset() }
  final def programStatusCleanupPending: Boolean                                  =
    synchronized { programStatusSession.cleanupPending }

/** Deterministic negotiation and cleanup state, shared by JVM and Native backends. */
private[terminal] final class ProgramStatusSession(write: String => Unit):
  private var deadline                                     = Option.empty[Long]
  private var active                                       = false
  private var dirty                                        = false
  def supported: Boolean                                   = active
  def cleanupPending: Boolean                              = dirty
  def request(nowMillis: Long, timeoutMillis: Long): Unit  =
    require(!dirty, "reset program status before renegotiating")
    active = false
    val timeout = math.max(0L, math.min(timeoutMillis, 60000L))
    deadline =
      Some(if nowMillis > Long.MaxValue - timeout then Long.MaxValue else nowMillis + timeout)
    try write(ProgramStatusProtocol.Query)
    catch
      case error: Throwable =>
        deadline = None
        throw error
  def receive(reply: String, arrivalMillis: Long): Boolean =
    val accepted = ProgramStatusProtocol.isReply(reply) && deadline.exists(arrivalMillis <= _)
    if accepted then
      active = true
      deadline = None
    else if deadline.exists(arrivalMillis > _) then deadline = None
    accepted
  def report(status: ProgramStatus): Boolean               =
    if !active then false
    else
      dirty = true
      write(ProgramStatusProtocol.format(status))
      if status.state === ProgramStatusState.Clear then dirty = false
      true
  def reset(): Unit                                        =
    active = false
    deadline = None
    if dirty then
      write(ProgramStatusProtocol.format(ProgramStatus(ProgramStatusState.Clear)))
      dirty = false
