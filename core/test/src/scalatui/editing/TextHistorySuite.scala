package scalatui.editing

class TextHistorySuite extends munit.FunSuite:
  test("undo stack preserves undo-only LIFO snapshots and limit"):
    val stack = UndoStack[String](maxEntries = 2)
    stack.push("one")
    stack.push("two")
    stack.push("three")

    assertEquals(stack.length, 2)
    assertEquals(stack.pop(), Some("three"))
    assertEquals(stack.pop(), Some("two"))
    assertEquals(stack.pop(), None)

  test("kill ring accumulates kills and rotates yank-pop candidates"):
    val ring = KillRing()
    ring.push("world", prepend = false)
    ring.push("hello ", prepend = true, accumulate = true)
    ring.push("!", prepend = false)

    assertEquals(ring.peek, Some("!"))
    ring.rotate()
    assertEquals(ring.peek, Some("hello world"))
    ring.rotate()
    assertEquals(ring.peek, Some("!"))
