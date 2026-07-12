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

  def state: KittyKeyboardProtocolState = current

  def begin(nowMillis: Long, timeoutMillis: Long): KittyKeyboardProtocolState.Pending =
    nextId += 1
    val pending = KittyKeyboardProtocolState.Pending(
      nextId,
      nowMillis + math.max(0L, timeoutMillis)
    )
    current = pending
    pending

  def receiveResponse(response: String, nowMillis: Long): Boolean = current match
    case pending: KittyKeyboardProtocolState.Pending if nowMillis <= pending.deadlineMillis =>
      parseResponse(response) match
        case Some(flags) =>
          current = KittyKeyboardProtocolState.Active(flags)
          true
        case None        => false
    case pending: KittyKeyboardProtocolState.Pending if nowMillis > pending.deadlineMillis  =>
      current = KittyKeyboardProtocolState.Inactive
      false
    case _                                                                                  => false

  def expire(nowMillis: Long): Unit = current match
    case pending: KittyKeyboardProtocolState.Pending if nowMillis > pending.deadlineMillis =>
      current = KittyKeyboardProtocolState.Inactive
    case _                                                                                 => ()

  def disable(): Unit =
    current = KittyKeyboardProtocolState.Inactive

  private def parseResponse(response: String): Option[Int] =
    KittyKeyboardProtocol.Response.findFirstMatchIn(response).flatMap(matchValue =>
      scala.util.Try(matchValue.group(1).toInt).toOption
    )

/** Shared Kitty keyboard protocol escape sequences and capability hook. */
object KittyKeyboardProtocol:
  val QuerySequence: String   = "\u001b[?u"
  val EnableSequence: String  = "\u001b[>1u"
  val DisableSequence: String = "\u001b[<u"

  private[terminal] val Response = "\u001b\\[\\?(\\d+)u".r

/**
 * Optional terminal capability for interactive backends with Kitty keyboard protocol hooks.
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
