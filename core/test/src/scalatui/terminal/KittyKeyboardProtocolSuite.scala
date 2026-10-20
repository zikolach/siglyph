package scalatui.terminal

class KittyKeyboardProtocolSuite extends munit.FunSuite:
  test("zero flags and the exact deadline are valid; duplicates cannot push again"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    negotiator.begin(100L, 20L)
    assert(negotiator.receiveResponse("\u001b[?0u", 120L))
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Active(0))
    assert(!negotiator.receiveResponse("\u001b[?3u", 120L))

  test("only a complete exact decimal integer response is valid"):
    Vector(
      "x\u001b[?0u",
      "\u001b[?0ux",
      "\u001b[?u",
      "\u001b[?-1u",
      "\u001b[?1;2u",
      "\u001b[?2147483648u",
      "\u001b[?0",
      "\u001b[?0u\n"
    ).foreach { raw =>
      val negotiator = KittyKeyboardProtocolNegotiator()
      val pending    = negotiator.begin(100L, 20L)
      assert(!negotiator.receiveResponse(raw, 110L), raw)
      assertEquals(negotiator.state, pending)
    }

  test("late and cancelled responses cannot activate negotiation"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    negotiator.begin(100L, 20L)
    assert(!negotiator.receiveResponse("\u001b[?0u", 121L))
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)
    negotiator.begin(200L, 20L)
    negotiator.disable()
    assert(!negotiator.receiveResponse("\u001b[?0u", 210L))

  test("expiry preserves a timely admitted reply without changing public inactive state"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    negotiator.begin(100L, 20L)
    negotiator.expire(121L)
    negotiator.expire(200L)
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)
    assert(!negotiator.receiveResponse("\u001b[?0u", 121L))
    assert(!negotiator.receiveResponse("\u001b[?;u", 120L))
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)
    assert(negotiator.receiveResponse("\u001b[?0u", 120L))
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Active(0))
    assert(!negotiator.receiveResponse("\u001b[?0u", 120L))

  test("explicit cancellation discards an expired request and its timely admitted reply"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    negotiator.begin(100L, 20L)
    negotiator.expire(121L)
    negotiator.disable()
    assert(!negotiator.receiveResponse("\u001b[?0u", 120L))
    assertEquals(negotiator.state, KittyKeyboardProtocolState.Inactive)

  test("a new request replaces the single retained expired request"):
    val negotiator = KittyKeyboardProtocolNegotiator()
    negotiator.begin(100L, 20L)
    negotiator.expire(121L)
    negotiator.begin(0L, 10L)
    negotiator.expire(11L)
    assert(!negotiator.receiveResponse("\u001b[?0u", 120L))
    assert(negotiator.receiveResponse("\u001b[?0u", 10L))
