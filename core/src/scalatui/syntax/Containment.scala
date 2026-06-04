package scalatui.syntax

object Containment:
  /**
   * Type-constrained collection containment syntax.
   *
   * Standard collection `contains` can widen the searched value type for some collection shapes,
   * allowing accidental comparisons such as `List(1).contains("1")`. This helper requires the
   * candidate to conform to the collection element type while still delegating to the collection's
   * own `contains` implementation.
   */
  extension [A](values: Seq[A])
    infix def contains_(value: A): Boolean = values.contains(value)

  extension [A](values: Set[A])
    infix def contains_(value: A): Boolean = values.contains(value)
