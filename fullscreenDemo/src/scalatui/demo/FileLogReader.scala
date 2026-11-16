package scalatui.demo

import java.io.{ByteArrayOutputStream, IOException, RandomAccessFile}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.BasicFileAttributes
import scalatui.syntax.Equality.*

private[demo] final case class FileLogLine(text: String, truncated: Boolean)
private[demo] final case class FileLogBatch(
    lines: Vector[FileLogLine],
    notice: Option[String],
    offset: Long
)

/**
 * Polling regular-file reader. No descriptor survives a poll; incomplete/oversized lines are
 * bounded.
 */
private[demo] final class FileLogReader(
    path: Path,
    maxReadBytes: Int = 65536,
    maxLineBytes: Int = 4096,
    maxLines: Int = 256
):
  require(maxReadBytes > 0 && maxReadBytes <= 1048576)
  require(maxLineBytes > 0 && maxLineBytes <= 65536)
  require(maxLines > 0 && maxLines <= 1000)
  private var position    = 0L
  private var identity    = Option.empty[String]
  private var prefix      = Vector.empty[Byte]
  private var initialized = false
  private var partial     = Array.emptyByteArray
  private var dropping    = false

  private def attributes(): BasicFileAttributes =
    Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)

  def poll(): Either[String, FileLogBatch] = synchronized {
    try
      val before = attributes()
      if !before.isRegularFile then Left("source is not a regular file (symlinks are not followed)")
      else
        val key  = Option(before.fileKey()).map(_.toString)
        val file = new RandomAccessFile(path.toFile, "r")
        try
          val prefixBytes    = new Array[Byte](math.min(64L, before.size()).toInt)
          val prefixRead     = file.read(prefixBytes)
          val observedPrefix = prefixBytes.take(math.max(0, prefixRead)).toVector
          val rotated        = initialized && (key !== identity)
          val rewritten      = initialized &&
            (before.size() < position ||
              !observedPrefix.startsWith(prefix.take(math.min(prefix.size, observedPrefix.size))))
          val reset          = rotated || rewritten
          var nextPosition   = if reset then 0L else position
          val buffer         = new ByteArrayOutputStream(maxLineBytes)
          if !reset then buffer.write(partial)
          var discard        = if reset then false else dropping
          file.seek(nextPosition)
          val bytes          = new Array[Byte](maxReadBytes)
          val count          = math.max(0, file.read(bytes))
          val lines          = Vector.newBuilder[FileLogLine]
          var emitted        = 0
          var consumed       = 0
          while consumed < count && emitted < maxLines do
            val value = bytes(consumed)
            if value === '\n'.toByte then
              val raw    = buffer.toByteArray
              val length =
                if raw.lastOption.exists(_ === '\r'.toByte) then raw.length - 1 else raw.length
              val text   = new String(raw, 0, length, StandardCharsets.UTF_8)
              lines += FileLogLine(text, discard)
              emitted += 1
              buffer.reset()
              discard = false
            else if buffer.size() < maxLineBytes then buffer.write(value.toInt & 0xff)
            else discard = true
            consumed += 1
          nextPosition += consumed.toLong
          val after          = attributes()
          val afterKey       = Option(after.fileKey()).map(_.toString)
          if !after.isRegularFile || (afterKey !== key) || after.size() < nextPosition then
            Left("source changed during read; retrying without committing this batch")
          else
            position = nextPosition
            identity = key
            prefix = observedPrefix
            initialized = true
            partial = buffer.toByteArray
            dropping = discard
            val notice = if rotated then
              Some("file replaced; unread old tail and partial line discarded")
            else if rewritten then Some("file truncated/rewritten; partial line discarded")
            else None
            Right(FileLogBatch(lines.result(), notice, position))
        finally file.close()
    catch
      case error: IOException =>
        Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName).take(256))
  }
