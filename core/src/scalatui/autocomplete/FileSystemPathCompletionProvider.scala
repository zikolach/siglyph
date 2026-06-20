package scalatui.autocomplete

import java.io.{File, IOException}
import java.nio.file.{DirectoryIteratorException, Files, Path}
import java.util.Locale

import scala.collection.mutable.PriorityQueue

import scalatui.syntax.Equality.*

/** Configuration for the dependency-free filesystem path completion helper. */
final case class FileSystemPathCompletionOptions(
    baseDirectory: File = File("."),
    maxResults: Int = 100,
    maxScannedEntries: Int = 4096,
    includeHidden: Boolean = false,
    includeGitEntries: Boolean = false,
    directoriesFirst: Boolean = true,
    allowAbsolutePaths: Boolean = false,
    allowHomeExpansion: Boolean = false
) derives CanEqual

/**
 * Dependency-free [[PathCompletionProvider]] that enumerates local filesystem entries.
 *
 * The provider completes synchronously by default, returns deterministic results, bounds retained
 * result count through [[FileSystemPathCompletionOptions.maxResults]], limits scanned directory
 * entries through [[FileSystemPathCompletionOptions.maxScannedEntries]], and reports
 * missing/unreadable/out-of-root directories as an empty result rather than failing the
 * autocomplete request.
 */
final class FileSystemPathCompletionProvider(
    options: FileSystemPathCompletionOptions = FileSystemPathCompletionOptions(),
    dispatch: Runnable => Unit = _.run()
) extends PathCompletionProvider:
  private val canonicalBase: Option[Path] =
    try Some(options.baseDirectory.getCanonicalFile.toPath.normalize())
    catch case _: (IOException | SecurityException) => None

  override def requestPathSuggestions(
      request: PathCompletionRequest,
      callback: PathCompletionProvider.Callback
  ): AutocompleteRequestHandle =
    val handle = CancellableHandle()
    try
      dispatch { () =>
        try
          if !handle.cancelled then
            val result = suggestions(request.prefix.rawPrefix, handle)
            if !handle.cancelled then callback.complete(result)
        catch case e: Throwable => if !handle.cancelled then callback.fail(e)
      }
    catch case e: Throwable => if !handle.cancelled then callback.fail(e)
    handle

  private final class CancellableHandle extends AutocompleteRequestHandle:
    @volatile private var cancelledValue = false
    override def cancel(): Unit          = cancelledValue = true
    def cancelled: Boolean               = cancelledValue

  private def suggestions(rawPrefix: String, handle: CancellableHandle): Vector[PathCompletion] =
    if handle.cancelled then Vector.empty
    else
      val context = CompletionContext(rawPrefix)
      resolveDirectory(context.directoryPrefix) match
        case Some(parent) if isListable(parent) && !isInsideGitDirectory(parent) =>
          topEntries(parent.toPath, context.entryPrefix, handle)
            .map(entry => completion(entry.toFile, context.outputPrefix))
        case _                                                                   => Vector.empty

  private def resolveDirectory(directoryPrefix: String): Option[File] =
    val raw = if directoryPrefix.isEmpty then "." else directoryPrefix
    if isDisallowedHome(raw) then None
    else
      val expanded = expandHome(raw)
      val file     = File(expanded)
      if file.isAbsolute && !options.allowAbsolutePaths then None
      else
        val resolved = if file.isAbsolute then file else File(options.baseDirectory, expanded)
        canonicalInBase(resolved)

  private def canonicalInBase(file: File): Option[File] =
    try
      val candidate = file.getCanonicalFile.toPath.normalize()
      canonicalBase.filter(candidate.startsWith).map(_ => candidate.toFile)
    catch case _: (IOException | SecurityException) => None

  private def isListable(file: File): Boolean =
    try file.isDirectory && file.canRead
    catch case _: (IOException | SecurityException) => false

  private def topEntries(
      directory: Path,
      entryPrefix: String,
      handle: CancellableHandle
  ): Vector[Path] =
    val retained = PriorityQueue.empty[Path](using Ordering.by(path => sortKey(path.toFile)))
    var stream   = Option.empty[java.nio.file.DirectoryStream[Path]]
    try
      val opened   = Files.newDirectoryStream(directory)
      stream = Some(opened)
      val iterator = opened.iterator()
      var scanned  = 0
      while !handle.cancelled && scanned < math.max(
          0,
          options.maxScannedEntries
        ) && iterator.hasNext
      do
        val entry = iterator.next()
        scanned += 1
        if entry.getFileName.toString.startsWith(entryPrefix) && isCanonicalInBase(entry) &&
          allowedEntry(entry.toFile)
        then
          retainTopEntry(retained, entry)
      retained.toVector.sortBy(path => sortKey(path.toFile))
    catch case _: (DirectoryIteratorException | IOException | SecurityException) => Vector.empty
    finally
      stream.foreach { opened =>
        try opened.close()
        catch case _: IOException => ()
      }

  private def retainTopEntry(retained: PriorityQueue[Path], entry: Path): Unit =
    val maxResults = math.max(0, options.maxResults)
    if maxResults > 0 && retained.size < maxResults then retained.enqueue(entry)
    else if maxResults > 0 && Ordering[(Int, String, String)].compare(
        sortKey(entry.toFile),
        sortKey(retained.head.toFile)
      ) < 0
    then
      retained.dequeue()
      retained.enqueue(entry)

  private def allowedEntry(entry: File): Boolean =
    val name = entry.getName
    !hasUnsafeName(name) &&
    (if name === ".git" then options.includeGitEntries
     else options.includeHidden || !isHidden(entry))

  private def hasUnsafeName(name: String): Boolean =
    name.exists(ch => ch.isControl || ch === 0x1b.toChar)

  private def isCanonicalInBase(entry: Path): Boolean =
    try
      canonicalBase.exists(base =>
        entry.toFile.getCanonicalFile.toPath.normalize().startsWith(base)
      )
    catch case _: (IOException | SecurityException) => false

  private def isHidden(entry: File): Boolean =
    try entry.getName.startsWith(".") || entry.isHidden
    catch case _: SecurityException => true

  private def isInsideGitDirectory(directory: File): Boolean =
    !options.includeGitEntries && hasAncestorNamed(directory, ".git")

  private def hasAncestorNamed(file: File, name: String): Boolean =
    var current = Option(file)
    var found   = false
    while !found && current.nonEmpty do
      val value = current.get
      if value.getName === name then found = true
      current = Option(value.getParentFile)
    found

  private def sortKey(entry: File): (Int, String, String) =
    val kind = if options.directoriesFirst && entry.isDirectory then 0 else 1
    (kind, entry.getName.toLowerCase(Locale.ROOT), entry.getName)

  private def completion(entry: File, outputPrefix: String): PathCompletion =
    val directory = entry.isDirectory
    val suffix    = if directory then "/" else ""
    val name      = entry.getName + suffix
    PathCompletion(
      path = outputPrefix + name,
      label = name,
      isDirectory = directory
    )

  private def isDisallowedHome(path: String): Boolean =
    !options.allowHomeExpansion && (path === "~" || path.startsWith("~/"))

  private def expandHome(path: String): String =
    if path === "~" then System.getProperty("user.home")
    else if path.startsWith("~/") then System.getProperty("user.home") + path.drop(1)
    else path

  private final case class CompletionContext(
      directoryPrefix: String,
      entryPrefix: String,
      outputPrefix: String
  )

  private object CompletionContext:
    def apply(rawPrefix: String): CompletionContext =
      val slashIndex = rawPrefix.lastIndexOf('/')
      if rawPrefix.endsWith("/") then CompletionContext(rawPrefix, "", rawPrefix)
      else if slashIndex >= 0 then
        val directoryPrefix = rawPrefix.take(slashIndex + 1)
        CompletionContext(directoryPrefix, rawPrefix.drop(slashIndex + 1), directoryPrefix)
      else CompletionContext("", rawPrefix, "")
