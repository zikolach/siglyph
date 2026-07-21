package scalatui.autocomplete

import java.io.{File, IOException}
import java.nio.file.{DirectoryIteratorException, Files, Path}
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable.{ArrayBuffer, ArrayDeque, HashSet}

import scalatui.syntax.Equality.*
import scalatui.unicode.TextCase

/** Configuration for the dependency-free filesystem path completion helper. */
final case class FileSystemPathCompletionOptions(
    baseDirectory: File = File("."),
    maxResults: Int = 100,
    maxScannedEntries: Int = 4096,
    includeHidden: Boolean = false,
    includeGitEntries: Boolean = false,
    directoriesFirst: Boolean = true,
    allowAbsolutePaths: Boolean = false,
    allowHomeExpansion: Boolean = false,
    currentDirectory: Option[File] = None,
    containmentRoots: Vector[File] = Vector.empty,
    allowParentTraversal: Boolean = false
) derives CanEqual

/** Bounds for opt-in recursive attachment completion. */
final case class RecursiveAttachmentCompletionOptions(
    paths: FileSystemPathCompletionOptions = FileSystemPathCompletionOptions(),
    maxDepth: Int = 8,
    maxVisitedEntries: Int = 4096,
    maxResults: Int = 100
) derives CanEqual

/**
 * Dependency-free [[PathCompletionProvider]] that enumerates one local filesystem directory.
 *
 * `baseDirectory` remains the source-compatible legacy setting. Unless the newer fields are set, it
 * is both the completion current directory and the sole canonical containment root. Parent, home,
 * and absolute syntax require their corresponding explicit policy and are still rejected when their
 * canonical targets escape every configured root. Symlink targets are checked before disclosure.
 *
 * Directory enumeration stops at `maxScannedEntries`. Results are stable-sorted within that
 * evaluated candidate set and then limited by `maxResults`; which entries a filesystem presents
 * before the scan bound is implementation-defined.
 */
final class FileSystemPathCompletionProvider(
    options: FileSystemPathCompletionOptions = FileSystemPathCompletionOptions(),
    dispatch: Runnable => Unit = _.run()
) extends PathCompletionProvider:
  private val policy = FileSystemCompletionPolicy(options)

  override def requestPathSuggestions(
      request: PathCompletionRequest,
      callback: PathCompletionProvider.Callback
  ): AutocompleteRequestHandle =
    FileSystemCompletionDispatch.request(dispatch, callback) { handle =>
      suggestions(request.prefix.rawPrefix, handle)
    }

  private def suggestions(
      rawPrefix: String,
      handle: FileSystemCompletionHandle
  ): Vector[PathCompletion] =
    if handle.cancelled then Vector.empty
    else
      val context = FileSystemCompletionContext(rawPrefix)
      policy.resolveDirectory(context.directoryPrefix) match
        case Some(parent) if policy.isListable(parent) && !policy.isInsideGitDirectory(parent) =>
          topEntries(parent, context.entryPrefix, handle)
            .map(entry => policy.completion(entry, context.outputPrefix))
        case _                                                                                 => Vector.empty

  private def topEntries(
      directory: Path,
      entryPrefix: String,
      handle: FileSystemCompletionHandle
  ): Vector[Path] =
    val evaluated = ArrayBuffer.empty[Path]
    var stream    = Option.empty[java.nio.file.DirectoryStream[Path]]
    try
      val opened   = Files.newDirectoryStream(directory)
      stream = Some(opened)
      val iterator = opened.iterator()
      var scanned  = 0
      val scanMax  = math.max(0, options.maxScannedEntries)
      while !handle.cancelled && scanned < scanMax && iterator.hasNext do
        val entry = iterator.next()
        scanned += 1
        if !handle.cancelled && entry.getFileName.toString.startsWith(entryPrefix) &&
          policy.allowedCanonicalEntry(entry)
        then evaluated += entry
      evaluated.toVector.sortBy(policy.sortKey).take(math.max(0, options.maxResults))
    catch case _: (DirectoryIteratorException | IOException | SecurityException) => Vector.empty
    finally FileSystemCompletionDispatch.close(stream)

/**
 * Opt-in iterative recursive provider for attachment prefixes.
 *
 * It searches only attachment requests, uses no shell tools, and bounds depth, visited directory
 * entries, retained results, and cancellation latency. Contained symlink directories may be
 * traversed once by canonical identity; escaping symlinks are neither suggested nor descended.
 */
final class RecursiveAttachmentCompletionProvider(
    options: RecursiveAttachmentCompletionOptions = RecursiveAttachmentCompletionOptions(),
    dispatch: Runnable => Unit = _.run()
) extends PathCompletionProvider:
  private val policy = FileSystemCompletionPolicy(options.paths)

  override def requestPathSuggestions(
      request: PathCompletionRequest,
      callback: PathCompletionProvider.Callback
  ): AutocompleteRequestHandle =
    FileSystemCompletionDispatch.request(dispatch, callback) { handle =>
      if request.prefix.isAttachment then suggestions(request.prefix.rawPrefix, handle)
      else Vector.empty
    }

  private def suggestions(
      rawPrefix: String,
      handle: FileSystemCompletionHandle
  ): Vector[PathCompletion] =
    val context = FileSystemCompletionContext(rawPrefix)
    policy.resolveDirectory(context.directoryPrefix) match
      case Some(start) if policy.isListable(start) && !policy.isInsideGitDirectory(start) =>
        traverse(start, context, handle)
      case _                                                                              => Vector.empty

  private def traverse(
      start: Path,
      context: FileSystemCompletionContext,
      handle: FileSystemCompletionHandle
  ): Vector[PathCompletion] =
    val pending            = ArrayDeque(PendingDirectory(start, context.outputPrefix, 0))
    val visitedDirectories = HashSet.empty[Path]
    policy.canonicalContained(start).foreach(visitedDirectories += _)
    val results            = ArrayBuffer.empty[PathCompletion]
    val depthLimit         = math.max(0, options.maxDepth)
    val visitedLimit       = math.max(0, options.maxVisitedEntries)
    val resultLimit        = math.max(0, options.maxResults)
    var visitedEntries     = 0

    while !handle.cancelled && pending.nonEmpty && visitedEntries < visitedLimit &&
      results.length < resultLimit
    do
      val current = pending.removeHead()
      val entries = directoryEntries(
        current.path,
        visitedLimit - visitedEntries,
        handle
      )
      visitedEntries += entries.scanned
      var index   = 0
      while !handle.cancelled && index < entries.paths.length && results.length < resultLimit do
        val entry = entries.paths(index)
        index += 1
        policy.canonicalContained(entry).foreach { canonical =>
          if !handle.cancelled && policy.allowedEntry(entry) then
            val directory  = policy.isDirectory(entry)
            val name       = entry.getFileName.toString
            val suffix     = if directory then "/" else ""
            val outputPath = current.outputPrefix + name + suffix
            if name.startsWith(context.entryPrefix) ||
              outputPath.stripSuffix("/").startsWith(rawPrefixForMatch(context))
            then
              results += PathCompletion(outputPath, name + suffix, directory)
            if directory && current.depth < depthLimit &&
              !visitedDirectories.contains(canonical)
            then
              visitedDirectories += canonical
              pending.append(PendingDirectory(
                entry,
                current.outputPrefix + name + "/",
                current.depth + 1
              ))
        }
    results.toVector

  private def directoryEntries(
      directory: Path,
      remaining: Int,
      handle: FileSystemCompletionHandle
  ): ScannedEntries =
    val entries = ArrayBuffer.empty[Path]
    var stream  = Option.empty[java.nio.file.DirectoryStream[Path]]
    var scanned = 0
    try
      val opened   = Files.newDirectoryStream(directory)
      stream = Some(opened)
      val iterator = opened.iterator()
      while !handle.cancelled && scanned < remaining && iterator.hasNext do
        entries += iterator.next()
        scanned += 1
      ScannedEntries(entries.toVector.sortBy(policy.sortKey), scanned)
    catch
      case _: (DirectoryIteratorException | IOException | SecurityException) =>
        ScannedEntries(Vector.empty, scanned)
    finally FileSystemCompletionDispatch.close(stream)

  private def rawPrefixForMatch(context: FileSystemCompletionContext): String =
    context.outputPrefix + context.entryPrefix

  private final case class PendingDirectory(path: Path, outputPrefix: String, depth: Int)
  private final case class ScannedEntries(paths: Vector[Path], scanned: Int)

private[autocomplete] final class FileSystemCompletionPolicy(
    options: FileSystemPathCompletionOptions
):
  private val configuredCurrent = options.currentDirectory.getOrElse(options.baseDirectory)
  private val configuredRoots   =
    if options.containmentRoots.nonEmpty then options.containmentRoots
    else Vector(options.baseDirectory)
  private val canonicalRoots    = configuredRoots.flatMap(canonicalPath).distinct
  private val canonicalCurrent  = canonicalPath(configuredCurrent.toPath)
    .filter(path => canonicalRoots.exists(path.startsWith))

  def resolveDirectory(directoryPrefix: String): Option[Path] =
    val raw        = if directoryPrefix.isEmpty then "." else directoryPrefix
    val homeSyntax = raw === "~" || raw.startsWith("~/")
    val rawFile    = File(raw)
    if homeSyntax && !options.allowHomeExpansion then None
    else if rawFile.isAbsolute && !options.allowAbsolutePaths then None
    else if !homeSyntax && !rawFile.isAbsolute && hasParentSegment(raw) &&
      !options.allowParentTraversal
    then None
    else
      expandHome(raw).flatMap { expanded =>
        val file      = File(expanded)
        val candidate =
          if file.isAbsolute || homeSyntax then file.toPath
          else canonicalCurrent.map(_.resolve(expanded)).orNull
        Option(candidate).flatMap(canonicalContained)
      }

  def canonicalContained(path: Path): Option[Path] =
    canonicalPath(path).filter(candidate => canonicalRoots.exists(candidate.startsWith))

  def allowedCanonicalEntry(entry: Path): Boolean =
    canonicalContained(entry).nonEmpty && allowedEntry(entry)

  def allowedEntry(entry: Path): Boolean =
    val name = entry.getFileName.toString
    !hasUnsafeName(name) &&
    (if name === ".git" then options.includeGitEntries
     else options.includeHidden || !isHidden(entry)) &&
    (options.includeGitEntries || !isInsideGitDirectory(entry))

  def isListable(path: Path): Boolean =
    try Files.isDirectory(path) && Files.isReadable(path)
    catch case _: (IOException | SecurityException) => false

  def isDirectory(path: Path): Boolean =
    try Files.isDirectory(path)
    catch case _: SecurityException => false

  def isInsideGitDirectory(path: Path): Boolean =
    !options.includeGitEntries && (
      hasPathSegment(path, ".git") || canonicalPath(path).exists(hasPathSegment(_, ".git"))
    )

  def sortKey(entry: Path): (Int, String, String) =
    val name = entry.getFileName.toString
    val kind = if options.directoriesFirst && isDirectory(entry) then 0 else 1
    (kind, TextCase.lowercase(name), name)

  def completion(entry: Path, outputPrefix: String): PathCompletion =
    val directory = isDirectory(entry)
    val suffix    = if directory then "/" else ""
    val name      = entry.getFileName.toString + suffix
    PathCompletion(outputPrefix + name, name, directory)

  private def canonicalPath(file: File): Option[Path] = canonicalPath(file.toPath)

  private def canonicalPath(path: Path): Option[Path] =
    try Some(path.toFile.getCanonicalFile.toPath.normalize())
    catch case _: (IOException | SecurityException) => None

  private def expandHome(path: String): Option[String] =
    if path === "~" then Option(System.getProperty("user.home"))
    else if path.startsWith("~/") then
      Option(System.getProperty("user.home")).map(_ + path.drop(1))
    else Some(path)

  private def hasParentSegment(path: String): Boolean =
    path.split('/').exists(_ === "..")

  private def hasUnsafeName(name: String): Boolean =
    name.exists(ch => ch.isControl || ch === 0x1b.toChar)

  private def isHidden(entry: Path): Boolean =
    try entry.getFileName.toString.startsWith(".") || Files.isHidden(entry)
    catch case _: (IOException | SecurityException) => true

  private def hasPathSegment(path: Path, name: String): Boolean =
    val iterator = path.iterator()
    var found    = false
    while !found && iterator.hasNext do found = iterator.next().toString === name
    found

private[autocomplete] final class FileSystemCompletionHandle extends AutocompleteRequestHandle:
  private val cancelledValue  = AtomicBoolean(false)
  override def cancel(): Unit = cancelledValue.set(true)
  def cancelled: Boolean      = cancelledValue.get()

private[autocomplete] object FileSystemCompletionDispatch:
  def request(
      dispatch: Runnable => Unit,
      callback: PathCompletionProvider.Callback
  )(
      work: FileSystemCompletionHandle => Vector[PathCompletion]
  ): AutocompleteRequestHandle =
    val handle = FileSystemCompletionHandle()
    try
      dispatch { () =>
        try
          if !handle.cancelled then
            val result = work(handle)
            if !handle.cancelled then callback.complete(result)
        catch case e: Throwable => if !handle.cancelled then callback.fail(e)
      }
    catch case e: Throwable => if !handle.cancelled then callback.fail(e)
    handle

  def close(stream: Option[java.nio.file.DirectoryStream[Path]]): Unit =
    stream.foreach { opened =>
      try opened.close()
      catch case _: IOException => ()
    }

private[autocomplete] final case class FileSystemCompletionContext(
    directoryPrefix: String,
    entryPrefix: String,
    outputPrefix: String
)

private[autocomplete] object FileSystemCompletionContext:
  def apply(rawPrefix: String): FileSystemCompletionContext =
    val slashIndex = rawPrefix.lastIndexOf('/')
    if rawPrefix.endsWith("/") then FileSystemCompletionContext(rawPrefix, "", rawPrefix)
    else if slashIndex >= 0 then
      val directoryPrefix = rawPrefix.take(slashIndex + 1)
      FileSystemCompletionContext(directoryPrefix, rawPrefix.drop(slashIndex + 1), directoryPrefix)
    else FileSystemCompletionContext("", rawPrefix, "")
