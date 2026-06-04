package scalatui.syntax

import java.util.Objects

object Equality:
  /**
   * Type-constrained equality syntax inspired by Cats' `Eq` syntax, but using normal Scala equality
   * semantics and no type class.
   *
   * Unlike universal `==`, both sides must conform to the same inferred static type, which catches
   * many accidental comparisons between unrelated types while remaining dependency-free.
   */
  extension [A](left: A)
    infix def ===(right: A): Boolean = Objects.equals(left, right)
    infix def !==(right: A): Boolean = !Objects.equals(left, right)
