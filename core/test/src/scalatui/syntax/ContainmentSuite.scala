package scalatui.syntax

import scalatui.syntax.Containment.*

class ContainmentSuite extends munit.FunSuite:
  test("typed containment checks collection elements"):
    assert(Vector(1, 2, 3).contains_(2))
    assert(!Vector(1, 2, 3).contains_(4))
    assert(Vector("a", "b").contains_("a"))
