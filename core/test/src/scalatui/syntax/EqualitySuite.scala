package scalatui.syntax

import scalatui.syntax.Equality.*

class EqualitySuite extends munit.FunSuite:
  test("typed equality compares same static type"):
    assert(1 === 1)
    assert(1 !== 2)
    assert("a" === "a")
    assert("a" !== "b")

  test("typed equality works with explicit common ADT supertype"):
    enum Example derives CanEqual:
      case One, Two
    val one: Example = Example.One
    assert(one === Example.One)
    assert(one !== Example.Two)
