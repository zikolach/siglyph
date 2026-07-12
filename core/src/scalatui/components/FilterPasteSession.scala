package scalatui.components

import scalatui.syntax.Equality.*
import scalatui.terminal.{TerminalInputChunk, TerminalUtf8Decoder}

private[components] final class FilterPasteSession(initialQuery: String):
  private val decoder               = TerminalUtf8Decoder()
  private val appendedBuilder       = StringBuilder()
  private var acceptedChars         = 0L
  private var appendCalls           = 0L
  private var cachedQuery           = initialQuery
  private var queryDirty            = false
  private var queryMaterializations = 0L

  def query: String =
    if queryDirty then
      val combined = StringBuilder(initialQuery.length + appendedBuilder.length)
      combined.append(initialQuery)
      combined.append(appendedBuilder)
      cachedQuery = combined.result()
      queryDirty = false
      queryMaterializations += 1
    cachedQuery

  def changed: Boolean = acceptedChars !== 0L

  private[components] def initialQueryReference: String        = initialQuery
  private[components] def retainedCachedQueryReference: String = cachedQuery
  private[components] def acceptedCharacterCount: Long         = acceptedChars
  private[components] def appendCount: Long                    = appendCalls
  private[components] def queryMaterializationCount: Long      = queryMaterializations

  def append(chunk: TerminalInputChunk): Unit = appendDecoded(decoder.process(chunk))

  def finish(): Unit = appendDecoded(decoder.flush())

  private def appendDecoded(decoded: String): Unit =
    val normalized = decoded.replace('\n', ' ').replace('\r', ' ')
    if normalized.nonEmpty then
      appendedBuilder.append(normalized)
      acceptedChars += normalized.length
      appendCalls += 1
      queryDirty = true
      cachedQuery = initialQuery
