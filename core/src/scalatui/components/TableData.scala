package scalatui.components

import scalatui.syntax.Equality.*

/**
 * Immutable indexed rows for an experimental [[DataTable]]. Row values and IDs must stay immutable.
 */
final class TableData[A] private (
    val rows: Vector[A],
    val ids: Vector[String],
    private val indices: Map[String, Int]
):
  def size: Int                        = rows.size
  def indexOf(id: String): Option[Int] = indices.get(id)

object TableData:
  /**
   * Capture stable IDs and validate uniqueness in O(n), before publishing data to a component. The
   * key function runs once per row here, never during viewport rendering or navigation.
   */
  def apply[A](rows: Vector[A], id: A => String): TableData[A] =
    require(rows.size < Int.MaxValue, "table must leave room for its header row")
    val ids     = rows.map(id)
    require(ids.forall(value => Option(value).nonEmpty), "table IDs must not be null")
    val indices = ids.iterator.zipWithIndex.toMap
    require(indices.size === ids.size, "table IDs must be unique")
    new TableData(rows, ids, indices)

/** One fixed-preference column. The table clips widths from left to right to fit the viewport. */
final case class TableColumn[A](title: String, preferredWidth: Int, cell: A => String):
  require(preferredWidth > 0, "column preferred width must be positive")

/** Immutable selection notification, including the current row value after data replacement. */
final case class TableSelection[A](id: String, index: Int, row: A)
