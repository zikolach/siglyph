package scalatui.terminal

import scalatui.syntax.Equality.*

/** Public state for conservative Kitty keyboard protocol negotiation. */
sealed trait KittyKeyboardProtocolState derives CanEqual

object KittyKeyboardProtocolState:
  case object Inactive                                     extends KittyKeyboardProtocolState
  final case class Pending(id: Long, deadlineMillis: Long) extends KittyKeyboardProtocolState
  final case class Active(flags: Int)                      extends KittyKeyboardProtocolState

/** Pure negotiation helper that is testable without a live terminal. */
final class KittyKeyboardProtocolNegotiator:
  private var nextId                              = 0L
  private var current: KittyKeyboardProtocolState = KittyKeyboardProtocolState.Inactive
  // Keep at most one expired request for replies admitted before its deadline but handled later.
  private var expiredPending                      = Option.empty[KittyKeyboardProtocolState.Pending]

  def state: KittyKeyboardProtocolState = current

  def begin(nowMillis: Long, timeoutMillis: Long): KittyKeyboardProtocolState.Pending =
    nextId += 1
    val pending = KittyKeyboardProtocolState.Pending(
      nextId,
      nowMillis + math.max(0L, timeoutMillis)
    )
    current = pending
    expiredPending = None
    pending

  def receiveResponse(response: String, nowMillis: Long): Boolean =
    expire(nowMillis)
    val pending = current match
      case value: KittyKeyboardProtocolState.Pending => Some(value)
      case KittyKeyboardProtocolState.Inactive       => expiredPending
      case _                                         => None
    if pending.exists(nowMillis <= _.deadlineMillis) then
      KittyKeyboardProtocol.parseResponse(response) match
        case Some(flags) =>
          current = KittyKeyboardProtocolState.Active(flags)
          expiredPending = None
          true
        case None        => false
    else false

  def expire(nowMillis: Long): Unit = current match
    case pending: KittyKeyboardProtocolState.Pending if nowMillis > pending.deadlineMillis =>
      expiredPending = Some(pending)
      current = KittyKeyboardProtocolState.Inactive
    case _                                                                                 => ()

  def disable(): Unit =
    current = KittyKeyboardProtocolState.Inactive
    expiredPending = None

/** Shared Kitty keyboard protocol escape sequences and capability hook. */
object KittyKeyboardProtocol:
  val QuerySequence: String   = "\u001b[?u"
  val EnableSequence: String  = "\u001b[>1u"
  val DisableSequence: String = "\u001b[<u"

  private val Response = "\u001b\\[\\?(\\d+)u".r

  private[scalatui] def parseResponse(response: String): Option[Int] = response match
    case Response(flags) => scala.util.Try(flags.toInt).toOption
    case _               => None

/**
 * Optional terminal capability for interactive backends with Kitty keyboard protocol hooks. TUI
 * requests negotiation automatically. Direct backend consumers can use these hooks explicitly.
 * Disabling pending negotiation cancels the query without popping a mode. After an enable attempt,
 * disabling pops the mode once. If output fails, the backend retains cleanup for a later stop.
 * Output-side requests and state changes must not synchronously deliver terminal callbacks.
 */
trait KittyKeyboardProtocolTerminal:
  def keyboardProtocolState: KittyKeyboardProtocolState
  def requestKittyKeyboardProtocol(timeoutMillis: Long = 100L): Unit
  def acceptKittyKeyboardProtocolResponse(
      response: String,
      nowMillis: Long = System.currentTimeMillis()
  ): Boolean
  def disableKittyKeyboardProtocol(): Unit
