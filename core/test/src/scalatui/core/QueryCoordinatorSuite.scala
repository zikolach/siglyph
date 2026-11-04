package scalatui.core

import QueryCoordinator.*
import scalatui.terminal.{RgbColor, TerminalColorScheme, TerminalCursorProtocol}

class QueryCoordinatorSuite extends munit.FunSuite:
  test("subscriptions share reserved and emitted flights in registration order"):
    val coordinator             = QueryCoordinator()
    val first                   = coordinator.subscriber[Int](_ => ())
    val second                  = coordinator.subscriber[Int](_ => ())
    val (reserved, reservation) = coordinator.subscribe(None, first)
    val (shared, duplicate)     = coordinator.subscribe(Some(reserved), second)
    assertEquals(reservation, Some(reserved.id))
    assertEquals(duplicate, None)
    assertEquals(shared.subscribers, Vector(first, second))
    assertEquals(shared.phase, QueryFlightPhase.Reserved)
    assertEquals(coordinator.writeReservations, 1)
    val (emitted, completions)  = coordinator.emitted(Some(shared), reserved.id, stopping = false)
    assertEquals(emitted.map(_.phase), Some(QueryFlightPhase.Emitted))
    assertEquals(completions, Vector.empty)
    val (_, laterReservation)   = coordinator.subscribe(emitted, coordinator.subscriber[Int](_ => ()))
    assertEquals(laterReservation, None)
    assertEquals(coordinator.writeReservations, 0)

  test("cancellation preserves an empty flight and prevents a second request"):
    val coordinator      = QueryCoordinator()
    val subscriber       = coordinator.subscriber[RgbColor](_ => fail("cancelled callback"))
    val (flight, _)      = coordinator.subscribe(None, subscriber)
    coordinator.backgroundColorFlight = Some(flight)
    coordinator.cancel(subscriber)
    coordinator.cancel(subscriber)
    assertEquals(coordinator.backgroundColorFlight.map(_.subscribers), Some(Vector.empty))
    val (_, reservation) = coordinator.subscribe(
      coordinator.backgroundColorFlight,
      coordinator.subscriber[RgbColor](_ => ())
    )
    assertEquals(reservation, None)
    val completion       = QueryCompletion(subscriber, TerminalQueryResult.Stopped)
    assert(!coordinator.claim(completion))

  test("reply snapshot survives slot clearing and can still be cancelled before claim"):
    val coordinator = QueryCoordinator()
    val subscriber  = coordinator.subscriber[RgbColor](_ => ())
    val (flight, _) = coordinator.subscribe(None, subscriber)
    coordinator.backgroundColorFlight = Some(flight)
    val completions =
      coordinator.complete(coordinator.backgroundColorFlight, TerminalQueryResult.InvalidResponse)
    assertEquals(coordinator.backgroundColorFlight, Some(flight))
    coordinator.backgroundColorFlight = None
    assertEquals(completions.map(_.subscriber), Vector(subscriber))
    coordinator.cancel(subscriber)
    assert(!coordinator.claim(completions.head))

  test("stop retains reserved flights and does not complete emitted cursor flights"):
    val coordinator           = QueryCoordinator()
    val (background, _)       = coordinator.subscribe(None, coordinator.subscriber[RgbColor](_ => ()))
    val (scheme, _)           =
      coordinator.subscribe(None, coordinator.subscriber[TerminalColorScheme](_ => ()))
    val (cursor, _)           = coordinator.subscribe(
      None,
      coordinator.subscriber[TerminalCursorProtocol.CursorPosition](_ => ())
    )
    coordinator.backgroundColorFlight = Some(background)
    coordinator.colorSchemeFlight =
      coordinator.emitted(Some(scheme), scheme.id, stopping = false)._1
    coordinator.cursorPositionFlight =
      coordinator.emitted(Some(cursor), cursor.id, stopping = false)._1
    val completions           = coordinator.stopEmitted()
    assertEquals(completions.map(_.subscriber.id), scheme.subscribers.map(_.id))
    assertEquals(coordinator.backgroundColorFlight, Some(background))
    assertEquals(coordinator.colorSchemeFlight, None)
    assert(coordinator.cursorPositionFlight.nonEmpty)
    val (stopped, afterWrite) =
      coordinator.emitted(coordinator.backgroundColorFlight, background.id, stopping = true)
    assertEquals(stopped, None)
    assertEquals(afterWrite.map(_.result), Vector(TerminalQueryResult.Stopped))
    assertEquals(coordinator.writeReservations, 0)

  test("failed emission releases its reservation and preserves failure identity"):
    val coordinator            = QueryCoordinator()
    val (flight, _)            = coordinator.subscribe(None, coordinator.subscriber[Int](_ => ()))
    val error                  = RuntimeException("write failed")
    val (cleared, completions) = coordinator.failed(Some(flight), flight.id, error)
    assertEquals(cleared, None)
    assertEquals(completions.map(_.result), Vector(TerminalQueryResult.Failed(error)))
    assertEquals(coordinator.writeReservations, 0)

  test("reply committed during write is not recreated when emission finishes"):
    val coordinator              = QueryCoordinator()
    val (flight, _)              = coordinator.subscribe(None, coordinator.subscriber[Int](_ => ()))
    val (remaining, completions) = coordinator.emitted[Int](None, flight.id, stopping = false)
    assertEquals(remaining, None)
    assertEquals(completions, Vector.empty)
    assertEquals(coordinator.writeReservations, 0)

  test("claimed callbacks cannot be cancelled or claimed twice and may subscribe reentrantly"):
    val coordinator = QueryCoordinator()
    var called      = 0
    val subscriber  = coordinator.subscriber[Int] { _ =>
      called += 1
      coordinator.subscribe(None, coordinator.subscriber[Int](_ => ()))
      ()
    }
    val completion  = QueryCompletion(subscriber, TerminalQueryResult.Success(1))
    assert(coordinator.claim(completion))
    coordinator.cancel(subscriber)
    assertEquals(subscriber.state, QuerySubscriberState.Claimed)
    completion.invoke()
    coordinator.completed(completion)
    assertEquals(called, 1)
    assertEquals(subscriber.state, QuerySubscriberState.Completed)
    assert(!coordinator.claim(completion))
