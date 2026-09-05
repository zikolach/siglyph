package scalatui.components

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer

/** Immutable state for fullscreen search in the primary [[ScrollView]]. */
final case class ViewportSearchState(
    active: Boolean,
    query: String,
    matchCount: Int,
    currentMatch: Option[Int]
) derives CanEqual

private[components] final case class TranscriptSearchMatch(
    row: Int,
    column: Int,
    width: Int,
    text: String
) derives CanEqual

private[components] final class TranscriptSearchIndex(maxMatches: Int):
  require(maxMatches > 0, "Search match bound must be positive")

  private var indexedRevision = Long.MinValue
  private var indexedWidth    = -1
  private var rows            = Vector.empty[TranscriptSearchIndex.Row]
  private var cachedQuery     = ""
  private var cachedMatches   = Vector.empty[TranscriptSearchMatch]
  private var mutationToken   = 0L

  def requiresDocument(revision: Long, width: Int): Boolean = synchronized {
    (indexedRevision !== revision) || (indexedWidth !== width)
  }

  def matches(
      revision: Long,
      width: Int,
      query: String,
      rendered: Option[Iterator[String]],
      recordScans: Int => Unit
  ): Vector[TranscriptSearchMatch] =
    val buildToken = synchronized {
      Option.when((indexedRevision !== revision) || (indexedWidth !== width))(mutationToken)
    }
    val build      = buildToken.map { token =>
      val indexed = TranscriptSearchIndex.indexRows(rendered.getOrElse(Iterator.empty))
      recordScans(indexed.scannedRows)
      token -> indexed
    }
    val snapshot   = synchronized {
      build match
        case Some((token, indexed)) if token === mutationToken =>
          rows = indexed.rows
          indexedRevision = revision
          indexedWidth = width
          cachedQuery = ""
          cachedMatches = Vector.empty
          mutationToken += 1L
        case Some(_)                                           => ()
        case None                                              => ()
      Option.when(indexedRevision === revision && indexedWidth === width)(
        (rows, mutationToken, cachedQuery, cachedMatches)
      )
    }
    snapshot.fold(Vector.empty) { case (indexedRows, token, previousQuery, previousMatches) =>
      if previousQuery === query then previousMatches
      else
        val found = TranscriptSearchIndex.findMatches(indexedRows, query, maxMatches)
        synchronized {
          if mutationToken === token && indexedRevision === revision && indexedWidth === width then
            cachedQuery = query
            cachedMatches = found
            found
          else Vector.empty
        }
    }

  def clear(): Unit = synchronized {
    indexedRevision = Long.MinValue
    indexedWidth = -1
    rows = Vector.empty
    cachedQuery = ""
    cachedMatches = Vector.empty
    mutationToken += 1L
  }

private object TranscriptSearchIndex:
  final val MaxIndexedRows: Int      = 65536
  final val MaxIndexedGraphemes: Int = 262144
  final val MaxIndexedUtf8Bytes: Int = 1048576

  private final case class Row(
      normalized: String,
      owner: Array[Int],
      graphemes: Vector[String],
      columns: Vector[Int]
  )

  private final case class IndexedRows(rows: Vector[Row], scannedRows: Int)

  private def indexRows(rendered: Iterator[String]): IndexedRows =
    val rows              = Vector.newBuilder[Row]
    var retainedGraphemes = 0
    var retainedBytes     = 0
    var scannedRows       = 0
    var full              = false
    while rendered.hasNext && scannedRows < MaxIndexedRows && !full do
      val graphemes    = Vector.newBuilder[String]
      val normalized   = StringBuilder()
      val owner        = ArrayBuffer.empty[Int]
      val columns      = Vector.newBuilder[Int]
      var rowGraphemes = 0
      var column       = 0
      val completed    = Ansi.foreachSelectionGraphemeWhile(
        rendered.next(),
        MaxIndexedGraphemes - retainedGraphemes,
        MaxIndexedUtf8Bytes - retainedBytes
      ) { grapheme =>
        val remainingBytes = MaxIndexedUtf8Bytes - retainedBytes
        Unicode.normalizedSearchTextBounded(grapheme, remainingBytes).exists { value =>
          val bytes = value.getBytes(StandardCharsets.UTF_8).length
          if retainedGraphemes >= MaxIndexedGraphemes || bytes > remainingBytes then
            full = true
            false
          else
            columns += column
            graphemes += grapheme
            normalized.append(value)
            var offset = 0
            while offset < value.length do
              owner += rowGraphemes
              offset += 1
            rowGraphemes += 1
            retainedGraphemes += 1
            retainedBytes += bytes
            column += Unicode.graphemeWidth(grapheme)
            true
        }
      }
      if !completed then full = true
      rows += Row(normalized.result(), owner.toArray, graphemes.result(), columns.result())
      scannedRows += 1
    IndexedRows(rows.result(), scannedRows)

  private def findMatches(
      rows: Vector[Row],
      query: String,
      maxMatches: Int
  ): Vector[TranscriptSearchMatch] =
    val needle = Unicode.normalizedSearchTextBounded(query, MaxIndexedUtf8Bytes).getOrElse("")
    if needle.isEmpty then Vector.empty
    else
      val found    = Vector.newBuilder[TranscriptSearchMatch]
      var count    = 0
      var rowIndex = 0
      while rowIndex < rows.length && count < maxMatches do
        val row  = rows(rowIndex)
        var from = 0
        while from <= row.normalized.length - needle.length && count < maxMatches do
          val start = row.normalized.indexOf(needle, from)
          if start < 0 then from = row.normalized.length + 1
          else
            val firstGrapheme = row.owner(start)
            val lastGrapheme  = row.owner(start + needle.length - 1)
            val matched       = row.graphemes.slice(firstGrapheme, lastGrapheme + 1)
            found += TranscriptSearchMatch(
              rowIndex,
              row.columns(firstGrapheme),
              matched.map(Unicode.graphemeWidth).sum,
              matched.mkString
            )
            count += 1
            from = start + 1
        rowIndex += 1
      found.result()
