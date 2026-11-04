package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{RgbColor, TerminalColorScheme, TerminalCursorProtocol}

/**
 * Query bookkeeping only. Every operation runs under TUI's existing lifecycle lock; callbacks and
 * terminal writes remain the runtime owner's responsibility.
 */
private[core] final class QueryCoordinator:
  import QueryCoordinator.*

  var backgroundColorFlight    = Option.empty[QueryFlight[RgbColor]]
  var colorSchemeFlight        = Option.empty[QueryFlight[TerminalColorScheme]]
  var cursorPositionFlight     = Option.empty[QueryFlight[TerminalCursorProtocol.CursorPosition]]
  var writeReservations        = 0
  private var nextFlightId     = 0L
  private var nextSubscriberId = 0L

  def subscriber[A](callback: TerminalQueryResult[A] => Unit): QuerySubscriber[A] =
    nextSubscriberId += 1
    QuerySubscriber(nextSubscriberId, callback)

  def subscribe[A](
      flight: Option[QueryFlight[A]],
      subscriber: QuerySubscriber[A]
  ): (QueryFlight[A], Option[Long]) = flight match
    case Some(value) => (value.copy(subscribers = value.subscribers :+ subscriber), None)
    case None        =>
      nextFlightId += 1
      writeReservations += 1
      (QueryFlight(nextFlightId, QueryFlightPhase.Reserved, Vector(subscriber)), Some(nextFlightId))

  def emitted[A](
      flight: Option[QueryFlight[A]],
      id: Long,
      stopping: Boolean
  ): (Option[QueryFlight[A]], Vector[QueryCompletion[A]]) =
    finishWrite(flight, id, if stopping then Some(TerminalQueryResult.Stopped) else None)

  def failed[A](
      flight: Option[QueryFlight[A]],
      id: Long,
      error: Throwable
  ): (Option[QueryFlight[A]], Vector[QueryCompletion[A]]) =
    finishWrite(flight, id, Some(TerminalQueryResult.Failed(error)))

  private def finishWrite[A](
      flight: Option[QueryFlight[A]],
      id: Long,
      result: Option[TerminalQueryResult[A]]
  ): (Option[QueryFlight[A]], Vector[QueryCompletion[A]]) =
    writeReservations -= 1
    flight match
      case Some(value) if value.id === id =>
        result match
          case Some(outcome) => (None, complete(flight, outcome))
          case None          => (Some(value.copy(phase = QueryFlightPhase.Emitted)), Vector.empty)
      case _                              => (flight, Vector.empty)

  /** Snapshot before ingress publication commits removal of the flight. */
  def complete[A](
      flight: Option[QueryFlight[A]],
      result: TerminalQueryResult[A]
  ): Vector[QueryCompletion[A]] =
    flight.toVector.flatMap(_.subscribers).map(QueryCompletion(_, result))

  def stopEmitted(): Vector[QueryCompletion[?]] =
    val background = stopFlight(backgroundColorFlight)
    backgroundColorFlight = background._1
    val scheme     = stopFlight(colorSchemeFlight)
    colorSchemeFlight = scheme._1
    background._2 ++ scheme._2

  private def stopFlight[A](flight: Option[QueryFlight[A]])
      : (Option[QueryFlight[A]], Vector[QueryCompletion[A]]) =
    flight match
      case Some(value) if value.phase === QueryFlightPhase.Emitted =>
        (None, complete(flight, TerminalQueryResult.Stopped))
      case _                                                       => (flight, Vector.empty)

  def cancel(subscriber: QuerySubscriber[?]): Unit =
    if subscriber.state === QuerySubscriberState.Active then
      subscriber.state = QuerySubscriberState.Cancelled
      backgroundColorFlight = remove(backgroundColorFlight, subscriber)
      colorSchemeFlight = remove(colorSchemeFlight, subscriber)
      cursorPositionFlight = remove(cursorPositionFlight, subscriber)

  private def remove[A](
      flight: Option[QueryFlight[A]],
      subscriber: QuerySubscriber[?]
  ): Option[QueryFlight[A]] =
    flight.map(value => value.copy(subscribers = value.subscribers.filterNot(_ eq subscriber)))

  def claim(completion: QueryCompletion[?]): Boolean =
    if completion.subscriber.state === QuerySubscriberState.Active then
      completion.subscriber.state = QuerySubscriberState.Claimed
      true
    else false

  def completed(completion: QueryCompletion[?]): Unit =
    completion.subscriber.state = QuerySubscriberState.Completed

private[core] object QueryCoordinator:
  enum QueryFlightPhase derives CanEqual:
    case Reserved, Emitted

  final case class QueryFlight[A](
      id: Long,
      phase: QueryFlightPhase,
      subscribers: Vector[QuerySubscriber[A]]
  )

  enum QuerySubscriberState derives CanEqual:
    case Active, Claimed, Cancelled, Completed

  final case class QuerySubscriber[A](id: Long, callback: TerminalQueryResult[A] => Unit):
    var state: QuerySubscriberState = QuerySubscriberState.Active

  final case class QueryCompletion[A](
      subscriber: QuerySubscriber[A],
      result: TerminalQueryResult[A]
  ):
    def invoke(): Unit = subscriber.callback(result)
